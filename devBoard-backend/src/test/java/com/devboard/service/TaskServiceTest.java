package com.devboard.service;

import com.devboard.dto.task.CreateTaskRequest;
import com.devboard.dto.task.MoveTaskRequest;
import com.devboard.dto.task.TaskResponse;
import com.devboard.dto.task.UpdateTaskRequest;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Project;
import com.devboard.entity.Task;
import com.devboard.entity.User;
import com.devboard.entity.enums.ColumnRole;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.entity.enums.TaskActivityType;
import com.devboard.entity.enums.TaskPriority;
import com.devboard.entity.enums.TaskType;
import com.devboard.event.TaskActivityEvent;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.TaskMapper;
import com.devboard.repository.BoardColumnRepository;
import com.devboard.repository.TaskActivityRepository;
import com.devboard.repository.TaskCommentRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private BoardColumnRepository boardColumnRepository;

    @Mock
    private TaskCommentRepository taskCommentRepository;

    @Mock
    private TaskActivityRepository taskActivityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TaskService taskService;

    private Project project;
    private Board board;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(20L);

        board = new Board();
        board.setId(30L);
        board.setProject(project);

        lenient().when(taskMapper.toResponse(any(Task.class), anyList(), anyList()))
                .thenAnswer(invocation -> TaskResponse.builder().id(((Task) invocation.getArgument(0)).getId()).build());
    }

    private BoardColumn column(Long id, String name, ColumnRole role, Integer wipLimit) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setBoard(board);
        column.setName(name);
        column.setRole(role);
        column.setWipLimit(wipLimit);
        return column;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }

    private Task task(Long id, BoardColumn column, Long creatorId, int position) {
        Task task = new Task();
        task.setId(id);
        task.setColumn(column);
        task.setTitle("Título original");
        task.setType(TaskType.OTHER);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreator(user(creatorId));
        task.setPosition(position);
        return task;
    }

    // ---- create ----

    @Test
    void create_deveCriarTarefa_comColunaETituloApenas() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(backlog));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L)));
        when(taskRepository.countByColumnIdAndArchivedFalse(1L)).thenReturn(2L);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        CreateTaskRequest request = new CreateTaskRequest();
        request.setColumnId(1L);
        request.setTitle("Implementar login");

        taskService.create(request, 9L);

        verify(permissionService).requireRole(20L, 9L, ProjectRole.DEVELOPER);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Implementar login");
        assertThat(saved.getPosition()).isEqualTo(2);
        assertThat(saved.getType()).isEqualTo(TaskType.OTHER);
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);

        ArgumentCaptor<TaskActivityEvent> eventCaptor = ArgumentCaptor.forClass(TaskActivityEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(TaskActivityType.CREATED);
    }

    @Test
    void create_deveLancarInvalidRequest_quandoResponsavelNaoEhMembro() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(backlog));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L)));
        when(permissionService.resolveRole(20L, 50L)).thenReturn(null);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setColumnId(1L);
        request.setTitle("Implementar login");
        request.setAssigneeId(50L);

        assertThatThrownBy(() -> taskService.create(request, 9L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void create_deveLancarResourceNotFound_quandoColunaInexistente() {
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.empty());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setColumnId(1L);
        request.setTitle("Implementar login");

        assertThatThrownBy(() -> taskService.create(request, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_devePropagarAccessDenied_quandoSemPermissao() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(backlog));
        org.mockito.Mockito.doThrow(new AccessDeniedException("sem permissão"))
                .when(permissionService).requireRole(20L, 9L, ProjectRole.DEVELOPER);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setColumnId(1L);
        request.setTitle("Implementar login");

        assertThatThrownBy(() -> taskService.create(request, 9L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- update ----

    @Test
    void update_deveGerarAtividadeDeTitulo_quandoTituloAlterado() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task existing = task(100L, backlog, 9L, 0);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(existing));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("Novo título");

        taskService.update(100L, request, 9L);

        verify(permissionService).requireTaskEditable(existing, 9L);
        assertThat(existing.getTitle()).isEqualTo("Novo título");

        ArgumentCaptor<TaskActivityEvent> eventCaptor = ArgumentCaptor.forClass(TaskActivityEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(TaskActivityType.TITLE_CHANGED);
    }

    @Test
    void update_naoDeveGerarAtividade_quandoNadaMudou() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task existing = task(100L, backlog, 9L, 0);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(existing));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle(existing.getTitle());
        request.setType(existing.getType());
        request.setPriority(existing.getPriority());

        taskService.update(100L, request, 9L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_deveGerarAtividadeDeResponsavel_quandoResponsavelAlterado() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task existing = task(100L, backlog, 9L, 0);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(existing));
        when(permissionService.resolveRole(20L, 5L)).thenReturn(ProjectRole.DEVELOPER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L)));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle(existing.getTitle());
        request.setType(existing.getType());
        request.setPriority(existing.getPriority());
        request.setAssigneeId(5L);

        taskService.update(100L, request, 9L);

        assertThat(existing.getAssignee().getId()).isEqualTo(5L);
        ArgumentCaptor<TaskActivityEvent> eventCaptor = ArgumentCaptor.forClass(TaskActivityEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(TaskActivityType.ASSIGNEE_CHANGED);
    }

    @Test
    void update_devePropagarAccessDenied_quandoDeveloperNaoEhCriadorNemResponsavel() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task existing = task(100L, backlog, 9L, 0);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new AccessDeniedException("sem permissão"))
                .when(permissionService).requireTaskEditable(existing, 40L);

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("Outro título");

        assertThatThrownBy(() -> taskService.update(100L, request, 40L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- move ----

    @Test
    void move_deveReordenarNaMesmaColuna() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task task1 = task(100L, backlog, 9L, 0);
        Task task2 = task(101L, backlog, 9L, 1);
        Task task3 = task(102L, backlog, 9L, 2);

        when(taskRepository.findByIdWithDetails(102L)).thenReturn(Optional.of(task3));
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(backlog));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L))
                .thenReturn(new ArrayList<>(List.of(task1, task2, task3)));

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(1L);
        request.setPosition(0);

        taskService.move(102L, request, 9L);

        assertThat(task3.getPosition()).isEqualTo(0);
        assertThat(task1.getPosition()).isEqualTo(1);
        assertThat(task2.getPosition()).isEqualTo(2);
    }

    @Test
    void move_deveMoverParaOutraColuna_eReindexarOrigemEDestino() {
        BoardColumn origin = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        BoardColumn target = column(2L, "To-Do", ColumnRole.TODO, null);
        Task moving = task(100L, origin, 9L, 0);
        Task remaining = task(101L, origin, 9L, 1);
        Task existingInTarget = task(200L, target, 9L, 0);

        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(moving));
        when(boardColumnRepository.findById(2L)).thenReturn(Optional.of(target));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L))
                .thenReturn(new ArrayList<>(List.of(moving, remaining)));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(2L))
                .thenReturn(new ArrayList<>(List.of(existingInTarget)));

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(2L);
        request.setPosition(1);

        taskService.move(100L, request, 9L);

        assertThat(moving.getColumn()).isEqualTo(target);
        assertThat(remaining.getPosition()).isEqualTo(0);
        assertThat(existingInTarget.getPosition()).isEqualTo(0);
        assertThat(moving.getPosition()).isEqualTo(1);

        ArgumentCaptor<TaskActivityEvent> eventCaptor = ArgumentCaptor.forClass(TaskActivityEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(TaskActivityType.MOVED);
    }

    @Test
    void move_deveLancarConflict_quandoLimiteWipAtingido() {
        BoardColumn origin = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        BoardColumn target = column(2L, "To-Do", ColumnRole.TODO, 1);
        Task moving = task(100L, origin, 9L, 0);
        Task existingInTarget = task(200L, target, 9L, 0);

        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(moving));
        when(boardColumnRepository.findById(2L)).thenReturn(Optional.of(target));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L))
                .thenReturn(new ArrayList<>(List.of(moving)));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(2L))
                .thenReturn(new ArrayList<>(List.of(existingInTarget)));

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(2L);
        request.setPosition(0);

        assertThatThrownBy(() -> taskService.move(100L, request, 9L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void move_deveRegistrarConclusao_quandoEntraEmColunaDone() {
        BoardColumn origin = column(1L, "In Progress", ColumnRole.IN_PROGRESS, null);
        BoardColumn done = column(2L, "Done", ColumnRole.DONE, null);
        Task moving = task(100L, origin, 9L, 0);

        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(moving));
        when(boardColumnRepository.findById(2L)).thenReturn(Optional.of(done));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L))
                .thenReturn(new ArrayList<>(List.of(moving)));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(2L))
                .thenReturn(new ArrayList<>());

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(2L);
        request.setPosition(0);

        taskService.move(100L, request, 9L);

        assertThat(moving.getCompletedAt()).isNotNull();
    }

    @Test
    void move_deveLimparConclusao_quandoSaiDeColunaDone() {
        BoardColumn done = column(1L, "Done", ColumnRole.DONE, null);
        BoardColumn target = column(2L, "In Progress", ColumnRole.IN_PROGRESS, null);
        Task moving = task(100L, done, 9L, 0);
        moving.setCompletedAt(java.time.LocalDateTime.now().minusDays(1));

        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(moving));
        when(boardColumnRepository.findById(2L)).thenReturn(Optional.of(target));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L))
                .thenReturn(new ArrayList<>(List.of(moving)));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(2L))
                .thenReturn(new ArrayList<>());

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(2L);
        request.setPosition(0);

        taskService.move(100L, request, 9L);

        assertThat(moving.getCompletedAt()).isNull();
    }

    @Test
    void move_deveLancarInvalidRequest_quandoPosicaoNegativa() {
        BoardColumn origin = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task moving = task(100L, origin, 9L, 0);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(moving));

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(1L);
        request.setPosition(-1);

        assertThatThrownBy(() -> taskService.move(100L, request, 9L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void move_deveLancarInvalidRequest_quandoColunaDeOutroProjeto() {
        BoardColumn origin = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task moving = task(100L, origin, 9L, 0);

        Project otherProject = new Project();
        otherProject.setId(999L);
        Board otherBoard = new Board();
        otherBoard.setProject(otherProject);
        BoardColumn otherColumn = new BoardColumn();
        otherColumn.setId(5L);
        otherColumn.setBoard(otherBoard);

        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(moving));
        when(boardColumnRepository.findById(5L)).thenReturn(Optional.of(otherColumn));

        MoveTaskRequest request = new MoveTaskRequest();
        request.setColumnId(5L);
        request.setPosition(0);

        assertThatThrownBy(() -> taskService.move(100L, request, 9L))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- archive ----

    @Test
    void archive_devePermitirCriador_eReindexarRestantes() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task toArchive = task(100L, backlog, 9L, 0);
        Task remaining = task(101L, backlog, 9L, 1);

        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(toArchive));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L))
                .thenReturn(new ArrayList<>(List.of(toArchive, remaining)));

        taskService.archive(100L, 9L);

        assertThat(toArchive.getArchived()).isTrue();
        assertThat(toArchive.getArchivedAt()).isNotNull();
        assertThat(remaining.getPosition()).isEqualTo(0);

        ArgumentCaptor<TaskActivityEvent> eventCaptor = ArgumentCaptor.forClass(TaskActivityEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(TaskActivityType.ARCHIVED);
    }

    @Test
    void archive_devePropagarAccessDenied_quandoDeveloperNaoRelacionado() {
        BoardColumn backlog = column(1L, "Backlog", ColumnRole.BACKLOG, null);
        Task toArchive = task(100L, backlog, 9L, 0);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(toArchive));
        org.mockito.Mockito.doThrow(new AccessDeniedException("sem permissão"))
                .when(permissionService).requireTaskEditable(toArchive, 40L);

        assertThatThrownBy(() -> taskService.archive(100L, 40L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
