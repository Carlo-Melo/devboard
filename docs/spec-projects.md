# spec-projects.md — Módulo de Projetos

> **Pré-requisito**: `claude.md` + `spec-authentication.md`

**Escopo de entrega**: Produto v1.0.0 — Prioridade 2
**Dependências**: Autenticação (usuário autenticado é sempre o dono do projeto criado)

---

## 1. OBJETIVO

O projeto é o espaço de trabalho de uma equipe no devBoard. Ele reúne membros, permissões e um ou mais quadros. Tarefas pertencem a um quadro e, por consequência, a um projeto.

O projeto **não** possui repositório GitHub próprio. A vinculação com GitHub pertence ao quadro: cada quadro pode estar sem repositório ou vinculado a um único repositório, conforme `spec-board-kanban.md` e `spec-github-integration.md`.

Assim, um único projeto pode organizar áreas independentes — por exemplo, frontend, backend, infraestrutura e planejamento — em boards diferentes. Boards sem repositório continuam plenamente funcionais como Kanban.

---

## 2. ESCOPO

### Dentro do escopo
- Criar, listar, visualizar, editar e arquivar projetos
- Criar automaticamente o board padrão do projeto
- Exibir os boards do projeto e seus respectivos estados de vínculo com GitHub

### Fora do escopo da versão 1.0.0
- Templates de projeto
- Duplicação de projetos
- Transferência de propriedade
- Espaços de trabalho com organizações ou equipes acima do projeto

> Listagem de repositórios, vínculo/desvínculo, sincronização e automações GitHub são responsabilidades do board, não do projeto.

---

## 3. MODELO DE DADOS

### 3.1 Projeto

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| nome | texto | obrigatório, 3–100 caracteres |
| descrição | texto longo | opcional |
| dono | referência a usuário | obrigatório, definido na criação |
| arquivado | booleano | default falso |
| criado em / atualizado em | timestamp | automático |

**Regras**
- Todo projeto possui ao menos um board; o último board não pode ser excluído.
- Um projeto pode possuir vários boards, com ou sem repositório GitHub.
- O projeto não armazena dados de repositório, branches ou sincronização. Esses dados pertencem exclusivamente ao board vinculado.
- Arquivar é *soft delete*: o projeto some das listagens padrão, mas seus dados permanecem.
- Ao arquivar um projeto, os webhooks de todos os repositórios vinculados aos seus boards são removidos de forma assíncrona. Uma falha externa é registrada e não desfaz o arquivamento local.
- O dono não pode ser removido do projeto nem rebaixado de papel.

---

## 4. ENDPOINTS

Todos exigem autenticação. Base: `/api/projects`.

### 4.1 Criar projeto — `POST /`

**Entrada**: nome, descrição (opcional)

**Comportamento**
1. Cria o projeto com o usuário autenticado como dono.
2. Cria automaticamente o board padrão, inicialmente nomeado `Main Board`, com as cinco colunas definidas em `spec-board-kanban.md`.
3. O board padrão é criado sem repositório vinculado e pode ser renomeado posteriormente.

**Saída (201)**: projeto criado, já com o board padrão

**Erros**
| Situação | Status |
|---|---|
| Nome ausente ou fora do tamanho | 400 |

---

### 4.2 Listar projetos — `GET /`

**Entrada (query)**: `archived` (default falso), `page`, `size`

**Comportamento**: retorna projetos onde o usuário é dono **ou** membro, ordenados por data de atualização decrescente.

**Saída (200)**: lista paginada com dados resumidos — id, nome, descrição, dono, contagem de membros, contagem de boards e contagem de boards com repositório GitHub vinculado.

> A listagem não traz boards, tarefas nem dados de repositórios.

---

### 4.3 Detalhar projeto — `GET /{projectId}`

**Saída (200)**: dados completos do projeto, incluindo dono, membros com seus papéis e lista de boards sem tarefas. Cada resumo de board informa se possui repositório GitHub vinculado; os detalhes do vínculo são consultados no próprio board.

**Erros**
| Situação | Status |
|---|---|
| Usuário não é dono nem membro | 403 |
| Projeto inexistente | 404 |

---

### 4.4 Editar projeto — `PUT /{projectId}`

**Entrada**: nome, descrição

**Permissão**: dono ou membro com papel `ADMIN`

---

### 4.5 Arquivar projeto — `DELETE /{projectId}`

**Comportamento**: marca o projeto como arquivado e enfileira a remoção dos webhooks de todos os repositórios vinculados aos seus boards. Os boards, tarefas e vínculos são preservados para fins de histórico, mas deixam de aparecer nas listagens padrão e não recebem novas automações enquanto o projeto estiver arquivado.

**Permissão**: apenas o dono

**Saída (204)**

---

## 5. FLUXOS

### 5.1 Criação de projeto
1. Usuário abre o formulário de novo projeto.
2. Usuário informa nome e, opcionalmente, uma descrição.
3. Sistema cria o projeto, o board padrão inicialmente chamado `Main Board` e suas cinco colunas.
4. Usuário é levado ao board padrão, que pode ser renomeado ou complementado com outros boards.

### 5.2 Organização por área
1. Um administrador cria boards para as áreas necessárias, como `Frontend`, `Backend`, `Infraestrutura` e `Planejamento`.
2. Cada board pode permanecer sem integração ou ser vinculado ao seu próprio repositório GitHub.
3. As tarefas de cada área permanecem no seu board; eventos de um repositório só podem automatizar tarefas do board ao qual ele está vinculado.

### 5.3 Arquivamento
Ao arquivar um projeto, seus boards e tarefas são preservados, os webhooks vinculados aos boards são removidos em segundo plano e nenhuma automação de GitHub é aplicada enquanto o projeto estiver arquivado.

---

## 6. PERMISSÕES

| Ação | Dono | Admin | Developer | Viewer |
|---|:---:|:---:|:---:|:---:|
| Criar projeto | qualquer usuário autenticado ||||
| Visualizar projeto | ✅ | ✅ | ✅ | ✅ |
| Editar dados do projeto | ✅ | ✅ | ❌ | ❌ |
| Arquivar projeto | ✅ | ❌ | ❌ | ❌ |

---

## 7. CRITÉRIOS DE ACEITE

- [ ] Projeto criado nasce com um board padrão inicialmente chamado `Main Board` e cinco colunas padrão
- [ ] O board padrão pode ser renomeado sem deixar de ser o board padrão
- [ ] Um projeto pode possuir mais de um board
- [ ] Projeto sem repositório vinculado em nenhum board continua funcional como Kanban
- [ ] Listagem devolve projetos onde o usuário é dono e onde é membro
- [ ] Listagem não devolve projetos arquivados por padrão
- [ ] Usuário sem vínculo com o projeto recebe 403 ao tentar acessá-lo
- [ ] Arquivar projeto remove, de forma assíncrona, os webhooks dos repositórios vinculados aos seus boards

---

**Próxima spec**: `spec-board-kanban.md`
