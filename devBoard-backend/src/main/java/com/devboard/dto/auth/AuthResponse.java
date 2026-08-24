package com.devboard.dto.auth;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuthResponse {

    private String token;
    private LocalDateTime expiresAt;
    private UserResponse user;
}
