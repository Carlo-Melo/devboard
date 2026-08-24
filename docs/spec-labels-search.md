# spec-labels-search.md — Labels, Busca e Filtros

> **Pré-requisito**: `claude.md` + `spec-tasks.md` + `spec-board-kanban.md`

**Status**: MVP — Prioridade 7
**Dependências**: Tarefas, Quadro, Projetos

---

## 1. OBJETIVO

Dois recursos complementares que tornam o quadro utilizável à medida que o volume de tarefas cresce:

- **Labels**: categorização livre e visual, definida por projeto e sincronizável com as labels do GitHub.
- **Busca e filtros**: encontrar tarefas específicas sem depender de leitura visual do quadro.

---

## 2. ESCOPO

### Dentro do escopo
- Criar, editar e excluir labels do projeto
- Conjunto de labels sugeridas na criação do projeto
- Aplicar e remover labels em tarefas
- Sincronizar labels com o repositório GitHub vinculado
- Busca textual em tarefas
- Filtros combinados
- Ordenação de resultados
- Filtros predefinidos de uso frequente

### Fora do escopo
- Labels globais compartilhadas entre projetos
- Filtros salvos pelo usuário
- Busca full-text em comentários

---

## 3. LABELS

### 3.1 Modelo

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| projeto | referência | obrigatório |
| nome | texto | obrigatório, 1–50 caracteres, único dentro do projeto (sem distinção de maiúsculas) |
| cor | texto | código hexadecimal; obrigatório |
| descrição | texto | opcional |
| github label name | texto | opcional; nome correspondente no repositório vinculado |
| criado em / atualizado em | timestamp | automático |

**Regras**
- Labels pertencem ao projeto, não ao quadro. Todas as tarefas do projeto podem usá-las.
- Excluir uma label a remove de todas as tarefas que a usavam; as tarefas permanecem.
- A cor é obrigatória porque a label existe para ser reconhecida visualmente no card.

### 3.2 Conjunto sugerido

Criado junto com o projeto. O usuário pode editar ou excluir livremente.

| Nome | Uso |
|---|---|
| bug | defeito a corrigir |
| feature | funcionalidade nova |
| enhancement | melhoria de algo existente |
| documentation | documentação |
| urgent | demanda prioridade imediata |
| blocked | impedida por dependência externa |

### 3.3 Sincronização com o GitHub

Quando o projeto tem repositório vinculado:

| Origem | Comportamento |
|---|---|
| Label criada no devBoard | criada no repositório com mesmo nome e cor, se ainda não existir |
| Label aplicada a tarefa com issue vinculada | aplicada também à issue |
| Label removida de tarefa com issue vinculada | removida também da issue |
| Labels alteradas na issue no GitHub | refletidas na tarefa; labels inexistentes no projeto são criadas automaticamente |

Falhas de sincronização não bloqueiam a operação local — são registradas em log.

### 3.4 Endpoints

| Endpoint | Descrição | Permissão |
|---|---|---|
| `GET /api/projects/{projectId}/labels` | lista as labels do projeto | qualquer participante |
| `POST /api/projects/{projectId}/labels` | cria label (nome, cor, descrição) | dono ou `ADMIN` |
| `PUT /api/labels/{labelId}` | edita nome, cor ou descrição | dono ou `ADMIN` |
| `DELETE /api/labels/{labelId}` | exclui e remove de todas as tarefas | dono ou `ADMIN` |
| `POST /api/tasks/{taskId}/labels` | aplica uma ou mais labels à tarefa | quem pode editar a tarefa |
| `DELETE /api/tasks/{taskId}/labels/{labelId}` | remove a label da tarefa | quem pode editar a tarefa |

**Erros de label**
| Situação | Status |
|---|---|
| Nome duplicado no projeto | 409 |
| Cor em formato inválido | 400 |
| Label de outro projeto aplicada à tarefa | 400 |

---

## 4. BUSCA E FILTROS

### 4.1 Endpoint de busca — `GET /api/projects/{projectId}/tasks/search`

Retorna tarefas em formato de lista (não agrupadas por coluna), paginadas.

**Parâmetros de busca textual**

| Parâmetro | Comportamento |
|---|---|
| `q` | busca parcial e sem distinção de maiúsculas no título e na descrição |

**Parâmetros de filtro** — todos opcionais e combináveis com E lógico:

| Parâmetro | Valores aceitos | Observação |
|---|---|---|
| `boardId` | id de quadro | restringe a um quadro |
| `columnId` | um ou mais ids | aceita múltiplos valores |
| `columnRole` | papel semântico | alternativa ao id, resistente a renomeações |
| `assigneeId` | um ou mais ids, ou `unassigned` | `unassigned` traz tarefas sem responsável |
| `creatorId` | um ou mais ids | |
| `type` | um ou mais tipos de tarefa | |
| `priority` | uma ou mais prioridades | |
| `labelId` | um ou mais ids | por padrão traz tarefas com qualquer uma das labels |
| `labelMatch` | `any` \| `all` | `all` exige todas as labels informadas |
| `dueBefore` / `dueAfter` | data | intervalo de prazo |
| `overdue` | booleano | prazo vencido e tarefa não concluída |
| `createdBefore` / `createdAfter` | data | intervalo de criação |
| `hasGithubBranch` | booleano | possui branch vinculada |
| `hasOpenPr` | booleano | possui PR em aberto |
| `archived` | booleano | default falso |

**Parâmetros de ordenação e paginação**

| Parâmetro | Valores | Default |
|---|---|---|
| `sortBy` | `priority`, `dueDate`, `createdAt`, `updatedAt`, `title`, `assignee` | `updatedAt` |
| `sortDir` | `asc`, `desc` | `desc` |
| `page` / `size` | numérico | 0 / 20; máximo de 100 por página |

Na ordenação por prioridade, a ordem natural é `URGENT` → `HIGH` → `MEDIUM` → `LOW`, e não alfabética.

**Saída (200)**: lista paginada de tarefas em formato resumido, com o total de resultados.

### 4.2 Filtros no quadro

O endpoint de visualização do quadro (`spec-board-kanban.md`) aceita o mesmo vocabulário de filtros. A diferença é o formato da resposta: o quadro devolve as tarefas agrupadas por coluna, mantendo colunas vazias visíveis; a busca devolve uma lista plana.

### 4.3 Filtros predefinidos

Atalhos expostos ao usuário, montados sobre o mesmo endpoint de busca:

| Atalho | Equivale a |
|---|---|
| Minhas tarefas | responsável = usuário atual, não arquivadas |
| Minhas tarefas em andamento | responsável = usuário atual, coluna de papel `IN_PROGRESS` |
| Atrasadas | prazo vencido, não concluídas |
| Urgentes em aberto | prioridade `URGENT`, coluna diferente de `DONE` |
| Sem responsável | sem responsável, não arquivadas |
| QA pendente | tipo `QA`, coluna diferente de `DONE` |
| Aguardando revisão | coluna de papel `IN_REVIEW` |
| Com PR aberto | possui PR em aberto |
| Paradas há mais de 7 dias | sem atualização há mais de 7 dias, coluna diferente de `DONE` |

---

## 5. FLUXOS

### 5.1 Criação de label durante a edição da tarefa
1. Usuário abre o seletor de labels em uma tarefa.
2. Digita um nome que ainda não existe.
3. Sistema oferece criar a label ali mesmo, com cor sugerida.
4. Label é criada no projeto e aplicada à tarefa.
5. Se o projeto tem repositório vinculado, a label é criada também no GitHub.

### 5.2 Busca combinada
1. Usuário busca por um termo e adiciona filtros de responsável e prioridade.
2. Cliente envia todos os parâmetros em uma única requisição.
3. Sistema aplica os critérios em conjunto e devolve a lista paginada.
4. Alterar a ordenação reconsulta o mesmo endpoint, preservando os filtros.

### 5.3 Label removida de um projeto
1. Admin exclui uma label.
2. Sistema remove a associação de todas as tarefas que a usavam.
3. As tarefas continuam existindo, apenas sem aquela label.
4. Se houver issues vinculadas, a label é removida delas no GitHub.

---

## 6. CRITÉRIOS DE ACEITE

- [ ] Projeto novo já vem com o conjunto de labels sugeridas
- [ ] Nome de label duplicado no projeto é rejeitado, ignorando maiúsculas
- [ ] Excluir label a remove das tarefas sem excluí-las
- [ ] Label de outro projeto não pode ser aplicada à tarefa
- [ ] Label criada no devBoard aparece no repositório GitHub vinculado
- [ ] Label alterada na issue do GitHub reflete na tarefa
- [ ] Falha na sincronização de label não impede a operação local
- [ ] Busca textual encontra por título e por descrição, sem distinção de maiúsculas
- [ ] Filtros combinados funcionam em conjunto, não isoladamente
- [ ] `labelMatch=all` exige todas as labels informadas
- [ ] `assigneeId=unassigned` traz apenas tarefas sem responsável
- [ ] Ordenação por prioridade segue a ordem semântica, não a alfabética
- [ ] Filtro `overdue` ignora tarefas já concluídas
- [ ] Paginação respeita o limite máximo de 100 itens
- [ ] Filtros no quadro mantêm colunas vazias visíveis

---

**Próxima spec**: `spec-notifications.md`
