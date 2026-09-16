package com.devboard.dto.member;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InvitePublicResponse {
    private String projectName;
    private String inviterName;
    private String role;
    private LocalDateTime expiresAt;
}
