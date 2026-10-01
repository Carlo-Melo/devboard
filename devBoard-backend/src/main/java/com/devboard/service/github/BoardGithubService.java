package com.devboard.service.github;

import com.devboard.dto.board.*;
import com.devboard.entity.*;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.event.GithubJobEvent;
import com.devboard.exception.*;
import com.devboard.mapper.GithubMapper;
import com.devboard.repository.*;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class BoardGithubService {
    private final BoardRepository boards;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final BoardLabelMappingRepository mappings;
    private final PermissionService permissions;
    private final GithubRepositoryClient client;
    private final GithubMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<GithubRepoResponse> repositories(Long userId, String search) {
        User user = user(userId);
        if (user.getGithubToken() == null) throw new AccessDeniedException("GitHub não conectado");
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT);
        return client.repositories(user.getGithubToken()).stream()
                .filter(r -> r.fullName().toLowerCase(Locale.ROOT).contains(query))
                .map(r -> mapper.repository(r, boards.existsByGithubRepoId(r.id()))).toList();
    }
    @Transactional
    public GithubSettingsResponse link(Long boardId, LinkGithubRequest request, Long userId) {
        Board b = board(boardId, userId, ProjectRole.ADMIN);
        if (b.hasGithubRepo()) throw new ConflictException("Board já possui repositório vinculado");
        if (boards.existsByGithubRepoId(request.getGithubRepoId())) throw new ConflictException("Repositório já vinculado a outro board");
        User user = user(userId);
        if (user.getGithubToken() == null) throw new AccessDeniedException("GitHub não conectado");
        var repo = client.repository(user.getGithubToken(), request.getGithubRepoId());
        if (!repo.writable()) throw new AccessDeniedException("Acesso de escrita ao repositório é necessário");
        b.setGithubRepoId(repo.id()); b.setGithubRepoOwner(repo.owner()); b.setGithubRepoName(repo.name()); b.setGithubRepoUrl(repo.url());
        b.setGithubUser(user); b.setGithubReauthRequired(false);
        b.setDefaultBaseBranch(request.getDefaultBaseBranch() == null || request.getDefaultBaseBranch().isBlank() ? repo.defaultBranch() : request.getDefaultBaseBranch());
        b.setWatchedBranches(new ArrayList<>(request.getWatchedBranches() == null ? List.of() : request.getWatchedBranches()));
        b.setMoveOnCommit(true); b.setMoveOnPrOpen(true); b.setMoveOnPrMerge(true); b.setImportIssues(true); b.setCloseIssueOnDone(true);
        boards.saveAndFlush(b); // database constraint also arbitrates links racing across different boards
        events.publishEvent(new GithubJobEvent("LINK", b.getId(), repo.id(), userId, null, null, null));
        return mapper.settings(b);
    }
    @Transactional
    public void unlink(Long boardId, Long userId) {
        Board b = board(boardId, userId, ProjectRole.ADMIN);
        if (!b.hasGithubRepo()) return;
        queueRemoval(b);
        for (Task t : tasks.findByColumnBoardId(boardId)) clearTask(t);
        mappings.deleteByBoardId(boardId);
        b.setGithubRepoId(null); b.setGithubRepoOwner(null); b.setGithubRepoName(null); b.setGithubRepoUrl(null);
        b.setGithubUser(null); b.setGithubHookId(null); b.setGithubReauthRequired(false);
        b.getWatchedBranches().clear(); b.setDefaultBaseBranch("main"); b.setLastSyncAt(null);
    }
    public static void clearTask(Task t) {
        t.setGithubIssueId(null); t.setGithubIssueNumber(null); t.setGithubIssueUrl(null);
        t.setGithubPrId(null); t.setGithubPrUrl(null); t.setGithubPrState(null); t.setBranch(null);
    }
    @Transactional
    public void sync(Long boardId, Long userId) {
        Board b = board(boardId, userId, ProjectRole.DEVELOPER);
        if (!b.hasGithubRepo()) throw new InvalidRequestException("Board sem repositório vinculado");
        events.publishEvent(new GithubJobEvent("SYNC", boardId, b.getGithubRepoId(), userId, b.getGithubHookId(), null, null));
    }
    @Transactional(readOnly = true)
    public GithubSettingsResponse settings(Long boardId, Long userId) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
        permissions.requireRole(b.getProject().getId(), userId, ProjectRole.VIEWER);
        return mapper.settings(b);
    }
    @Transactional
    public GithubSettingsResponse update(Long boardId, GithubSettingsRequest r, Long userId) {
        Board b = board(boardId, userId, ProjectRole.ADMIN);
        if (!r.getBranchPattern().contains("{id}")) throw new InvalidRequestException("Padrão de branch deve conter {id}");
        b.setMoveOnCommit(r.isMoveOnCommit()); b.setMoveOnPrOpen(r.isMoveOnPrOpen()); b.setMoveOnPrMerge(r.isMoveOnPrMerge());
        b.setImportIssues(r.isImportIssues()); b.setCloseIssueOnDone(r.isCloseIssueOnDone()); b.setBranchPattern(r.getBranchPattern());
        b.setDefaultBaseBranch(r.getDefaultBaseBranch());
        b.setWatchedBranches(new ArrayList<>(b.hasGithubRepo() && r.getWatchedBranches() != null ? r.getWatchedBranches() : List.of()));
        return mapper.settings(b);
    }
    @Transactional
    public void removeProjectHooks(Long projectId) { boards.findByProjectId(projectId).forEach(this::queueRemoval); }
    @Transactional
    public void removeBoardHook(Long boardId) { boards.findById(boardId).ifPresent(this::queueRemoval); }
    private void queueRemoval(Board b) {
        if (b.hasGithubRepo()) events.publishEvent(new GithubJobEvent("REMOVE", b.getId(), b.getGithubRepoId(),
                b.getGithubUser() == null ? b.getProject().getOwner().getId() : b.getGithubUser().getId(), b.getGithubHookId(), null, null));
    }
    private Board board(Long id, Long userId, ProjectRole role) {
        Board b = boards.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
        permissions.requireRole(b.getProject().getId(), userId, role);
        if (Boolean.TRUE.equals(b.getProject().getArchived())) throw new ConflictException("Projeto arquivado");
        return b;
    }
    private User user(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado")); }
}
