package com.devboard.service;

import com.devboard.dto.board.BoardColumnResponse;
import com.devboard.dto.board.BoardResponse;
import com.devboard.dto.board.ColumnDefinitionRequest;
import com.devboard.dto.board.CreateBoardRequest;
import com.devboard.dto.board.CreateColumnRequest;
import com.devboard.dto.board.ReorderColumnsRequest;
import com.devboard.dto.board.UpdateBoardRequest;
import com.devboard.dto.board.UpdateColumnRequest;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Project;
import com.devboard.entity.enums.ColumnRole;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.BoardMapper;
import com.devboard.repository.BoardColumnRepository;
import com.devboard.repository.BoardRepository;
import com.devboard.repository.ProjectRepository;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private static final String DEFAULT_COLUMN_COLOR = "#6B7280";

    private static final List<ColumnDefinitionRequest> DEFAULT_COLUMNS = List.of(
            columnDefinition("Backlog", ColumnRole.BACKLOG),
            columnDefinition("To-Do", ColumnRole.TODO),
            columnDefinition("In Progress", ColumnRole.IN_PROGRESS),
            columnDefinition("In Review", ColumnRole.IN_REVIEW),
            columnDefinition("Done", ColumnRole.DONE)
    );

    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final BoardMapper boardMapper;

    /** Quadro padrão criado junto com o projeto (spec-board-kanban.md, 5.1). Sem verificação de permissão: a chamada já vem de dentro da criação do projeto. */
    @Transactional
    public void createDefaultBoard(Project project) {
        Board board = new Board();
        board.setProject(project);
        board.setName("Main Board");
        board.setIsDefault(true);
        Board saved = boardRepository.save(board);

        saveColumns(saved, DEFAULT_COLUMNS);
    }

    @Transactional
    public BoardResponse createBoard(Long projectId, CreateBoardRequest request, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.ADMIN);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado"));

        Board board = new Board();
        board.setProject(project);
        board.setName(request.getName());
        board.setDescription(request.getDescription());
        board.setIsDefault(false);
        Board saved = boardRepository.save(board);

        List<ColumnDefinitionRequest> definitions = (request.getColumns() == null || request.getColumns().isEmpty())
                ? DEFAULT_COLUMNS
                : request.getColumns();
        validateColumnDefinitions(definitions);
        List<BoardColumn> columns = saveColumns(saved, definitions);

        log.info("Quadro criado: id={}, projectId={}", saved.getId(), projectId);
        return boardMapper.toResponse(saved, columns);
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> listBoards(Long projectId, Long userId) {
        permissionService.requireRole(projectId, userId, ProjectRole.VIEWER);

        return boardRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(board -> boardMapper.toResponse(board, boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoardView(Long boardId, Long userId) {
        Board board = findBoardOrThrow(boardId);
        permissionService.requireRole(board.getProject().getId(), userId, ProjectRole.VIEWER);

        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId);
        return boardMapper.toResponse(board, columns);
    }

    @Transactional
    public BoardResponse updateBoard(Long boardId, UpdateBoardRequest request, Long userId) {
        Board board = findBoardOrThrow(boardId);
        permissionService.requireRole(board.getProject().getId(), userId, ProjectRole.ADMIN);

        board.setName(request.getName());
        board.setDescription(request.getDescription());

        if (request.isDefaultBoard() && !Boolean.TRUE.equals(board.getIsDefault())) {
            boardRepository.findByProjectIdAndIsDefaultTrue(board.getProject().getId())
                    .ifPresent(previous -> previous.setIsDefault(false));
            board.setIsDefault(true);
        }

        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId);
        return boardMapper.toResponse(board, columns);
    }

    @Transactional
    public void deleteBoard(Long boardId, Long userId) {
        Board board = findBoardOrThrow(boardId);
        permissionService.requireRole(board.getProject().getId(), userId, ProjectRole.ADMIN);

        if (boardRepository.countByProjectId(board.getProject().getId()) <= 1) {
            throw new ConflictException("Não é possível excluir o único quadro do projeto");
        }

        boardColumnRepository.deleteAll(boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId));
        boardRepository.delete(board);

        log.info("Quadro excluído: id={}, userId={}", boardId, userId);
    }

    @Transactional
    public BoardColumnResponse createColumn(Long boardId, CreateColumnRequest request, Long userId) {
        Board board = findBoardOrThrow(boardId);
        permissionService.requireRole(board.getProject().getId(), userId, ProjectRole.ADMIN);

        if (boardColumnRepository.existsByBoardIdAndName(boardId, request.getName())) {
            throw new ConflictException("Já existe uma coluna com esse nome neste quadro");
        }

        ColumnRole role = request.getRole() != null ? request.getRole() : ColumnRole.NONE;
        if (role != ColumnRole.NONE && boardColumnRepository.existsByBoardIdAndRole(boardId, role)) {
            throw new ConflictException("Papel semântico já usado por outra coluna deste quadro");
        }

        List<BoardColumn> existing = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId);
        int targetPosition = request.getPosition() != null
                ? Math.max(0, Math.min(request.getPosition(), existing.size()))
                : existing.size();

        shiftPositions(existing, targetPosition, 1);

        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setName(request.getName());
        column.setColor(request.getColor() != null ? request.getColor() : DEFAULT_COLUMN_COLOR);
        column.setPosition(targetPosition);
        column.setRole(role);
        column.setWipLimit(request.getWipLimit());
        BoardColumn saved = boardColumnRepository.save(column);

        return boardMapper.toColumnResponse(saved);
    }

    @Transactional
    public BoardColumnResponse updateColumn(Long columnId, UpdateColumnRequest request, Long userId) {
        BoardColumn column = findColumnOrThrow(columnId);
        Long boardId = column.getBoard().getId();
        permissionService.requireRole(column.getBoard().getProject().getId(), userId, ProjectRole.ADMIN);

        if (!column.getName().equals(request.getName())
                && boardColumnRepository.existsByBoardIdAndNameAndIdNot(boardId, request.getName(), columnId)) {
            throw new ConflictException("Já existe uma coluna com esse nome neste quadro");
        }

        ColumnRole targetRole = request.getRole() != null ? request.getRole() : column.getRole();
        if (targetRole != ColumnRole.NONE
                && boardColumnRepository.existsByBoardIdAndRoleAndIdNot(boardId, targetRole, columnId)) {
            throw new ConflictException("Papel semântico já usado por outra coluna deste quadro");
        }

        column.setName(request.getName());
        column.setColor(request.getColor());
        column.setRole(targetRole);
        column.setWipLimit(request.getWipLimit());

        return boardMapper.toColumnResponse(column);
    }

    @Transactional
    public void deleteColumn(Long columnId, Long moveTasksTo, Long userId) {
        BoardColumn column = findColumnOrThrow(columnId);
        Board board = column.getBoard();
        permissionService.requireRole(board.getProject().getId(), userId, ProjectRole.ADMIN);

        if (boardColumnRepository.countByBoardId(board.getId()) <= 1) {
            throw new ConflictException("Não é possível excluir a última coluna do quadro");
        }

        // Nenhuma tarefa existe ainda no sistema (spec-tasks.md não implementada) — o caminho
        // "coluna com tarefas exige destino" fica pendente até o módulo de tarefas existir.
        List<BoardColumn> remaining = boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId());
        boardColumnRepository.delete(column);
        remaining.remove(column);
        shiftPositions(remaining, column.getPosition() + 1, -1);

        log.info("Coluna excluída: id={}, boardId={}, userId={}", columnId, board.getId(), userId);
    }

    @Transactional
    public List<BoardColumnResponse> reorderColumns(Long boardId, ReorderColumnsRequest request, Long userId) {
        Board board = findBoardOrThrow(boardId);
        permissionService.requireRole(board.getProject().getId(), userId, ProjectRole.DEVELOPER);

        List<BoardColumn> existing = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId);
        Set<Long> existingIds = new HashSet<>();
        existing.forEach(column -> existingIds.add(column.getId()));
        Set<Long> requestedIds = new HashSet<>(request.getColumnIds());

        if (request.getColumnIds().size() != existing.size() || !existingIds.equals(requestedIds)) {
            throw new InvalidRequestException("A lista deve conter exatamente todas as colunas do quadro, sem repetições nem omissões");
        }

        Map<Long, BoardColumn> byId = new HashMap<>();
        existing.forEach(column -> byId.put(column.getId(), column));

        List<BoardColumnResponse> response = new ArrayList<>();
        int position = 0;
        for (Long columnId : request.getColumnIds()) {
            BoardColumn column = byId.get(columnId);
            column.setPosition(position++);
            response.add(boardMapper.toColumnResponse(column));
        }

        return response;
    }

    private void validateColumnDefinitions(List<ColumnDefinitionRequest> definitions) {
        Set<String> names = new HashSet<>();
        Set<ColumnRole> roles = new HashSet<>();
        for (ColumnDefinitionRequest definition : definitions) {
            if (!names.add(definition.getName())) {
                throw new ConflictException("Nomes de coluna duplicados: " + definition.getName());
            }
            ColumnRole role = definition.getRole() != null ? definition.getRole() : ColumnRole.NONE;
            if (role != ColumnRole.NONE && !roles.add(role)) {
                throw new ConflictException("Papel semântico duplicado: " + role);
            }
        }
    }

    private List<BoardColumn> saveColumns(Board board, List<ColumnDefinitionRequest> definitions) {
        List<BoardColumn> columns = new ArrayList<>();
        for (int position = 0; position < definitions.size(); position++) {
            ColumnDefinitionRequest definition = definitions.get(position);
            BoardColumn column = new BoardColumn();
            column.setBoard(board);
            column.setName(definition.getName());
            column.setColor(definition.getColor() != null ? definition.getColor() : DEFAULT_COLUMN_COLOR);
            column.setPosition(position);
            column.setRole(definition.getRole() != null ? definition.getRole() : ColumnRole.NONE);
            column.setWipLimit(definition.getWipLimit());
            columns.add(column);
        }
        return boardColumnRepository.saveAll(columns);
    }

    private void shiftPositions(List<BoardColumn> columns, int fromPositionInclusive, int delta) {
        for (BoardColumn column : columns) {
            if (column.getPosition() >= fromPositionInclusive) {
                column.setPosition(column.getPosition() + delta);
            }
        }
    }

    private Board findBoardOrThrow(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Quadro não encontrado"));
    }

    private BoardColumn findColumnOrThrow(Long columnId) {
        return boardColumnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("Coluna não encontrada"));
    }

    private static ColumnDefinitionRequest columnDefinition(String name, ColumnRole role) {
        ColumnDefinitionRequest definition = new ColumnDefinitionRequest();
        definition.setName(name);
        definition.setRole(role);
        return definition;
    }
}
