package com.devboard.service;

import com.devboard.dto.member.InviteResponse;
import com.devboard.dto.member.GithubImportResponse;
import com.devboard.dto.project.ProjectMemberResponse;
import com.devboard.entity.Project;
import com.devboard.entity.ProjectInvite;
import com.devboard.entity.ProjectMember;
import com.devboard.entity.Task;
import com.devboard.entity.User;
import com.devboard.entity.enums.InviteStatus;
import com.devboard.entity.enums.InviteType;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.mapper.MemberMapper;
import com.devboard.repository.ProjectInviteRepository;
import com.devboard.repository.ProjectMemberRepository;
import com.devboard.repository.ProjectRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import com.devboard.service.github.GithubClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectInviteRepository projectInviteRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionService permissionService;
    @Mock private MemberMapper memberMapper;
    @Mock private EmailService emailService;
    @Mock private GithubClient githubClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MemberService memberService;

    private Project project;
    private User owner;
    private User actor;

    @BeforeEach
    void setUp() {
        owner = user(1L, "owner@example.com");
        actor = user(2L, "admin@example.com");
        project = new Project();
        project.setId(10L);
        project.setName("Projeto Alpha");
        project.setOwner(owner);
        lenient().when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
        lenient().when(projectMemberRepository.findWithUsersByProjectIdOrderByJoinedAtAsc(10L)).thenReturn(List.of());
    }

    @Test
    void inviteByEmail_deveCriarConviteEPedirEnvioDeEmail_quandoAdminEEmailDisponivel() {
        when(projectInviteRepository.findFirstByProjectIdAndTypeAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                10L, InviteType.EMAIL, "novo@example.com", InviteStatus.PENDING)).thenReturn(Optional.empty());
        when(projectInviteRepository.save(any(ProjectInvite.class))).thenAnswer(invocation -> {
            ProjectInvite invite = invocation.getArgument(0);
            invite.setId(44L);
            return invite;
        });
        InviteResponse response = InviteResponse.builder().id(44L).build();
        when(memberMapper.toInviteResponse(any(ProjectInvite.class))).thenReturn(response);

        InviteResponse result = memberService.inviteByEmail(10L, "Novo@Example.com", ProjectRole.DEVELOPER, 2L);

        ArgumentCaptor<ProjectInvite> captor = ArgumentCaptor.forClass(ProjectInvite.class);
        verify(projectInviteRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("novo@example.com");
        assertThat(captor.getValue().getType()).isEqualTo(InviteType.EMAIL);
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.DEVELOPER);
        assertThat(captor.getValue().getMaxUses()).isEqualTo(1);
        verify(emailService).sendProjectInviteEmail(captor.getValue());
        assertThat(result).isSameAs(response);
    }

    @Test
    void inviteByEmail_deveReenviarConvitePendente_quandoConviteAtivoJaExiste() {
        ProjectInvite existing = invite(InviteType.EMAIL, ProjectRole.VIEWER);
        existing.setEmail("novo@example.com");
        when(projectInviteRepository.findFirstByProjectIdAndTypeAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                10L, InviteType.EMAIL, "novo@example.com", InviteStatus.PENDING)).thenReturn(Optional.of(existing));
        when(memberMapper.toInviteResponse(existing)).thenReturn(InviteResponse.builder().id(21L).build());

        memberService.inviteByEmail(10L, "novo@example.com", ProjectRole.ADMIN, 2L);

        verify(projectInviteRepository, never()).save(any(ProjectInvite.class));
        verify(emailService).sendProjectInviteEmail(existing);
    }

    @Test
    void inviteByEmail_deveLancarConflict_quandoEmailJaPertenceAoDono() {
        assertThatThrownBy(() -> memberService.inviteByEmail(10L, "owner@example.com", ProjectRole.VIEWER, 2L))
                .isInstanceOf(ConflictException.class);
        verify(projectInviteRepository, never()).save(any());
    }

    @Test
    void acceptInvite_deveAdicionarMembroEConsumirConviteDeUsoUnico() {
        User invitedUser = user(3L, "invited@example.com");
        ProjectInvite invite = invite(InviteType.EMAIL, ProjectRole.DEVELOPER);
        invite.setEmail("invited@example.com");
        invite.setMaxUses(1);
        when(projectInviteRepository.findByToken("token")).thenReturn(Optional.of(invite));
        when(userRepository.findById(3L)).thenReturn(Optional.of(invitedUser));
        when(projectMemberRepository.existsByProjectIdAndUserId(10L, 3L)).thenReturn(false);
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberMapper.toMemberResponse(any(ProjectMember.class))).thenReturn(ProjectMemberResponse.builder().build());

        memberService.acceptInvite("token", 3L);

        ArgumentCaptor<ProjectMember> member = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(member.capture());
        assertThat(member.getValue().getUser()).isSameAs(invitedUser);
        assertThat(member.getValue().getRole()).isEqualTo(ProjectRole.DEVELOPER);
        assertThat(invite.getUses()).isEqualTo(1);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    void acceptInvite_deveLancarAccessDenied_quandoEmailDoUsuarioDiverge() {
        ProjectInvite invite = invite(InviteType.EMAIL, ProjectRole.VIEWER);
        invite.setEmail("destinatario@example.com");
        when(projectInviteRepository.findByToken("token")).thenReturn(Optional.of(invite));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "outro@example.com")));

        assertThatThrownBy(() -> memberService.acceptInvite("token", 3L))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectMemberRepository, never()).save(any());
    }

    @Test
    void getPublicInvite_deveRejeitarConviteExpirado() {
        ProjectInvite invite = invite(InviteType.LINK, ProjectRole.VIEWER);
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(projectInviteRepository.findByToken("expired")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> memberService.getPublicInvite("expired"))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.EXPIRED);
    }

    @Test
    void updateRole_deveImpedirAdminDeAlterarOutroAdmin() {
        ProjectMember target = member(4L, ProjectRole.ADMIN);
        when(projectMemberRepository.findById(4L)).thenReturn(Optional.of(target));
        when(permissionService.isOwner(10L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> memberService.updateRole(10L, 4L, ProjectRole.VIEWER, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removeMember_deveLimparResponsavelDasTarefasDoProjeto() {
        User removedUser = user(4L, "removed@example.com");
        ProjectMember member = member(4L, ProjectRole.DEVELOPER);
        member.setUser(removedUser);
        Task task = new Task();
        task.setId(100L);
        task.setAssignee(removedUser);
        when(projectMemberRepository.findById(4L)).thenReturn(Optional.of(member));
        when(permissionService.isOwner(10L, 2L)).thenReturn(false);
        when(taskRepository.findActiveByProjectIdAndAssigneeId(10L, 4L)).thenReturn(List.of(task));

        memberService.removeMember(10L, 4L, 2L);

        assertThat(task.getAssignee()).isNull();
        verify(eventPublisher).publishEvent((Object) any());
        verify(projectMemberRepository).delete(member);
    }

    @Test
    void leaveProject_deveImpedirDonoDeSair() {
        when(permissionService.isOwner(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> memberService.leaveProject(10L, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectMemberRepository, never()).delete(any());
    }

    @Test
    void importGithubCollaborators_deveAdicionarUsuarioEncontradoPorEmail() {
        project.setGithubRepoId(22L);
        project.setGithubRepoOwner("devboard");
        project.setGithubRepoName("api");
        actor.setGithubToken("token-github");
        User existingUser = user(7L, "colaborador@example.com");

        when(githubClient.fetchCollaborators("token-github", "devboard", "api"))
                .thenReturn(List.of(new GithubClient.GithubCollaborator(99L, "colaborador", "colaborador@example.com")));
        when(userRepository.findByGithubId(99L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("colaborador@example.com")).thenReturn(Optional.of(existingUser));
        when(projectMemberRepository.existsByProjectIdAndUserId(10L, 7L)).thenReturn(false);

        GithubImportResponse result = memberService.importGithubCollaborators(
                10L, ProjectRole.DEVELOPER, null, 2L);

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(existingUser);
        assertThat(result.getAdded()).isEqualTo(1);
        assertThat(result.getInvited()).isZero();
        assertThat(result.getIgnored()).isZero();
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        return user;
    }

    private ProjectInvite invite(InviteType type, ProjectRole role) {
        ProjectInvite invite = new ProjectInvite();
        invite.setId(20L);
        invite.setProject(project);
        invite.setInvitedBy(actor);
        invite.setType(type);
        invite.setRole(role);
        invite.setToken("token");
        invite.setStatus(InviteStatus.PENDING);
        invite.setUses(0);
        invite.setExpiresAt(LocalDateTime.now().plusDays(1));
        return invite;
    }

    private ProjectMember member(Long id, ProjectRole role) {
        ProjectMember member = new ProjectMember();
        member.setId(id);
        member.setProject(project);
        member.setUser(user(id, "member" + id + "@example.com"));
        member.setRole(role);
        return member;
    }
}
