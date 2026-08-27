package com.devboard.controller;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.project.CreateProjectRequest;
import com.devboard.dto.project.ProjectResponse;
import com.devboard.dto.project.ProjectSummaryResponse;
import com.devboard.dto.project.UpdateProjectRequest;
import com.devboard.security.SecurityUser;
import com.devboard.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request,
                                                   @AuthenticationPrincipal SecurityUser user) {
        ProjectResponse response = projectService.create(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProjectSummaryResponse>> list(
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(projectService.list(user.getId(), archived, page, size));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getById(@PathVariable Long projectId,
                                                    @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(projectService.getById(projectId, user.getId()));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long projectId,
                                                   @Valid @RequestBody UpdateProjectRequest request,
                                                   @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(projectService.update(projectId, request, user.getId()));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> archive(@PathVariable Long projectId,
                                         @AuthenticationPrincipal SecurityUser user) {
        projectService.archive(projectId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
