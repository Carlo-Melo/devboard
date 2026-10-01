package com.devboard.dto.task;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskResponse {

    private Long githubIssueId;
    private String githubIssueUrl;
    private Long githubPrId;
    private String githubPrUrl;
    private String githubPrState;
    private String branch;
    private java.util.List<com.devboard.dto.label.LabelResponse> labels;
    private Long id;
    private Long projectId;
    private Long boardId;
    private Long columnId;
    private String columnName;
    private String title;
    private String description;
    private Integer position;
    private String type;
    private String priority;
    private UserResponse assignee;
    private List<UserResponse> collaborators;
    private UserResponse creator;
    private LocalDate dueDate;
    private Integer estimate;
    private boolean archived;
    private LocalDateTime archivedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponse> comments;
    private List<ActivityResponse> activities;
}
