package com.devboard.service;

import com.devboard.dto.task.CommentResponse;
import com.devboard.dto.task.CreateCommentRequest;
import com.devboard.dto.task.UpdateCommentRequest;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Project;
import com.devboard.entity.Task;
import com.devboard.entity.TaskComment;
import com.devboard.entity.User;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.event.TaskActivityEvent;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.TaskMapper;
import com.devboard.repository.TaskCommentRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCommentServiceTest {

    @Mock
    private TaskCommentRepository taskCommentRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TaskCommentService taskCommentService;

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

    private TaskComment comment(Long id, Long authorId, Task task) {
        User author = new User();
        author.setId(authorId);

        TaskComment comment = new TaskComment();
        comment.setId(id);
        comment.setAuthor(author);
        comment.setTask(task);
        comment.setContent("Comentário original");
        return comment;
    }

    @Test
    void create_deveSalvarComentario_eRegistrarAtividade() {
        Task task = task(100L);
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(task));
        when(userRepository.findById(9L)).thenReturn(Optional.of(new User()));
        when(taskCommentRepository.save(any(TaskComment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(taskMapper.toCommentResponse(any(TaskComment.class)))
                .thenReturn(CommentResponse.builder().id(1L).build());

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Feito!");

        taskCommentService.create(100L, request, 9L);

        verify(permissionService).requireRole(20L, 9L, ProjectRole.VIEWER);
        verify(eventPublisher).publishEvent(any(TaskActivityEvent.class));
    }

    @Test
    void create_deveLancarResourceNotFound_quandoTarefaInexistente() {
        when(taskRepository.findByIdWithDetails(100L)).thenReturn(Optional.empty());

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Feito!");

        assertThatThrownBy(() -> taskCommentService.create(100L, request, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_devePermitirAutor() {
        TaskComment comment = comment(1L, 9L, task(100L));
        when(taskCommentRepository.findById(1L)).thenReturn(Optional.of(comment));
        lenient().when(taskMapper.toCommentResponse(any(TaskComment.class)))
                .thenReturn(CommentResponse.builder().id(1L).build());

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Editado");

        taskCommentService.update(1L, request, 9L);

        assertThat(comment.getContent()).isEqualTo("Editado");
        assertThat(comment.getEdited()).isTrue();
    }

    @Test
    void update_deveLancarAccessDenied_quandoNaoEhAutor() {
        TaskComment comment = comment(1L, 9L, task(100L));
        when(taskCommentRepository.findById(1L)).thenReturn(Optional.of(comment));

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Editado");

        assertThatThrownBy(() -> taskCommentService.update(1L, request, 40L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_devePermitirAutor_semVerificarPapel() {
        TaskComment comment = comment(1L, 9L, task(100L));
        when(taskCommentRepository.findById(1L)).thenReturn(Optional.of(comment));

        taskCommentService.delete(1L, 9L);

        verify(taskCommentRepository).delete(comment);
        verify(permissionService, never()).requireRole(any(), any(), any());
    }

    @Test
    void delete_deveExigirAdmin_quandoNaoEhAutor() {
        TaskComment comment = comment(1L, 9L, task(100L));
        when(taskCommentRepository.findById(1L)).thenReturn(Optional.of(comment));

        taskCommentService.delete(1L, 2L);

        verify(permissionService).requireRole(20L, 2L, ProjectRole.ADMIN);
        verify(taskCommentRepository).delete(comment);
    }

    @Test
    void delete_devePropagarAccessDenied_quandoNaoEhAutorNemAdmin() {
        TaskComment comment = comment(1L, 9L, task(100L));
        when(taskCommentRepository.findById(1L)).thenReturn(Optional.of(comment));
        doThrow(new AccessDeniedException("sem permissão"))
                .when(permissionService).requireRole(20L, 2L, ProjectRole.ADMIN);

        assertThatThrownBy(() -> taskCommentService.delete(1L, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskCommentRepository, never()).delete(any());
    }
}
