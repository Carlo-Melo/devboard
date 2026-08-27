package com.devboard.dto.board;

import lombok.Builder;
import lombok.Data;

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
}
