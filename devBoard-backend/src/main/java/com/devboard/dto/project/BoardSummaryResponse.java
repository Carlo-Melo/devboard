package com.devboard.dto.project;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BoardSummaryResponse {

    private Long id;
    private String name;
    private boolean defaultBoard;
}
