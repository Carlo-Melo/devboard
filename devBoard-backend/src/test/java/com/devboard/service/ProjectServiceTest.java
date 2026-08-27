package com.devboard.service;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.project.CreateProjectRequest;
import com.devboard.dto.project.ProjectResponse;
import com.devboard.dto.project.ProjectSummaryResponse;
import com.devboard.dto.project.UpdateProjectRequest;
import com.devboard.entity.Project;
import com.devboard.entity.User;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.ProjectMapper;
import com.devboard.repository.BoardRepository;
import com.devboard.repository.ProjectMemberRepository;
import com.devboard.repository.ProjectRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private BoardService boardService;

    @InjectMocks
    private ProjectService projectService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("joao_dev");

        lenient().when(projectMapper.toResponse(any(Project.class), any(ProjectRole.class), anyBoolean(), anyList(), anyList()))
                .thenReturn(ProjectResponse.builder().id(10L).name("Projeto X").build());
        lenient().when(projectMapper.toSummary(any(Project.class), anyLong()))
                .thenReturn(ProjectSummaryResponse.builder().id(10L).name("Projeto X").build());
    }

    @Test
    void create_deveCriarProjetoEDelegarQuadroPadraoAoBoardService_quandoDadosValidos() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        Project saved = new Project();
        saved.setId(10L);
        saved.setOwner(owner);
        when(projectRepository.save(any(Project.class))).thenReturn(saved);

        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of());
        when(boardRepository.findByProjectId(10L)).thenReturn(List.of());

        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("Projeto X");

        ProjectResponse response = projectService.create(request, 1L);

        assertThat(response.getId()).isEqualTo(10L);
        verify(boardService).createDefaultBoard(saved);
    }

    @Test
    void create_deveLancarAccessDenied_quandoRepositorioInformadoSemGithubConectado() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("Projeto X");
        request.setGithubRepoId(999L);

        assertThatThrownBy(() -> projectService.create(request, 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void list_deveRetornarProjetosAcessiveisAoUsuario() {
        Project project = new Project();
        project.setId(10L);
        project.setOwner(owner);
        Page<Project> page = new PageImpl<>(List.of(project), PageRequest.of(0, 20), 1);

        when(projectRepository.findAccessibleByUser(eq(1L), eq(false), any())).thenReturn(page);
        when(projectMemberRepository.countByProjectId(10L)).thenReturn(0L);

        PageResponse<ProjectSummaryResponse> response = projectService.list(1L, false, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void list_deveLimitarTamanhoA100_quandoSizeMaiorQueLimite() {
        when(projectRepository.findAccessibleByUser(anyLong(), any(Boolean.class), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        projectService.list(1L, false, 0, 500);

        ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectRepository).findAccessibleByUser(anyLong(), any(Boolean.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getById_deveRetornarProjeto_quandoUsuarioTemVinculo() {
        Project project = new Project();
        project.setId(10L);
        project.setOwner(owner);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(permissionService.resolveRole(10L, 1L)).thenReturn(ProjectRole.ADMIN);
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of());
        when(boardRepository.findByProjectId(10L)).thenReturn(List.of());

        ProjectResponse response = projectService.getById(10L, 1L);

        assertThat(response.getId()).isEqualTo(10L);
        verify(permissionService).requireRole(10L, 1L, ProjectRole.VIEWER);
    }

    @Test
    void getById_devePropagarResourceNotFound_quandoUsuarioSemVinculo() {
        doThrow(new ResourceNotFoundException("Projeto não encontrado"))
                .when(permissionService).requireRole(10L, 5L, ProjectRole.VIEWER);

        assertThatThrownBy(() -> projectService.getById(10L, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_deveAtualizarDados_quandoPapelAdmin() {
        Project project = new Project();
        project.setId(10L);
        project.setOwner(owner);
        project.setName("Nome antigo");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(permissionService.resolveRole(10L, 1L)).thenReturn(ProjectRole.ADMIN);
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of());
        when(boardRepository.findByProjectId(10L)).thenReturn(List.of());

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("Nome novo");
        request.setDescription("Nova descrição");

        projectService.update(10L, request, 1L);

        assertThat(project.getName()).isEqualTo("Nome novo");
        assertThat(project.getDescription()).isEqualTo("Nova descrição");
        verify(permissionService).requireRole(10L, 1L, ProjectRole.ADMIN);
    }

    @Test
    void update_deveLancarAccessDenied_quandoPapelInsuficiente() {
        doThrow(new AccessDeniedException("Você não tem permissão para esta ação"))
                .when(permissionService).requireRole(10L, 2L, ProjectRole.ADMIN);

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("Nome novo");

        assertThatThrownBy(() -> projectService.update(10L, request, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void archive_deveArquivarProjeto_quandoUsuarioEhDono() {
        Project project = new Project();
        project.setId(10L);
        project.setOwner(owner);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        projectService.archive(10L, 1L);

        assertThat(project.getArchived()).isTrue();
        assertThat(project.getArchivedAt()).isNotNull();
        verify(permissionService).requireOwner(10L, 1L);
    }

    @Test
    void archive_deveLancarAccessDenied_quandoUsuarioNaoEhDono() {
        doThrow(new AccessDeniedException("Apenas o dono do projeto pode realizar esta ação"))
                .when(permissionService).requireOwner(10L, 2L);

        assertThatThrownBy(() -> projectService.archive(10L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

}
