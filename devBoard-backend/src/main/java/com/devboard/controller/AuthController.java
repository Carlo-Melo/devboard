package com.devboard.controller;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.dto.auth.ChangePasswordRequest;
import com.devboard.dto.auth.LoginRequest;
import com.devboard.dto.auth.RegisterRequest;
import com.devboard.dto.auth.UpdateProfileRequest;
import com.devboard.dto.common.MessageResponse;
import com.devboard.dto.common.UserResponse;
import com.devboard.exception.InvalidRequestException;
import com.devboard.ratelimit.RateLimit;
import com.devboard.security.SecurityUser;
import com.devboard.service.AuthService;
import com.devboard.service.github.GithubOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GithubOAuthService githubOAuthService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @RateLimit(key = "#httpRequest.remoteAddr", limit = 10, window = 3600)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/github/login")
    public ResponseEntity<Void> githubLogin(@RequestParam(required = false) String redirectUri) {
        String authorizationUrl = githubOAuthService.buildAuthorizationUrl(redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizationUrl)).build();
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> githubCallback(@RequestParam(required = false) String code,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) String error) {
        if (error != null) {
            throw new AccessDeniedException("Autorização do GitHub negada");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidRequestException("Code do GitHub ausente");
        }

        String redirectUrl = githubOAuthService.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        return ResponseEntity.ok(new MessageResponse(true, "Sessão encerrada"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(authService.getProfile(user.getId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal SecurityUser user,
                                                       @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(user.getId(), request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal SecurityUser user,
                                                           @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getId(), request);
        return ResponseEntity.ok(new MessageResponse(true, "Senha alterada com sucesso"));
    }
}
