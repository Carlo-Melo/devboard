package com.devboard.dto.project;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectSummaryResponse {

    private Long id;
    private String name;
    private String description;
    private UserResponse owner;
    private long memberCount;
    private boolean githubLinked;
    private LocalDateTime lastSyncAt;
    private LocalDateTime updatedAt;
}
