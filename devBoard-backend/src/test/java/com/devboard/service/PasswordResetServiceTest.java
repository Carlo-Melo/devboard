package com.devboard.service;

import com.devboard.dto.auth.ResetPasswordRequest;
import com.devboard.dto.auth.TokenValidationResponse;
import com.devboard.entity.PasswordResetToken;
import com.devboard.entity.User;
import com.devboard.entity.enums.AuthProvider;
import com.devboard.entity.enums.PasswordResetTokenStatus;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.RateLimitExceededException;
import com.devboard.ratelimit.RateLimiterService;
import com.devboard.repository.PasswordResetTokenRepository;
import com.devboard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RateLimiterService rateLimiterService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, tokenRepository, passwordEncoder, emailService, rateLimiterService);
        ReflectionTestUtils.setField(passwordResetService, "expirationHours", 24L);
        ReflectionTestUtils.setField(passwordResetService, "maxAttempts", 5);
    }

    @Test
    void forgotPassword_deveCriarTokenEEnviarEmail_quandoEmailExisteComSenha() {
        User user = traditionalUser();
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserAndStatus(user, PasswordResetTokenStatus.PENDING)).thenReturn(List.of());

        passwordResetService.forgotPassword("joao@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PasswordResetTokenStatus.PENDING);
        verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq(user), anyString());
    }

    @Test
    void forgotPassword_naoDeveCriarToken_quandoEmailNaoExiste() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        passwordResetService.forgotPassword("ninguem@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void forgotPassword_naoDeveCriarToken_quandoContaGithubSemSenha() {
        User user = new User();
        user.setId(2L);
        user.setEmail("gh@example.com");
        user.setAuthProvider(AuthProvider.GITHUB);
        user.setPassword(null);
        when(userRepository.findByEmail("gh@example.com")).thenReturn(Optional.of(user));

        passwordResetService.forgotPassword("gh@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void validateResetToken_deveRetornarValido_quandoTokenPendenteENaoExpirado() {
        PasswordResetToken token = pendingToken(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        TokenValidationResponse response = passwordResetService.validateResetToken("raw-token");

        assertThat(response.isValid()).isTrue();
    }

    @Test
    void validateResetToken_deveLancarInvalidRequest_quandoTokenNaoExiste() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.validateResetToken("raw-token"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void validateResetToken_deveLancarInvalidRequestEExpirarToken_quandoPrazoVencido() {
        PasswordResetToken token = pendingToken(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.validateResetToken("raw-token"))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(token.getStatus()).isEqualTo(PasswordResetTokenStatus.EXPIRED);
    }

    @Test
    void resetPassword_deveAtualizarSenhaEMarcarTokenUsado_quandoValido() {
        when(rateLimiterService.tryConsume(anyString(), org.mockito.ArgumentMatchers.eq(5), anyLong())).thenReturn(true);
        User user = traditionalUser();
        PasswordResetToken token = pendingToken(LocalDateTime.now().plusHours(1));
        token.setUser(user);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NovaSenha123")).thenReturn("nova-hash");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("NovaSenha123");
        request.setConfirmPassword("NovaSenha123");

        passwordResetService.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("nova-hash");
        assertThat(token.getStatus()).isEqualTo(PasswordResetTokenStatus.USED);
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void resetPassword_deveLancarConflict_quandoTokenJaUtilizado() {
        when(rateLimiterService.tryConsume(anyString(), org.mockito.ArgumentMatchers.eq(5), anyLong())).thenReturn(true);
        PasswordResetToken token = pendingToken(LocalDateTime.now().plusHours(1));
        token.setStatus(PasswordResetTokenStatus.USED);
        token.setUser(traditionalUser());
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("NovaSenha123");
        request.setConfirmPassword("NovaSenha123");

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resetPassword_deveLancarRateLimitEInvalidarToken_quandoLimiteDeTentativasExcedido() {
        when(rateLimiterService.tryConsume(anyString(), org.mockito.ArgumentMatchers.eq(5), anyLong())).thenReturn(false);
        when(rateLimiterService.remainingSeconds(anyString())).thenReturn(120L);

        PasswordResetToken token = pendingToken(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("NovaSenha123");
        request.setConfirmPassword("NovaSenha123");

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(token.getStatus()).isEqualTo(PasswordResetTokenStatus.EXPIRED);
    }

    private User traditionalUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("joao_dev");
        user.setEmail("joao@example.com");
        user.setPassword("hash-fake");
        user.setAuthProvider(AuthProvider.TRADITIONAL);
        return user;
    }

    private PasswordResetToken pendingToken(LocalDateTime expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(1L);
        token.setTokenHash("hash");
        token.setStatus(PasswordResetTokenStatus.PENDING);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
