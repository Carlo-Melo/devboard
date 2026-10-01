package com.devboard.dto.project;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private UserResponse owner;
    private String currentUserRole;
    private boolean currentUserOwner;


    private boolean archived;

    private List<ProjectMemberResponse> members;
    private List<BoardSummaryResponse> boards;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
