package com.devboard.service.github;

import com.devboard.async.GithubEventListener;
import com.devboard.entity.*;
import com.devboard.event.GithubJobEvent;
import com.devboard.exception.ExternalServiceException;
import com.devboard.mapper.GithubMapper;
import com.devboard.repository.*;
import com.devboard.security.PermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardGithubLifecycleTest {
    @Mock BoardRepository boards;
    @Mock UserRepository users;
    @Mock TaskRepository tasks;
    @Mock BoardLabelMappingRepository mappings;
    @Mock PermissionService permissions;
    @Mock GithubRepositoryClient client;
    @Mock GithubMapper mapper;
    @Mock ApplicationEventPublisher events;
    @InjectMocks BoardGithubService service;

    @Test void suspend_devePublicarSnapshotDoHookSemApagarReferencias() {
        User owner = new User(); owner.setId(7L); Project project = new Project(); project.setOwner(owner);
        Board board = new Board(); board.setId(2L); board.setProject(project); board.setGithubRepoId(90L); board.setGithubHookId(12L); board.setGithubRepoName("historico"); board.setWatchedBranches(new java.util.ArrayList<>(java.util.List.of("main")));
        when(boards.findById(2L)).thenReturn(Optional.of(board));
        service.suspendBoard(2L);
        ArgumentCaptor<GithubJobEvent> job = ArgumentCaptor.forClass(GithubJobEvent.class); verify(events).publishEvent(job.capture());
        assertThat(job.getValue().operation()).isEqualTo("REMOVE"); assertThat(job.getValue().hookId()).isEqualTo(12L);
        assertThat(board.getGithubHookId()).isNull(); assertThat(board.getGithubRepoId()).isEqualTo(90L);
        assertThat(board.getGithubRepoName()).isEqualTo("historico"); assertThat(board.getWatchedBranches()).containsExactly("main");
        verifyNoInteractions(tasks, mappings, client);
    }
    @Test void resume_deveAgendarRegistroEImportacaoSemChamadaExternaSincrona() {
        Board board = new Board(); board.setId(2L); board.setGithubRepoId(90L);
        when(boards.findById(2L)).thenReturn(Optional.of(board)); service.resumeBoard(2L, 7L);
        verify(events).publishEvent(new GithubJobEvent("LINK", 2L, 90L, 7L, null, null, null));
        verifyNoInteractions(client, tasks, mappings);
    }
    @Test void failure_deveRepetirFalhaTransitoriaSemPropagarParaOperacaoLocal() {
        GithubJobService jobs = mock(GithubJobService.class); GithubEventProcessor processor = mock(GithubEventProcessor.class);
        GithubEventListener listener = new GithubEventListener(jobs, processor);
        GithubJobEvent event = new GithubJobEvent("REMOVE", 2L, 90L, 7L, 12L, null, null);
        doThrow(new ExternalServiceException("Indisponível")).doNothing().when(jobs).execute(event);
        assertThatCode(() -> listener.job(event)).doesNotThrowAnyException();
        verify(jobs, times(2)).execute(event);
    }
}
