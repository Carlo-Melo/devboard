package com.devboard.dto.board;

import com.devboard.dto.task.TaskSummaryResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BoardColumnResponse {

    private Long id;
    private String name;
    private String color;
    private Integer position;
    private String role;
    private Integer wipLimit;
    private long taskCount;
    private List<TaskSummaryResponse> tasks;
}
