package com.devboard.controller;
import com.devboard.dto.label.*;
import com.devboard.security.SecurityUser;
import com.devboard.service.LabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
public class LabelController {
    private final LabelService service;
    @GetMapping("/api/projects/{projectId}/labels") public List<LabelResponse> list(@PathVariable Long projectId, @AuthenticationPrincipal SecurityUser u) { return service.list(projectId,u.getId()); }
    @PostMapping("/api/projects/{projectId}/labels") public ResponseEntity<LabelResponse> create(@PathVariable Long projectId, @Valid @RequestBody LabelRequest r, @AuthenticationPrincipal SecurityUser u) { return ResponseEntity.status(201).body(service.create(projectId,r,u.getId())); }
    @PutMapping("/api/labels/{id}") public LabelResponse update(@PathVariable Long id, @Valid @RequestBody LabelRequest r, @AuthenticationPrincipal SecurityUser u) { return service.update(id,r,u.getId()); }
    @DeleteMapping("/api/labels/{id}") public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser u) { service.delete(id,u.getId()); return ResponseEntity.noContent().build(); }
    @PostMapping("/api/tasks/{id}/labels") public List<LabelResponse> apply(@PathVariable Long id, @RequestBody List<Long> labelIds, @AuthenticationPrincipal SecurityUser u) { return service.apply(id,labelIds,u.getId()); }
    @DeleteMapping("/api/tasks/{id}/labels/{labelId}") public ResponseEntity<Void> remove(@PathVariable Long id, @PathVariable Long labelId, @AuthenticationPrincipal SecurityUser u) { service.remove(id,labelId,u.getId()); return ResponseEntity.noContent().build(); }
}
