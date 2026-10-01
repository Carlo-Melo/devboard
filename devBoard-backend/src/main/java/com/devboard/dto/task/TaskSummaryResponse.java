package com.devboard.dto.task;

import com.devboard.dto.common.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** Versão enxuta usada na leitura do quadro — nunca traz descrição, comentários ou atividades. */
@Data
@Builder
public class TaskSummaryResponse {

    private Long githubIssueId;
    private String githubIssueUrl;
    private Long githubPrId;
    private String githubPrUrl;
    private String githubPrState;
    private String branch;
    private java.util.List<com.devboard.dto.label.LabelResponse> labels;
    private Long id;
    private String title;
    private String type;
    private String priority;
    private UserResponse assignee;
    private LocalDate dueDate;
    private Integer estimate;
    private Integer position;
    private long commentCount;
}
