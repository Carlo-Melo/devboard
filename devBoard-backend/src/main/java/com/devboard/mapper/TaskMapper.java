package com.devboard.mapper;

import com.devboard.dto.task.ActivityResponse;
import com.devboard.dto.task.CommentResponse;
import com.devboard.dto.task.TaskResponse;
import com.devboard.dto.task.TaskSummaryResponse;
import com.devboard.entity.Task;
import com.devboard.entity.TaskActivity;
import com.devboard.entity.TaskComment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final UserMapper userMapper;

    public TaskResponse toResponse(Task task, List<TaskComment> comments, List<TaskActivity> activities) {
        return TaskResponse.builder()
                .id(task.getId())
                .projectId(task.resolveProjectId())
                .boardId(task.getColumn().getBoard().getId())
                .columnId(task.getColumn().getId())
                .columnName(task.getColumn().getName())
                .title(task.getTitle())
                .description(task.getDescription())
                .position(task.getPosition())
                .type(task.getType().name())
                .priority(task.getPriority().name())
                .assignee(task.getAssignee() != null ? userMapper.toResponse(task.getAssignee()) : null)
                .collaborators(task.getCollaborators().stream().map(userMapper::toResponse).toList())
                .creator(userMapper.toResponse(task.getCreator()))
                .dueDate(task.getDueDate())
                .estimate(task.getEstimate())
                .archived(task.getArchived())
                .archivedAt(task.getArchivedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .comments(comments.stream().map(this::toCommentResponse).toList())
                .activities(activities.stream().map(this::toActivityResponse).toList())
                .build();
    }

    public TaskSummaryResponse toSummary(Task task, long commentCount) {
        return TaskSummaryResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .type(task.getType().name())
                .priority(task.getPriority().name())
                .assignee(task.getAssignee() != null ? userMapper.toResponse(task.getAssignee()) : null)
                .dueDate(task.getDueDate())
                .estimate(task.getEstimate())
                .position(task.getPosition())
                .commentCount(commentCount)
                .build();
    }

    public CommentResponse toCommentResponse(TaskComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .taskId(comment.getTask().getId())
                .author(userMapper.toResponse(comment.getAuthor()))
                .content(comment.getContent())
                .edited(comment.getEdited())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    public ActivityResponse toActivityResponse(TaskActivity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .taskId(activity.getTask().getId())
                .author(activity.getAuthor() != null ? userMapper.toResponse(activity.getAuthor()) : null)
                .type(activity.getType().name())
                .description(activity.getDescription())
                .metadata(activity.getMetadata())
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
