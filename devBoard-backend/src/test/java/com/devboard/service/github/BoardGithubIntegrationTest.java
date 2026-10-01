package com.devboard.service.github;

import com.devboard.async.GithubEventListener;
import com.devboard.dto.board.*;
import com.devboard.dto.project.CreateProjectRequest;
import com.devboard.dto.task.*;
import com.devboard.entity.*;
import com.devboard.entity.enums.*;
import com.devboard.repository.*;
import com.devboard.security.JwtTokenProvider;
import com.devboard.service.*;
import com.devboard.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"github.webhook-secret=test-webhook-secret", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
class BoardGithubIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users; @Autowired ProjectService projects; @Autowired BoardService boards;
    @Autowired BoardGithubService github; @Autowired TaskService tasks; @Autowired GithubEventProcessor processor;
    @Autowired JwtTokenProvider jwt; @Autowired TransactionTemplate transaction;
    @MockBean GithubRepositoryClient client;
    @MockBean GithubEventListener listener;
    User owner; Long projectId, boardId, repoId; String token;
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString().substring(0,12);
        User u = new User(); u.setUsername("board-"+suffix); u.setEmail(suffix+"@example.com"); u.setGithubToken("fake-test-token"); owner=users.save(u);
        token = "Bearer " + jwt.generateToken(owner);
        CreateProjectRequest p = new CreateProjectRequest(); p.setName("Projeto " + suffix);
        var created = projects.create(p, owner.getId()); projectId=created.getId(); boardId=created.getBoards().get(0).getId(); repoId=100000L+boardId;
        when(client.repository("fake-test-token",repoId)).thenReturn(new GithubRepositoryClient.RepositoryData(repoId,"org","repo","org/repo",null,"https://github.com/org/repo","main",true));
        LinkGithubRequest r = new LinkGithubRequest(); r.setGithubRepoId(repoId); github.link(boardId,r,owner.getId());
    }
    @Test void projetoEBoards_deveRenomearPadraoECriarOutroSemGitHub() {
        UpdateBoardRequest r=new UpdateBoardRequest(); r.setName("Frontend"); boards.updateBoard(boardId,r,owner.getId());
        assertThat(boards.getBoardView(boardId,owner.getId()).isDefaultBoard()).isTrue();
        var second=secondBoard(projectId);
        assertThat(second.getGithubRepoId()).isNull(); assertThat(second.getColumns()).hasSize(5);
        assertThat(projects.list(owner.getId(),false,0,20).getContent()).anySatisfy(p -> { assertThat(p.getId()).isEqualTo(projectId); assertThat(p.getBoardCount()).isEqualTo(2); assertThat(p.getGithubBoardCount()).isEqualTo(1); });
    }
    @Test void link_deveGarantirUnicidadeEntreProjetosNoBanco() {
        CreateProjectRequest p=new CreateProjectRequest(); p.setName("Outro projeto"); Long another=projects.create(p,owner.getId()).getBoards().get(0).getId();
        LinkGithubRequest r=new LinkGithubRequest(); r.setGithubRepoId(repoId);
        assertThatThrownBy(() -> github.link(another,r,owner.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE boards SET github_repo_id=? WHERE id=?",repoId,another)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void move_deveRejeitarOutroBoardMesmoProjeto() {
        Long task=task(boardId); var second=secondBoard(projectId);
        MoveTaskRequest r=new MoveTaskRequest(); r.setColumnId(second.getColumns().get(0).getId()); r.setPosition(0);
        assertThatThrownBy(() -> tasks.move(task,r,owner.getId())).isInstanceOf(InvalidRequestException.class);
    }
    @Test void webhook_deveValidarHmacAntesDeRegistrarEntrega() throws Exception {
        mvc.perform(post("/webhook/github").contentType("application/json").content("{}").header("X-Hub-Signature-256","sha256=00")).andExpect(status().isUnauthorized());
    }
    @Test void webhook_deveAceitarRepositorioDesconhecidoSemProcessar() throws Exception {
        String id=UUID.randomUUID().toString(); send(id,"push",Map.of("repository",Map.of("id",Long.MAX_VALUE),"commits",List.of()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_deliveries WHERE id=?",Integer.class,id)).isZero();
    }
    @Test void webhook_deveSerAssincronoIdempotenteEIsoladoPorBoard() throws Exception {
        Long local=task(boardId); Long foreign=task(secondBoard(projectId).getId()); String id=UUID.randomUUID().toString();
        var body=Map.of("repository",Map.of("id",repoId),"ref","refs/heads/topic","commits",List.of(Map.of("id","abcdef","message","closes #"+foreign+" and #"+local)));
        send(id,"push",body); send(id,"push",body);
        assertThat(tasks.getById(local,owner.getId()).getColumnName()).isEqualTo("Backlog");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_deliveries WHERE id=?",Integer.class,id)).isEqualTo(1);
        processor.process(id); processor.process(id);
        assertThat(tasks.getById(local,owner.getId()).getColumnName()).isEqualTo("In Progress");
        assertThat(tasks.getById(foreign,owner.getId()).getColumnName()).isEqualTo("Backlog");
    }
    @Test void push_deveConcluirSemRegressaoEIgnorarAutomacaoDesligada() throws Exception {
        Long task=task(boardId);
        event("push",Map.of("ref","refs/heads/feature/task-"+task+"-demo","commits",List.of(Map.of("id","first","message","closes #"+task))));
        assertThat(tasks.getById(task,owner.getId()).getColumnName()).isEqualTo("Done");
        event("push",Map.of("ref","refs/heads/feature/task-"+task+"-demo","commits",List.of(Map.of("id","second","message","WIP more work"))));
        assertThat(tasks.getById(task,owner.getId()).getColumnName()).isEqualTo("Done");
        Long another=task(boardId); GithubSettingsRequest r=new GithubSettingsRequest(); r.setMoveOnCommit(false); github.update(boardId,r,owner.getId());
        event("push",Map.of("ref","refs/heads/feature/task-"+another+"-demo","commits",List.of(Map.of("id","third","message","work"))));
        assertThat(tasks.getById(another,owner.getId()).getColumnName()).isEqualTo("Backlog");
    }
    @Test void issues_deveImportarNoBoardEMapearLabelPorBoard() throws Exception {
        var issue=Map.of("id",repoId*10,"number",1,"title","Issue importada","body","Descrição","html_url","https://github.com/org/repo/issues/1","labels",List.of(Map.of("id",13,"name","bug","color","123456")));
        event("issues",Map.of("action","opened","issue",issue));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks t JOIN board_columns c ON c.id=t.column_id WHERE c.board_id=? AND t.github_issue_id=?",Integer.class,boardId,repoId*10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_label_mappings WHERE board_id=?",Integer.class,boardId)).isEqualTo(1);
        event("issues",Map.of("action","closed","issue",issue));
        assertThat(jdbc.queryForObject("SELECT c.semantic_role FROM tasks t JOIN board_columns c ON c.id=t.column_id WHERE c.board_id=? AND t.github_issue_id=?",String.class,boardId,repoId*10)).isEqualTo("DONE");
        event("issues",Map.of("action","reopened","issue",issue));
        assertThat(jdbc.queryForObject("SELECT c.semantic_role FROM tasks t JOIN board_columns c ON c.id=t.column_id WHERE c.board_id=? AND t.github_issue_id=?",String.class,boardId,repoId*10)).isEqualTo("TODO");
    }
    @Test void pr_deveVincularMoverParaRevisaoEConcluirNoMerge() throws Exception {
        Long task=task(boardId); Map<String,Object> pr=new HashMap<>();
        pr.put("id",123); pr.put("title","Implementa #"+task); pr.put("html_url","https://github.com/org/repo/pull/1");
        pr.put("head",Map.of("ref","feature/task-"+task+"-demo","repo",Map.of("id",repoId)));
        event("pull_request",Map.of("action","opened","pull_request",pr));
        assertThat(tasks.getById(task,owner.getId()).getColumnName()).isEqualTo("In Review");
        pr.put("merged",true); event("pull_request",Map.of("action","closed","pull_request",pr));
        assertThat(tasks.getById(task,owner.getId()).getGithubPrState()).isEqualTo("MERGED");
        assertThat(tasks.getById(task,owner.getId()).getCompletedAt()).isNotNull();
    }
    @Test void webhook_deveIgnorarProjetoArquivadoEEntregaAnteriorAoDesvinculo() throws Exception {
        Long task=task(boardId); String id=UUID.randomUUID().toString();
        send(id,"push",Map.of("repository",Map.of("id",repoId),"ref","refs/heads/feature/task-"+task+"-demo","commits",List.of(Map.of("id","a","message","work"))));
        github.unlink(boardId,owner.getId()); processor.process(id);
        assertThat(tasks.getById(task,owner.getId()).getColumnName()).isEqualTo("Backlog");
        projects.archive(projectId,owner.getId());
        send(UUID.randomUUID().toString(),"push",Map.of("repository",Map.of("id",repoId)));
    }
    @Test void permissoes_deveNegarViewerENaoMembro() throws Exception {
        User u=new User(); String unique=UUID.randomUUID().toString(); u.setUsername(unique);u.setEmail(unique+"@example.com");u=users.save(u);
        String viewer="Bearer "+jwt.generateToken(u);
        mvc.perform(get("/api/boards/"+boardId+"/github-settings").header("Authorization",viewer)).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'VIEWER')",projectId,u.getId());
        mvc.perform(get("/api/boards/"+boardId+"/github-settings").header("Authorization",viewer)).andExpect(status().isOk());
        mvc.perform(delete("/api/boards/"+boardId+"/link-github").header("Authorization",viewer)).andExpect(status().isForbidden());
    }
    private BoardResponse secondBoard(Long project) { CreateBoardRequest r=new CreateBoardRequest();r.setName("Board "+UUID.randomUUID());return boards.createBoard(project,r,owner.getId()); }
    private Long task(Long board) { CreateTaskRequest r=new CreateTaskRequest();r.setColumnId(boards.getBoardView(board,owner.getId()).getColumns().get(0).getId());r.setTitle("Tarefa de teste");return tasks.create(r,owner.getId()).getId(); }
    private void event(String type, Map<String,Object> body) throws Exception { Map<String,Object> data=new HashMap<>(body);data.put("repository",Map.of("id",repoId));String id=UUID.randomUUID().toString();send(id,type,data);processor.process(id); }
    private void send(String id,String type,Object body) throws Exception {
        byte[] payload=json.writeValueAsBytes(body); Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        mvc.perform(post("/webhook/github").contentType("application/json").content(payload).header("X-Hub-Signature-256","sha256="+HexFormat.of().formatHex(mac.doFinal(payload))).header("X-GitHub-Delivery",id).header("X-GitHub-Event",type)).andExpect(status().isAccepted());
    }
}
