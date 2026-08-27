package com.devboard.controller;

import com.devboard.dto.auth.ForgotPasswordRequest;
import com.devboard.dto.auth.ResetPasswordRequest;
import com.devboard.dto.auth.TokenValidationResponse;
import com.devboard.dto.common.MessageResponse;
import com.devboard.ratelimit.RateLimit;
import com.devboard.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @RateLimit(key = "#request.email", limit = 3, window = 3600)
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.getEmail());
        return new MessageResponse(true, "Se o email existir em nossa base, você receberá as instruções de recuperação.");
    }

    @GetMapping("/reset-password/validate-token")
    public TokenValidationResponse validateToken(@RequestParam String token) {
        return passwordResetService.validateResetToken(token);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return new MessageResponse(true, "Senha redefinida com sucesso");
    }
}
