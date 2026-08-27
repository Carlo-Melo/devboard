package com.devboard.mapper;

import com.devboard.dto.board.BoardColumnResponse;
import com.devboard.dto.board.BoardResponse;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BoardMapper {

    public BoardResponse toResponse(Board board, List<BoardColumn> columns) {
        return BoardResponse.builder()
                .id(board.getId())
                .projectId(board.getProject().getId())
                .name(board.getName())
                .description(board.getDescription())
                .defaultBoard(board.getIsDefault())
                .columns(columns.stream().map(this::toColumnResponse).toList())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .build();
    }

    public BoardColumnResponse toColumnResponse(BoardColumn column) {
        return BoardColumnResponse.builder()
                .id(column.getId())
                .name(column.getName())
                .color(column.getColor())
                .position(column.getPosition())
                .role(column.getRole().name())
                .wipLimit(column.getWipLimit())
                .taskCount(0)
                .build();
    }
}
