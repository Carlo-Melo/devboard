package com.devboard.controller;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.project.*;
import com.devboard.security.SecurityUser;
import com.devboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@RequestParam(required = false) Long projectId, @AuthenticationPrincipal SecurityUser user) {
        return dashboardService.summary(user.getId(), projectId);
    }
    @GetMapping("/my-tasks")
    public PageResponse<DashboardTaskResponse> tasks(@RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "8") int size,
            @AuthenticationPrincipal SecurityUser user) {
        return dashboardService.myTasks(user.getId(), projectId, page, size);
    }
    @GetMapping("/activities")
    public PageResponse<DashboardActivityResponse> activities(@RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal SecurityUser user) {
        return dashboardService.activities(user.getId(), projectId, page, size);
    }
}
