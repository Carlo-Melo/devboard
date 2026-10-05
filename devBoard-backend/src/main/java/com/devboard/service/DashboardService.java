package com.devboard.service;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.project.*;
import com.devboard.entity.enums.ColumnRole;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.entity.enums.TaskPriority;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.repository.ProjectRepository;
import com.devboard.repository.TaskActivityRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final TaskRepository tasks;
    private final TaskActivityRepository activities;
    private final ProjectRepository projects;
    private final PermissionService permissions;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long userId, Long projectId) {
        validateProject(userId, projectId);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        Map<String, Long> stages = new LinkedHashMap<>();
        for (ColumnRole role : ColumnRole.values()) stages.put(role.name(), 0L);
        for (Object[] row : tasks.countMyTasksByRole(userId, projectId)) stages.put(((ColumnRole) row[0]).name(), (Long) row[1]);
        Map<String, Long> priorities = new LinkedHashMap<>();
        for (TaskPriority priority : TaskPriority.values()) priorities.put(priority.name(), 0L);
        for (Object[] row : tasks.countMyPendingByPriority(userId, projectId)) priorities.put(((TaskPriority) row[0]).name(), (Long) row[1]);
        return new DashboardSummaryResponse(projectId == null ? projects.countAccessibleActive(userId) : 1,
                tasks.countMyPending(userId, projectId), tasks.countMyOverdue(userId, today, projectId),
                tasks.countMyDueSoon(userId, today, today.plusDays(7), projectId), stages, priorities);
    }

    @Transactional(readOnly = true)
    public PageResponse<DashboardTaskResponse> myTasks(Long userId, Long projectId, int page, int size) {
        validateProject(userId, projectId);
        int safeSize = Math.max(1, Math.min(size, 100));
        return PageResponse.from(tasks.findMyDashboardTasks(userId, projectId, LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")), PageRequest.of(Math.max(0, page), safeSize)), x -> x);
    }

    @Transactional(readOnly = true)
    public PageResponse<DashboardActivityResponse> activities(Long userId, Long projectId, int page, int size) {
        validateProject(userId, projectId);
        return PageResponse.from(activities.findDashboardActivities(userId, projectId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"))), x -> x);
    }

    private void validateProject(Long userId, Long projectId) {
        if (projectId == null) return;
        if (projects.findById(projectId).filter(project -> !Boolean.TRUE.equals(project.getArchived())).isEmpty())
            throw new ResourceNotFoundException("Projeto não encontrado");
        permissions.requireRole(projectId, userId, ProjectRole.VIEWER);
    }
}
