# spec-board-kanban.md — Módulo de Quadro Kanban

> **Pré-requisito**: `claude.md` + `spec-projects.md`

**Status**: MVP — Prioridade 3
**Dependências**: Projetos

---

## 1. OBJETIVO

O quadro é a superfície visual do projeto. Ele organiza tarefas em colunas ordenadas que representam etapas de um fluxo de trabalho. As colunas são totalmente customizáveis, mas o sistema entrega um conjunto padrão pronto para uso.

As colunas também são o alvo das automações do GitHub: mover uma tarefa entre colunas é o que traduz "commit feito" ou "PR mergeado" em estado visível no quadro.

---

## 2. ESCOPO

### Dentro do escopo
- Criar, listar, editar e excluir quadros de um projeto
- Criar, editar, reordenar e excluir colunas
- Conjunto padrão de colunas na criação do projeto
- Visualização do quadro completo com tarefas agrupadas por coluna
- Filtros de visualização do quadro
- Papéis semânticos de coluna (usados pelas automações)
- Limite de trabalho em progresso (WIP) por coluna

### Fora do escopo
- Visualizações alternativas (lista, calendário, timeline)
- Swimlanes
- Templates de quadro compartilháveis entre projetos

---

## 3. MODELO DE DADOS

### 3.1 Quadro

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| projeto | referência | obrigatório |
| nome | texto | obrigatório, 3–100 caracteres |
| descrição | texto | opcional |
| é padrão | booleano | exatamente um quadro padrão por projeto |
| criado em / atualizado em | timestamp | automático |

**Regras**
- Todo projeto tem no mínimo um quadro; o último não pode ser excluído.
- Marcar um quadro como padrão desmarca o anterior.

### 3.2 Coluna

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| quadro | referência | obrigatório |
| nome | texto | obrigatório, 1–50 caracteres, único dentro do quadro |
| cor | texto | código hexadecimal; default definido pelo sistema |
| posição | numérico | sequencial a partir de 0, sem lacunas |
| papel semântico | enum | `BACKLOG` \| `TODO` \| `IN_PROGRESS` \| `IN_REVIEW` \| `DONE` \| `NONE` |
| limite WIP | numérico | opcional; nulo significa sem limite |
| criado em / atualizado em | timestamp | automático |

**Regras sobre o papel semântico**
- É o que permite às automações do GitHub saberem para onde mover uma tarefa, independentemente do nome que o usuário deu à coluna.
- Cada papel diferente de `NONE` pode ser atribuído a no máximo uma coluna por quadro.
- Colunas criadas manualmente nascem com papel `NONE`, salvo escolha explícita.
- Se um papel necessário a uma automação não existir no quadro, a automação daquele evento é ignorada silenciosamente (apenas registrada em log).

**Regras sobre o limite WIP**
- Ao mover uma tarefa para uma coluna que já atingiu o limite, o sistema rejeita a operação com erro específico.
- O limite não se aplica a movimentações automáticas originadas de webhooks do GitHub: nesses casos a tarefa é movida e uma atividade de alerta é registrada.

### 3.3 Conjunto padrão de colunas

Criado automaticamente junto com o quadro padrão do projeto:

| Posição | Nome | Papel | Uso |
|---|---|---|---|
| 0 | Backlog | `BACKLOG` | tarefas ainda não priorizadas |
| 1 | To-Do | `TODO` | prontas para começar |
| 2 | In Progress | `IN_PROGRESS` | em execução |
| 3 | In Review | `IN_REVIEW` | aguardando revisão ou QA |
| 4 | Done | `DONE` | concluídas |

---

## 4. ENDPOINTS

Todos exigem autenticação e vínculo com o projeto.

### 4.1 Criar quadro — `POST /api/projects/{projectId}/boards`

**Entrada**: nome, descrição (opcional), colunas iniciais (opcional — se omitido, aplica o conjunto padrão)

**Permissão**: dono ou `ADMIN`

**Saída (201)**: quadro com suas colunas

---

### 4.2 Listar quadros — `GET /api/projects/{projectId}/boards`

**Saída (200)**: quadros do projeto com suas colunas e a contagem de tarefas por coluna, **sem** as tarefas em si.

---

### 4.3 Visualizar quadro — `GET /api/boards/{boardId}`

**Entrada (query, todos opcionais)**: `assigneeId`, `label`, `priority`, `type`, `search`

**Comportamento**: retorna o quadro com todas as colunas na ordem correta e, dentro de cada uma, as tarefas ordenadas por posição. Os filtros são aplicados às tarefas, nunca às colunas — uma coluna sem tarefas correspondentes ao filtro aparece vazia, não some.

**Saída (200)**: quadro, colunas e tarefas em formato resumido (dados suficientes para renderizar o card)

> Este é o endpoint mais acessado do sistema. Deve evitar consultas em cascata por tarefa.

---

### 4.4 Editar quadro — `PUT /api/boards/{boardId}`

**Entrada**: nome, descrição, indicador de padrão

**Permissão**: dono ou `ADMIN`

---

### 4.5 Excluir quadro — `DELETE /api/boards/{boardId}`

**Permissão**: dono ou `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| É o único quadro do projeto | 409 |
| Quadro possui tarefas | 409 — exige mover ou excluir as tarefas antes |

---

### 4.6 Criar coluna — `POST /api/boards/{boardId}/columns`

**Entrada**: nome, cor (opcional), posição (opcional — default: ao final), papel semântico (opcional), limite WIP (opcional)

**Comportamento**: ao inserir em uma posição intermediária, as colunas seguintes são deslocadas.

**Erros**
| Situação | Status |
|---|---|
| Nome duplicado no quadro | 409 |
| Papel semântico já usado por outra coluna | 409 |

---

### 4.7 Editar coluna — `PUT /api/columns/{columnId}`

**Entrada**: nome, cor, papel semântico, limite WIP

**Observação**: a posição não é alterada aqui — usa-se o endpoint de reordenação.

---

### 4.8 Excluir coluna — `DELETE /api/columns/{columnId}`

**Entrada (query)**: `moveTasksTo` (id de coluna de destino, opcional)

**Comportamento**
- Sem tarefas: exclui e reajusta as posições restantes.
- Com tarefas e `moveTasksTo` informado: move as tarefas e exclui.
- Com tarefas e sem destino informado: rejeita.

**Erros**
| Situação | Status |
|---|---|
| Coluna com tarefas e sem destino informado | 409 |
| É a última coluna do quadro | 409 |

---

### 4.9 Reordenar colunas — `PUT /api/boards/{boardId}/columns/reorder`

**Entrada**: lista completa de ids de coluna na nova ordem

**Validações**: a lista deve conter exatamente todas as colunas do quadro, sem repetições nem omissões.

**Saída (200)**: colunas na nova ordem

---

## 5. FLUXOS

### 5.1 Nascimento do quadro
Quando um projeto é criado, o sistema gera um quadro padrão chamado "Main Board" e aplica o conjunto padrão de cinco colunas com seus papéis semânticos. Nenhuma ação do usuário é necessária.

### 5.2 Abertura do quadro
1. Usuário acessa o projeto e seleciona um quadro.
2. Sistema devolve colunas ordenadas e tarefas agrupadas.
3. Cliente renderiza as colunas lado a lado, com contador e, quando definido, o limite WIP.
4. Filtros aplicados pelo usuário reconsultam o mesmo endpoint.

### 5.3 Reorganização do fluxo de trabalho
1. Usuário arrasta uma coluna para outra posição.
2. Cliente envia a ordem completa resultante.
3. Sistema regrava as posições em uma única operação, evitando estados intermediários inconsistentes.

### 5.4 Coluna que atingiu o limite WIP
1. Usuário tenta mover uma tarefa para a coluna cheia.
2. Sistema rejeita com erro específico e mensagem indicando o limite.
3. Se a mesma movimentação vier de um webhook do GitHub, ela é executada assim mesmo e uma atividade de alerta é registrada no projeto.

---

## 6. PERMISSÕES

| Ação | Dono | Admin | Developer | Viewer |
|---|:---:|:---:|:---:|:---:|
| Visualizar quadro | ✅ | ✅ | ✅ | ✅ |
| Criar / editar / excluir quadro | ✅ | ✅ | ❌ | ❌ |
| Criar / editar / excluir coluna | ✅ | ✅ | ❌ | ❌ |
| Reordenar colunas | ✅ | ✅ | ✅ | ❌ |

---

## 7. CRITÉRIOS DE ACEITE

- [ ] Criar projeto gera quadro padrão com as cinco colunas e seus papéis
- [ ] Visualizar quadro devolve colunas ordenadas com tarefas agrupadas
- [ ] Filtro por responsável, label, prioridade ou tipo afeta apenas as tarefas
- [ ] Coluna sem tarefas após filtro continua visível e vazia
- [ ] Não é possível criar duas colunas com o mesmo nome no quadro
- [ ] Não é possível atribuir o mesmo papel semântico a duas colunas
- [ ] Excluir coluna com tarefas exige coluna de destino
- [ ] Excluir a última coluna do quadro é rejeitado
- [ ] Excluir o último quadro do projeto é rejeitado
- [ ] Reordenação exige a lista completa e resulta em posições sem lacunas
- [ ] Limite WIP bloqueia movimentação manual e permite movimentação automática com alerta

---

**Próxima spec**: `spec-tasks.md`
