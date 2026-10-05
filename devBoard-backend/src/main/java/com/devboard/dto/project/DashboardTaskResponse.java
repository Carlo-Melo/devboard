package com.devboard.dto.project;

import java.time.LocalDate;

public record DashboardTaskResponse(Long id, String title, Long projectId, String projectName,
        Long boardId, String boardName, String columnName, String columnRole, String priority,
        LocalDate dueDate) {}
