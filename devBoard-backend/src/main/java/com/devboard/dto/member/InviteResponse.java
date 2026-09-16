package com.devboard.dto.member;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InviteResponse {
    private Long id;
    private Long projectId;
    private String type;
    private String email;
    private String role;
    private String status;
    private String acceptanceUrl;
    private UserResponse invitedBy;
    private Integer uses;
    private Integer maxUses;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
