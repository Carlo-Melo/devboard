package com.devboard.dto.project;

import java.util.Map;

public record DashboardSummaryResponse(long activeProjects, long pendingTasks, long overdueTasks,
        long dueSoonTasks, Map<String, Long> tasksByStage, Map<String, Long> pendingByPriority) {}
