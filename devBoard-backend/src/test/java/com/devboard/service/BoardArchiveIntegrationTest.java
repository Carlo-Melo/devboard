package com.devboard.service;

import com.devboard.async.GithubEventListener;
import com.devboard.dto.board.*;
import com.devboard.dto.project.CreateProjectRequest;
import com.devboard.entity.*;
import com.devboard.entity.enums.*;
import com.devboard.event.GithubJobEvent;
import com.devboard.repository.*;
import com.devboard.security.JwtTokenProvider;
import com.devboard.service.github.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"github.webhook-secret=test-webhook-secret", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@Transactional
class BoardArchiveIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BoardService service;
    @Autowired ProjectService projects;
    @Autowired TaskService taskService;
    @Autowired DashboardService dashboard;
    @Autowired BoardGithubService github;
    @Autowired GithubJobService jobs;
    @Autowired GithubEventProcessor processor;
    @Autowired GithubWebhookService webhooks;
    @Autowired BoardRepository boards;
    @Autowired BoardColumnRepository columns;
    @Autowired UserRepository users;
    @Autowired TaskRepository tasks;
    @Autowired TaskActivityRepository activities;
    @Autowired TaskCommentRepository comments;
    @Autowired ProjectMemberRepository members;
    @Autowired LabelRepository labels;
    @Autowired BoardLabelMappingRepository mappings;
    @Autowired JwtTokenProvider jwt;
    @MockBean GithubRepositoryClient client;
    @MockBean GithubEventListener listener;
    User owner;
    Long projectId, mainId, boardId, taskId, repoId;

    @BeforeEach void setup() {
        owner = user(); owner.setGithubToken("fake-test-token"); users.saveAndFlush(owner);
        CreateProjectRequest request = new CreateProjectRequest(); request.setName("TCC test " + UUID.randomUUID());
        var project = projects.create(request, owner.getId()); projectId = project.getId(); mainId = project.getBoards().get(0).getId();
        CreateBoardRequest board = new CreateBoardRequest(); board.setName("Backend"); boardId = service.createBoard(projectId, board, owner.getId()).getId();
        BoardColumn column = columns.findByBoardIdOrderByPositionAsc(boardId).get(0);
        Task task = new Task(); task.setColumn(column); task.setTitle("Entrega preservada"); task.setCreator(owner); task.setAssignee(owner); task.setPosition(0);
        task.setGithubIssueId(9001L); task.setGithubIssueNumber(12); task.setBranch("feature/preservada"); taskId = tasks.saveAndFlush(task).getId();
        task.setGithubPrId(8001L); task.setGithubPrState(PullRequestState.OPEN);
        Label label = labels.findByProjectIdOrderByNameAsc(projectId).get(0); task.getLabels().add(label); tasks.saveAndFlush(task);
        BoardLabelMapping mapping = new BoardLabelMapping(); mapping.setBoard(column.getBoard()); mapping.setLabel(label); mapping.setGithubLabelId(7001L); mapping.setGithubLabelName(label.getName()); mappings.saveAndFlush(mapping);
        TaskActivity activity = new TaskActivity(); activity.setTask(task); activity.setType(TaskActivityType.CREATED); activity.setDescription("Histórico preservado"); activities.saveAndFlush(activity);
        TaskComment comment = new TaskComment(); comment.setTask(task); comment.setAuthor(owner); comment.setContent("Comentário preservado"); comments.saveAndFlush(comment);
        repoId = 900000L + boardId;
        when(client.repository("fake-test-token", repoId)).thenReturn(new GithubRepositoryClient.RepositoryData(repoId, "org", "repo", "org/repo", null, "https://github.com/org/repo", "main", true, false));
    }

    User user() {
        String suffix = UUID.randomUUID().toString(); User user = new User(); user.setUsername("archive-" + suffix); user.setEmail(suffix + "@example.com"); return users.saveAndFlush(user);
    }
    User member(ProjectRole role) {
        User user = user(); ProjectMember member = new ProjectMember(); member.setProject(boards.findById(boardId).orElseThrow().getProject()); member.setUser(user); member.setRole(role); members.saveAndFlush(member); return user;
    }
    String token(User user) { return "Bearer " + jwt.generateToken(user); }
    void link(Long id) { LinkGithubRequest request = new LinkGithubRequest(); request.setGithubRepoId(repoId); github.link(id, request, owner.getId()); }

    @Test void archiveRestore_devePreservarDadosEExcluirDasConsultasAtivas() throws Exception {
        link(boardId);
        service.archiveBoard(boardId, owner.getId());
        assertThat(boards.findById(boardId).orElseThrow().getArchivedAt()).isNotNull();
        assertThat(service.listBoards(projectId, owner.getId())).extracting(BoardResponse::getId).containsExactly(mainId);
        assertThat(projects.getById(projectId, owner.getId()).getBoards()).extracting(b -> b.getId()).containsExactly(mainId);
        var summary = projects.list(owner.getId(), false, 0, 20).getContent().stream().filter(p -> p.getId().equals(projectId)).findFirst().orElseThrow();
        assertThat(summary.getBoardCount()).isEqualTo(1); assertThat(summary.getGithubBoardCount()).isZero();
        assertThat(github.destinations(owner.getId(), 0, 100).getContent()).extracting(GithubDestinationResponse::boardId).doesNotContain(boardId);
        assertThat(boards.findLinkedGithubRepoIds(List.of(repoId))).isEmpty();
        assertThat(dashboard.summary(owner.getId(), projectId).pendingTasks()).isZero();
        assertThat(dashboard.myTasks(owner.getId(), projectId, 0, 20).getContent()).isEmpty();
        assertThat(dashboard.activities(owner.getId(), projectId, 0, 20).getContent()).isEmpty();
        assertThat(service.listArchivedBoards(projectId, owner.getId(), 0, 1000).getSize()).isEqualTo(100);
        assertThat(service.listArchivedBoards(projectId, owner.getId(), 0, 20).getContent()).extracting(BoardResponse::getId).containsExactly(boardId);
        mvc.perform(get("/api/boards/" + boardId).header("Authorization", token(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true)).andExpect(jsonPath("$.columns").isEmpty());
        service.restoreBoard(boardId, owner.getId());
        Task restored = tasks.findById(taskId).orElseThrow();
        assertThat(restored.getArchived()).isFalse(); assertThat(restored.getTitle()).isEqualTo("Entrega preservada");
        assertThat(restored.getGithubIssueId()).isEqualTo(9001L); assertThat(restored.getBranch()).isEqualTo("feature/preservada");
        assertThat(restored.getGithubPrId()).isEqualTo(8001L); assertThat(restored.getLabels()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_label_mappings WHERE board_id=?", Integer.class, boardId)).isEqualTo(1);
        assertThat(activities.findByTaskIdOrderByCreatedAtDesc(taskId)).hasSize(1);
        assertThat(comments.findByTaskIdOrderByCreatedAtAsc(taskId)).hasSize(1);
        assertThat(service.getBoardView(boardId, owner.getId()).getColumns().get(0).getTasks()).hasSize(1);
        assertThat(boards.findById(boardId).orElseThrow().getArchivedAt()).isNull();
    }

    @Test void archive_deveProtegerMainBoardEPadraoRenomeado() throws Exception {
        mvc.perform(post("/api/boards/" + mainId + "/archive").header("Authorization", token(owner))).andExpect(status().isConflict());
        UpdateBoardRequest rename = new UpdateBoardRequest(); rename.setName("Principal"); service.updateBoard(mainId, rename, owner.getId());
        mvc.perform(post("/api/boards/" + mainId + "/archive").header("Authorization", token(owner))).andExpect(status().isConflict());
        rename.setName("Main Board"); service.updateBoard(boardId, rename, owner.getId());
        mvc.perform(post("/api/boards/" + boardId + "/archive").header("Authorization", token(owner))).andExpect(status().isConflict());
    }

    @Test void lifecycle_deveAutorizarDonoEAdminENegarOutrosPapeis() throws Exception {
        User developer = member(ProjectRole.DEVELOPER), viewer = member(ProjectRole.VIEWER), admin = member(ProjectRole.ADMIN), outsider = user();
        for (String action : List.of("archive", "restore")) {
            for (User denied : List.of(developer, viewer)) mvc.perform(post("/api/boards/" + boardId + "/" + action).header("Authorization", token(denied))).andExpect(status().isForbidden());
            mvc.perform(post("/api/boards/" + boardId + "/" + action).header("Authorization", token(outsider))).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/boards/" + boardId + "/archive").header("Authorization", token(admin))).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/" + projectId + "/boards/archived").header("Authorization", token(viewer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/" + projectId + "/boards/archived").header("Authorization", token(outsider))).andExpect(status().isNotFound());
        mvc.perform(post("/api/boards/" + boardId + "/restore").header("Authorization", token(owner))).andExpect(status().isOk());
        mvc.perform(post("/api/boards/" + boardId + "/archive").header("Authorization", token(owner))).andExpect(status().isNoContent());
        mvc.perform(post("/api/boards/" + boardId + "/restore").header("Authorization", token(admin))).andExpect(status().isOk());
    }

    @Test void archived_deveBloquearTarefasColunasComentariosEConfiguracoes() throws Exception {
        service.archiveBoard(boardId, owner.getId());
        String auth = token(owner);
        for (String suffix : List.of("", "/comments", "/activities")) mvc.perform(get("/api/tasks/" + taskId + suffix).header("Authorization", auth)).andExpect(status().isConflict());
        mvc.perform(delete("/api/tasks/" + taskId).header("Authorization", auth)).andExpect(status().isConflict());
        mvc.perform(post("/api/tasks").header("Authorization", auth).contentType("application/json").content("{\"columnId\":" + tasks.findById(taskId).orElseThrow().getColumn().getId() + ",\"title\":\"Não criar\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/boards/" + boardId + "/columns").header("Authorization", auth).contentType("application/json").content("{\"name\":\"Não criar\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/boards/" + boardId).header("Authorization", auth).contentType("application/json").content("{\"name\":\"Não editar\"}")).andExpect(status().isConflict());
        assertThat(tasks.findByColumnBoardId(boardId)).hasSize(1);
    }

    @Test void github_deveLiberarRepoParaMainBoardERecusarRestauracaoComConflito() throws Exception {
        link(boardId); service.archiveBoard(boardId, owner.getId()); link(mainId);
        mvc.perform(post("/api/boards/" + boardId + "/restore").header("Authorization", token(owner))).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("outro board ativo")));
        assertThat(boards.findById(boardId).orElseThrow().getArchived()).isTrue();
        assertThat(tasks.findById(taskId).orElseThrow().getGithubIssueId()).isEqualTo(9001L);
        assertThat(boards.findByGithubRepoIdAndArchivedFalse(repoId).orElseThrow().getId()).isEqualTo(mainId);
        jobs.execute(new GithubJobEvent("REMOVE", boardId, repoId, owner.getId(), 123L, null, null));
        verify(client, never()).removeHook(anyString(), anyLong(), any(), anyString());
        github.unlink(mainId, owner.getId()); service.restoreBoard(boardId, owner.getId());
        assertThat(boards.findByGithubRepoIdAndArchivedFalse(repoId).orElseThrow().getId()).isEqualTo(boardId);
    }

    @Test void github_deveDescartarWebhooksEJobsEnquantoArquivado() throws Exception {
        link(boardId);
        String delivery = UUID.randomUUID().toString();
        byte[] payload = json.writeValueAsBytes(Map.of("repository", Map.of("id", repoId), "ref", "refs/heads/feature/task-" + taskId + "-demo", "commits", List.of(Map.of("id", "a", "message", "closes #" + taskId))));
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        webhooks.receive(payload, signature, delivery, "push");
        webhooks.receive(payload, signature, delivery + "-stale", "push");
        long oldGeneration = boards.findById(boardId).orElseThrow().getGithubGeneration();
        service.archiveBoard(boardId, owner.getId()); processor.process(delivery);
        webhooks.receive(payload, signature, delivery + "-after", "push");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_deliveries WHERE id=?", Integer.class, delivery + "-after")).isZero();
        jobs.execute(new GithubJobEvent("LINK", boardId, repoId, owner.getId(), null, null, null));
        jobs.execute(new GithubJobEvent("SYNC", boardId, repoId, owner.getId(), null, null, null));
        verify(client, never()).registerHook(anyString(), anyLong(), anyString(), anyString());
        verify(client, never()).issues(anyString(), anyLong());
        assertThat(tasks.findById(taskId).orElseThrow().getColumn().getRole()).isEqualTo(ColumnRole.BACKLOG);
        assertThat(activities.findByTaskIdOrderByCreatedAtDesc(taskId)).hasSize(1);
        service.restoreBoard(boardId, owner.getId());
        processor.process(delivery + "-stale");
        jobs.execute(new GithubJobEvent("SYNC", boardId, repoId, owner.getId(), null, null, null, oldGeneration));
        verify(client, never()).issues(anyString(), anyLong());
        assertThat(tasks.findById(taskId).orElseThrow().getColumn().getRole()).isEqualTo(ColumnRole.BACKLOG);
        jobs.execute(new GithubJobEvent("LINK", boardId, repoId, owner.getId(), null, null, null, boards.findById(boardId).orElseThrow().getGithubGeneration()));
        verify(client).registerHook(anyString(), anyLong(), anyString(), anyString());
        verify(client).issues(anyString(), anyLong());
    }

    @Test void lifecycle_deveSerIdempotenteENegarRestauracaoEmProjetoArquivado() {
        service.archiveBoard(boardId, owner.getId());
        var archivedAt = boards.findById(boardId).orElseThrow().getArchivedAt();
        service.archiveBoard(boardId, owner.getId());
        assertThat(boards.findById(boardId).orElseThrow().getArchivedAt()).isEqualTo(archivedAt);
        boards.findById(mainId).orElseThrow().getProject().setArchived(true);
        assertThatThrownBy(() -> service.restoreBoard(boardId, owner.getId())).isInstanceOf(com.devboard.exception.ConflictException.class);
        assertThat(boards.findById(boardId).orElseThrow().getArchived()).isTrue();
    }
}
