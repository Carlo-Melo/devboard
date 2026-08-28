package com.devboard.service;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.task.ActivityResponse;
import com.devboard.entity.Task;
import com.devboard.entity.TaskActivity;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra o que aconteceu numa tarefa (visível a todos do projeto). Chamada apenas pelo
 * {@code TaskActivityListener}, depois do commit da transação principal — nunca diretamente pelos
 * demais services (claude.md — Atividades e Notificações).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TaskActivityRepository taskActivityRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(TaskActivityEvent event) {
        try {
            TaskActivity activity = new TaskActivity();
            activity.setTask(event.getTask());
            if (event.getActorUserId() != null) {
                userRepository.findById(event.getActorUserId()).ifPresent(activity::setAuthor);
            }
            activity.setType(event.getType());
            activity.setDescription(event.getDescription());
            if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
                activity.setMetadata(objectMapper.writeValueAsString(event.getMetadata()));
            }
            taskActivityRepository.save(activity);
        } catch (Exception ex) {
            log.error("Falha ao registrar atividade da tarefa {}: {}", event.getTask().getId(), ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> listByTask(Long taskId, TaskActivityType type, int page, int size, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
        permissionService.requireRole(task.resolveProjectId(), userId, ProjectRole.VIEWER);

        int clampedSize = Math.min(size, MAX_PAGE_SIZE) <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TaskActivity> result = type != null
                ? taskActivityRepository.findByTaskIdAndTypeOrderByCreatedAtDesc(taskId, type, pageRequest)
                : taskActivityRepository.findByTaskIdOrderByCreatedAtDesc(taskId, pageRequest);

        return PageResponse.from(result, taskMapper::toActivityResponse);
    }
}
