package com.devboard.service.github;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.entity.User;
import com.devboard.entity.enums.AuthProvider;
import com.devboard.exception.ExternalServiceException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.repository.UserRepository;
import com.devboard.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubOAuthServiceTest {

    private static final String CODE = "code-fake";
    private static final String STATE = "state-fake";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private GithubClient githubClient;

    @Mock
    private GithubOAuthStateService stateService;

    @InjectMocks
    private GithubOAuthService githubOAuthService;

    private GithubClient.GithubProfile profile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(githubOAuthService, "clientId", "client-id-fake");
        ReflectionTestUtils.setField(githubOAuthService, "clientSecret", "client-secret-fake");
        ReflectionTestUtils.setField(githubOAuthService, "callbackUrl", "http://localhost:8080/api/auth/github/callback");
        ReflectionTestUtils.setField(githubOAuthService, "frontendBaseUrl", "http://localhost:4200");

        profile = new GithubClient.GithubProfile(999L, "octocat", "Octo Cat", "http://avatar.example.com/octocat.png");

        lenient().when(stateService.consume(STATE)).thenReturn(Optional.of("/boards/1"));
        lenient().when(githubClient.exchangeCode(CODE, "client-id-fake", "client-secret-fake",
                        "http://localhost:8080/api/auth/github/callback"))
                .thenReturn(new GithubClient.TokenResponse("access-token-fake"));
        lenient().when(githubClient.fetchProfile("access-token-fake")).thenReturn(profile);
        lenient().when(githubClient.fetchPrimaryVerifiedEmail("access-token-fake")).thenReturn("octocat@example.com");
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(authService.buildAuthResponse(any(User.class))).thenReturn(
                AuthResponse.builder().token("jwt-fake").expiresAt(LocalDateTime.now().plusHours(24)).build());
    }

    @Test
    void handleCallback_deveCriarNovoUsuario_quandoNaoEncontradoPorGithubIdNemEmail() {
        when(userRepository.findByGithubId(999L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("octocat@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("octocat")).thenReturn(false);

        String redirectUrl = githubOAuthService.handleCallback(CODE, STATE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("octocat");
        assertThat(saved.getEmail()).isEqualTo("octocat@example.com");
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getGithubId()).isEqualTo(999L);
        assertThat(saved.getGithubToken()).isEqualTo("access-token-fake");
        assertThat(redirectUrl).startsWith("http://localhost:4200/auth/github/callback#token=jwt-fake");
        assertThat(redirectUrl).contains("returnUrl=%2Fboards%2F1");
    }

    @Test
    void handleCallback_deveGerarSufixoNumerico_quandoUsernameColide() {
        when(userRepository.findByGithubId(999L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("octocat@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("octocat")).thenReturn(true);
        when(userRepository.existsByUsername("octocat2")).thenReturn(false);

        githubOAuthService.handleCallback(CODE, STATE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("octocat2");
    }

    @Test
    void handleCallback_deveAtualizarUsuarioExistente_quandoEncontradoPorGithubId() {
        User existing = new User();
        existing.setId(5L);
        existing.setUsername("octocat");
        existing.setEmail("octocat@example.com");
        existing.setAuthProvider(AuthProvider.GITHUB);
        existing.setGithubId(999L);

        when(userRepository.findByGithubId(999L)).thenReturn(Optional.of(existing));

        githubOAuthService.handleCallback(CODE, STATE);

        verify(userRepository, never()).findByEmail(any());
        assertThat(existing.getGithubToken()).isEqualTo("access-token-fake");
        assertThat(existing.getGithubUsername()).isEqualTo("octocat");
    }

    @Test
    void handleCallback_deveVincularContaTradicional_quandoEncontradoPorEmail_semAlterarAuthProvider() {
        User traditional = new User();
        traditional.setId(7L);
        traditional.setUsername("joao_dev");
        traditional.setEmail("octocat@example.com");
        traditional.setPassword("hash-fake");
        traditional.setAuthProvider(AuthProvider.TRADITIONAL);

        when(userRepository.findByGithubId(999L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("octocat@example.com")).thenReturn(Optional.of(traditional));

        githubOAuthService.handleCallback(CODE, STATE);

        assertThat(traditional.getAuthProvider()).isEqualTo(AuthProvider.TRADITIONAL);
        assertThat(traditional.getPassword()).isEqualTo("hash-fake");
        assertThat(traditional.getGithubId()).isEqualTo(999L);
        assertThat(traditional.getGithubToken()).isEqualTo("access-token-fake");
    }

    @Test
    void handleCallback_deveLancarInvalidRequest_quandoStateInvalido() {
        when(stateService.consume(STATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubOAuthService.handleCallback(CODE, STATE))
                .isInstanceOf(InvalidRequestException.class);

        verify(githubClient, never()).exchangeCode(any(), any(), any(), any());
    }

    @Test
    void handleCallback_devePropagarExcecao_quandoGithubIndisponivel() {
        when(githubClient.exchangeCode(eq(CODE), any(), any(), any()))
                .thenThrow(new ExternalServiceException("GitHub indisponível"));

        assertThatThrownBy(() -> githubOAuthService.handleCallback(CODE, STATE))
                .isInstanceOf(ExternalServiceException.class);

        verify(userRepository, never()).save(any());
    }
}
