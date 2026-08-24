package com.devboard.service;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.dto.auth.RegisterRequest;
import com.devboard.entity.User;
import com.devboard.entity.enums.AuthProvider;
import com.devboard.exception.ConflictException;
import com.devboard.mapper.UserMapper;
import com.devboard.repository.UserRepository;
import com.devboard.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        String token = jwtTokenProvider.generateToken(saved);

        return AuthResponse.builder()
                .token(token)
                .expiresAt(jwtTokenProvider.resolveExpiration())
                .user(userMapper.toResponse(saved))
                .build();
    }
}
