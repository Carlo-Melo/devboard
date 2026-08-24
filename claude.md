# claude.md — Guia de Desenvolvimento devBoard

> **LEIA ESTE ARQUIVO ANTES DE DESENVOLVER QUALQUER FUNCIONALIDADE**

Constituição do projeto. Define **como** implementar. As specs em `docs/` definem **o que** implementar.

Quando spec e este arquivo se contradizem: a spec manda no comportamento, este arquivo manda na forma.

---

## 📋 INFORMAÇÕES DO PROJETO

**Nome**: devBoard — Kanban integrado ao GitHub
**Objetivo**: gerenciamento de projetos para pequenas equipes de desenvolvimento
**Público**: equipes de 2 a 10 devs
**Status**: MVP

---

## 📁 ESTRUTURA DO REPOSITÓRIO

```
devBoard/
├── devBoard-backend/          # Java + Spring Boot
├── devBoard-frontend/         # Angular
├── docs/                      # Especificações
│   ├── claude.md              # Este arquivo (leia primeiro)
│   ├── README.md
│   ├── spec-authentication.md
│   ├── spec-projects.md
│   ├── spec-board-kanban.md
│   ├── spec-tasks.md
│   ├── spec-github-integration.md
│   ├── spec-members.md
│   ├── spec-labels-search.md
│   └── spec-notifications.md
└── README.md
```

---

## 🔧 STACK

### Backend
| Item | Escolha |
|---|---|
| Linguagem | Java 17+ |
| Framework | Spring Boot 3.2+ |
| Banco | PostgreSQL 14+ |
| Autenticação | JWT (JJWT 0.12+) |
| Migrations | Liquibase |
| GitHub API | `org.kohsuke:github-api` |
| Cache / rate limit | Caffeine (in-memory, MVP) |
| Build | Maven 3.8+ |
| Logging | SLF4J + Logback |
| Testes | JUnit 5 + Mockito |

### Frontend
| Item | Escolha |
|---|---|
| Framework | Angular 17+ |
| Linguagem | TypeScript 5.2+ |
| Componentes | Standalone |
| Forms | Reactive Forms |
| HTTP | HttpClient + RxJS |
| Styling | SCSS |
| Testes | Jasmine + Karma; Cypress (E2E) |
| Lint / format | ESLint + Prettier |
| Node | 18+ |

**Sem versionamento de API** — `/api/...` implica v1.

---

## 🏗️ ARQUITETURA

### Backend — Layered

```
Controller   ← HTTP, validação de entrada, tradução de erro
    ↓
Service      ← regra de negócio, permissão, transação
    ↓
Repository   ← acesso a dados
    ↓
PostgreSQL
```

**Regras invioláveis**
- Controller nunca acessa Repository direto.
- Controller nunca contém regra de negócio.
- Service nunca conhece `HttpServletRequest` nem `ResponseEntity`.
- Entity nunca sai do Service — só DTO atravessa a fronteira.
- Toda escrita acontece dentro de `@Transactional` no Service.

### Frontend — Smart/Dumb + Services

```
Smart Component (container: estado, chamadas de API)
    ↓
Dumb Component (apresentação: @Input / @Output)
    ↓
Service (HTTP + estado compartilhado)
    ↓
Backend API
```

---

## 📁 ESTRUTURA DE PASTAS

### Backend (`devBoard-backend/`)

```
src/main/java/com/devboard/
├── config/            # SecurityConfig, CorsConfig, AsyncConfig, CacheConfig
├── controller/        # REST Controllers
├── service/           # Regra de negócio
│   └── github/        # Integração GitHub (client, sync, webhook processor)
├── repository/        # JPA Repositories
├── entity/            # Entidades JPA
│   └── enums/         # Todos os enums do domínio
├── dto/               # DTOs por domínio
│   ├── auth/
│   ├── project/
│   ├── board/
│   ├── task/
│   ├── member/
│   ├── label/
│   ├── notification/
│   └── common/        # PageResponse, ApiError, MessageResponse
├── mapper/            # Entity → DTO
├── exception/         # Exceções + GlobalExceptionHandler
├── security/          # JWT, filtro, SecurityUser, PermissionService
├── async/             # @Async, listeners de evento, processadores
├── scheduler/         # Rotinas agendadas (prazos, limpeza)
├── ratelimit/         # Controle de taxa
├── util/              # Slugify, criptografia, helpers
└── DevBoardApplication.java

src/main/resources/
├── application.yml
├── application-dev.yml
├── application-prod.yml
└── db/changelog/      # Liquibase
```

### Frontend (`devBoard-frontend/`)

```
src/app/
├── core/
│   ├── services/       # AuthService, ProjectService, TaskService...
│   ├── guards/         # AuthGuard, RoleGuard
│   ├── interceptors/   # AuthInterceptor, ErrorInterceptor
│   └── models/         # Interfaces + enums espelhando o backend
├── shared/
│   ├── components/
│   ├── pipes/
│   ├── directives/     # *hasRole
│   └── constants/
└── features/
    ├── auth/           # login, register, password-recovery
    ├── projects/       # project-list, project-detail, project-create
    ├── board/          # kanban, columns
    ├── tasks/          # task-card, task-detail, task-create
    ├── members/        # member-list, member-invite
    └── notifications/  # notification-center
```

---

## 📝 NOMENCLATURA

### Backend
| Elemento | Convenção | Exemplo |
|---|---|---|
| Pacote | lowercase | `com.devboard.service` |
| Classe | PascalCase | `TaskService` |
| Método | camelCase | `moveTask` |
| Constante | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| Enum (valor) | UPPER_SNAKE_CASE | `IN_PROGRESS` |
| DTO entrada | `{Ação}{Entidade}Request` | `CreateTaskRequest` |
| DTO saída | `{Entidade}Response` | `TaskResponse` |
| DTO resumido | `{Entidade}SummaryResponse` | `TaskSummaryResponse` |

### Frontend
| Elemento | Convenção | Exemplo |
|---|---|---|
| Pasta / arquivo | kebab-case | `task-detail.component.ts` |
| Classe | PascalCase | `TaskDetailComponent` |
| Interface | PascalCase, sem prefixo `I` | `Task`, `TaskResponse` |
| Método / variável | camelCase | `loadTasks`, `isLoading` |

---

## 🧩 ENUMS

Todos os enums do domínio ficam em `entity/enums/`. Persistidos como `VARCHAR` via `@Enumerated(EnumType.STRING)` — **nunca** `ORDINAL`, porque reordenar o enum corromperia dados existentes.

Enums que carregam comportamento devem carregá-lo neles mesmos, não em `if` espalhados pelos services.

```java
public enum TaskType {
    DEV(true),
    QA(false),
    DESIGN(false),
    DOCUMENTATION(true),
    OPERATIONAL(false),
    OTHER(false);

    private final boolean supportsBranch;

    TaskType(boolean supportsBranch) {
        this.supportsBranch = supportsBranch;
    }

    public boolean supportsBranch() {
        return supportsBranch;
    }
}
```

```java
public enum TaskPriority {
    LOW(1), MEDIUM(2), HIGH(3), URGENT(4);

    private final int weight;

    TaskPriority(int weight) { this.weight = weight; }

    // Ordenação por prioridade usa o peso, nunca a ordem alfabética
    public int getWeight() { return weight; }
}
```

**Espelhar no frontend** em `core/models/`, como union type:

```typescript
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type ColumnRole = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'NONE';
```

---

## 🔐 SEGURANÇA

### JWT
- Algoritmo HS512, expiração 24h
- Claims: `sub` (username), `userId`, `email`
- Secret vem de variável de ambiente, **nunca** versionado
- Filtro popula o `SecurityContext` com `SecurityUser`

### Senha
- BCrypt, força 10
- Regra: mínimo 8 caracteres, com maiúscula, minúscula e número
- A validação de força mora em um `@Validator` customizado, reaproveitado por registro, alteração e redefinição

### Dados sensíveis em repouso

O token do GitHub e o token de recuperação de senha **nunca** são persistidos em texto puro.

- **Token GitHub**: criptografado com AES-GCM. A chave vem de variável de ambiente. Implementar em `util/CryptoService`, aplicado via `@Converter` JPA — assim a entidade trabalha com o valor puro e o banco só vê o cifrado.
- **Token de recuperação**: persistido como hash SHA-256. O valor puro só existe no email enviado. A validação compara hashes.

```java
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    // convertToDatabaseColumn → cifra
    // convertToEntityAttribute → decifra
}
```

Nenhum desses valores pode aparecer em log, em resposta de API ou em `toString()`. Anotar com `@ToString.Exclude`.

### Endpoints públicos
`/api/auth/register`, `/api/auth/login`, `/api/auth/github/**`, `/api/auth/forgot-password`, `/api/auth/reset-password/**`, `/api/invites/{token}` (GET), `/webhook/github`, `/api/health`

Todo o resto exige `Authorization: Bearer {token}`.

---

## 🛡️ PERMISSÕES

As specs definem 7 matrizes de papel. A verificação é **centralizada** em `security/PermissionService` — nunca replicada dentro de cada service.

Papéis: `ADMIN`, `DEVELOPER`, `VIEWER`. O **dono** do projeto não é um registro de membro: sua autoridade vem da propriedade e supera qualquer papel.

```java
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    /** Papel efetivo do usuário no projeto. Dono resolve como ADMIN. */
    public ProjectRole resolveRole(Long projectId, Long userId) { ... }

    public boolean isOwner(Long projectId, Long userId) { ... }

    /** Lança AccessDeniedException se o papel efetivo for inferior ao exigido. */
    public void requireRole(Long projectId, Long userId, ProjectRole minimum) { ... }

    public void requireOwner(Long projectId, Long userId) { ... }

    /** Para regras do tipo "developer só edita a própria tarefa". */
    public void requireTaskEditable(Task task, Long userId) { ... }
}
```

**Como usar no service** — sempre como primeira linha, antes de qualquer leitura de negócio:

```java
public TaskResponse moveTask(Long taskId, MoveTaskRequest request, Long userId) {
    Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));

    permissionService.requireRole(task.getProjectId(), userId, ProjectRole.DEVELOPER);
    // ... regra de negócio
}
```

**Distinção obrigatória**
- Usuário **sem vínculo** com o projeto → `404` (não revela que o recurso existe)
- Usuário **com vínculo, mas papel insuficiente** → `403`

### Frontend

O guard protege a rota; a diretiva esconde o controle:

```html
<button *hasRole="'DEVELOPER'; project: projectId">Nova tarefa</button>
```

Esconder no frontend é conveniência. **A autorização real é sempre no backend** — nunca confiar no cliente.

---

## 📄 PAGINAÇÃO

Toda listagem que pode crescer é paginada. Formato único de resposta em `dto/common/PageResponse`:

```java
@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
```

| Parâmetro | Default | Limite |
|---|---|---|
| `page` | 0 | — |
| `size` | 20 | 100 |
| `sortBy` | varia por endpoint | — |
| `sortDir` | `desc` | `asc` \| `desc` |

`size` acima de 100 é silenciosamente reduzido a 100 — não é erro.

**Listas não paginadas** (por serem naturalmente pequenas e limitadas): colunas de um quadro, labels de um projeto, membros de um projeto.

**Exceção importante**: o endpoint de visualização do quadro devolve as tarefas agrupadas por coluna, **sem paginação**. É a leitura mais frequente do sistema e precisa de uma única consulta com `JOIN FETCH` — nunca uma consulta por coluna nem por tarefa.

---

## 🗑️ SOFT DELETE

Projetos e tarefas são arquivados, não removidos. Sinalizados por campo booleano (`archived`) mais o momento do arquivamento.

```java
@Column(nullable = false)
private Boolean archived = false;

private LocalDateTime archivedAt;
```

**Regras**
- `DELETE /api/...` nesses recursos significa arquivar e responde `204`.
- Consultas de listagem filtram `archived = false` por padrão.
- Repositories expõem métodos explícitos: `findByProjectIdAndArchivedFalse(...)`.
- O histórico de atividades sobrevive ao arquivamento.
- Comentários, labels e membros são removidos de fato (hard delete) — não têm valor histórico isolado.

---

## ⚙️ PROCESSAMENTO ASSÍNCRONO

Nada que dependa de serviço externo pode bloquear a resposta HTTP.

**Vai para assíncrono**: envio de email, processamento de webhook, importação de issues, chamadas de escrita ao GitHub, geração de notificação.

**MVP usa `@Async` com pool dedicado** — não há fila externa. Configurar em `config/AsyncConfig`, com pools separados por natureza da carga (github, email, notification) para que um não afogue o outro.

```java
@Async("githubExecutor")
public void processWebhookEvent(Long projectId, String eventType, String payload) { ... }
```

**Regras**
- Método assíncrono é sempre `void` ou `CompletableFuture`; nunca retorna valor consumido pelo controller.
- Método assíncrono abre a própria transação — `@Async` sai do contexto transacional do chamador.
- Toda exceção é capturada e registrada dentro do método. Exceção que escapa de `@Async` some silenciosamente.
- Falha assíncrona **nunca** desfaz a operação síncrona que a originou. Mover a tarefa no quadro vale mesmo que o GitHub esteja fora do ar.
- Chamada assíncrona precisa vir de outro bean. Chamar um `@Async` de dentro da mesma classe executa síncrono.

### Nova tentativa

Falhas transitórias (rede, `5xx`, limite de taxa do GitHub) tentam até 3 vezes com espera crescente. Falhas definitivas (`401`, `404`, payload inválido) não repetem — são registradas com o payload original.

### Rotinas agendadas

Em `scheduler/`, com `@Scheduled`. No MVP: aviso de prazo próximo e vencido (de hora em hora) e limpeza de notificações lidas com mais de 90 dias (diária).

Toda rotina precisa ser idempotente: rodar duas vezes não pode duplicar notificação.

---

## 🚦 RATE LIMITING

Implementado em `ratelimit/`, com Caffeine, por chave composta.

| Operação | Limite | Chave |
|---|---|---|
| Solicitar recuperação de senha | 3 / hora | email + IP |
| Redefinir senha | 5 tentativas | token |
| Login | 10 / hora | IP |
| Sincronização manual do GitHub | 1 / 5 min | projeto |
| Webhook recebido | 100 / min | IP de origem |

Limite excedido → `429`, com `Retry-After` no cabeçalho.

Aplicar via anotação em cima do método do controller, para manter a regra visível junto ao endpoint:

```java
@RateLimit(key = "#request.email", limit = 3, window = 3600)
@PostMapping("/forgot-password")
```

Estourar o limite de redefinição de senha invalida o token, além de responder `429`.

---

## 🔁 IDEMPOTÊNCIA DE WEBHOOK

O GitHub reenvia entregas. Cada entrega traz um identificador único no cabeçalho `X-GitHub-Delivery`.

Antes de processar, registrar o identificador em tabela dedicada com restrição de unicidade. Violação da restrição significa reentrega — descartar sem processar e sem erro.

Registros com mais de 7 dias são removidos por rotina agendada.

**Prevenção de laço**: alterações que o devBoard faz no GitHub (fechar issue, aplicar label) geram webhooks de volta. Registrar a operação de saída antes de executá-la e descartar o webhook correspondente na volta. Sem isso, fechar uma issue vira ciclo infinito.

---

## 📢 ATIVIDADES E NOTIFICAÇÕES

Transversal a quase todos os módulos. Centralizar em dois services, chamados pelos demais — nunca duplicar a lógica.

- `ActivityService` — registra o que aconteceu (visível a todos do projeto)
- `NotificationService` — avisa quem precisa saber (dirigido a uma pessoa)

**Regras que o `NotificationService` aplica sozinho**, e que nenhum service chamador precisa repetir:
- O autor da ação nunca é notificado dela
- Um evento gera no máximo uma notificação por destinatário
- Precedência: menção › atribuição › movimentação › comentário
- Só notifica quem ainda é membro do projeto
- Respeita as preferências do destinatário

Ambos são acionados **depois** do commit da transação principal, via evento de aplicação. Assim a atividade nunca registra algo que acabou de sofrer rollback:

```java
eventPublisher.publishEvent(new TaskMovedEvent(task, fromColumn, toColumn, userId));
```

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async("notificationExecutor")
public void onTaskMoved(TaskMovedEvent event) { ... }
```

---

## ❌ TRATAMENTO DE ERROS

Um único `GlobalExceptionHandler` com `@RestControllerAdvice`. Controller nunca contém `try/catch` para traduzir erro.

### Exceções e status

| Exceção | Status | Uso |
|---|---|---|
| `ResourceNotFoundException` | 404 | recurso inexistente ou sem vínculo do usuário |
| `InvalidRequestException` | 400 | regra de negócio violada |
| `MethodArgumentNotValidException` | 400 | falha de `@Valid` (do Spring) |
| `UnauthorizedException` | 401 | credencial ausente ou inválida |
| `AccessDeniedException` | 403 | papel insuficiente |
| `ConflictException` | 409 | duplicidade, estado incompatível, limite WIP |
| `RateLimitExceededException` | 429 | limite de taxa |
| `ExternalServiceException` | 503 | GitHub indisponível |

### Formato de resposta

```json
{
  "timestamp": "2026-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Descrição legível do erro",
  "path": "/api/tasks",
  "fieldErrors": [
    { "field": "title", "message": "Título é obrigatório" }
  ]
}
```

`fieldErrors` só aparece em erros de validação.

**Mensagens sensíveis**: em login e recuperação de senha, a mensagem é genérica e idêntica em todos os casos de falha. Não revelar quais emails existem.

---

## 🗄️ BANCO DE DADOS

### Convenções
| Elemento | Padrão |
|---|---|
| Tabela | lowercase, plural — `tasks` |
| Coluna | snake_case — `github_issue_id` |
| Chave estrangeira | `fk_{tabela}_{coluna}` |
| Índice | `idx_{tabela}_{coluna}` |
| Unicidade | `uq_{tabela}_{colunas}` |

### Tipos
| Dado | Tipo |
|---|---|
| Data e hora | `TIMESTAMP WITH TIME ZONE` |
| Data | `DATE` |
| Enum | `VARCHAR(50)` |
| Texto longo | `TEXT` |
| Booleano | `BOOLEAN NOT NULL DEFAULT FALSE` |

### Índices obrigatórios
Toda chave estrangeira, todo campo usado em filtro frequente (`archived`, `assignee_id`, `column_id`, `github_issue_id`, `user_id` em notificações) e toda combinação única definida nas specs.

### Migrations
Liquibase em `db/changelog/`, formato `V{n}__{descricao}.sql`, uma migration por módulo entregue. **Migration aplicada nunca é editada** — corrige-se com uma nova.

### Desempenho
`FetchType.LAZY` em todo `@ManyToOne` e `@OneToMany`. O default de `@ManyToOne` é EAGER e precisa ser sobrescrito explicitamente. Consultas que precisam do relacionamento usam `JOIN FETCH` — o alvo é zero consulta N+1 no carregamento do quadro.

---

## 💻 EXEMPLOS DE REFERÊNCIA — BACKEND

### Controller

```java
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        TaskResponse response = taskService.create(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{taskId}/move")
    public ResponseEntity<TaskResponse> move(
            @PathVariable Long taskId,
            @Valid @RequestBody MoveTaskRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(taskService.move(taskId, request, user.getId()));
    }
}
```

Sem `try/catch`, sem regra de negócio, sem acesso a repository.

### Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardColumnRepository columnRepository;
    private final PermissionService permissionService;
    private final TaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TaskResponse move(Long taskId, MoveTaskRequest request, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));

        permissionService.requireRole(task.getProjectId(), userId, ProjectRole.DEVELOPER);

        BoardColumn target = columnRepository.findById(request.getColumnId())
                .orElseThrow(() -> new ResourceNotFoundException("Coluna não encontrada"));

        if (!target.getBoard().getProject().getId().equals(task.getProjectId())) {
            throw new InvalidRequestException("Coluna pertence a outro projeto");
        }

        if (target.isWipLimitReached()) {
            throw new ConflictException("Limite de trabalho em progresso atingido");
        }

        BoardColumn origin = task.getColumn();
        applyMove(task, target, request.getPosition());

        eventPublisher.publishEvent(new TaskMovedEvent(task, origin, target, userId));

        return taskMapper.toResponse(task);
    }
}
```

Ordem: carregar → permissão → validar → executar → publicar evento → mapear.

### Mapper

```java
@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) { ... }

    public TaskSummaryResponse toSummary(Task task) { ... }
}
```

Mapeamento fica em `mapper/`, nunca inline no service. Response completo e resumido são DTOs distintos — o quadro carrega centenas de tarefas e não deve trafegar descrição, comentários nem histórico.

### Entity

```java
@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_tasks_column_id", columnList = "column_id"),
    @Index(name = "idx_tasks_assignee_id", columnList = "assignee_id")
})
@Getter @Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn column;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TaskType type = TaskType.OTHER;

    @Column(nullable = false)
    private Boolean archived = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

Usar `@Getter @Setter` em vez de `@Data`: `@Data` gera `equals`/`hashCode` sobre todos os campos, o que quebra com relacionamentos lazy e coleções.

---

## 💻 EXEMPLOS DE REFERÊNCIA — FRONTEND

### Service

```typescript
@Injectable({ providedIn: 'root' })
export class TaskService {

  private readonly apiUrl = `${environment.apiUrl}/tasks`;

  constructor(private http: HttpClient) {}

  create(request: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(this.apiUrl, request);
  }

  move(taskId: number, columnId: number, position: number): Observable<Task> {
    return this.http.put<Task>(`${this.apiUrl}/${taskId}/move`, { columnId, position });
  }

  search(filters: TaskFilters, page = 0, size = 20): Observable<PageResponse<TaskSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<PageResponse<TaskSummary>>(`${this.apiUrl}/search`, { params });
  }
}
```

### Componente

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {

  loginForm: FormGroup;
  isLoading = false;
  errorMessage: string | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      emailOrUsername: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    this.authService.login(this.loginForm.value).subscribe({
      next: () => this.router.navigate(['/projects']),
      error: (err: ApiError) => {
        this.isLoading = false;
        this.errorMessage = err.message;
      }
    });
  }
}
```

### Interceptor de erro

Normaliza toda falha HTTP no formato `ApiError` antes de chegar ao componente. `401` limpa a sessão e redireciona ao login. `403` exibe mensagem de permissão. `5xx` exibe mensagem genérica. Nenhum componente lida com `HttpErrorResponse` cru.

### Modelos

```typescript
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  status: number;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}
```

Toda interface espelha exatamente o DTO do backend. Divergência entre os dois é bug.

---

## 📡 COMUNICAÇÃO FRONTEND-BACKEND

```typescript
// environment.ts
export const environment = { production: false, apiUrl: 'http://localhost:8080/api' };

// environment.prod.ts
export const environment = { production: true,  apiUrl: 'https://api.devboard.app/api' };
```

`AuthInterceptor` injeta o JWT em toda requisição. Token guardado em `localStorage` sob a chave `devboardToken`.

---

## 🧪 TESTES

### Backend
- **Unitário** (obrigatório): services, com repositories e clientes externos mockados
- **Integração**: controllers com `@SpringBootTest` + `MockMvc`
- Cobertura mínima: 70%
- Padrão AAA; nome do teste descreve o comportamento: `move_deveLancarConflict_quandoLimiteWipAtingido`
- **Sempre testar**: caminho feliz, cada erro previsto na spec e cada regra de permissão

### Frontend
- **Unitário**: services e pipes
- **Componente**: renderização e interação
- **E2E**: fluxos completos (login, criar tarefa, mover no quadro)
- Cobertura mínima: 70%

**Definição de pronto**: todos os critérios de aceite da spec passam.

---

## 🚀 SETUP

```bash
# Backend
cd devBoard-backend
mvn clean package
mvn spring-boot:run

# Frontend
cd devBoard-frontend
npm install
ng serve --open
ng build --configuration production
```

### Variáveis de ambiente obrigatórias

| Variável | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | conexão PostgreSQL |
| `JWT_SECRET` | assinatura do token (mínimo 64 caracteres) |
| `ENCRYPTION_KEY` | criptografia do token GitHub |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | OAuth |
| `GITHUB_WEBHOOK_SECRET` | validação de assinatura de webhook |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` | envio de email |
| `APP_BASE_URL` | montagem de links em emails |

Nenhum segredo é versionado.

---

## 📚 ORDEM DE IMPLEMENTAÇÃO

| Sprint | Módulos | Specs |
|---|---|---|
| 1 | autenticação, projetos, quadro, tarefas | `spec-authentication`, `spec-projects`, `spec-board-kanban`, `spec-tasks` |
| 2 | integração GitHub, membros | `spec-github-integration`, `spec-members` |
| 3 | labels e busca, notificações | `spec-labels-search`, `spec-notifications` |

Ao final da Sprint 1 o devBoard é um Kanban funcional. A Sprint 2 entrega o diferencial.

**Dentro de cada módulo**: enum → entity → migration → repository → DTO → mapper → service → controller → teste.

---

## 🔍 CHECKLIST ANTES DE ABRIR UM PR

- [ ] Controller sem regra de negócio e sem acesso a repository
- [ ] Verificação de permissão como primeira operação de cada método de service
- [ ] Nenhuma entity atravessando a fronteira do service
- [ ] Escrita dentro de `@Transactional`
- [ ] `FetchType.LAZY` em todo relacionamento
- [ ] Listagem que cresce está paginada
- [ ] Enum persistido como `STRING`
- [ ] Chamada a serviço externo em método assíncrono
- [ ] Exceção tratada dentro do método assíncrono
- [ ] Nenhum segredo em log, resposta ou código
- [ ] Migration nova, nenhuma existente alterada
- [ ] Índice criado para toda chave estrangeira e filtro frequente
- [ ] Testes cobrindo caminho feliz, erros previstos e permissões
- [ ] Todos os critérios de aceite da spec verificados

---

**Versão**: 3.0
**Escopo**: backend (Java/Spring) + frontend (Angular)
**Specs**: `docs/`
