package com.devboard.service;

import com.devboard.dto.member.GithubImportResponse;
import com.devboard.dto.member.InvitePublicResponse;
import com.devboard.dto.member.InviteResponse;
import com.devboard.dto.project.ProjectMemberResponse;
import com.devboard.entity.Project;
import com.devboard.entity.ProjectInvite;
import com.devboard.entity.ProjectMember;
import com.devboard.entity.Task;
import com.devboard.entity.User;
import com.devboard.entity.enums.InviteStatus;
import com.devboard.entity.enums.InviteType;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.entity.enums.TaskActivityType;
import com.devboard.event.TaskActivityEvent;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.exception.UnauthorizedException;
import com.devboard.mapper.MemberMapper;
import com.devboard.repository.ProjectInviteRepository;
import com.devboard.repository.ProjectMemberRepository;
import com.devboard.repository.ProjectRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import com.devboard.service.github.GithubClient;
import com.devboard.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int INVITE_EXPIRATION_DAYS = 7;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectInviteRepository projectInviteRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final MemberMapper memberMapper;
    private final EmailService emailService;
    private final GithubClient githubClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InviteResponse inviteByEmail(Long projectId, String email, ProjectRole role, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);
        Project project = findProject(projectId);
        User inviter = findUser(userId);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        assertEmailCanBeInvited(project, normalizedEmail);
        ProjectInvite invite = projectInviteRepository
                .findFirstByProjectIdAndTypeAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        projectId, InviteType.EMAIL, normalizedEmail, InviteStatus.PENDING)
                .filter(existing -> existing.isUsableAt(LocalDateTime.now()))
                .orElseGet(() -> createInvite(project, inviter, InviteType.EMAIL, normalizedEmail, role, 1));

        emailService.sendProjectInviteEmail(invite);
        log.info("Convite por email preparado: projectId={}, inviteId={}, inviterId={}", projectId, invite.getId(), userId);
        return memberMapper.toInviteResponse(invite);
    }

    @Transactional
    public InviteResponse createInviteLink(Long projectId, ProjectRole role, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);
        Project project = findProject(projectId);
        User inviter = findUser(userId);

        ProjectInvite invite = projectInviteRepository
                .findFirstByProjectIdAndTypeAndRoleAndStatusOrderByCreatedAtDesc(
                        projectId, InviteType.LINK, role, InviteStatus.PENDING)
                .filter(existing -> existing.isUsableAt(LocalDateTime.now()))
                .orElseGet(() -> createInvite(project, inviter, InviteType.LINK, null, role, null));

        return memberMapper.toInviteResponse(invite);
    }

    @Transactional
    public InvitePublicResponse getPublicInvite(String token) {
        ProjectInvite invite = findInviteByToken(token);
        assertInviteUsable(invite);
        return memberMapper.toPublicResponse(invite);
    }

    @Transactional
    public ProjectMemberResponse acceptInvite(String token, Long userId) {
        ProjectInvite invite = findInviteByToken(token);
        assertInviteUsable(invite);
        User user = findUser(userId);

        if (invite.getType() == InviteType.EMAIL && !invite.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new AccessDeniedException("Este convite pertence a outro email");
        }
        if (invite.getProject().getOwner().getId().equals(userId)
                || projectMemberRepository.existsByProjectIdAndUserId(invite.getProject().getId(), userId)) {
            throw new ConflictException("Usuário já é membro do projeto");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(invite.getProject());
        member.setUser(user);
        member.setRole(invite.getRole());
        member.setInvitedBy(invite.getInvitedBy());
        ProjectMember saved = projectMemberRepository.save(member);

        invite.setUses(invite.getUses() + 1);
        if (invite.getMaxUses() != null && invite.getUses() >= invite.getMaxUses()) {
            invite.setStatus(InviteStatus.ACCEPTED);
        }

        log.info("Convite aceito: projectId={}, memberId={}, userId={}", invite.getProject().getId(), saved.getId(), userId);
        return memberMapper.toMemberResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Long projectId, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.VIEWER);
        return projectMemberRepository.findWithUsersByProjectIdOrderByJoinedAtAsc(projectId).stream()
                .map(memberMapper::toMemberResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listInvites(Long projectId, InviteStatus status, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);
        List<ProjectInvite> invites = status == null
                ? projectInviteRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                : projectInviteRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
        return invites.stream().map(memberMapper::toInviteResponse).toList();
    }

    @Transactional
    public ProjectMemberResponse updateRole(Long projectId, Long memberId, ProjectRole role, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);
        ProjectMember member = findMember(projectId, memberId);
        if (!permissionService.isOwner(projectId, userId)
                && member.getRole() == ProjectRole.ADMIN
                && !member.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Apenas o dono pode alterar o papel de outro administrador");
        }
        member.setRole(role);
        return memberMapper.toMemberResponse(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long memberId, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);
        ProjectMember member = findMember(projectId, memberId);
        assertMemberCanBeRemoved(projectId, member, userId);
        clearAssignmentsAndPublishActivities(projectId, member.getUser(), userId);
        projectMemberRepository.delete(member);
        log.info("Membro removido: projectId={}, memberId={}, actorId={}", projectId, memberId, userId);
    }

    @Transactional
    public void leaveProject(Long projectId, Long userId) {
        if (permissionService.isOwner(projectId, userId)) {
            throw new AccessDeniedException("O dono não pode sair do projeto");
        }
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado"));
        clearAssignmentsAndPublishActivities(projectId, member.getUser(), userId);
        projectMemberRepository.delete(member);
        log.info("Membro saiu do projeto: projectId={}, userId={}", projectId, userId);
    }

    @Transactional
    public void revokeInvite(Long inviteId, Long userId) {
        ProjectInvite invite = projectInviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convite não encontrado"));
        permissionService.requireRole(invite.getProject().getId(), userId, ProjectRole.ADMIN);
        invite.setStatus(InviteStatus.REVOKED);
    }

    @Transactional
    public GithubImportResponse importGithubCollaborators(Long projectId, ProjectRole role, List<String> requestedLogins,
                                                            Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);
        Project project = findProject(projectId);
        if (!project.hasGithubRepo()) {
            throw new InvalidRequestException("Projeto não possui repositório GitHub vinculado");
        }
        User inviter = findUser(userId);
        if (inviter.getGithubToken() == null) {
            throw new UnauthorizedException("GitHub não conectado");
        }

        Predicate<GithubClient.GithubCollaborator> selected = requestedLogins == null || requestedLogins.isEmpty()
                ? collaborator -> true
                : collaborator -> requestedLogins.stream().anyMatch(login -> login.equalsIgnoreCase(collaborator.login()));

        int added = 0;
        int invited = 0;
        int ignored = 0;
        for (GithubClient.GithubCollaborator collaborator : githubClient.fetchCollaborators(
                inviter.getGithubToken(), project.getGithubRepoOwner(), project.getGithubRepoName())) {
            if (!selected.test(collaborator)) {
                continue;
            }
            User user = userRepository.findByGithubId(collaborator.id()).orElse(null);
            String email = collaborator.email() == null ? null : collaborator.email().trim().toLowerCase(Locale.ROOT);
            if (user == null && email != null && !email.isBlank()) {
                user = userRepository.findByEmail(email).orElse(null);
            }
            if (user != null) {
                if (project.getOwner().getId().equals(user.getId())
                        || projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
                    ignored++;
                    continue;
                }
                ProjectMember member = new ProjectMember();
                member.setProject(project);
                member.setUser(user);
                member.setRole(role);
                member.setInvitedBy(inviter);
                projectMemberRepository.save(member);
                added++;
            } else if (email != null && !email.isBlank()) {
                if (canInviteEmail(project, email)) {
                    ProjectInvite invite = projectInviteRepository
                            .findFirstByProjectIdAndTypeAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                                    projectId, InviteType.EMAIL, email, InviteStatus.PENDING)
                            .filter(existing -> existing.isUsableAt(LocalDateTime.now()))
                            .orElseGet(() -> createInvite(project, inviter, InviteType.EMAIL, email, role, 1));
                    emailService.sendProjectInviteEmail(invite);
                    invited++;
                } else {
                    ignored++;
                }
            } else {
                ignored++;
            }
        }
        return GithubImportResponse.builder().added(added).invited(invited).ignored(ignored).build();
    }

    private ProjectInvite createInvite(Project project, User inviter, InviteType type, String email,
                                       ProjectRole role, Integer maxUses) {
        ProjectInvite invite = new ProjectInvite();
        invite.setProject(project);
        invite.setType(type);
        invite.setEmail(email);
        invite.setRole(role);
        invite.setToken(HashUtil.generateSecureToken());
        invite.setInvitedBy(inviter);
        invite.setStatus(InviteStatus.PENDING);
        invite.setMaxUses(maxUses);
        invite.setExpiresAt(LocalDateTime.now().plusDays(INVITE_EXPIRATION_DAYS));
        return projectInviteRepository.save(invite);
    }

    private void assertEmailCanBeInvited(Project project, String email) {
        if (!canInviteEmail(project, email)) {
            throw new ConflictException("Email já é membro ou dono do projeto");
        }
    }

    private boolean canInviteEmail(Project project, String email) {
        if (project.getOwner().getEmail().equalsIgnoreCase(email)) {
            return false;
        }
        return projectMemberRepository.findWithUsersByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                .noneMatch(member -> member.getUser().getEmail().equalsIgnoreCase(email));
    }

    private void assertInviteUsable(ProjectInvite invite) {
        if (!invite.isUsableAt(LocalDateTime.now())) {
            if (invite.getStatus() == InviteStatus.PENDING && !invite.getExpiresAt().isAfter(LocalDateTime.now())) {
                invite.setStatus(InviteStatus.EXPIRED);
            }
            throw new InvalidRequestException("Convite inválido ou expirado");
        }
    }

    private void assertMemberCanBeRemoved(Long projectId, ProjectMember member, Long actorId) {
        if (!permissionService.isOwner(projectId, actorId)
                && member.getRole() == ProjectRole.ADMIN
                && !member.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Apenas o dono pode remover outro administrador");
        }
    }

    private void clearAssignmentsAndPublishActivities(Long projectId, User removedUser, Long actorId) {
        List<Task> assignedTasks = taskRepository.findActiveByProjectIdAndAssigneeId(projectId, removedUser.getId());
        for (Task task : assignedTasks) {
            task.setAssignee(null);
            eventPublisher.publishEvent(new TaskActivityEvent(task, actorId, TaskActivityType.ASSIGNEE_CHANGED,
                    "Responsável removido porque o membro saiu do projeto"));
        }
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado"));
    }

    private ProjectInvite findInviteByToken(String token) {
        return projectInviteRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRequestException("Convite inválido ou expirado"));
    }

    private ProjectMember findMember(Long projectId, Long memberId) {
        return projectMemberRepository.findById(memberId)
                .filter(member -> Objects.equals(member.getProject().getId(), projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}
