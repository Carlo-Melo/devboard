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
        List<GithubRepositoryClient.RepositoryData> repositories = client.repositories(user.getGithubToken());
        Set<Long> linked = repositories.isEmpty() ? Set.of() : new HashSet<>(boards.findLinkedGithubRepoIds(repositories.stream().map(GithubRepositoryClient.RepositoryData::id).toList()));
        return repositories.stream()
                .filter(r -> r.fullName().toLowerCase(Locale.ROOT).contains(query))
                .map(r -> mapper.repository(r, linked.contains(r.id()))).toList();
    }
    @Transactional(readOnly = true)
    public com.devboard.dto.common.PageResponse<GithubRepoResponse> publicRepositories(Long userId, String search, int page, int size) {
        User user = user(userId);
        if (user.getGithubToken() == null) throw new org.springframework.security.access.AccessDeniedException("GitHub não conectado");
        String login = client.authenticatedLogin(user.getGithubToken());
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<GithubRepositoryClient.RepositoryData> personal = client.repositories(user.getGithubToken()).stream()
                .filter(r -> !r.isPrivate() && r.owner().equalsIgnoreCase(login))
                .filter(r -> r.fullName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(GithubRepositoryClient.RepositoryData::fullName, String.CASE_INSENSITIVE_ORDER)).toList();
        return pageRepositories(personal, page, size);
    }
    @Transactional(readOnly = true)
    public com.devboard.dto.common.PageResponse<GithubRepoResponse> availableRepositories(Long userId, String search, int page, int size) {
        User user = user(userId);
        if (user.getGithubToken() == null) throw new org.springframework.security.access.AccessDeniedException("GitHub não conectado");
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<GithubRepositoryClient.RepositoryData> available = client.repositories(user.getGithubToken()).stream()
                .filter(r -> r.fullName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(GithubRepositoryClient.RepositoryData::fullName, String.CASE_INSENSITIVE_ORDER)).toList();
        return pageRepositories(available, page, size);
    }
    private com.devboard.dto.common.PageResponse<GithubRepoResponse> pageRepositories(
            List<GithubRepositoryClient.RepositoryData> repositories, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int start = Math.min(repositories.size(), safePage * safeSize);
        int end = Math.min(repositories.size(), start + safeSize);
        List<GithubRepositoryClient.RepositoryData> slice = repositories.subList(start, end);
        Set<Long> linked = slice.isEmpty() ? Set.of() : new HashSet<>(boards.findLinkedGithubRepoIds(slice.stream().map(GithubRepositoryClient.RepositoryData::id).toList()));
        List<GithubRepoResponse> content = slice.stream().map(r -> mapper.repository(r, linked.contains(r.id()))).toList();
        int pages = (repositories.size() + safeSize - 1) / safeSize;
        return com.devboard.dto.common.PageResponse.<GithubRepoResponse>builder().content(content).page(safePage).size(safeSize)
                .totalElements(repositories.size()).totalPages(pages).first(safePage == 0).last(safePage + 1 >= pages).build();
    }
    @Transactional(readOnly = true)
    public com.devboard.dto.common.PageResponse<com.devboard.dto.board.GithubDestinationResponse> destinations(Long userId, int page, int size) {
        org.springframework.data.domain.Page<Board> result = boards.findEligibleGithubDestinations(userId,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
        return com.devboard.dto.common.PageResponse.from(result, mapper::destination);
    }
    @Transactional
    public GithubSettingsResponse link(Long boardId, LinkGithubRequest request, Long userId) {
        Board b = board(boardId, userId, ProjectRole.ADMIN);
        if (b.hasGithubRepo()) throw new ConflictException("Board já possui repositório vinculado");
        if (boards.existsByGithubRepoIdAndArchivedFalse(request.getGithubRepoId())) throw new ConflictException("Repositório já vinculado a outro board");
        User user = user(userId);
        if (user.getGithubToken() == null) throw new AccessDeniedException("GitHub não conectado");
        var repo = client.repository(user.getGithubToken(), request.getGithubRepoId());
        if (!repo.writable()) throw new AccessDeniedException("Acesso de escrita ao repositório é necessário");
        b.setGithubRepoId(repo.id()); b.setGithubRepoOwner(repo.owner()); b.setGithubRepoName(repo.name()); b.setGithubRepoUrl(repo.url());
        b.setGithubUser(user); b.setGithubReauthRequired(false);
        b.setDefaultBaseBranch(request.getDefaultBaseBranch() == null || request.getDefaultBaseBranch().isBlank() ? repo.defaultBranch() : request.getDefaultBaseBranch());
        b.setWatchedBranches(new ArrayList<>(request.getWatchedBranches() == null ? List.of() : request.getWatchedBranches()));
        b.setMoveOnCommit(true); b.setMoveOnPrOpen(true); b.setMoveOnPrMerge(true); b.setImportIssues(true); b.setCloseIssueOnDone(true);
        b.setGithubGeneration(b.getGithubGeneration() + 1);
        boards.saveAndFlush(b); // database constraint also arbitrates links racing across different boards
        events.publishEvent(new GithubJobEvent("LINK", b.getId(), repo.id(), userId, null, null, null, b.getGithubGeneration()));
        return mapper.settings(b);
    }
    @Transactional
    public void unlink(Long boardId, Long userId) {
        Board b = board(boardId, userId, ProjectRole.ADMIN);
        if (!b.hasGithubRepo()) return;
        queueRemoval(b);
        b.setGithubGeneration(b.getGithubGeneration() + 1);
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
        events.publishEvent(new GithubJobEvent("SYNC", boardId, b.getGithubRepoId(), userId, b.getGithubHookId(), null, null, b.getGithubGeneration()));
    }
    @Transactional(readOnly = true)
    public GithubSettingsResponse settings(Long boardId, Long userId) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
        permissions.requireRole(b.getProject().getId(), userId, ProjectRole.VIEWER);
        // Archived settings expose metadata for the UI's recovery link; writes remain blocked.
        if (!Boolean.TRUE.equals(b.getArchived())) permissions.requireActiveBoard(b);
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
    public void removeProjectHooks(Long projectId) { boards.findByProjectIdAndArchivedFalse(projectId).forEach(this::queueRemoval); }
    @Transactional
    public void removeBoardHook(Long boardId) { boards.findById(boardId).ifPresent(this::queueRemoval); }

    /** Internal lifecycle operations: caller has already authorized ADMIN and locked the board. */
    @Transactional
    public void suspendBoard(Long boardId) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
        queueRemoval(b);
        b.setGithubHookId(null);
        b.setGithubGeneration(b.getGithubGeneration() + 1);
        // Keep repository metadata, issue/PR references, branches, mappings and historical tasks.
    }

    @Transactional
    public void resumeBoard(Long boardId, Long userId) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
        if (b.hasGithubRepo()) events.publishEvent(new GithubJobEvent("LINK", boardId, b.getGithubRepoId(),
                b.getGithubUser() == null ? userId : b.getGithubUser().getId(), null, null, null, b.getGithubGeneration()));
    }
    private void queueRemoval(Board b) {
        if (b.hasGithubRepo()) events.publishEvent(new GithubJobEvent("REMOVE", b.getId(), b.getGithubRepoId(),
                b.getGithubUser() == null ? b.getProject().getOwner().getId() : b.getGithubUser().getId(), b.getGithubHookId(), null, null, b.getGithubGeneration()));
    }
    private Board board(Long id, Long userId, ProjectRole role) {
        Board b = boards.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
        permissions.requireRole(b.getProject().getId(), userId, role);
        permissions.requireActiveBoard(b);
        if (Boolean.TRUE.equals(b.getProject().getArchived())) throw new ConflictException("Projeto arquivado");
        return b;
    }
    private User user(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado")); }
}
