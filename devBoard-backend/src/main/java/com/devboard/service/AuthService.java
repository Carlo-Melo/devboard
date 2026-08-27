package com.devboard.service;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.dto.auth.ChangePasswordRequest;
import com.devboard.dto.auth.LoginRequest;
import com.devboard.dto.auth.RegisterRequest;
import com.devboard.dto.auth.UpdateProfileRequest;
import com.devboard.dto.common.UserResponse;
import com.devboard.entity.User;
import com.devboard.entity.enums.AuthProvider;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.exception.UnauthorizedException;
import com.devboard.mapper.UserMapper;
import com.devboard.repository.UserRepository;
import com.devboard.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username já está em uso");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email já está em uso");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setAuthProvider(AuthProvider.TRADITIONAL);

        User saved = userRepository.save(user);
        log.info("Usuário registrado: id={}", saved.getId());

        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getEmailOrUsername();
        Optional<User> userOpt = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier);

        User user = userOpt.orElseThrow(this::invalidCredentials);

        if (user.getPassword() == null) {
            throw new UnauthorizedException("Esta conta usa login via GitHub. Entre pelo GitHub.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials();
        }

        log.info("Login bem-sucedido: userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return userMapper.toResponse(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        user.setFullName(request.getFullName());
        user.setAvatarUrl(request.getAvatarUrl());
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserOrThrow(userId);

        if (user.getPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AccessDeniedException("Senha atual incorreta");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new InvalidRequestException("Nova senha deve ser diferente da atual");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        log.info("Senha alterada: userId={}", userId);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Credenciais inválidas");
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .expiresAt(jwtTokenProvider.resolveExpiration())
                .user(userMapper.toResponse(user))
                .build();
    }
}
