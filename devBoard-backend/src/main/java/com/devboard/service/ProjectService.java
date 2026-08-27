package com.devboard.service;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.project.CreateProjectRequest;
import com.devboard.dto.project.ProjectResponse;
import com.devboard.dto.project.ProjectSummaryResponse;
import com.devboard.dto.project.UpdateProjectRequest;
import com.devboard.entity.Board;
import com.devboard.entity.Project;
import com.devboard.entity.ProjectMember;
import com.devboard.entity.User;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.ProjectMapper;
import com.devboard.repository.BoardRepository;
import com.devboard.repository.ProjectMemberRepository;
import com.devboard.repository.ProjectRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ProjectMapper projectMapper;
    private final BoardService boardService;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (request.getGithubRepoId() != null && owner.getGithubToken() == null) {
            throw new AccessDeniedException("GitHub não conectado");
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setOwner(owner);
        if (request.getDefaultBaseBranch() != null && !request.getDefaultBaseBranch().isBlank()) {
            project.setDefaultBaseBranch(request.getDefaultBaseBranch());
        }

        Project saved = projectRepository.save(project);
        boardService.createDefaultBoard(saved);

        log.info("Projeto criado: id={}, ownerId={}", saved.getId(), userId);

        return toDetailResponse(saved, ProjectRole.ADMIN, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> list(Long userId, boolean archived, int page, int size) {
        int clampedSize = Math.min(size, MAX_PAGE_SIZE) <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampedSize, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Project> projects = projectRepository.findAccessibleByUser(userId, archived, pageRequest);

        return PageResponse.from(projects, project ->
                projectMapper.toSummary(project, projectMemberRepository.countByProjectId(project.getId())));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long projectId, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.VIEWER);

        Project project = findProjectOrThrow(projectId);
        ProjectRole currentUserRole = permissionService.resolveRole(projectId, userId);
        boolean isOwner = permissionService.isOwner(projectId, userId);

        return toDetailResponse(project, currentUserRole, isOwner);
    }

    @Transactional
    public ProjectResponse update(Long projectId, UpdateProjectRequest request, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);

        Project project = findProjectOrThrow(projectId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        if (request.getWatchedBranches() != null) {
            project.setWatchedBranches(request.getWatchedBranches());
        }
        if (request.getDefaultBaseBranch() != null && !request.getDefaultBaseBranch().isBlank()) {
            project.setDefaultBaseBranch(request.getDefaultBaseBranch());
        }

        ProjectRole currentUserRole = permissionService.resolveRole(projectId, userId);
        boolean isOwner = permissionService.isOwner(projectId, userId);
        return toDetailResponse(project, currentUserRole, isOwner);
    }

    @Transactional
    public void archive(Long projectId, Long userId) {
        permissionService.requireOwner(projectId, userId);

        Project project = findProjectOrThrow(projectId);
        project.setArchived(true);
        project.setArchivedAt(LocalDateTime.now());

        log.info("Projeto arquivado: id={}, userId={}", projectId, userId);
    }

    private ProjectResponse toDetailResponse(Project project, ProjectRole currentUserRole, boolean currentUserOwner) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(project.getId());
        List<Board> boards = boardRepository.findByProjectId(project.getId());
        return projectMapper.toResponse(project, currentUserRole, currentUserOwner, members, boards);
    }

    private Project findProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado"));
    }
}
