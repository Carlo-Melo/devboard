package com.devboard.service.github;

import com.devboard.entity.*;
import com.devboard.entity.enums.*;
import com.devboard.event.*;
import com.devboard.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** All lookups start with the repository's board; task IDs alone never authorize an event. */
@Service @RequiredArgsConstructor @Slf4j
public class GithubEventProcessor {
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProjectMemberRepository members;
    private final LabelRepository labels;
    private final BoardLabelMappingRepository mappings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private static final Pattern REFERENCE = Pattern.compile("#(\\d+)");
    private static final Pattern BRANCH = Pattern.compile("(?:^|/)task-(\\d+)(?:-|$)");

    @Transactional
    public void process(String deliveryId) throws Exception {
        var rows = jdbc.queryForList("SELECT * FROM github_deliveries WHERE id = ? FOR UPDATE", deliveryId);
        if (rows.isEmpty() || !"PENDING".equals(rows.get(0).get("status"))) return;
        var row = rows.get(0);
        Long boardId = ((Number) row.get("board_id")).longValue();
        Long repoId = ((Number) row.get("repository_id")).longValue();
        Board b = boards.findLockedById(boardId).orElse(null);
        if (b != null && !Boolean.TRUE.equals(b.getArchived()) && b.getGithubGeneration() == ((Number) row.get("board_generation")).longValue()
                && repoId.equals(b.getGithubRepoId()) && !Boolean.TRUE.equals(b.getProject().getArchived())) {
            JsonNode payload = json.readTree((String) row.get("payload"));
            switch ((String) row.get("event_type")) {
                case "push" -> push(b, payload);
                case "pull_request" -> pullRequest(b, payload);
                case "issues" -> issue(b, payload.path("issue"), payload.path("action").asText());
                default -> { }
            }
        }
        jdbc.update("UPDATE github_deliveries SET status = 'PROCESSED' WHERE id = ?", deliveryId);
    }
    private List<Task> identify(Board b, String branch, String text, long issueId) {
        List<Task> candidates = tasks.findByColumnBoardIdAndArchivedFalse(b.getId());
        if (branch != null && !branch.isBlank()) {
            var registered = candidates.stream().filter(t -> branch.equals(t.getBranch())).findFirst();
            if (registered.isPresent()) return List.of(registered.get());
            var match = BRANCH.matcher(branch);
            if (match.find()) {
                String id = match.group(1);
                var found = candidates.stream().filter(t -> t.getId().toString().equals(id)).findFirst();
                if (found.isPresent()) return List.of(found.get());
            }
        }
        Set<String> ids = new LinkedHashSet<>();
        var matcher = REFERENCE.matcher(text == null ? "" : text);
        while (matcher.find()) ids.add(matcher.group(1));
        List<Task> result = new ArrayList<>();
        for (String id : ids) candidates.stream().filter(t -> t.getId().toString().equals(id)).findFirst().ifPresent(result::add);
        if (!result.isEmpty()) return result;
        return candidates.stream().filter(t -> issueId > 0 && Objects.equals(t.getGithubIssueId(), issueId)).limit(1).toList();
    }
    private void push(Board b, JsonNode p) {
        String branch = p.path("ref").asText().replaceFirst("^refs/heads/", "");
        if (!b.getWatchedBranches().isEmpty() && !b.getWatchedBranches().contains(branch)) return;
        for (JsonNode commit : p.path("commits")) {
            String message = commit.path("message").asText();
            List<Task> found = identify(b, branch, message, 0);
            if (found.isEmpty()) log.info("Commit sem tarefa correspondente: boardId={}", b.getId());
            for (int i=0; i<found.size(); i++) {
                Task t = found.get(i);
                activity(t, TaskActivityType.COMMIT_RECEIVED, "Commit recebido", Map.of("hash", commit.path("id").asText(),
                        "message", message, "author", commit.path("author").path("name").asText(), "url", commit.path("url").asText()));
                if (i != 0 || !b.isMoveOnCommit()) continue;
                boolean closes = Pattern.compile("(?i)\\b(closes|fixes|resolves)\\s+#" + t.getId() + "\\b").matcher(message).find();
                ColumnRole current = t.getColumn().getRole();
                if (closes) move(t, ColumnRole.DONE, false);
                else if (current == ColumnRole.BACKLOG || current == ColumnRole.TODO) move(t, ColumnRole.IN_PROGRESS, false);
            }
        }
    }
    private void pullRequest(Board b, JsonNode p) {
        JsonNode pr = p.path("pull_request");
        String action = p.path("action").asText();
        // Fork PRs target this repository, but cannot borrow another board's registered branch.
        String branch = pr.path("head").path("repo").path("id").asLong() == b.getGithubRepoId()
                ? pr.path("head").path("ref").asText() : null;
        List<Task> found = identify(b, branch, pr.path("title").asText() + " " + pr.path("body").asText(), pr.path("id").asLong());
        if (found.isEmpty()) { log.info("PR sem tarefa correspondente: boardId={}", b.getId()); return; }
        Task t = found.get(0);
        t.setGithubPrId(pr.path("id").asLong()); t.setGithubPrUrl(pr.path("html_url").asText());
        switch (action) {
            case "opened", "reopened" -> {
                t.setGithubPrState(PullRequestState.OPEN);
                activity(t, TaskActivityType.PR_OPENED, "Pull request aberto", Map.of("url", pr.path("html_url").asText()));
                if (b.isMoveOnPrOpen()) move(t, ColumnRole.IN_REVIEW, "reopened".equals(action));
            }
            case "closed" -> {
                boolean merged = pr.path("merged").asBoolean();
                t.setGithubPrState(merged ? PullRequestState.MERGED : PullRequestState.CLOSED);
                activity(t, merged ? TaskActivityType.PR_MERGED : TaskActivityType.PR_CLOSED,
                        merged ? "Pull request integrado" : "Pull request fechado sem merge", Map.of("mergedBy", pr.path("merged_by").path("login").asText()));
                if (merged && b.isMoveOnPrMerge()) move(t, ColumnRole.DONE, false);
            }
            case "synchronize" -> activity(t, TaskActivityType.COMMIT_RECEIVED, "Pull request atualizado", Map.of("hash", pr.path("head").path("sha").asText()));
            default -> { }
        }
    }
    /** Used by both authenticated webhooks and the repository-scoped import job. */
    public void issue(Board b, JsonNode issue, String action) {
        if (Boolean.TRUE.equals(b.getArchived())) return;
        if (issue.has("pull_request")) return;
        long issueId = issue.path("id").asLong();
        if (issueId <= 0) return;
        if (Set.of("closed", "reopened", "labeled", "unlabeled").contains(action)) {
            int consumed = jdbc.update("DELETE FROM github_outgoing_operations WHERE id = (SELECT id FROM github_outgoing_operations WHERE repository_id = ? AND issue_id = ? AND action = ? AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour' ORDER BY id LIMIT 1)", b.getGithubRepoId(), issueId, action);
            if (consumed > 0) return;
        }
        Task t = tasks.findByColumnBoardIdAndGithubIssueId(b.getId(), issueId).orElse(null);
        if (t == null) {
            if (!b.isImportIssues() || !Set.of("opened", "sync").contains(action)) return;
            BoardColumn backlog = columns.findByBoardIdOrderByPositionAsc(b.getId()).stream().filter(c -> c.getRole() == ColumnRole.BACKLOG).findFirst().orElse(null);
            if (backlog == null) { log.info("Importação ignorada: boardId={} sem BACKLOG", b.getId()); return; }
            t = new Task(); t.setColumn(backlog); t.setCreator(b.getProject().getOwner()); t.setType(TaskType.DEV);
            t.setPosition((int) tasks.countByColumnIdAndArchivedFalse(backlog.getId()));
            t.setGithubIssueId(issueId); t.setGithubIssueNumber(issue.path("number").asInt()); t.setGithubIssueUrl(issue.path("html_url").asText());
            t.setTitle(issue.path("title").asText()); t.setDescription(issue.path("body").asText(null));
            tasks.save(t); activity(t, TaskActivityType.CREATED, "Tarefa importada do GitHub", Map.of());
        }
        if (Boolean.TRUE.equals(t.getArchived())) return;
        if ((action.equals("edited") || action.equals("sync")) && !t.isGithubManuallyEdited()) {
            t.setTitle(issue.path("title").asText()); t.setDescription(issue.path("body").asText(null));
        }
        if (Set.of("opened", "labeled", "unlabeled", "sync").contains(action)) importLabels(b, t, issue.path("labels"));
        if (Set.of("opened", "assigned", "unassigned", "sync").contains(action)) {
            User assignee = users.findByGithubId(issue.path("assignee").path("id").asLong()).orElse(null);
            if (assignee != null && (b.getProject().getOwner().getId().equals(assignee.getId()) || members.existsByProjectIdAndUserId(b.getProject().getId(), assignee.getId()))) t.setAssignee(assignee);
            else if (action.equals("unassigned")) t.setAssignee(null);
        }
        if (action.equals("closed") || (action.equals("sync") && issue.path("state").asText().equals("closed"))) move(t, ColumnRole.DONE, false, false);
        if (action.equals("reopened") && t.getColumn().getRole() == ColumnRole.DONE) move(t, ColumnRole.TODO, true, false);
    }
    private void importLabels(Board b, Task t, JsonNode source) {
        Set<Label> imported = new HashSet<>();
        for (JsonNode remote : source) {
            String name = remote.path("name").asText();
            if (name.isBlank() || name.length() > 50) continue;
            Label l = labels.findByProjectIdAndNameIgnoreCase(b.getProject().getId(), name).orElseGet(() -> {
                Label created = new Label(); created.setProject(b.getProject()); created.setName(name);
                String color = remote.path("color").asText(); created.setColor(color.matches("[a-fA-F0-9]{6}") ? "#"+color : "#6B7280");
                return labels.save(created);
            });
            BoardLabelMapping mapping = mappings.findByLabelIdAndBoardId(l.getId(), b.getId()).orElseGet(BoardLabelMapping::new);
            mapping.setLabel(l); mapping.setBoard(b); mapping.setGithubLabelId(remote.path("id").asLong()); mapping.setGithubLabelName(name);
            mappings.save(mapping); imported.add(l);
        }
        t.setLabels(imported);
    }
    private void move(Task task, ColumnRole role, boolean regress) { move(task, role, regress, true); }
    private void move(Task task, ColumnRole role, boolean regress, boolean syncIssue) {
        Board b = task.getColumn().getBoard();
        BoardColumn target = columns.findByBoardIdOrderByPositionAsc(b.getId()).stream().filter(c -> c.getRole() == role).findFirst().orElse(null);
        BoardColumn origin = task.getColumn();
        if (target == null) { log.info("Automação ignorada: boardId={}, papel ausente={}", b.getId(), role); return; }
        if (target.getId().equals(origin.getId()) || (!regress && target.getPosition() < origin.getPosition())) return;
        List<Task> source = tasks.findByColumnIdAndArchivedFalseOrderByPositionAsc(origin.getId());
        source.removeIf(t -> t.getId().equals(task.getId()));
        for (int i=0;i<source.size();i++) source.get(i).setPosition(i);
        int count = (int) tasks.countByColumnIdAndArchivedFalse(target.getId());
        task.setColumn(target); task.setPosition(count); task.setCompletedAt(role == ColumnRole.DONE ? LocalDateTime.now() : null);
        activity(task, TaskActivityType.MOVED, "Movida pelo GitHub para " + target.getName(), Map.of("fromColumnId", origin.getId(), "toColumnId", target.getId()));
        if (target.getWipLimit() != null && count >= target.getWipLimit()) activity(task, TaskActivityType.WIP_EXCEEDED, "Automação excedeu o limite WIP", Map.of());
        if (syncIssue && task.getGithubIssueId() != null && b.isCloseIssueOnDone() && role == ColumnRole.DONE)
            events.publishEvent(new GithubJobEvent("ISSUE_STATE", b.getId(), b.getGithubRepoId(), b.getGithubUser() == null ? b.getProject().getOwner().getId() : b.getGithubUser().getId(), null, task.getId(), "closed", b.getGithubGeneration()));
        log.info("Automação aplicada: boardId={}, taskId={}, papel={}", b.getId(), task.getId(), role);
    }
    private void activity(Task t, TaskActivityType type, String description, Map<String,Object> metadata) {
        events.publishEvent(new TaskActivityEvent(t, null, type, description, metadata));
    }
}
