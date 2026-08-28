package com.devboard.dto.task;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ActivityResponse {

    private Long id;
    private Long taskId;
    private UserResponse author;
    private String type;
    private String description;
    private String metadata;
    private LocalDateTime createdAt;
}
