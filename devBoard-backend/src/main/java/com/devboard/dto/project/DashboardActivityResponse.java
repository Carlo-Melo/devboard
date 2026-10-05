package com.devboard.dto.project;

import java.time.LocalDateTime;

public record DashboardActivityResponse(Long id, Long taskId, String taskTitle, Long projectId,
        String projectName, String authorName, String type, String description, LocalDateTime createdAt) {}
