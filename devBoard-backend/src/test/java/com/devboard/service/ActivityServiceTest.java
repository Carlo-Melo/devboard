package com.devboard.service;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.task.ActivityResponse;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Project;
import com.devboard.entity.Task;
import com.devboard.entity.TaskActivity;
import com.devboard.entity.User;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.entity.enums.TaskActivityType;
import com.devboard.event.TaskActivityEvent;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.TaskMapper;
import com.devboard.repository.TaskActivityRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private TaskActivityRepository taskActivityRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private TaskMapper taskMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActivityService activityService() {
        return new ActivityService(taskActivityRepository, taskRepository, userRepository,
                permissionService, taskMapper, objectMapper);
    }

    private Task task(Long id) {
        Project project = new Project();
        project.setId(20L);
        Board board = new Board();
        board.setProject(project);
        BoardColumn column = new BoardColumn();
        column.setBoard(board);

        Task task = new Task();
        task.setId(id);
        task.setColumn(column);
        return task;
    }

    @Test
    void record_deveSalvarAtividade_comAutorEMetadados() {
        ActivityService service = activityService();
        Task task = task(100L);
        User author = new User();
        author.setId(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(author));

        TaskActivityEvent event = new TaskActivityEvent(task, 9L, TaskActivityType.MOVED, "Movida",
                Map.of("fromColumnId", 1L, "toColumnId", 2L));

        service.record(event);

        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);
        verify(taskActivityRepository).save(captor.capture());
        TaskActivity saved = captor.getValue();
        assertThat(saved.getTask()).isEqualTo(task);
        assertThat(saved.getAuthor()).isEqualTo(author);
        assertThat(saved.getType()).isEqualTo(TaskActivityType.MOVED);
        assertThat(saved.getMetadata()).contains("fromColumnId");
    }

    @Test
    void record_naoDevePropagarExcecao_quandoSalvarFalha() {
        ActivityService service = activityService();
        Task task = task(100L);
        when(taskActivityRepository.save(any(TaskActivity.class))).thenThrow(new RuntimeException("erro de banco"));

        TaskActivityEvent event = new TaskActivityEvent(task, null, TaskActivityType.CREATED, "Tarefa criada");

        service.record(event);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void listByTask_deveExigirPermissaoDeVisualizacao() {
        ActivityService service = activityService();
        Task task = task(100L);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));
        doThrow(new AccessDeniedException("sem permissão"))
                .when(permissionService).requireRole(20L, 5L, ProjectRole.VIEWER);

        assertThatThrownBy(() -> service.listByTask(100L, null, 0, 20, 5L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listByTask_deveLancarResourceNotFound_quandoTarefaInexistente() {
        ActivityService service = activityService();
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByTask(999L, null, 0, 20, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByTask_deveFiltrarPorTipo_quandoInformado() {
        ActivityService service = activityService();
        Task task = task(100L);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));
        Page<TaskActivity> page = new PageImpl<>(List.of());
        when(taskActivityRepository.findByTaskIdAndTypeOrderByCreatedAtDesc(eq(100L), eq(TaskActivityType.MOVED), any()))
                .thenReturn(page);

        PageResponse<ActivityResponse> response = service.listByTask(100L, TaskActivityType.MOVED, 0, 20, 5L);

        assertThat(response.getContent()).isEmpty();
        verify(taskActivityRepository, never()).findByTaskIdOrderByCreatedAtDesc(any(), any());
    }
}
