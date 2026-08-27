package com.devboard.service;

import com.devboard.dto.auth.ResetPasswordRequest;
import com.devboard.dto.auth.TokenValidationResponse;
import com.devboard.entity.PasswordResetToken;
import com.devboard.entity.User;
import com.devboard.entity.enums.PasswordResetTokenStatus;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.RateLimitExceededException;
import com.devboard.ratelimit.RateLimiterService;
import com.devboard.repository.PasswordResetTokenRepository;
import com.devboard.repository.UserRepository;
import com.devboard.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String RATE_LIMIT_KEY_PREFIX = "reset-password:";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RateLimiterService rateLimiterService;

    @Value("${app.password-reset.expiration-hours}")
    private long expirationHours;

    @Value("${app.password-reset.max-attempts}")
    private int maxAttempts;

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getPassword() == null) {
                return;
            }

            invalidatePendingTokens(user);

            String rawToken = HashUtil.generateSecureToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(HashUtil.sha256Hex(rawToken));
            token.setExpiresAt(LocalDateTime.now().plusHours(expirationHours));
            tokenRepository.save(token);

            emailService.sendPasswordResetEmail(user, rawToken);
            log.info("Token de recuperação de senha criado: userId={}", user.getId());
        });
    }

    @Transactional
    public TokenValidationResponse validateResetToken(String rawToken) {
        PasswordResetToken token = findPendingTokenOrThrow(rawToken);
        return TokenValidationResponse.builder()
                .valid(true)
                .expiresAt(token.getExpiresAt())
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + request.getToken();
        boolean allowed = rateLimiterService.tryConsume(rateLimitKey, maxAttempts, expirationHours * 3600L);

        if (!allowed) {
            tokenRepository.findByTokenHash(HashUtil.sha256Hex(request.getToken()))
                    .ifPresent(token -> token.setStatus(PasswordResetTokenStatus.EXPIRED));
            throw new RateLimitExceededException(
                    "Limite de tentativas excedido para este token. Solicite uma nova recuperação de senha.",
                    rateLimiterService.remainingSeconds(rateLimitKey));
        }

        PasswordResetToken token = findValidTokenOrThrow(request.getToken());

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        token.setStatus(PasswordResetTokenStatus.USED);
        token.setUsedAt(LocalDateTime.now());

        log.info("Senha redefinida via token de recuperação: userId={}", user.getId());
    }

    private PasswordResetToken findPendingTokenOrThrow(String rawToken) {
        PasswordResetToken token = tokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidRequestException("Token inválido ou expirado"));

        if (token.getStatus() != PasswordResetTokenStatus.PENDING || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (token.getStatus() == PasswordResetTokenStatus.PENDING) {
                token.setStatus(PasswordResetTokenStatus.EXPIRED);
            }
            throw new InvalidRequestException("Token inválido ou expirado");
        }

        return token;
    }

    private PasswordResetToken findValidTokenOrThrow(String rawToken) {
        PasswordResetToken token = tokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidRequestException("Token inválido ou expirado"));

        if (token.getStatus() == PasswordResetTokenStatus.USED) {
            throw new ConflictException("Token já utilizado");
        }
        if (token.getStatus() == PasswordResetTokenStatus.EXPIRED || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setStatus(PasswordResetTokenStatus.EXPIRED);
            throw new InvalidRequestException("Token inválido ou expirado");
        }

        return token;
    }

    private void invalidatePendingTokens(User user) {
        tokenRepository.findByUserAndStatus(user, PasswordResetTokenStatus.PENDING)
                .forEach(token -> token.setStatus(PasswordResetTokenStatus.EXPIRED));
    }
}
