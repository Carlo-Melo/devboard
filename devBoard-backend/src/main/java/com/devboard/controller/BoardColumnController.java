package com.devboard.controller;

import com.devboard.dto.board.BoardColumnResponse;
import com.devboard.dto.board.CreateColumnRequest;
import com.devboard.dto.board.ReorderColumnsRequest;
import com.devboard.dto.board.UpdateColumnRequest;
import com.devboard.security.SecurityUser;
import com.devboard.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BoardColumnController {

    private final BoardService boardService;

    @PostMapping("/api/boards/{boardId}/columns")
    public ResponseEntity<BoardColumnResponse> create(@PathVariable Long boardId,
                                                        @Valid @RequestBody CreateColumnRequest request,
                                                        @AuthenticationPrincipal SecurityUser user) {
        BoardColumnResponse response = boardService.createColumn(boardId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/columns/{columnId}")
    public ResponseEntity<BoardColumnResponse> update(@PathVariable Long columnId,
                                                        @Valid @RequestBody UpdateColumnRequest request,
                                                        @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(boardService.updateColumn(columnId, request, user.getId()));
    }

    @DeleteMapping("/api/columns/{columnId}")
    public ResponseEntity<Void> delete(@PathVariable Long columnId,
                                        @RequestParam(required = false) Long moveTasksTo,
                                        @AuthenticationPrincipal SecurityUser user) {
        boardService.deleteColumn(columnId, moveTasksTo, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/boards/{boardId}/columns/reorder")
    public ResponseEntity<List<BoardColumnResponse>> reorder(@PathVariable Long boardId,
                                                               @Valid @RequestBody ReorderColumnsRequest request,
                                                               @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(boardService.reorderColumns(boardId, request, user.getId()));
    }
}
