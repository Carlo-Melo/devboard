package com.devboard.service;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.dto.auth.ChangePasswordRequest;
import com.devboard.dto.auth.LoginRequest;
import com.devboard.dto.auth.RegisterRequest;
import com.devboard.dto.auth.UpdateProfileRequest;
import com.devboard.entity.User;
import com.devboard.entity.enums.AuthProvider;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.UnauthorizedException;
import com.devboard.mapper.UserMapper;
import com.devboard.repository.UserRepository;
import com.devboard.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("joao_dev");
        registerRequest.setEmail("joao@example.com");
        registerRequest.setPassword("SecurePass123");
        registerRequest.setConfirmPassword("SecurePass123");
        registerRequest.setFullName("João Silva");

        lenient().when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("token-fake");
        lenient().when(jwtTokenProvider.resolveExpiration()).thenReturn(LocalDateTime.now().plusHours(24));
        lenient().when(userMapper.toResponse(any(User.class)))
                .thenReturn(com.devboard.dto.common.UserResponse.builder().id(1L).username("joao_dev").build());
    }

    @Test
    void register_deveCriarUsuarioERetornarToken_quandoDadosValidos() {
        when(userRepository.existsByUsername("joao_dev")).thenReturn(false);
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hash-fake");

        User saved = new User();
        saved.setId(1L);
        saved.setUsername("joao_dev");
        saved.setEmail("joao@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("token-fake");
        assertThat(response.getUser().getUsername()).isEqualTo("joao_dev");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_deveLancarConflict_quandoUsernameJaExiste() {
        when(userRepository.existsByUsername("joao_dev")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void register_deveLancarConflict_quandoEmailJaExiste() {
        when(userRepository.existsByUsername("joao_dev")).thenReturn(false);
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void login_deveAutenticar_quandoIdentificadorEhEmail() {
        User user = traditionalUser();
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePass123", "hash-fake")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setEmailOrUsername("joao@example.com");
        request.setPassword("SecurePass123");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("token-fake");
    }

    @Test
    void login_deveAutenticar_quandoIdentificadorEhUsername() {
        User user = traditionalUser();
        when(userRepository.findByUsername("joao_dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePass123", "hash-fake")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setEmailOrUsername("joao_dev");
        request.setPassword("SecurePass123");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("token-fake");
    }

    @Test
    void login_deveLancarUnauthorized_quandoUsuarioNaoExiste() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmailOrUsername("ninguem@example.com");
        request.setPassword("qualquer");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    void login_deveLancarUnauthorizedComMensagemIdentica_quandoSenhaErrada() {
        User user = traditionalUser();
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash-fake")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setEmailOrUsername("joao@example.com");
        request.setPassword("errada");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    void login_deveOrientarLoginGithub_quandoContaGithubSemSenha() {
        User user = new User();
        user.setId(2L);
        user.setEmail("gh@example.com");
        user.setUsername("gh_user");
        user.setAuthProvider(AuthProvider.GITHUB);
        user.setPassword(null);
        when(userRepository.findByEmail("gh@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setEmailOrUsername("gh@example.com");
        request.setPassword("qualquer");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("GitHub");
    }

    @Test
    void changePassword_deveAlterarSenha_quandoDadosValidos() {
        User user = traditionalUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePass123", "hash-fake")).thenReturn(true);
        when(passwordEncoder.matches("NovaSenha123", "hash-fake")).thenReturn(false);
        when(passwordEncoder.encode("NovaSenha123")).thenReturn("nova-hash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("SecurePass123");
        request.setNewPassword("NovaSenha123");
        request.setConfirmPassword("NovaSenha123");

        authService.changePassword(1L, request);

        assertThat(user.getPassword()).isEqualTo("nova-hash");
    }

    @Test
    void changePassword_deveLancarAccessDenied_quandoSenhaAtualIncorreta() {
        User user = traditionalUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash-fake")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("errada");
        request.setNewPassword("NovaSenha123");
        request.setConfirmPassword("NovaSenha123");

        assertThatThrownBy(() -> authService.changePassword(1L, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void changePassword_deveLancarInvalidRequest_quandoNovaSenhaIgualAtual() {
        User user = traditionalUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePass123", "hash-fake")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("SecurePass123");
        request.setNewPassword("SecurePass123");
        request.setConfirmPassword("SecurePass123");

        assertThatThrownBy(() -> authService.changePassword(1L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateProfile_deveAtualizarNomeEAvatar() {
        User user = traditionalUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Novo Nome");
        request.setAvatarUrl("http://avatar.example.com/a.png");

        authService.updateProfile(1L, request);

        assertThat(user.getFullName()).isEqualTo("Novo Nome");
        assertThat(user.getAvatarUrl()).isEqualTo("http://avatar.example.com/a.png");
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
}
