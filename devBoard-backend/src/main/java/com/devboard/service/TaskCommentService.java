package com.devboard.service;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.task.CommentResponse;
import com.devboard.dto.task.CreateCommentRequest;
import com.devboard.dto.task.UpdateCommentRequest;
import com.devboard.entity.Task;
import com.devboard.entity.TaskComment;
import com.devboard.entity.User;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.entity.enums.TaskActivityType;
import com.devboard.event.TaskActivityEvent;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.TaskMapper;
import com.devboard.repository.TaskCommentRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.repository.UserRepository;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menção (@username) e a notificação correspondente (spec-tasks.md 5.3) ficam pendentes até o
 * NotificationService existir (Sprint 3, spec-notifications.md) — o comentário é salvo como texto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TaskCommentRepository taskCommentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final TaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentResponse create(Long taskId, CreateCommentRequest request, Long userId) {
        Task task = findTaskOrThrow(taskId);
        permissionService.requireRole(task.resolveProjectId(), userId, ProjectRole.VIEWER);

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setContent(request.getContent());
        TaskComment saved = taskCommentRepository.save(comment);

        eventPublisher.publishEvent(new TaskActivityEvent(task, userId, TaskActivityType.COMMENTED, "Adicionou um comentário"));

        return taskMapper.toCommentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(Long taskId, int page, int size, Long userId) {
        Task task = findTaskOrThrow(taskId);
        permissionService.requireRole(task.resolveProjectId(), userId, ProjectRole.VIEWER);

        int clampedSize = Math.min(size, MAX_PAGE_SIZE) <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampedSize, Sort.by(Sort.Direction.ASC, "createdAt"));

        Page<TaskComment> result = taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId, pageRequest);
        return PageResponse.from(result, taskMapper::toCommentResponse);
    }

    @Transactional
    public CommentResponse update(Long commentId, UpdateCommentRequest request, Long userId) {
        TaskComment comment = findCommentOrThrow(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new AccessDeniedException("Apenas o autor pode editar o comentário");
        }

        comment.setContent(request.getContent());
        comment.setEdited(true);

        return taskMapper.toCommentResponse(comment);
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        TaskComment comment = findCommentOrThrow(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            permissionService.requireRole(comment.getTask().resolveProjectId(), userId, ProjectRole.ADMIN);
        }

        taskCommentRepository.delete(comment);
        log.info("Comentário excluído: id={}, taskId={}, userId={}", commentId, comment.getTask().getId(), userId);
    }

    private Task findTaskOrThrow(Long taskId) {
        return taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
    }

    private TaskComment findCommentOrThrow(Long commentId) {
        return taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado"));
    }
}
