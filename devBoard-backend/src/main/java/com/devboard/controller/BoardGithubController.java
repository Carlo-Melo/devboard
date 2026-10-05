package com.devboard.controller;
import com.devboard.dto.board.*;
import com.devboard.ratelimit.RateLimit;
import com.devboard.security.SecurityUser;
import com.devboard.service.github.BoardGithubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.concurrent.Callable;

@RestController @RequiredArgsConstructor
public class BoardGithubController {
    private final BoardGithubService service;
    @GetMapping("/api/github-repos")
    public Callable<List<GithubRepoResponse>> repositories(@AuthenticationPrincipal SecurityUser u, @RequestParam(required=false) String search) {
        return () -> service.repositories(u.getId(), search);
    }
    @GetMapping("/api/github-repos/public")
    public Callable<com.devboard.dto.common.PageResponse<GithubRepoResponse>> publicRepositories(
            @AuthenticationPrincipal SecurityUser u, @RequestParam(required=false) String search,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return () -> service.publicRepositories(u.getId(), search, page, size);
    }
    @GetMapping("/api/github-repos/available")
    public Callable<com.devboard.dto.common.PageResponse<GithubRepoResponse>> availableRepositories(
            @AuthenticationPrincipal SecurityUser u, @RequestParam(required=false) String search,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return () -> service.availableRepositories(u.getId(), search, page, size);
    }
    @GetMapping("/api/github-repos/destinations")
    public com.devboard.dto.common.PageResponse<com.devboard.dto.board.GithubDestinationResponse> destinations(
            @AuthenticationPrincipal SecurityUser u, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="100") int size) {
        return service.destinations(u.getId(), page, size);
    }
    @PostMapping("/api/boards/{boardId}/link-github")
    public Callable<GithubSettingsResponse> link(@PathVariable Long boardId, @Valid @RequestBody LinkGithubRequest r, @AuthenticationPrincipal SecurityUser u) {
        return () -> service.link(boardId, r, u.getId());
    }
    @DeleteMapping("/api/boards/{boardId}/link-github")
    public ResponseEntity<Void> unlink(@PathVariable Long boardId, @AuthenticationPrincipal SecurityUser u) {
        service.unlink(boardId, u.getId()); return ResponseEntity.noContent().build();
    }
    @PostMapping("/api/boards/{boardId}/sync-github")
    @RateLimit(key = "#boardId", limit = 1, window = 300)
    public ResponseEntity<Void> sync(@PathVariable Long boardId, @AuthenticationPrincipal SecurityUser u) {
        service.sync(boardId, u.getId()); return ResponseEntity.accepted().build();
    }
    @GetMapping("/api/boards/{boardId}/github-settings")
    public GithubSettingsResponse settings(@PathVariable Long boardId, @AuthenticationPrincipal SecurityUser u) { return service.settings(boardId, u.getId()); }
    @PutMapping("/api/boards/{boardId}/github-settings")
    public GithubSettingsResponse update(@PathVariable Long boardId, @Valid @RequestBody GithubSettingsRequest r, @AuthenticationPrincipal SecurityUser u) { return service.update(boardId, r, u.getId()); }
}
