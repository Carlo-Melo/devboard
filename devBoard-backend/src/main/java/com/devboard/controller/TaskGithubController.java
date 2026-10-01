package com.devboard.controller;
import com.devboard.dto.task.*;
import com.devboard.security.SecurityUser;
import com.devboard.service.github.TaskGithubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.Callable;
@RestController @RequiredArgsConstructor
public class TaskGithubController {
    private final TaskGithubService service;
    @PostMapping("/api/tasks/{id}/branch") public ResponseEntity<Void> branch(@PathVariable Long id, @Valid @RequestBody CreateBranchRequest r, @AuthenticationPrincipal SecurityUser u) { service.branch(id,r,u.getId()); return ResponseEntity.accepted().build(); }
    @PostMapping("/api/tasks/{id}/github-issue") public Callable<ResponseEntity<Void>> link(@PathVariable Long id, @Valid @RequestBody LinkIssueRequest r, @AuthenticationPrincipal SecurityUser u) { return () -> { service.link(id,r.githubIssueId(),u.getId()); return ResponseEntity.noContent().build(); }; }
    @DeleteMapping("/api/tasks/{id}/github-issue") public ResponseEntity<Void> unlink(@PathVariable Long id, @AuthenticationPrincipal SecurityUser u) { service.unlink(id,u.getId()); return ResponseEntity.noContent().build(); }
    @PostMapping("/api/tasks/{id}/github-issue/create") public ResponseEntity<Void> create(@PathVariable Long id, @AuthenticationPrincipal SecurityUser u) { service.createIssue(id,u.getId()); return ResponseEntity.accepted().build(); }
}
