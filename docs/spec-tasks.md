# spec-tasks.md — Módulo de Tarefas

> **Pré-requisito**: `claude.md` + `spec-board-kanban.md`

**Status**: MVP — Prioridade 4
**Dependências**: Quadro Kanban, Projetos, Autenticação

---

## 1. OBJETIVO

A tarefa é a unidade de trabalho do devBoard. Diferente de ferramentas puramente voltadas a código, a tarefa aqui é **genérica por design**: comporta tanto "implementar endpoint de login" quanto "validar manualmente as telas de cadastro" ou "escrever a documentação de deploy".

O tipo da tarefa é o que carrega essa distinção, e é ele que determina se recursos ligados ao GitHub — criar branch, vincular PR — fazem sentido para aquele item.

---

## 2. ESCOPO

### Dentro do escopo
- Criar, visualizar, editar e arquivar tarefas
- Tipos genéricos de tarefa (desenvolvimento, QA, design, documentação, operacional, outro)
- Mover tarefas entre colunas e reordenar dentro da coluna
- Responsável principal e co-responsáveis
- Prioridade, labels, prazo e estimativa
- Comentários com menções
- Histórico de atividades da tarefa
- Vínculo com issue e PR do GitHub
- Criação de branch a partir da tarefa

### Fora do escopo
- Subtarefas e dependências entre tarefas
- Anexos de arquivo (fase futura)
- Recorrência
- Controle de tempo trabalhado

---

## 3. MODELO DE DADOS

### 3.1 Tarefa

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema; é o número exibido como `#id` |
| coluna | referência | obrigatório; determina o projeto e o quadro |
| título | texto | obrigatório, 3–255 caracteres |
| descrição | texto longo | opcional; aceita Markdown |
| posição | numérico | ordem dentro da coluna, sequencial a partir de 0 |
| tipo | enum | ver seção 3.2; default `OTHER` |
| prioridade | enum | `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT`; default `MEDIUM` |
| responsável | referência a usuário | opcional; deve ser membro do projeto |
| co-responsáveis | lista de referências | opcional; todos membros do projeto |
| criador | referência a usuário | obrigatório, imutável |
| labels | lista | referências a labels do projeto |
| prazo | data | opcional |
| estimativa | numérico | opcional; pontos de história |
| github issue id / url | numérico / URL | opcional |
| github pr id / url / estado | numérico / URL / enum | opcional; estado: `OPEN` \| `MERGED` \| `CLOSED` |
| branch | texto | opcional; nome da branch criada ou vinculada |
| arquivada | booleano | default falso |
| concluída em | timestamp | preenchido ao entrar em coluna de papel `DONE` |
| criada em / atualizada em | timestamp | automático |

### 3.2 Tipos de tarefa

| Tipo | Uso | Recursos GitHub |
|---|---|---|
| `DEV` | feature, correção de bug, refatoração, débito técnico | branch, issue e PR |
| `QA` | teste manual, validação de telas, teste em produção | issue |
| `DESIGN` | UI, UX, protótipos | issue |
| `DOCUMENTATION` | documentação técnica, guias | branch, issue e PR |
| `OPERATIONAL` | deploy, monitoramento, infraestrutura | issue |
| `OTHER` | qualquer outra atividade | issue |

**Regra**: a ação de criar branch só é oferecida para tipos que a suportam. Para os demais, a operação é rejeitada com erro explicativo.

### 3.3 Comentário

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| tarefa | referência | obrigatório |
| autor | referência a usuário | obrigatório, imutável |
| conteúdo | texto longo | obrigatório, não vazio; aceita Markdown e menções |
| editado | booleano | verdadeiro após a primeira edição |
| criado em / atualizado em | timestamp | automático |

**Menções**: o padrão `@username` referencia um membro do projeto. Menções a não membros ficam como texto simples.

### 3.4 Atividade da tarefa

Registro imutável e apenas de leitura. Gerado pelo sistema, nunca pelo usuário diretamente.

| Campo | Tipo |
|---|---|
| tarefa | referência |
| autor | referência a usuário; nulo quando originado do GitHub sem correspondência |
| tipo | enum (ver abaixo) |
| descrição | texto gerado pelo sistema |
| metadados | dados adicionais do evento (ex.: hash e mensagem do commit, colunas de origem e destino) |
| criado em | timestamp |

**Tipos de atividade**: criação, edição, movimentação, atribuição, mudança de prioridade, mudança de prazo, adição/remoção de label, comentário, vínculo com issue, criação de branch, commit recebido, PR aberto, PR mergeado, PR fechado sem merge, arquivamento.

---

## 4. ENDPOINTS

Todos exigem autenticação e vínculo com o projeto da tarefa.

### 4.1 Criar tarefa — `POST /api/tasks`

**Entrada**: id da coluna, título, descrição, tipo, prioridade, responsável, co-responsáveis, labels, prazo, estimativa, github issue id — apenas coluna e título são obrigatórios

**Comportamento**
1. Valida que responsável e co-responsáveis são membros do projeto.
2. Insere a tarefa ao final da coluna.
3. Registra atividade de criação.
4. Notifica o responsável, se houver e se for diferente do criador.

**Saída (201)**: tarefa criada

**Erros**
| Situação | Status |
|---|---|
| Título ausente ou fora do tamanho | 400 |
| Responsável não é membro do projeto | 400 |
| Label não pertence ao projeto | 400 |
| Coluna inexistente | 404 |
| Sem permissão para criar | 403 |

---

### 4.2 Detalhar tarefa — `GET /api/tasks/{taskId}`

**Saída (200)**: todos os dados da tarefa, comentários em ordem cronológica, atividades em ordem cronológica decrescente e dados do vínculo GitHub (issue, PR, branch e commits recebidos).

---

### 4.3 Editar tarefa — `PUT /api/tasks/{taskId}`

**Entrada**: qualquer campo editável — título, descrição, tipo, prioridade, responsável, co-responsáveis, labels, prazo, estimativa

**Comportamento**: cada campo alterado gera sua própria atividade, permitindo que o histórico mostre exatamente o que mudou. Mudança de responsável notifica o novo responsável.

**Campos não editáveis**: criador, coluna (usar movimentação), posição, identificadores do GitHub (gerenciados pelos endpoints próprios).

---

### 4.4 Mover tarefa — `PUT /api/tasks/{taskId}/move`

**Entrada**: id da coluna de destino, posição de destino

**Comportamento**
1. Valida o limite WIP da coluna de destino.
2. Atualiza coluna e posição, reordenando as tarefas afetadas na origem e no destino.
3. Se o destino tem papel `DONE`, registra a conclusão; se sai de `DONE`, limpa a conclusão.
4. Registra atividade de movimentação com origem e destino.
5. Se a tarefa tem issue vinculada, sincroniza o estado no GitHub conforme `spec-github-integration.md`.

**Erros**
| Situação | Status |
|---|---|
| Posição inválida | 400 |
| Coluna de destino em outro projeto | 400 |
| Limite WIP atingido | 409 |
| Coluna inexistente | 404 |

---

### 4.5 Arquivar tarefa — `DELETE /api/tasks/{taskId}`

**Comportamento**: soft delete. A tarefa desaparece do quadro mas o histórico permanece. Não fecha a issue vinculada no GitHub.

**Permissão**: criador, responsável, dono ou `ADMIN`

---

### 4.6 Comentários

- `POST /api/tasks/{taskId}/comments` — cria comentário; processa menções e notifica mencionados, criador e responsável
- `GET /api/tasks/{taskId}/comments` — lista paginada em ordem cronológica
- `PUT /api/comments/{commentId}` — edita; apenas o autor; marca como editado
- `DELETE /api/comments/{commentId}` — remove; autor, dono ou `ADMIN`

---

### 4.7 Atividades — `GET /api/tasks/{taskId}/activities`

**Entrada (query)**: `type` (opcional), `page`, `size`

**Saída (200)**: histórico paginado, mais recente primeiro. Somente leitura.

---

### 4.8 Criar branch — `POST /api/tasks/{taskId}/branch`

**Entrada**: branch base (opcional — default: branch base do projeto), nome customizado (opcional)

**Comportamento**
1. Valida que o projeto tem repositório vinculado e que o tipo da tarefa suporta branch.
2. Gera o nome no padrão `feature/task-{id}-{titulo-normalizado}`, com o título convertido para minúsculas, sem acentos, com hífens no lugar de espaços e truncado em 50 caracteres.
3. Cria a branch no GitHub a partir da base indicada.
4. Persiste o nome na tarefa e registra atividade.

**Erros**
| Situação | Status |
|---|---|
| Projeto sem repositório vinculado | 400 |
| Tipo de tarefa não suporta branch | 400 |
| Tarefa já possui branch | 409 |
| Branch já existe no repositório | 409 |
| Token GitHub inválido | 401 |
| GitHub indisponível | 503 |

---

### 4.9 Vínculo com GitHub

- `POST /api/tasks/{taskId}/github-issue` — vincula uma issue existente; importa título, descrição e labels se a tarefa ainda não os tiver
- `DELETE /api/tasks/{taskId}/github-issue` — desfaz o vínculo; a issue permanece no GitHub
- `POST /api/tasks/{taskId}/github-issue/create` — cria uma issue nova no GitHub a partir da tarefa e já a vincula

---

## 5. FLUXOS

### 5.1 Tarefa de desenvolvimento, do início ao fim
1. Membro cria a tarefa do tipo `DEV` no `Backlog`, define prioridade e responsável.
2. Responsável move a tarefa para `To-Do` e depois aciona "criar branch".
3. Sistema cria a branch no GitHub seguindo o padrão de nomenclatura e registra a atividade.
4. Desenvolvedor faz checkout e commita. Os webhooks movem a tarefa para `In Progress` automaticamente.
5. Abertura do PR move a tarefa para `In Review`.
6. Merge do PR move a tarefa para `Done` e registra a conclusão.

O detalhamento de cada automação está em `spec-github-integration.md`.

### 5.2 Tarefa não relacionada a código
1. Membro cria tarefa do tipo `QA` — por exemplo, "validar manualmente as telas de cadastro".
2. A opção de criar branch não é oferecida.
3. A tarefa percorre as mesmas colunas, movida manualmente.
4. O responsável registra o resultado da validação nos comentários.
5. Ao concluir, move para `Done` manualmente.

Este fluxo é deliberadamente idêntico ao anterior do ponto de vista do quadro — a diferença está apenas nos recursos do GitHub disponíveis.

### 5.3 Comentário com menção
1. Autor escreve o comentário incluindo `@username`.
2. Sistema identifica menções que correspondem a membros do projeto.
3. Comentário é salvo e atividade registrada.
4. Mencionados recebem notificação de menção; criador e responsável recebem notificação de comentário. Ninguém recebe notificação duplicada, e o autor não é notificado da própria ação.

---

## 6. PERMISSÕES

| Ação | Dono | Admin | Developer | Viewer |
|---|:---:|:---:|:---:|:---:|
| Visualizar tarefas | ✅ | ✅ | ✅ | ✅ |
| Criar tarefa | ✅ | ✅ | ✅ | ❌ |
| Editar qualquer tarefa | ✅ | ✅ | ❌ | ❌ |
| Editar tarefa própria ou atribuída | ✅ | ✅ | ✅ | ❌ |
| Mover tarefa | ✅ | ✅ | ✅ | ❌ |
| Arquivar tarefa | ✅ | ✅ | apenas próprias | ❌ |
| Atribuir responsável | ✅ | ✅ | ✅ | ❌ |
| Comentar | ✅ | ✅ | ✅ | ✅ |
| Editar / excluir comentário próprio | ✅ | ✅ | ✅ | ✅ |
| Excluir comentário de terceiros | ✅ | ✅ | ❌ | ❌ |
| Criar branch | ✅ | ✅ | ✅ | ❌ |

---

## 7. CRITÉRIOS DE ACEITE

- [ ] Tarefa é criada apenas com coluna e título
- [ ] Responsável fora do projeto é rejeitado
- [ ] Tarefa entra ao final da coluna escolhida
- [ ] Mover tarefa reordena corretamente origem e destino, sem lacunas de posição
- [ ] Entrar em coluna `DONE` registra a conclusão; sair dela limpa o registro
- [ ] Cada campo editado gera uma atividade própria e identificável
- [ ] Criar branch gera nome no padrão definido, sem acentos e truncado
- [ ] Criar branch em tarefa de tipo não suportado é rejeitado com mensagem clara
- [ ] Criar branch em projeto sem GitHub vinculado é rejeitado
- [ ] Segunda tentativa de criar branch na mesma tarefa é rejeitada
- [ ] Menção a membro gera notificação; menção a não membro fica como texto
- [ ] Autor de uma ação nunca é notificado da própria ação
- [ ] Viewer consegue comentar mas não consegue criar nem mover tarefas
- [ ] Developer não consegue arquivar tarefa de outro membro
- [ ] Arquivar tarefa preserva o histórico e não altera a issue no GitHub

---

**Próxima spec**: `spec-github-integration.md`
