package com.devboard.dto.board;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BoardResponse {

    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private boolean defaultBoard;
    private List<BoardColumnResponse> columns;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
