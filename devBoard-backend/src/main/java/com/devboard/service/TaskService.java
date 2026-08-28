package com.devboard.service;

import com.devboard.dto.task.CreateTaskRequest;
import com.devboard.dto.task.MoveTaskRequest;
import com.devboard.dto.task.TaskResponse;
import com.devboard.dto.task.UpdateTaskRequest;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Task;
import com.devboard.entity.TaskActivity;
import com.devboard.entity.TaskComment;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final TaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TaskResponse create(CreateTaskRequest request, Long userId) {
        BoardColumn column = findColumnOrThrow(request.getColumnId());
        Long projectId = column.getBoard().getProject().getId();
        permissionService.requireRole(projectId, userId, ProjectRole.DEVELOPER);

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        User assignee = null;
        if (request.getAssigneeId() != null) {
            validateProjectMember(projectId, request.getAssigneeId(), "Responsável");
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        }

        Task task = new Task();
        task.setColumn(column);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setType(request.getType() != null ? request.getType() : TaskType.OTHER);
        task.setPriority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM);
        task.setAssignee(assignee);
        task.setCreator(creator);
        task.setCollaborators(resolveCollaborators(projectId, request.getCollaboratorIds()));
        task.setDueDate(request.getDueDate());
        task.setEstimate(request.getEstimate());
        task.setPosition((int) taskRepository.countByColumnIdAndArchivedFalse(column.getId()));

        Task saved = taskRepository.save(task);

        eventPublisher.publishEvent(new TaskActivityEvent(saved, userId, TaskActivityType.CREATED, "Tarefa criada"));

        log.info("Tarefa criada: id={}, columnId={}, userId={}", saved.getId(), column.getId(), userId);
        return taskMapper.toResponse(saved, List.of(), List.of());
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(Long taskId, Long userId) {
        Task task = findTaskOrThrow(taskId);
        permissionService.requireRole(task.resolveProjectId(), userId, ProjectRole.VIEWER);

        return taskMapper.toResponse(task, loadComments(taskId), loadActivities(taskId));
    }

    @Transactional
    public TaskResponse update(Long taskId, UpdateTaskRequest request, Long userId) {
        Task task = findTaskOrThrow(taskId);
        permissionService.requireTaskEditable(task, userId);
        Long projectId = task.resolveProjectId();

        if (!task.getTitle().equals(request.getTitle())) {
            publishActivity(task, userId, TaskActivityType.TITLE_CHANGED,
                    "Título alterado de \"%s\" para \"%s\"".formatted(task.getTitle(), request.getTitle()));
            task.setTitle(request.getTitle());
        }

        if (!Objects.equals(task.getDescription(), request.getDescription())) {
            publishActivity(task, userId, TaskActivityType.DESCRIPTION_CHANGED, "Descrição atualizada");
            task.setDescription(request.getDescription());
        }

        TaskType newType = request.getType() != null ? request.getType() : TaskType.OTHER;
        if (task.getType() != newType) {
            publishActivity(task, userId, TaskActivityType.TYPE_CHANGED,
                    "Tipo alterado de %s para %s".formatted(task.getType(), newType));
            task.setType(newType);
        }

        TaskPriority newPriority = request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM;
        if (task.getPriority() != newPriority) {
            publishActivity(task, userId, TaskActivityType.PRIORITY_CHANGED,
                    "Prioridade alterada de %s para %s".formatted(task.getPriority(), newPriority));
            task.setPriority(newPriority);
        }

        Long currentAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        if (!Objects.equals(currentAssigneeId, request.getAssigneeId())) {
            User newAssignee = null;
            if (request.getAssigneeId() != null) {
                validateProjectMember(projectId, request.getAssigneeId(), "Responsável");
                newAssignee = userRepository.findById(request.getAssigneeId())
                        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            }
            publishActivity(task, userId, TaskActivityType.ASSIGNEE_CHANGED,
                    newAssignee != null ? "Responsável alterado para " + newAssignee.getUsername() : "Responsável removido");
            task.setAssignee(newAssignee);
        }

        Set<Long> currentCollaboratorIds = task.getCollaborators().stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> requestedCollaboratorIds = request.getCollaboratorIds() != null
                ? new HashSet<>(request.getCollaboratorIds())
                : Set.of();
        if (!currentCollaboratorIds.equals(requestedCollaboratorIds)) {
            task.setCollaborators(resolveCollaborators(projectId, request.getCollaboratorIds()));
            publishActivity(task, userId, TaskActivityType.COLLABORATORS_CHANGED, "Co-responsáveis atualizados");
        }

        if (!Objects.equals(task.getDueDate(), request.getDueDate())) {
            publishActivity(task, userId, TaskActivityType.DUE_DATE_CHANGED,
                    request.getDueDate() != null ? "Prazo alterado para " + request.getDueDate() : "Prazo removido");
            task.setDueDate(request.getDueDate());
        }

        if (!Objects.equals(task.getEstimate(), request.getEstimate())) {
            publishActivity(task, userId, TaskActivityType.ESTIMATE_CHANGED,
                    request.getEstimate() != null ? "Estimativa alterada para " + request.getEstimate() : "Estimativa removida");
            task.setEstimate(request.getEstimate());
        }

        return taskMapper.toResponse(task, loadComments(taskId), loadActivities(taskId));
    }

    @Transactional
    public TaskResponse move(Long taskId, MoveTaskRequest request, Long userId) {
        Task task = findTaskOrThrow(taskId);
        Long projectId = task.resolveProjectId();
        permissionService.requireRole(projectId, userId, ProjectRole.DEVELOPER);

        if (request.getPosition() == null || request.getPosition() < 0) {
            throw new InvalidRequestException("Posição inválida");
        }

        BoardColumn target = findColumnOrThrow(request.getColumnId());
        if (!target.getBoard().getProject().getId().equals(projectId)) {
            throw new InvalidRequestException("Coluna pertence a outro projeto");
        }

        BoardColumn origin = task.getColumn();
        boolean sameColumn = origin.getId().equals(target.getId());

        List<Task> originTasks = taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(origin.getId());
        originTasks.removeIf(t -> t.getId().equals(task.getId()));

        List<Task> targetTasks = sameColumn
                ? originTasks
                : taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(target.getId());

        if (!sameColumn && target.getWipLimit() != null && targetTasks.size() >= target.getWipLimit()) {
            throw new ConflictException("Limite de trabalho em progresso atingido");
        }

        int targetPosition = Math.min(request.getPosition(), targetTasks.size());
        targetTasks.add(targetPosition, task);
        task.setColumn(target);

        boolean wasDone = origin.getRole() == ColumnRole.DONE;
        boolean isDone = target.getRole() == ColumnRole.DONE;
        if (!wasDone && isDone) {
            task.setCompletedAt(LocalDateTime.now());
        } else if (wasDone && !isDone) {
            task.setCompletedAt(null);
        }

        if (!sameColumn) {
            reindex(originTasks);
            taskRepository.saveAll(originTasks);
        }
        reindex(targetTasks);
        taskRepository.saveAll(targetTasks);

        Map<String, Object> metadata = Map.of("fromColumnId", origin.getId(), "toColumnId", target.getId());
        eventPublisher.publishEvent(new TaskActivityEvent(task, userId, TaskActivityType.MOVED,
                "Movida de \"%s\" para \"%s\"".formatted(origin.getName(), target.getName()), metadata));

        log.info("Tarefa movida: id={}, fromColumnId={}, toColumnId={}, userId={}", taskId, origin.getId(), target.getId(), userId);
        return taskMapper.toResponse(task, loadComments(taskId), loadActivities(taskId));
    }

    @Transactional
    public void archive(Long taskId, Long userId) {
        Task task = findTaskOrThrow(taskId);
        permissionService.requireTaskEditable(task, userId);

        List<Task> remaining = taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(task.getColumn().getId());
        remaining.removeIf(t -> t.getId().equals(task.getId()));
        reindex(remaining);
        taskRepository.saveAll(remaining);

        task.setArchived(true);
        task.setArchivedAt(LocalDateTime.now());

        eventPublisher.publishEvent(new TaskActivityEvent(task, userId, TaskActivityType.ARCHIVED, "Tarefa arquivada"));

        log.info("Tarefa arquivada: id={}, userId={}", taskId, userId);
    }

    private void publishActivity(Task task, Long userId, TaskActivityType type, String description) {
        eventPublisher.publishEvent(new TaskActivityEvent(task, userId, type, description));
    }

    private Set<User> resolveCollaborators(Long projectId, List<Long> collaboratorIds) {
        if (collaboratorIds == null || collaboratorIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<User> collaborators = new HashSet<>();
        for (Long id : collaboratorIds) {
            validateProjectMember(projectId, id, "Co-responsável");
            collaborators.add(userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado")));
        }
        return collaborators;
    }

    private void validateProjectMember(Long projectId, Long userId, String fieldLabel) {
        if (permissionService.resolveRole(projectId, userId) == null) {
            throw new InvalidRequestException(fieldLabel + " não é membro do projeto");
        }
    }

    private void reindex(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
    }

    private List<TaskComment> loadComments(Long taskId) {
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    private List<TaskActivity> loadActivities(Long taskId) {
        return taskActivityRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    private Task findTaskOrThrow(Long taskId) {
        return taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
    }

    private BoardColumn findColumnOrThrow(Long columnId) {
        return boardColumnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("Coluna não encontrada"));
    }
}
