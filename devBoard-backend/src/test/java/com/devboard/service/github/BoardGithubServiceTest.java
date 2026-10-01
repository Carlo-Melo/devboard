package com.devboard.service.github;
import com.devboard.dto.board.*;
import com.devboard.entity.*;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.event.GithubJobEvent;
import com.devboard.exception.*;
import com.devboard.mapper.GithubMapper;
import com.devboard.repository.*;
import com.devboard.security.PermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardGithubServiceTest {
    @Mock BoardRepository boards; @Mock UserRepository users; @Mock TaskRepository tasks;
    @Mock BoardLabelMappingRepository mappings; @Mock PermissionService permissions;
    @Mock GithubRepositoryClient client; @Mock GithubMapper mapper; @Mock ApplicationEventPublisher events;
    @InjectMocks BoardGithubService service;
    Board board; User user; LinkGithubRequest request;
    @BeforeEach void setup() {
        user = new User(); user.setId(1L); user.setGithubToken("fake-test-token");
        Project p = new Project(); p.setId(10L); p.setOwner(user);
        board = new Board(); board.setId(20L); board.setProject(p);
        request = new LinkGithubRequest(); request.setGithubRepoId(30L);
        lenient().when(boards.findLockedById(20L)).thenReturn(Optional.of(board));
        lenient().when(users.findById(1L)).thenReturn(Optional.of(user));
    }
    @Test void link_devePersistirNoBoardEAgendarWebhook() {
        when(client.repository("fake-test-token",30)).thenReturn(new GithubRepositoryClient.RepositoryData(30,"org","repo","org/repo",null,"https://github.com/org/repo","develop",true));
        service.link(20L,request,1L);
        assertThat(board.getGithubRepoId()).isEqualTo(30L); assertThat(board.getDefaultBaseBranch()).isEqualTo("develop");
        verify(permissions).requireRole(10L,1L,ProjectRole.ADMIN);
        verify(events).publishEvent(new GithubJobEvent("LINK",20L,30L,1L,null,null,null));
    }
    @Test void link_deveRejeitarSegundoRepositorioNoBoard() {
        board.setGithubRepoId(99L);
        assertThatThrownBy(() -> service.link(20L,request,1L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(client);
    }
    @Test void link_deveRejeitarRepositorioJaVinculadoGlobalmente() {
        when(boards.existsByGithubRepoId(30L)).thenReturn(true);
        assertThatThrownBy(() -> service.link(20L,request,1L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(client);
    }
    @Test void link_deveRejeitarRepositorioSemEscrita() {
        when(client.repository("fake-test-token",30)).thenReturn(new GithubRepositoryClient.RepositoryData(30,"org","repo","org/repo",null,"url","main",false));
        assertThatThrownBy(() -> service.link(20L,request,1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(events);
    }
    @Test void link_deveVerificarPermissaoAntesDeConsultarGithub() {
        doThrow(new AccessDeniedException("denied")).when(permissions).requireRole(10L,1L,ProjectRole.ADMIN);
        assertThatThrownBy(() -> service.link(20L,request,1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(client);
    }
    @Test void unlink_devePreservarTarefasELimparTodasReferencias() {
        board.setGithubRepoId(30L); board.setGithubUser(user);
        Task t = new Task(); t.setGithubIssueId(8L); t.setBranch("feature/task-1-demo");
        when(tasks.findByColumnBoardId(20L)).thenReturn(List.of(t));
        service.unlink(20L,1L);
        assertThat(t.getGithubIssueId()).isNull(); assertThat(t.getBranch()).isNull(); assertThat(board.getGithubRepoId()).isNull();
        verify(tasks, never()).delete(any()); verify(mappings).deleteByBoardId(20L);
        verify(events).publishEvent(new GithubJobEvent("REMOVE",20L,30L,1L,null,null,null));
    }
    @Test void sync_deveRejeitarBoardSemRepositorio() {
        assertThatThrownBy(() -> service.sync(20L,1L)).isInstanceOf(InvalidRequestException.class);
    }
    @Test void settings_devePermitirBoardLocal() {
        when(boards.findById(20L)).thenReturn(Optional.of(board));
        service.settings(20L,1L); verify(permissions).requireRole(10L,1L,ProjectRole.VIEWER);
    }
}
