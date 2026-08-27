package com.devboard.controller;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.dto.auth.ChangePasswordRequest;
import com.devboard.dto.auth.LoginRequest;
import com.devboard.dto.auth.RegisterRequest;
import com.devboard.dto.auth.UpdateProfileRequest;
import com.devboard.dto.common.MessageResponse;
import com.devboard.dto.common.UserResponse;
import com.devboard.ratelimit.RateLimit;
import com.devboard.security.SecurityUser;
import com.devboard.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
