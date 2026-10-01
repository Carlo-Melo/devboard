# spec-board-kanban.md — Módulo de Quadro Kanban

> **Pré-requisito**: `claude.md` + `spec-projects.md`

**Escopo de entrega**: Produto v1.0.0 — Prioridade 3
**Dependências**: Projetos

---

## 1. OBJETIVO

O board é a superfície visual de uma área de trabalho dentro de um projeto. Ele organiza tarefas em colunas ordenadas que representam etapas de um fluxo de trabalho. Um projeto pode ter vários boards, permitindo separar, por exemplo, frontend, backend, infraestrutura e planejamento.

Cada board pode opcionalmente ser vinculado a um único repositório GitHub. Essa associação delimita as automações: commits, pull requests e issues de um repositório atualizam somente as tarefas do board ao qual ele pertence. Boards sem repositório continuam plenamente funcionais como Kanban.

As colunas também são o alvo das automações do GitHub: mover uma tarefa entre colunas é o que traduz “commit feito” ou “PR mergeado” em estado visível no board.

---

## 2. ESCOPO

### Dentro do escopo
- Criar, listar, editar e excluir boards de um projeto
- Criar, editar, reordenar e excluir colunas
- Conjunto padrão de colunas na criação do projeto
- Visualização do board completo com tarefas agrupadas por coluna
- Filtros de visualização do board
- Papéis semânticos de coluna (usados pelas automações)
- Limite de trabalho em progresso (WIP) por coluna
- Listar repositórios GitHub disponíveis ao usuário autenticado
- Vincular, desvincular e sincronizar manualmente o repositório de um board

### Fora do escopo da versão 1.0.0
- Visualizações alternativas (lista, calendário, timeline)
- Swimlanes
- Templates de board compartilháveis entre projetos
- Vincular mais de um repositório ao mesmo board
- Vincular o mesmo repositório a mais de um board

---

## 3. MODELO DE DADOS

### 3.1 Board

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| projeto | referência | obrigatório |
| nome | texto | obrigatório, 3–100 caracteres; único dentro do projeto |
| descrição | texto | opcional |
| é padrão | booleano | exatamente um board padrão por projeto |
| github repo id | numérico | opcional; identificador do repositório no GitHub |
| github repo owner | texto | opcional; dono ou organização do repositório |
| github repo name | texto | opcional |
| github repo url | URL | opcional |
| branches monitoradas | lista de texto | vazia quando não há repositório vinculado |
| branch base padrão | texto | usada ao criar branches de tarefas; default `main` |
| última sincronização | timestamp | nulo até a primeira sincronização |
| criado em / atualizado em | timestamp | automático |

**Regras**
- Todo projeto tem no mínimo um board; o último não pode ser excluído.
- O sistema cria um único board padrão ao criar o projeto. Ele inicia com o nome `Main Board`, mas esse é apenas o nome inicial: pode ser renomeado sem perder o indicador de board padrão.
- Marcar um board como padrão desmarca o anterior; a existência de um board padrão não depende de seu nome.
- Cada board pode ter no máximo um repositório GitHub vinculado.
- Um repositório GitHub pode estar vinculado a no máximo um board em todo o devBoard. A unicidade usa o identificador imutável do repositório no GitHub, não seu nome ou URL.
- Ao vincular um repositório, o sistema valida que o usuário autenticado possui acesso de escrita a ele.
- Dados de repositório, branches e sincronização não pertencem ao projeto nem podem ser compartilhados automaticamente com outro board.

### 3.2 Coluna

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| board | referência | obrigatório |
| nome | texto | obrigatório, 1–50 caracteres, único dentro do board |
| cor | texto | código hexadecimal; default definido pelo sistema |
| posição | numérico | sequencial a partir de 0, sem lacunas |
| papel semântico | enum | `BACKLOG` \| `TODO` \| `IN_PROGRESS` \| `IN_REVIEW` \| `DONE` \| `NONE` |
| limite WIP | numérico | opcional; nulo significa sem limite |
| criado em / atualizado em | timestamp | automático |

**Regras sobre o papel semântico**
- É o que permite às automações do GitHub saberem para onde mover uma tarefa, independentemente do nome que o usuário deu à coluna.
- Cada papel diferente de `NONE` pode ser atribuído a no máximo uma coluna por board.
- Colunas criadas manualmente nascem com papel `NONE`, salvo escolha explícita.
- Se um papel necessário a uma automação não existir no board, a automação daquele evento é ignorada silenciosamente e registrada em log.

**Regras sobre o limite WIP**
- Ao mover uma tarefa para uma coluna que já atingiu o limite, o sistema rejeita a operação com erro específico.
- O limite não se aplica a movimentações automáticas originadas de webhooks do GitHub: nesses casos a tarefa é movida e uma atividade de alerta é registrada no board.

### 3.3 Conjunto padrão de colunas

Criado automaticamente junto com o board padrão do projeto. Outros boards podem optar pelo mesmo conjunto:

| Posição | Nome | Papel | Uso |
|---|---|---|---|
| 0 | Backlog | `BACKLOG` | tarefas ainda não priorizadas |
| 1 | To-Do | `TODO` | prontas para começar |
| 2 | In Progress | `IN_PROGRESS` | em execução |
| 3 | In Review | `IN_REVIEW` | aguardando revisão ou QA |
| 4 | Done | `DONE` | concluídas |

---

## 4. ENDPOINTS

Todos exigem autenticação e vínculo com o projeto do board, salvo indicação contrária.

### 4.1 Criar board — `POST /api/projects/{projectId}/boards`

**Entrada**: nome, descrição (opcional), colunas iniciais (opcional — se omitido, aplica o conjunto padrão)

**Permissão**: dono ou `ADMIN`

**Saída (201)**: board com suas colunas

---

### 4.2 Listar boards — `GET /api/projects/{projectId}/boards`

**Saída (200)**: boards do projeto com suas colunas, contagem de tarefas por coluna e resumo do vínculo GitHub, **sem** as tarefas em si.

---

### 4.3 Visualizar board — `GET /api/boards/{boardId}`

**Entrada (query, todos opcionais)**: `assigneeId`, `label`, `priority`, `type`, `search`

**Comportamento**: retorna o board com todas as colunas na ordem correta e, dentro de cada uma, as tarefas ordenadas por posição. Os filtros são aplicados às tarefas, nunca às colunas — uma coluna sem tarefas correspondentes ao filtro aparece vazia, não some.

**Saída (200)**: board, resumo de seu repositório vinculado quando houver, colunas e tarefas em formato resumido (dados suficientes para renderizar o card).

> Este é o endpoint mais acessado do sistema. Deve evitar consultas em cascata por tarefa.

---

### 4.4 Editar board — `PUT /api/boards/{boardId}`

**Entrada**: nome, descrição, indicador de padrão

**Comportamento**: editar o nome do board padrão é permitido. O nome `Main Board` não é reservado nem define tecnicamente o board padrão.

**Permissão**: dono ou `ADMIN`

---

### 4.5 Excluir board — `DELETE /api/boards/{boardId}`

**Comportamento**: se houver repositório vinculado, enfileira a remoção do webhook antes de apagar o vínculo local. Falha ao remover o webhook no GitHub é registrada, mas não desfaz a exclusão local.

**Permissão**: dono ou `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| É o único board do projeto | 409 |
| Board possui tarefas | 409 — exige mover ou arquivar as tarefas antes |

---

### 4.6 Criar coluna — `POST /api/boards/{boardId}/columns`

**Entrada**: nome, cor (opcional), posição (opcional — default: ao final), papel semântico (opcional), limite WIP (opcional)

**Comportamento**: ao inserir em uma posição intermediária, as colunas seguintes são deslocadas.

**Erros**
| Situação | Status |
|---|---|
| Nome duplicado no board | 409 |
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
- Com tarefas e `moveTasksTo` informado: move as tarefas para uma coluna do mesmo board e exclui.
- Com tarefas e sem destino informado: rejeita.

**Erros**
| Situação | Status |
|---|---|
| Coluna com tarefas e sem destino informado | 409 |
| Destino pertence a outro board | 400 |
| É a última coluna do board | 409 |

---

### 4.9 Reordenar colunas — `PUT /api/boards/{boardId}/columns/reorder`

**Entrada**: lista completa de ids de coluna na nova ordem

**Validações**: a lista deve conter exatamente todas as colunas do board, sem repetições nem omissões.

**Saída (200)**: colunas na nova ordem

---

### 4.10 Listar repositórios GitHub — `GET /api/github-repos`

**Entrada (query)**: `search` (opcional, filtra por nome)

**Comportamento**: consulta a API do GitHub usando o token do usuário autenticado e retorna repositórios nos quais ele tem permissão de escrita, informando se cada um já está vinculado a algum board.

**Saída (200)**: lista com id, nome completo, descrição, URL, branch padrão e indicador de vínculo.

**Erros**
| Situação | Status |
|---|---|
| Usuário sem GitHub conectado | 403 |
| Token GitHub expirado ou revogado | 401 |
| GitHub indisponível | 503 |

---

### 4.11 Vincular repositório — `POST /api/boards/{boardId}/link-github`

**Entrada**: github repo id, branches monitoradas (opcional), branch base (opcional)

**Comportamento**
1. Valida que o usuário possui acesso de escrita ao repositório.
2. Garante que o board ainda não possui repositório e que o repositório não está vinculado a outro board.
3. Persiste os dados do repositório no board.
4. Registra o webhook do devBoard no repositório.
5. Agenda a importação de issues para esse mesmo board.

**Permissão**: dono ou `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| Board já possui repositório vinculado | 409 |
| Repositório já vinculado a outro board | 409 |
| Sem acesso ao repositório | 403 |

---

### 4.12 Desvincular repositório — `DELETE /api/boards/{boardId}/link-github`

**Comportamento**: remove o webhook no GitHub e limpa os dados de vínculo do board. As tarefas importadas permanecem no board, mas perdem a referência ao GitHub e param de sincronizar.

**Permissão**: dono ou `ADMIN`

---

### 4.13 Sincronizar manualmente — `POST /api/boards/{boardId}/sync-github`

**Comportamento**: reexecuta a importação/atualização de issues e a leitura de branches abertas para o repositório daquele board. A operação é assíncrona; a resposta confirma o enfileiramento.

**Limite de uso**: uma sincronização manual a cada 5 minutos por board. Excedido → 429.

**Erros**
| Situação | Status |
|---|---|
| Board sem repositório vinculado | 400 |
| GitHub indisponível | 503 |
| Limite de chamadas do GitHub atingido | 429 |

---

## 5. FLUXOS

### 5.1 Nascimento e renomeação do board padrão
Quando um projeto é criado, o sistema gera um board padrão chamado inicialmente `Main Board` e aplica o conjunto padrão de cinco colunas com seus papéis semânticos. Nenhuma ação do usuário é necessária.

O nome pode ser alterado por dono ou `ADMIN`. Mesmo renomeado, ele continua sendo o board padrão até que outro board seja explicitamente marcado como padrão.

### 5.2 Organização em múltiplos boards
1. Usuário acessa o projeto e visualiza seus boards.
2. Um administrador cria boards adicionais para as áreas necessárias.
3. Cada board define seu próprio fluxo de colunas e pode ou não ter um repositório vinculado.
4. Tarefas e automações de um board não atravessam para outro board, ainda que ambos pertençam ao mesmo projeto.

### 5.3 Vínculo de repositório em board existente
1. Usuário abre as configurações GitHub de um board.
2. Sistema apresenta os repositórios em que o usuário possui acesso de escrita.
3. Usuário escolhe um repositório, as branches monitoradas e a branch base.
4. Sistema valida a unicidade do repositório, registra o webhook e agenda a importação de issues para aquele board.
5. Eventos desse repositório passam a afetar exclusivamente as tarefas desse board.

### 5.4 Abertura do board
1. Usuário seleciona um board.
2. Sistema devolve colunas ordenadas e tarefas agrupadas.
3. Cliente renderiza as colunas lado a lado, com contador e, quando definido, o limite WIP.
4. Filtros aplicados pelo usuário reconsultam o mesmo endpoint.

### 5.5 Coluna que atingiu o limite WIP
1. Usuário tenta mover uma tarefa para a coluna cheia.
2. Sistema rejeita com erro específico e mensagem indicando o limite.
3. Se a mesma movimentação vier de um webhook do GitHub do repositório vinculado ao board, ela é executada assim mesmo e uma atividade de alerta é registrada.

---

## 6. PERMISSÕES

| Ação | Dono | Admin | Developer | Viewer |
|---|:---:|:---:|:---:|:---:|
| Visualizar board | ✅ | ✅ | ✅ | ✅ |
| Criar / editar / excluir board | ✅ | ✅ | ❌ | ❌ |
| Criar / editar / excluir coluna | ✅ | ✅ | ❌ | ❌ |
| Reordenar colunas | ✅ | ✅ | ✅ | ❌ |
| Vincular / desvincular repositório | ✅ | ✅ | ❌ | ❌ |
| Sincronizar manualmente | ✅ | ✅ | ✅ | ❌ |

---

## 7. CRITÉRIOS DE ACEITE

- [ ] Criar projeto gera um board padrão inicialmente chamado `Main Board`, com cinco colunas e seus papéis
- [ ] O board padrão pode ser renomeado sem perder seu indicador de padrão
- [ ] Um projeto pode criar e listar vários boards
- [ ] Board sem repositório vinculado funciona normalmente como Kanban
- [ ] Não é possível vincular mais de um repositório ao mesmo board
- [ ] Não é possível vincular um repositório já associado a outro board
- [ ] Vínculo de repositório sem acesso de escrita retorna 403
- [ ] Vincular repositório registra webhook e importa issues somente para o board vinculado
- [ ] Desvincular remove o webhook e mantém as tarefas no board
- [ ] Sincronização manual respeita o intervalo de 5 minutos por board
- [ ] Visualizar board devolve colunas ordenadas com tarefas agrupadas
- [ ] Filtro por responsável, label, prioridade ou tipo afeta apenas as tarefas
- [ ] Coluna sem tarefas após filtro continua visível e vazia
- [ ] Não é possível criar duas colunas com o mesmo nome no board
- [ ] Não é possível atribuir o mesmo papel semântico a duas colunas
- [ ] Excluir coluna com tarefas exige coluna de destino no mesmo board
- [ ] Excluir a última coluna do board é rejeitado
- [ ] Excluir o último board do projeto é rejeitado
- [ ] Reordenação exige a lista completa e resulta em posições sem lacunas
- [ ] Limite WIP bloqueia movimentação manual e permite movimentação automática com alerta

---

**Próxima spec**: `spec-tasks.md`
