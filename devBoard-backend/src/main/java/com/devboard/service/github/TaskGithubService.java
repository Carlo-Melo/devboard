package com.devboard.service.github;
import com.devboard.dto.task.CreateBranchRequest;
import com.devboard.entity.*;
import com.devboard.entity.enums.*;
import com.devboard.event.*;
import com.devboard.exception.*;
import com.devboard.repository.*;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class TaskGithubService {
    private final TaskRepository tasks;
    private final UserRepository users;
    private final PermissionService permissions;
    private final GithubRepositoryClient client;
    private final ApplicationEventPublisher events;
    @Transactional public void branch(Long id, CreateBranchRequest r, Long userId) {
        Task t = task(id); permissions.requireRole(t.resolveProjectId(), userId, ProjectRole.DEVELOPER);
        Board b = linked(t);
        if (!t.getType().supportsBranch()) throw new InvalidRequestException("Tipo de tarefa não suporta branch");
        if (t.getBranch() != null) throw new ConflictException("Tarefa já possui branch");
        String title = Normalizer.normalize(t.getTitle(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        title = title.substring(0, Math.min(50, title.length())).replaceAll("-$", "");
        String name = r.name() == null || r.name().isBlank() ? b.getBranchPattern().replace("{id}", id.toString()).replace("{title}", title) : r.name();
        String base = r.baseBranch() == null || r.baseBranch().isBlank() ? b.getDefaultBaseBranch() : r.baseBranch();
        if (!name.matches("[A-Za-z0-9_/-]+") || name.contains("//") || name.startsWith("/") || name.endsWith("/") || base.contains("\n")) throw new InvalidRequestException("Nome de branch inválido");
        events.publishEvent(job("BRANCH", b, t, userId, base + "\n" + name));
    }
    @Transactional public void link(Long id, Long issueId, Long userId) {
        Task t = task(id); permissions.requireTaskEditable(t, userId); attachIssue(t, issueId, userId);
    }
    /** Internal helper for creation; permissions are checked by the creating service first. */
    public void attachIssue(Task t, Long issueId, Long userId) {
        Board b = linked(t);
        var existing = tasks.findByColumnBoardIdAndGithubIssueId(b.getId(), issueId);
        if (existing.isPresent() && !existing.get().getId().equals(t.getId())) throw new ConflictException("Issue já vinculada a outra tarefa neste board");
        User u = users.findById(userId).orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
        if (u.getGithubToken() == null) throw new UnauthorizedException("GitHub não conectado");
        var issue = client.issueById(u.getGithubToken(), b.getGithubRepoId(), issueId);
        t.setGithubIssueId(issueId); t.setGithubIssueNumber(issue.path("number").asInt()); t.setGithubIssueUrl(issue.path("html_url").asText());
        if (t.getDescription() == null || t.getDescription().isBlank()) t.setDescription(issue.path("body").asText(null));
        if (t.getId() != null) events.publishEvent(new TaskActivityEvent(t, userId, TaskActivityType.GITHUB_ISSUE_LINKED, "Issue vinculada"));
    }
    @Transactional public void unlink(Long id, Long userId) {
        Task t = task(id); permissions.requireTaskEditable(t, userId); linked(t);
        t.setGithubIssueId(null); t.setGithubIssueNumber(null); t.setGithubIssueUrl(null);
    }
    @Transactional public void createIssue(Long id, Long userId) {
        Task t = task(id); permissions.requireTaskEditable(t, userId); Board b = linked(t);
        if (t.getGithubIssueId() != null) throw new ConflictException("Tarefa já possui issue");
        events.publishEvent(job("CREATE_ISSUE", b, t, userId, null));
    }
    @Transactional public void queueIssueState(Long id, boolean closed, Long userId) {
        Task t = task(id); Board b = t.getColumn().getBoard();
        if (!b.hasGithubRepo() || t.getGithubIssueId() == null || !b.isCloseIssueOnDone()) return;
        Long githubUserId = b.getGithubUser() == null ? userId : b.getGithubUser().getId();
        events.publishEvent(job("ISSUE_STATE", b, t, githubUserId, closed ? "closed" : "open"));
    }
    private GithubJobEvent job(String op, Board b, Task t, Long userId, String value) { return new GithubJobEvent(op,b.getId(),b.getGithubRepoId(),userId,null,t.getId(),value); }
    private Task task(Long id) { return tasks.findByIdWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada")); }
    private Board linked(Task t) {
        Board b = t.getColumn().getBoard();
        if (!b.hasGithubRepo()) throw new InvalidRequestException("Board sem repositório vinculado");
        if (Boolean.TRUE.equals(b.getProject().getArchived())) throw new ConflictException("Projeto arquivado");
        return b;
    }
}
