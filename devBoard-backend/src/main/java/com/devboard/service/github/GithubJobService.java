package com.devboard.service.github;

import com.devboard.entity.*;
import com.devboard.entity.enums.*;
import com.devboard.event.*;
import com.devboard.exception.*;
import com.devboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class GithubJobService {
    private final BoardRepository boards;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final BoardLabelMappingRepository mappings;
    private final GithubRepositoryClient client;
    private final GithubEventProcessor processor;
    private final GithubLoopService loops;
    private final ApplicationEventPublisher events;
    @Value("${github.webhook-url:http://localhost:8080/webhook/github}") private String webhookUrl;
    @Value("${github.webhook-secret:}") private String webhookSecret;

    @Transactional
    public void execute(GithubJobEvent job) {
        User actor = users.findById(job.userId()).orElseThrow(() -> new UnauthorizedException("Usuário GitHub indisponível"));
        String token = actor.getGithubToken();
        if (token == null) throw new UnauthorizedException("GitHub não conectado");
        if (job.operation().equals("REMOVE")) {
            client.removeHook(token, job.repositoryId(), job.hookId(), webhookUrl); return;
        }
        Board b = boards.findLockedById(job.boardId()).orElse(null);
        if (b == null || !Objects.equals(b.getGithubRepoId(), job.repositoryId()) || Boolean.TRUE.equals(b.getProject().getArchived())) return;
        if (job.operation().equals("LINK")) {
            if (webhookSecret.isBlank()) throw new InvalidRequestException("Segredo do webhook não configurado");
            b.setGithubHookId(client.registerHook(token, job.repositoryId(), webhookUrl, webhookSecret));
        }
        if (job.operation().equals("LINK") || job.operation().equals("SYNC")) {
            for (var issue : client.issues(token, job.repositoryId())) processor.issue(b, issue, "sync");
            client.branches(token, job.repositoryId());
            b.setLastSyncAt(LocalDateTime.now()); b.setGithubReauthRequired(false); return;
        }
        Task t = tasks.findLockedById(job.taskId()).orElse(null);
        if (t == null || !t.getColumn().getBoard().getId().equals(b.getId()) || Boolean.TRUE.equals(t.getArchived())) return;
        switch (job.operation()) {
            case "ISSUE_STATE" -> {
                if (t.getGithubIssueId() == null || !b.isCloseIssueOnDone()) return;
                boolean closed = "closed".equals(job.value());
                // Discard stale jobs after a subsequent local move.
                if ((t.getColumn().getRole() == ColumnRole.DONE) != closed) return;
                loops.mark(b.getGithubRepoId(), t.getGithubIssueId(), closed ? "closed" : "reopened");
                client.issueState(token, b.getGithubRepoId(), t.getGithubIssueNumber(), closed);
            }
            case "BRANCH" -> {
                if (t.getBranch() != null) return;
                String[] values = job.value().split("\\n", 2);
                client.branch(token, b.getGithubRepoId(), values[0], values[1]); t.setBranch(values[1]);
                events.publishEvent(new TaskActivityEvent(t, job.userId(), TaskActivityType.BRANCH_CREATED, "Branch criada: " + values[1]));
            }
            case "CREATE_ISSUE" -> {
                if (t.getGithubIssueId() != null) return;
                var issue = client.createIssue(token, b.getGithubRepoId(), t.getTitle(), t.getDescription());
                t.setGithubIssueId(issue.path("id").asLong()); t.setGithubIssueNumber(issue.path("number").asInt()); t.setGithubIssueUrl(issue.path("html_url").asText());
                events.publishEvent(new TaskActivityEvent(t, job.userId(), TaskActivityType.GITHUB_ISSUE_LINKED, "Issue criada no GitHub"));
                syncLabels(token, b, t);
            }
            case "LABELS" -> syncLabels(token, b, t);
            default -> throw new InvalidRequestException("Operação GitHub desconhecida");
        }
    }
    private void syncLabels(String token, Board b, Task t) {
        if (t.getGithubIssueId() == null) return;
        Map<String,String> colors = new LinkedHashMap<>();
        t.getLabels().forEach(l -> colors.put(l.getName(), l.getColor()));
        // GitHub emits one event per label addition/removal; suppress only matching repository/issue events.
        var remote = client.issueById(token, b.getGithubRepoId(), t.getGithubIssueId());
        Set<String> oldNames = new HashSet<>(); remote.path("labels").forEach(l -> oldNames.add(l.path("name").asText()));
        for (String name : colors.keySet()) if (!oldNames.contains(name)) loops.mark(b.getGithubRepoId(), t.getGithubIssueId(), "labeled");
        for (String name : oldNames) if (!colors.containsKey(name)) loops.mark(b.getGithubRepoId(), t.getGithubIssueId(), "unlabeled");
        for (var remoteLabel : client.labels(token, b.getGithubRepoId(), t.getGithubIssueNumber(), colors)) {
            Label l = t.getLabels().stream().filter(label -> label.getName().equals(remoteLabel.name())).findFirst().orElseThrow();
            var mapping = mappings.findByLabelIdAndBoardId(l.getId(), b.getId()).orElseGet(BoardLabelMapping::new);
            mapping.setLabel(l); mapping.setBoard(b); mapping.setGithubLabelId(remoteLabel.id()); mapping.setGithubLabelName(remoteLabel.name()); mappings.save(mapping);
        }
    }
    @Transactional
    public void requireReauthentication(Long boardId, Long repositoryId) {
        boards.findById(boardId).filter(b -> Objects.equals(b.getGithubRepoId(), repositoryId)).ifPresent(b -> b.setGithubReauthRequired(true));
    }
}
