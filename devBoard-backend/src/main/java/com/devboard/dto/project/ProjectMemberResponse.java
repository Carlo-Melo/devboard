package com.devboard.dto.project;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectMemberResponse {

    private UserResponse user;
    private String role;
}
