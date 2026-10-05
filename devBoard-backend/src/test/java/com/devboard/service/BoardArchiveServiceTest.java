package com.devboard.service;

import com.devboard.entity.*;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ConflictException;
import com.devboard.mapper.BoardMapper;
import com.devboard.repository.*;
import com.devboard.security.PermissionService;
import com.devboard.service.github.BoardGithubService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardArchiveServiceTest {
    @Mock BoardRepository boardRepository;
    @Mock BoardColumnRepository boardColumnRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock TaskCommentRepository taskCommentRepository;
    @Mock PermissionService permissionService;
    @Mock BoardMapper boardMapper;
    @Mock BoardGithubService githubService;
    @InjectMocks BoardService service;
    Board board;

    @BeforeEach void setup() {
        Project project = new Project(); project.setId(1L);
        board = new Board(); board.setId(2L); board.setName("Backend"); board.setProject(project);
        when(boardRepository.findLockedById(2L)).thenReturn(Optional.of(board));
    }
    @Test void archive_devePreservarTarefasEColunasEAgendarSuspensao() {
        service.archiveBoard(2L, 7L);
        assertThat(board.getArchived()).isTrue(); assertThat(board.getArchivedAt()).isNotNull();
        verify(permissionService).requireRole(1L, 7L, ProjectRole.ADMIN);
        verify(githubService).suspendBoard(2L);
        verify(boardRepository).saveAndFlush(board);
        verify(boardRepository, never()).delete(any()); verifyNoInteractions(taskRepository, taskCommentRepository, boardColumnRepository);
    }
    @Test void archive_deveSerIdempotente() {
        board.setArchived(true); service.archiveBoard(2L, 7L);
        verifyNoInteractions(githubService); verify(boardRepository, never()).saveAndFlush(any());
    }
    @Test void archive_deveNegarPermissaoAntesDeAlterar() {
        doThrow(new AccessDeniedException("Sem permissão")).when(permissionService).requireRole(1L, 7L, ProjectRole.ADMIN);
        assertThatThrownBy(() -> service.archiveBoard(2L, 7L)).isInstanceOf(AccessDeniedException.class);
        assertThat(board.getArchived()).isFalse(); verifyNoInteractions(githubService);
    }
    @Test void archive_deveProtegerPadraoRenomeado() {
        board.setIsDefault(true);
        assertThatThrownBy(() -> service.archiveBoard(2L, 7L)).isInstanceOf(ConflictException.class);
        assertThat(board.getArchived()).isFalse();
    }
    @Test void restore_deveRecusarConflitoSemAlterarArquivado() {
        board.setArchived(true); board.setGithubRepoId(90L);
        when(boardRepository.existsByGithubRepoIdAndArchivedFalse(90L)).thenReturn(true);
        assertThatThrownBy(() -> service.restoreBoard(2L, 7L)).isInstanceOf(ConflictException.class);
        assertThat(board.getArchived()).isTrue(); verifyNoInteractions(githubService);
    }
    @Test void restore_deveReativarEAgendarIntegracao() {
        board.setArchived(true); service.restoreBoard(2L, 7L);
        assertThat(board.getArchived()).isFalse();
        verify(boardRepository).saveAndFlush(board); verify(githubService).resumeBoard(2L, 7L);
        verifyNoInteractions(taskRepository, taskCommentRepository, boardColumnRepository);
    }
}
