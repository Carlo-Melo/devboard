package com.devboard.mapper;

import com.devboard.dto.common.UserResponse;
import com.devboard.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .authProvider(user.getAuthProvider().name())
                .githubConnected(user.getGithubId() != null)
                .build();
    }
}
