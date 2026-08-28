package com.devboard.service;

import com.devboard.dto.board.BoardColumnResponse;
import com.devboard.dto.board.BoardResponse;
import com.devboard.dto.board.CreateBoardRequest;
import com.devboard.dto.board.CreateColumnRequest;
import com.devboard.dto.board.ReorderColumnsRequest;
import com.devboard.dto.board.UpdateBoardRequest;
import com.devboard.dto.board.UpdateColumnRequest;
import com.devboard.entity.Board;
import com.devboard.entity.BoardColumn;
import com.devboard.entity.Project;
import com.devboard.entity.Task;
import com.devboard.entity.User;
import com.devboard.entity.enums.ColumnRole;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ConflictException;
import com.devboard.exception.InvalidRequestException;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.mapper.BoardMapper;
import com.devboard.repository.BoardColumnRepository;
import com.devboard.repository.BoardRepository;
import com.devboard.repository.ProjectRepository;
import com.devboard.repository.TaskCommentRepository;
import com.devboard.repository.TaskRepository;
import com.devboard.security.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardColumnRepository boardColumnRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskCommentRepository taskCommentRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private BoardMapper boardMapper;

    @InjectMocks
    private BoardService boardService;

    private Project project;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);

        project = new Project();
        project.setId(20L);
        project.setOwner(owner);

        lenient().when(boardMapper.toResponse(any(Board.class), anyList()))
                .thenAnswer(invocation -> BoardResponse.builder().id(((Board) invocation.getArgument(0)).getId()).build());
        lenient().when(boardMapper.toResponse(any(Board.class), anyList(), anyMap(), anyMap()))
                .thenAnswer(invocation -> BoardResponse.builder().id(((Board) invocation.getArgument(0)).getId()).build());
        lenient().when(boardMapper.toColumnResponse(any(BoardColumn.class)))
                .thenAnswer(invocation -> {
                    BoardColumn column = invocation.getArgument(0);
                    return BoardColumnResponse.builder().id(column.getId()).name(column.getName()).position(column.getPosition()).build();
                });
    }

    private Board board(Long id) {
        Board board = new Board();
        board.setId(id);
        board.setProject(project);
        board.setIsDefault(false);
        return board;
    }

    private BoardColumn column(Long id, Board board, String name, int position, ColumnRole role) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setBoard(board);
        column.setName(name);
        column.setPosition(position);
        column.setRole(role);
        return column;
    }

    // ---- createDefaultBoard ----

    @Test
    void createDefaultBoard_deveCriarQuadroComCincoColunasPadrao() {
        Board saved = board(100L);
        when(boardRepository.save(any(Board.class))).thenReturn(saved);

        boardService.createDefaultBoard(project);

        ArgumentCaptor<List<BoardColumn>> captor = ArgumentCaptor.forClass(List.class);
        verify(boardColumnRepository).saveAll(captor.capture());

        List<BoardColumn> columns = captor.getValue();
        assertThat(columns).hasSize(5);
        assertThat(columns.get(0).getName()).isEqualTo("Backlog");
        assertThat(columns.get(0).getRole()).isEqualTo(ColumnRole.BACKLOG);
        assertThat(columns.get(4).getName()).isEqualTo("Done");
        assertThat(columns.get(4).getRole()).isEqualTo(ColumnRole.DONE);
    }

    // ---- createBoard ----

    @Test
    void createBoard_deveCriarComColunasPadrao_quandoNenhumaColunaInformada() {
        when(projectRepository.findById(20L)).thenReturn(Optional.of(project));
        when(boardRepository.save(any(Board.class))).thenReturn(board(101L));

        CreateBoardRequest request = new CreateBoardRequest();
        request.setName("Sprint Board");

        boardService.createBoard(20L, request, 1L);

        verify(permissionService).requireRole(20L, 1L, ProjectRole.ADMIN);
        ArgumentCaptor<List<BoardColumn>> captor = ArgumentCaptor.forClass(List.class);
        verify(boardColumnRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(5);
    }

    @Test
    void createBoard_deveLancarConflict_quandoColunasCustomizadasTemNomeDuplicado() {
        when(projectRepository.findById(20L)).thenReturn(Optional.of(project));
        when(boardRepository.save(any(Board.class))).thenReturn(board(101L));

        CreateBoardRequest request = new CreateBoardRequest();
        request.setName("Sprint Board");
        request.setColumns(List.of(columnDef("A", null), columnDef("A", null)));

        assertThatThrownBy(() -> boardService.createBoard(20L, request, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createBoard_deveLancarConflict_quandoColunasCustomizadasTemPapelDuplicado() {
        when(projectRepository.findById(20L)).thenReturn(Optional.of(project));
        when(boardRepository.save(any(Board.class))).thenReturn(board(101L));

        CreateBoardRequest request = new CreateBoardRequest();
        request.setName("Sprint Board");
        request.setColumns(List.of(columnDef("A", ColumnRole.TODO), columnDef("B", ColumnRole.TODO)));

        assertThatThrownBy(() -> boardService.createBoard(20L, request, 1L))
                .isInstanceOf(ConflictException.class);
    }

    // ---- getBoardView ----

    @Test
    void getBoardView_deveRetornarQuadro_quandoUsuarioTemVinculo() {
        Board board = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(List.of());

        BoardResponse response = boardService.getBoardView(101L, 1L);

        assertThat(response.getId()).isEqualTo(101L);
        verify(permissionService).requireRole(20L, 1L, ProjectRole.VIEWER);
    }

    @Test
    void getBoardView_deveAgruparTarefasPorColuna_comUmaUnicaConsulta() {
        Board board = board(101L);
        BoardColumn backlog = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn todo = column(2L, board, "To-Do", 1, ColumnRole.TODO);
        Task taskInBacklog = new Task();
        taskInBacklog.setId(500L);
        taskInBacklog.setColumn(backlog);

        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(List.of(backlog, todo));
        when(taskRepository.findByColumnIdInAndArchivedFalseOrderByPosition(List.of(1L, 2L)))
                .thenReturn(List.of(taskInBacklog));

        ArgumentCaptor<Map> tasksByColumnCaptor = ArgumentCaptor.forClass(Map.class);
        boardService.getBoardView(101L, 1L);

        verify(boardMapper).toResponse(any(Board.class), anyList(), tasksByColumnCaptor.capture(), anyMap());
        Map<Long, List<Task>> tasksByColumn = tasksByColumnCaptor.getValue();
        assertThat(tasksByColumn.get(1L)).containsExactly(taskInBacklog);
        assertThat(tasksByColumn.get(2L)).isNull();
        verify(taskRepository).findByColumnIdInAndArchivedFalseOrderByPosition(List.of(1L, 2L));
    }

    @Test
    void getBoardView_devePropagarResourceNotFound_quandoQuadroNaoExiste() {
        when(boardRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.getBoardView(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- updateBoard ----

    @Test
    void updateBoard_deveDesmarcarQuadroAnterior_quandoNovoPadraoDefinido() {
        Board target = board(101L);
        Board previousDefault = board(102L);
        previousDefault.setIsDefault(true);

        when(boardRepository.findById(101L)).thenReturn(Optional.of(target));
        when(boardRepository.findByProjectIdAndIsDefaultTrue(20L)).thenReturn(Optional.of(previousDefault));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(List.of());

        UpdateBoardRequest request = new UpdateBoardRequest();
        request.setName("Renomeado");
        request.setDefaultBoard(true);

        boardService.updateBoard(101L, request, 1L);

        assertThat(target.getIsDefault()).isTrue();
        assertThat(previousDefault.getIsDefault()).isFalse();
        verify(permissionService).requireRole(20L, 1L, ProjectRole.ADMIN);
    }

    // ---- deleteBoard ----

    @Test
    void deleteBoard_deveLancarConflict_quandoEUnicoQuadroDoProjeto() {
        Board target = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(target));
        when(boardRepository.countByProjectId(20L)).thenReturn(1L);

        assertThatThrownBy(() -> boardService.deleteBoard(101L, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteBoard_deveExcluir_quandoHaOutrosQuadros() {
        Board target = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(target));
        when(boardRepository.countByProjectId(20L)).thenReturn(2L);
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(List.of());

        boardService.deleteBoard(101L, 1L);

        verify(boardRepository).delete(target);
    }

    // ---- createColumn ----

    @Test
    void createColumn_deveLancarConflict_quandoNomeJaExisteNoQuadro() {
        Board board = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.existsByBoardIdAndName(101L, "Backlog")).thenReturn(true);

        CreateColumnRequest request = new CreateColumnRequest();
        request.setName("Backlog");

        assertThatThrownBy(() -> boardService.createColumn(101L, request, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createColumn_deveLancarConflict_quandoPapelSemanticoJaUsado() {
        Board board = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.existsByBoardIdAndName(101L, "Nova")).thenReturn(false);
        when(boardColumnRepository.existsByBoardIdAndRole(101L, ColumnRole.DONE)).thenReturn(true);

        CreateColumnRequest request = new CreateColumnRequest();
        request.setName("Nova");
        request.setRole(ColumnRole.DONE);

        assertThatThrownBy(() -> boardService.createColumn(101L, request, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createColumn_deveInserirNoFinal_quandoPosicaoNaoInformada() {
        Board board = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.existsByBoardIdAndName(101L, "Nova")).thenReturn(false);

        BoardColumn existing1 = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn existing2 = column(2L, board, "To-Do", 1, ColumnRole.TODO);
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(new ArrayList<>(List.of(existing1, existing2)));
        when(boardColumnRepository.save(any(BoardColumn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateColumnRequest request = new CreateColumnRequest();
        request.setName("Nova");

        BoardColumnResponse response = boardService.createColumn(101L, request, 1L);

        assertThat(response.getPosition()).isEqualTo(2);
        assertThat(existing1.getPosition()).isEqualTo(0);
        assertThat(existing2.getPosition()).isEqualTo(1);
    }

    @Test
    void createColumn_deveDeslocarColunasSeguintes_quandoPosicaoIntermediaria() {
        Board board = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.existsByBoardIdAndName(101L, "Nova")).thenReturn(false);

        BoardColumn existing1 = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn existing2 = column(2L, board, "To-Do", 1, ColumnRole.TODO);
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(new ArrayList<>(List.of(existing1, existing2)));
        when(boardColumnRepository.save(any(BoardColumn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateColumnRequest request = new CreateColumnRequest();
        request.setName("Nova");
        request.setPosition(1);

        BoardColumnResponse response = boardService.createColumn(101L, request, 1L);

        assertThat(response.getPosition()).isEqualTo(1);
        assertThat(existing1.getPosition()).isEqualTo(0);
        assertThat(existing2.getPosition()).isEqualTo(2);
    }

    // ---- updateColumn ----

    @Test
    void updateColumn_deveLancarConflict_quandoNovoNomeJaExisteEmOutraColuna() {
        Board board = board(101L);
        BoardColumn column = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(column));
        when(boardColumnRepository.existsByBoardIdAndNameAndIdNot(101L, "To-Do", 1L)).thenReturn(true);

        UpdateColumnRequest request = new UpdateColumnRequest();
        request.setName("To-Do");

        assertThatThrownBy(() -> boardService.updateColumn(1L, request, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateColumn_deveAtualizar_quandoDadosValidos() {
        Board board = board(101L);
        BoardColumn column = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(column));
        when(boardColumnRepository.existsByBoardIdAndNameAndIdNot(101L, "Backlog Renomeado", 1L)).thenReturn(false);

        UpdateColumnRequest request = new UpdateColumnRequest();
        request.setName("Backlog Renomeado");
        request.setWipLimit(5);

        boardService.updateColumn(1L, request, 1L);

        assertThat(column.getName()).isEqualTo("Backlog Renomeado");
        assertThat(column.getWipLimit()).isEqualTo(5);
        verify(permissionService).requireRole(20L, 1L, ProjectRole.ADMIN);
    }

    // ---- deleteColumn ----

    @Test
    void deleteColumn_deveLancarConflict_quandoEUltimaColunaDoQuadro() {
        Board board = board(101L);
        BoardColumn column = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(column));
        when(boardColumnRepository.countByBoardId(101L)).thenReturn(1L);

        assertThatThrownBy(() -> boardService.deleteColumn(1L, null, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteColumn_deveReindexarPosicoesRestantes() {
        Board board = board(101L);
        BoardColumn column0 = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn column1 = column(2L, board, "To-Do", 1, ColumnRole.TODO);
        BoardColumn column2 = column(3L, board, "Done", 2, ColumnRole.DONE);

        when(boardColumnRepository.findById(2L)).thenReturn(Optional.of(column1));
        when(boardColumnRepository.countByBoardId(101L)).thenReturn(3L);
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L))
                .thenReturn(new ArrayList<>(List.of(column0, column1, column2)));

        boardService.deleteColumn(2L, null, 1L);

        verify(boardColumnRepository).delete(column1);
        assertThat(column0.getPosition()).isEqualTo(0);
        assertThat(column2.getPosition()).isEqualTo(1);
    }

    @Test
    void deleteColumn_deveLancarConflict_quandoTemTarefasESemDestino() {
        Board board = board(101L);
        BoardColumn column = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        Task task = new Task();
        task.setId(500L);
        task.setColumn(column);

        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(column));
        when(boardColumnRepository.countByBoardId(101L)).thenReturn(2L);
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L)).thenReturn(List.of(task));

        assertThatThrownBy(() -> boardService.deleteColumn(1L, null, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteColumn_deveMoverTarefas_quandoDestinoInformado() {
        Board board = board(101L);
        BoardColumn origin = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn target = column(2L, board, "To-Do", 1, ColumnRole.TODO);
        Task taskToMove = new Task();
        taskToMove.setId(500L);
        taskToMove.setColumn(origin);
        Task existingInTarget = new Task();
        existingInTarget.setId(600L);
        existingInTarget.setColumn(target);
        existingInTarget.setPosition(0);

        when(boardColumnRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(boardColumnRepository.findById(2L)).thenReturn(Optional.of(target));
        when(boardColumnRepository.countByBoardId(101L)).thenReturn(2L);
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(1L)).thenReturn(List.of(taskToMove));
        when(taskRepository.findByColumnIdAndArchivedFalseOrderByPositionAsc(2L)).thenReturn(List.of(existingInTarget));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L))
                .thenReturn(new ArrayList<>(List.of(origin, target)));

        boardService.deleteColumn(1L, 2L, 1L);

        assertThat(taskToMove.getColumn()).isEqualTo(target);
        assertThat(taskToMove.getPosition()).isEqualTo(1);
        verify(taskRepository).saveAll(List.of(taskToMove));
        verify(boardColumnRepository).delete(origin);
    }

    // ---- reorderColumns ----

    @Test
    void reorderColumns_deveAplicarNovaOrdem_quandoListaCompleta() {
        Board board = board(101L);
        BoardColumn column0 = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn column1 = column(2L, board, "To-Do", 1, ColumnRole.TODO);

        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(List.of(column0, column1));

        ReorderColumnsRequest request = new ReorderColumnsRequest();
        request.setColumnIds(List.of(2L, 1L));

        boardService.reorderColumns(101L, request, 1L);

        assertThat(column1.getPosition()).isEqualTo(0);
        assertThat(column0.getPosition()).isEqualTo(1);
        verify(permissionService).requireRole(20L, 1L, ProjectRole.DEVELOPER);
    }

    @Test
    void reorderColumns_deveLancarInvalidRequest_quandoListaIncompleta() {
        Board board = board(101L);
        BoardColumn column0 = column(1L, board, "Backlog", 0, ColumnRole.BACKLOG);
        BoardColumn column1 = column(2L, board, "To-Do", 1, ColumnRole.TODO);

        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(101L)).thenReturn(List.of(column0, column1));

        ReorderColumnsRequest request = new ReorderColumnsRequest();
        request.setColumnIds(List.of(1L));

        assertThatThrownBy(() -> boardService.reorderColumns(101L, request, 1L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void reorderColumns_deveLancarAccessDenied_quandoUsuarioViewer() {
        Board board = board(101L);
        when(boardRepository.findById(101L)).thenReturn(Optional.of(board));
        doThrow(new AccessDeniedException("Você não tem permissão para esta ação"))
                .when(permissionService).requireRole(20L, 5L, ProjectRole.DEVELOPER);

        ReorderColumnsRequest request = new ReorderColumnsRequest();
        request.setColumnIds(List.of(1L));

        assertThatThrownBy(() -> boardService.reorderColumns(101L, request, 5L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private com.devboard.dto.board.ColumnDefinitionRequest columnDef(String name, ColumnRole role) {
        com.devboard.dto.board.ColumnDefinitionRequest definition = new com.devboard.dto.board.ColumnDefinitionRequest();
        definition.setName(name);
        definition.setRole(role);
        return definition;
    }
}
