package com.devboard.security;

import com.devboard.entity.Project;
import com.devboard.entity.ProjectMember;
import com.devboard.entity.User;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.repository.ProjectMemberRepository;
import com.devboard.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository memberRepository;

    @InjectMocks
    private PermissionService permissionService;

    private Project project(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);

        Project project = new Project();
        project.setId(10L);
        project.setOwner(owner);
        return project;
    }

    @Test
    void resolveRole_deveRetornarAdmin_quandoUsuarioEhDono() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));

        ProjectRole role = permissionService.resolveRole(10L, 1L);

        assertThat(role).isEqualTo(ProjectRole.ADMIN);
    }

    @Test
    void resolveRole_deveRetornarPapelDoMembro_quandoUsuarioEhMembro() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));

        ProjectMember member = new ProjectMember();
        member.setRole(ProjectRole.DEVELOPER);
        when(memberRepository.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.of(member));

        ProjectRole role = permissionService.resolveRole(10L, 2L);

        assertThat(role).isEqualTo(ProjectRole.DEVELOPER);
    }

    @Test
    void resolveRole_deveRetornarNull_quandoSemVinculo() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));
        when(memberRepository.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.empty());

        ProjectRole role = permissionService.resolveRole(10L, 3L);

        assertThat(role).isNull();
    }

    @Test
    void requireRole_deveLancarResourceNotFound_quandoSemVinculo() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));
        when(memberRepository.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.requireRole(10L, 3L, ProjectRole.VIEWER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireRole_deveLancarAccessDenied_quandoPapelInsuficiente() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));

        ProjectMember member = new ProjectMember();
        member.setRole(ProjectRole.VIEWER);
        when(memberRepository.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> permissionService.requireRole(10L, 2L, ProjectRole.ADMIN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireRole_devePassar_quandoPapelSuficiente() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));

        permissionService.requireRole(10L, 1L, ProjectRole.ADMIN);
    }

    @Test
    void requireOwner_deveLancarAccessDenied_quandoNaoDono() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));

        assertThatThrownBy(() -> permissionService.requireOwner(10L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isOwner_deveRetornarTrue_quandoUsuarioEhDono() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(1L)));

        assertThat(permissionService.isOwner(10L, 1L)).isTrue();
    }
}
