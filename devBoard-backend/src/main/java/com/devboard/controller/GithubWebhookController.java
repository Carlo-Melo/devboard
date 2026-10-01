package com.devboard.controller;
import com.devboard.ratelimit.RateLimit;
import com.devboard.service.github.GithubWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class GithubWebhookController {
    private final GithubWebhookService service;
    @PostMapping("/webhook/github")
    @RateLimit(key = "#request.remoteAddr", limit = 100, window = 60)
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
            @RequestHeader(value="X-Hub-Signature-256", required=false) String signature,
            @RequestHeader(value="X-GitHub-Delivery", required=false) String delivery,
            @RequestHeader(value="X-GitHub-Event", required=false) String event, HttpServletRequest request) {
        service.receive(body, signature, delivery, event);
        return ResponseEntity.accepted().build();
    }
}
