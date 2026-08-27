package com.devboard.dto.auth;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String fullName;
    private String avatarUrl;
}
