package com.devboard.service.github;

import com.devboard.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component @RequiredArgsConstructor
public class GithubRepositoryClient {
    private final ObjectMapper json;
    public record RepositoryData(long id, String owner, String name, String fullName, String description,
                                 String url, String defaultBranch, boolean writable) {}
    public record LabelData(long id, String name) {}
    @FunctionalInterface private interface ApiCall<T> { T run() throws IOException; }

    private <T> T call(ApiCall<T> action) {
        try { return action.run(); }
        catch (HttpException e) {
            if (e.getResponseCode() == 401) throw new UnauthorizedException("Token GitHub inválido");
            if (e.getResponseCode() == 403 || e.getResponseCode() == 429) {
                if (e.getResponseCode() == 429 || (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("rate limit")))
                    throw new RateLimitExceededException("Limite de chamadas do GitHub atingido", 60);
                throw new AccessDeniedException("Sem acesso ao repositório GitHub");
            }
            if (e.getResponseCode() == 404) throw new InvalidRequestException("Recurso não pertence ao repositório ou não está acessível");
            if (e.getResponseCode() == 422) throw new ConflictException("Recurso já existe ou é inválido no GitHub");
            throw new ExternalServiceException("GitHub indisponível");
        } catch (IOException e) { throw new ExternalServiceException("GitHub indisponível"); }
    }
    private GitHub github(String token) throws IOException {
        return new GitHubBuilder().withOAuthToken(token).build();
    }
    private RepositoryData data(GHRepository r) throws IOException {
        return new RepositoryData(r.getId(), r.getOwnerName(), r.getName(), r.getFullName(), r.getDescription(),
                r.getHtmlUrl().toString(), r.getDefaultBranch(), r.hasPushAccess());
    }
    public List<RepositoryData> repositories(String token) {
        return call(() -> {
            List<RepositoryData> result = new ArrayList<>();
            for (GHRepository r : github(token).getMyself().listRepositories()) if (r.hasPushAccess()) result.add(data(r));
            return result;
        });
    }
    public RepositoryData repository(String token, long id) { return call(() -> data(github(token).getRepositoryById(id))); }
    public long registerHook(String token, long id, String url, String secret) {
        return call(() -> {
            GHRepository repo = github(token).getRepositoryById(id);
            for (GHHook hook : repo.getHooks()) if (url.equals(hook.getConfig().get("url"))) return hook.getId();
            return repo.createHook("web", Map.of("url", url, "content_type", "json", "secret", secret),
                    List.of(GHEvent.PUSH, GHEvent.PULL_REQUEST, GHEvent.ISSUES), true).getId();
        });
    }
    public void removeHook(String token, long repoId, Long hookId, String url) {
        call(() -> {
            GHRepository repo = github(token).getRepositoryById(repoId);
            for (GHHook hook : repo.getHooks()) if ((hookId != null && hook.getId() == hookId) || url.equals(hook.getConfig().get("url"))) hook.delete();
            return null;
        });
    }
    public List<JsonNode> issues(String token, long repoId) {
        return call(() -> {
            List<JsonNode> issues = new ArrayList<>();
            for (GHIssue issue : github(token).getRepositoryById(repoId).listIssues(GHIssueState.ALL))
                if (!issue.isPullRequest()) issues.add(issueNode(issue));
            return issues;
        });
    }
    public JsonNode issueById(String token, long repoId, long issueId) {
        return issues(token, repoId).stream().filter(i -> i.path("id").asLong() == issueId).findFirst()
                .orElseThrow(() -> new InvalidRequestException("Issue não pertence ao repositório do board"));
    }
    private JsonNode issueNode(GHIssue issue) throws IOException {
        ObjectNode n = json.createObjectNode();
        n.put("id", issue.getId()).put("number", issue.getNumber()).put("title", issue.getTitle())
                .put("body", issue.getBody()).put("html_url", issue.getHtmlUrl().toString())
                .put("state", issue.getState().name().toLowerCase(Locale.ROOT));
        var labels = n.putArray("labels");
        for (GHLabel l : issue.getLabels()) labels.addObject().put("id", l.getId()).put("name", l.getName()).put("color", l.getColor());
        if (issue.getAssignee() != null) n.putObject("assignee").put("id", issue.getAssignee().getId());
        return n;
    }
    public List<String> branches(String token, long repoId) {
        return call(() -> new ArrayList<>(github(token).getRepositoryById(repoId).getBranches().keySet()));
    }
    public void branch(String token, long repoId, String base, String name) {
        call(() -> {
            GHRepository repo = github(token).getRepositoryById(repoId);
            repo.createRef("refs/heads/" + name, repo.getRef("heads/" + base).getObject().getSha());
            return null;
        });
    }
    public JsonNode createIssue(String token, long repoId, String title, String body) {
        return call(() -> issueNode(github(token).getRepositoryById(repoId).createIssue(title).body(body == null ? "" : body).create()));
    }
    public void issueState(String token, long repoId, int number, boolean closed) {
        call(() -> { GHIssue i = github(token).getRepositoryById(repoId).getIssue(number); if (closed) i.close(); else i.reopen(); return null; });
    }
    public List<LabelData> labels(String token, long repoId, int number, Map<String, String> labels) {
        return call(() -> {
            GHRepository repo = github(token).getRepositoryById(repoId);
            Map<String, GHLabel> existing = new HashMap<>();
            for (GHLabel l : repo.listLabels()) existing.put(l.getName(), l);
            List<LabelData> result = new ArrayList<>();
            for (var entry : labels.entrySet()) {
                GHLabel l = existing.get(entry.getKey());
                if (l == null) l = repo.createLabel(entry.getKey(), entry.getValue().replace("#", ""));
                result.add(new LabelData(l.getId(), l.getName()));
            }
            repo.getIssue(number).setLabels(labels.keySet().toArray(String[]::new));
            return result;
        });
    }
}
