package com.devboard.controller;

import com.devboard.dto.board.BoardResponse;
import com.devboard.dto.board.CreateBoardRequest;
import com.devboard.dto.board.UpdateBoardRequest;
import com.devboard.security.SecurityUser;
import com.devboard.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @PostMapping("/api/projects/{projectId}/boards")
    public ResponseEntity<BoardResponse> create(@PathVariable Long projectId,
                                                 @Valid @RequestBody CreateBoardRequest request,
                                                 @AuthenticationPrincipal SecurityUser user) {
        BoardResponse response = boardService.createBoard(projectId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/projects/{projectId}/boards")
    public ResponseEntity<List<BoardResponse>> list(@PathVariable Long projectId,
                                                      @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(boardService.listBoards(projectId, user.getId()));
    }

    @GetMapping("/api/boards/{boardId}")
    public ResponseEntity<BoardResponse> getById(@PathVariable Long boardId,
                                                  @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(boardService.getBoardView(boardId, user.getId()));
    }

    @PutMapping("/api/boards/{boardId}")
    public ResponseEntity<BoardResponse> update(@PathVariable Long boardId,
                                                 @Valid @RequestBody UpdateBoardRequest request,
                                                 @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(boardService.updateBoard(boardId, request, user.getId()));
    }

    @DeleteMapping("/api/boards/{boardId}")
    public ResponseEntity<Void> delete(@PathVariable Long boardId,
                                        @AuthenticationPrincipal SecurityUser user) {
        boardService.deleteBoard(boardId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
