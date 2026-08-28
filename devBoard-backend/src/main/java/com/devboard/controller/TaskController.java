package com.devboard.controller;

import com.devboard.dto.common.PageResponse;
import com.devboard.dto.task.ActivityResponse;
import com.devboard.dto.task.CommentResponse;
import com.devboard.dto.task.CreateCommentRequest;
import com.devboard.dto.task.CreateTaskRequest;
import com.devboard.dto.task.MoveTaskRequest;
import com.devboard.dto.task.TaskResponse;
import com.devboard.dto.task.UpdateTaskRequest;
import com.devboard.entity.enums.TaskActivityType;
import com.devboard.security.SecurityUser;
import com.devboard.service.ActivityService;
import com.devboard.service.TaskCommentService;
import com.devboard.service.TaskService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskCommentService taskCommentService;
    private final ActivityService activityService;

    @PostMapping("/api/tasks")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request,
                                                @AuthenticationPrincipal SecurityUser user) {
        TaskResponse response = taskService.create(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getById(@PathVariable Long taskId,
                                                 @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(taskService.getById(taskId, user.getId()));
    }

    @PutMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long taskId,
                                                @Valid @RequestBody UpdateTaskRequest request,
                                                @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(taskService.update(taskId, request, user.getId()));
    }

    @PutMapping("/api/tasks/{taskId}/move")
    public ResponseEntity<TaskResponse> move(@PathVariable Long taskId,
                                              @Valid @RequestBody MoveTaskRequest request,
                                              @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(taskService.move(taskId, request, user.getId()));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> archive(@PathVariable Long taskId,
                                         @AuthenticationPrincipal SecurityUser user) {
        taskService.archive(taskId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<CommentResponse> createComment(@PathVariable Long taskId,
                                                           @Valid @RequestBody CreateCommentRequest request,
                                                           @AuthenticationPrincipal SecurityUser user) {
        CommentResponse response = taskCommentService.create(taskId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<PageResponse<CommentResponse>> listComments(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(taskCommentService.list(taskId, page, size, user.getId()));
    }

    @GetMapping("/api/tasks/{taskId}/activities")
    public ResponseEntity<PageResponse<ActivityResponse>> listActivities(
            @PathVariable Long taskId,
            @RequestParam(required = false) TaskActivityType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(activityService.listByTask(taskId, type, page, size, user.getId()));
    }
}
