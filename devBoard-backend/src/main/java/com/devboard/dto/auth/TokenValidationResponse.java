package com.devboard.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TokenValidationResponse {

    private boolean valid;
    private LocalDateTime expiresAt;
}
