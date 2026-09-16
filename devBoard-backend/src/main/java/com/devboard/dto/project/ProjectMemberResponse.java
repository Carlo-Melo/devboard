package com.devboard.dto.project;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectMemberResponse {

    private Long id;
    private Long projectId;
    private UserResponse user;
    private String role;
    private UserResponse invitedBy;
    private java.time.LocalDateTime joinedAt;
}
