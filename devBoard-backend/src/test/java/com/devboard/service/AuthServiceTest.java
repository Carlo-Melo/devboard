package com.devboard.service;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.dto.auth.RegisterRequest;
import com.devboard.entity.User;
import com.devboard.exception.ConflictException;
import com.devboard.mapper.UserMapper;
import com.devboard.repository.UserRepository;
import com.devboard.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setUsername("joao_dev");
        request.setEmail("joao@example.com");
        request.setPassword("SecurePass123");
        request.setConfirmPassword("SecurePass123");
        request.setFullName("João Silva");
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

        when(jwtTokenProvider.generateToken(saved)).thenReturn("token-fake");
        when(jwtTokenProvider.resolveExpiration()).thenReturn(LocalDateTime.now().plusHours(24));

        com.devboard.dto.common.UserResponse mappedUser = com.devboard.dto.common.UserResponse.builder()
                .id(1L)
                .username("joao_dev")
                .build();
        when(userMapper.toResponse(saved)).thenReturn(mappedUser);

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("token-fake");
        assertThat(response.getUser().getUsername()).isEqualTo("joao_dev");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_deveLancarConflict_quandoUsernameJaExiste() {
        when(userRepository.existsByUsername("joao_dev")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void register_deveLancarConflict_quandoEmailJaExiste() {
        when(userRepository.existsByUsername("joao_dev")).thenReturn(false);
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email");
    }
}
