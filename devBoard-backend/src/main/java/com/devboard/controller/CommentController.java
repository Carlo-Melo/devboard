package com.devboard.controller;

import com.devboard.dto.task.CommentResponse;
import com.devboard.dto.task.UpdateCommentRequest;
import com.devboard.security.SecurityUser;
import com.devboard.service.TaskCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final TaskCommentService taskCommentService;

    @PutMapping("/api/comments/{commentId}")
    public ResponseEntity<CommentResponse> update(@PathVariable Long commentId,
                                                    @Valid @RequestBody UpdateCommentRequest request,
                                                    @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(taskCommentService.update(commentId, request, user.getId()));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId,
                                        @AuthenticationPrincipal SecurityUser user) {
        taskCommentService.delete(commentId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
