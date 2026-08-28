package com.devboard.mapper;

import com.devboard.dto.board.BoardColumnResponse;
import com.devboard.dto.board.BoardResponse;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BoardMapper {

    private final TaskMapper taskMapper;

    public BoardResponse toResponse(Board board, List<BoardColumn> columns) {
        return toResponse(board, columns, Map.of(), Map.of());
    }

    /**
     * Leitura do quadro (spec-board-kanban.md 4.3): tarefas já vêm agrupadas por coluna a partir
     * de uma única consulta — {@code tasksByColumnId} e {@code commentCountByTaskId} nunca são
     * buscados aqui, apenas repassados.
     */
    public BoardResponse toResponse(Board board, List<BoardColumn> columns,
                                     Map<Long, List<Task>> tasksByColumnId,
                                     Map<Long, Long> commentCountByTaskId) {
        return BoardResponse.builder()
                .id(board.getId())
                .projectId(board.getProject().getId())
                .name(board.getName())
                .description(board.getDescription())
                .defaultBoard(board.getIsDefault())
                .columns(columns.stream()
                        .map(column -> toColumnResponse(column, tasksByColumnId.getOrDefault(column.getId(), List.of()), commentCountByTaskId))
                        .toList())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .build();
    }

    public BoardColumnResponse toColumnResponse(BoardColumn column) {
        return toColumnResponse(column, List.of(), Map.of());
    }

    private BoardColumnResponse toColumnResponse(BoardColumn column, List<Task> tasks, Map<Long, Long> commentCountByTaskId) {
        return BoardColumnResponse.builder()
                .id(column.getId())
                .name(column.getName())
                .color(column.getColor())
                .position(column.getPosition())
                .role(column.getRole().name())
                .wipLimit(column.getWipLimit())
                .taskCount(tasks.size())
                .tasks(tasks.stream()
                        .map(task -> taskMapper.toSummary(task, commentCountByTaskId.getOrDefault(task.getId(), 0L)))
                        .toList())
                .build();
    }
}
