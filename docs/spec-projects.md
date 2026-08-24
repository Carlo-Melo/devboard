# spec-projects.md — Módulo de Projetos

> **Pré-requisito**: `claude.md` + `spec-authentication.md`

**Status**: MVP — Prioridade 2
**Dependências**: Autenticação (usuário autenticado é sempre o dono do projeto criado)

---

## 1. OBJETIVO

O projeto é o contêiner de tudo no devBoard: quadros, tarefas e membros pertencem a um projeto. Um projeto pode — opcionalmente — estar vinculado a um repositório GitHub, o que habilita toda a sincronização automática descrita em `spec-github-integration.md`.

Um projeto sem repositório vinculado continua plenamente funcional como Kanban.

---

## 2. ESCOPO

### Dentro do escopo
- Criar, listar, visualizar, editar e arquivar projetos
- Listar repositórios GitHub disponíveis para o usuário
- Vincular e desvincular repositório GitHub
- Escolher quais branches são monitoradas
- Disparar sincronização manual com o GitHub
- Importar issues existentes do GitHub como tarefas

### Fora do escopo
- Templates de projeto
- Duplicação de projetos
- Transferência de propriedade
- Múltiplos repositórios por projeto

---

## 3. MODELO DE DADOS

### 3.1 Projeto

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| nome | texto | obrigatório, 3–100 caracteres |
| descrição | texto longo | opcional |
| dono | referência a usuário | obrigatório, definido na criação |
| github repo id | numérico | opcional; identificador do repositório no GitHub |
| github repo owner | texto | opcional; dono do repositório no GitHub |
| github repo name | texto | opcional |
| github repo url | URL | opcional |
| branches monitoradas | lista de texto | vazia quando não há repositório vinculado |
| branch base padrão | texto | usada ao criar branches de tarefa; default `main` |
| arquivado | booleano | default falso |
| última sincronização | timestamp | nulo até a primeira sincronização |
| criado em / atualizado em | timestamp | automático |

**Regras**
- Um repositório GitHub só pode estar vinculado a um projeto por vez, por usuário.
- Ao vincular um repositório, o sistema valida que o usuário autenticado tem acesso de escrita a ele.
- Arquivar é *soft delete*: o projeto some das listagens padrão mas os dados permanecem.
- O dono não pode ser removido do projeto nem rebaixado de papel.

---

## 4. ENDPOINTS

Todos exigem autenticação. Base: `/api/projects`.

### 4.1 Criar projeto — `POST /`

**Entrada**: nome, descrição (opcional), github repo id (opcional), branches monitoradas (opcional), branch base (opcional)

**Comportamento**
1. Cria o projeto com o usuário autenticado como dono.
2. Cria automaticamente um quadro padrão com as cinco colunas definidas em `spec-board-kanban.md`.
3. Se um repositório foi informado: valida o acesso, persiste o vínculo, registra o webhook no GitHub e agenda a importação de issues em segundo plano.

**Saída (201)**: projeto criado, já com o quadro padrão

**Erros**
| Situação | Status |
|---|---|
| Nome ausente ou fora do tamanho | 400 |
| Repositório informado não existe | 400 |
| Usuário sem acesso ao repositório | 403 |
| GitHub não conectado, mas repositório informado | 403 |

---

### 4.2 Listar projetos — `GET /`

**Entrada (query)**: `archived` (default falso), `page`, `size`

**Comportamento**: retorna projetos onde o usuário é dono **ou** membro, ordenados por data de atualização decrescente.

**Saída (200)**: lista paginada com dados resumidos — id, nome, descrição, dono, contagem de membros, indicador de vínculo GitHub, última sincronização

> A listagem não traz quadros nem tarefas.

---

### 4.3 Detalhar projeto — `GET /{projectId}`

**Saída (200)**: dados completos do projeto, incluindo dono, membros com seus papéis, dados do repositório vinculado, branches monitoradas e lista de quadros (sem as tarefas).

**Erros**
| Situação | Status |
|---|---|
| Usuário não é dono nem membro | 403 |
| Projeto inexistente | 404 |

---

### 4.4 Editar projeto — `PUT /{projectId}`

**Entrada**: nome, descrição, branches monitoradas, branch base

**Permissão**: dono ou membro com papel `ADMIN`

**Observação**: a troca de repositório vinculado não acontece aqui — usa-se os endpoints de vínculo.

---

### 4.5 Arquivar projeto — `DELETE /{projectId}`

**Comportamento**: marca o projeto como arquivado; remove o webhook registrado no GitHub, se houver.

**Permissão**: apenas o dono

**Saída (204)**

---

### 4.6 Listar repositórios GitHub — `GET /github-repos`

**Entrada (query)**: `search` (opcional, filtra por nome)

**Comportamento**: consulta a API do GitHub usando o token do usuário autenticado e retorna os repositórios em que ele tem permissão de escrita, indicando quais já estão vinculados a algum projeto seu.

**Saída (200)**: lista com id, nome completo, descrição, URL, branch padrão e indicador de já vinculado

**Erros**
| Situação | Status |
|---|---|
| Usuário sem GitHub conectado | 403 |
| Token GitHub expirado ou revogado | 401 |
| GitHub indisponível | 503 |

---

### 4.7 Vincular repositório — `POST /{projectId}/link-github`

**Entrada**: github repo id, branches monitoradas (opcional), branch base (opcional)

**Comportamento**
1. Valida acesso do usuário ao repositório.
2. Persiste os dados do repositório no projeto.
3. Registra o webhook do devBoard no repositório.
4. Agenda a importação de issues.

**Permissão**: dono ou `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| Projeto já possui repositório vinculado | 409 |
| Repositório já vinculado a outro projeto do usuário | 409 |
| Sem acesso ao repositório | 403 |

---

### 4.8 Desvincular repositório — `DELETE /{projectId}/link-github`

**Comportamento**: remove o webhook no GitHub e limpa os dados de vínculo. As tarefas importadas permanecem no quadro, mas perdem a referência ao GitHub e param de sincronizar.

**Permissão**: dono ou `ADMIN`

---

### 4.9 Sincronizar manualmente — `POST /{projectId}/sync-github`

**Comportamento**: reexecuta a importação/atualização de issues e a leitura de branches abertas. A operação é assíncrona; a resposta confirma o enfileiramento.

**Limite de uso**: uma sincronização manual a cada 5 minutos por projeto. Excedido → 429.

**Erros**
| Situação | Status |
|---|---|
| Projeto sem repositório vinculado | 400 |
| GitHub indisponível | 503 |
| Limite de chamadas do GitHub atingido | 429 |

---

## 5. FLUXOS

### 5.1 Criação de projeto com repositório
1. Usuário abre o formulário de novo projeto.
2. Sistema oferece a lista de repositórios GitHub do usuário (se conectado).
3. Usuário informa nome, descrição e seleciona o repositório e as branches a monitorar.
4. Sistema cria o projeto, o quadro padrão e as cinco colunas.
5. Sistema registra o webhook no repositório.
6. Sistema agenda a importação de issues.
7. Usuário é levado ao quadro; as issues aparecem gradualmente conforme a importação conclui.

### 5.2 Importação de issues (assíncrona)
Para cada issue aberta ou fechada do repositório:
- Se já existe tarefa com aquele issue id: atualiza título, descrição, responsável e labels, e ajusta a coluna conforme o estado (aberta → `To-Do`, fechada → `Done`), respeitando movimentações manuais posteriores.
- Se não existe: cria tarefa no `Backlog`, do tipo `DEV`, vinculada ao issue.

Ao final, registra o momento da sincronização e uma atividade de projeto do tipo sincronização.

Falhas parciais não abortam o processo: cada issue é tratada isoladamente e os erros são registrados em log.

### 5.3 Vínculo em projeto já existente
Mesma sequência da criação, exceto que o quadro e as colunas já existem. Issues importadas entram no `Backlog` sem alterar as tarefas já criadas manualmente.

---

## 6. PERMISSÕES

| Ação | Dono | Admin | Developer | Viewer |
|---|:---:|:---:|:---:|:---:|
| Criar projeto | qualquer usuário autenticado |||| 
| Visualizar projeto | ✅ | ✅ | ✅ | ✅ |
| Editar dados do projeto | ✅ | ✅ | ❌ | ❌ |
| Vincular / desvincular GitHub | ✅ | ✅ | ❌ | ❌ |
| Sincronizar manualmente | ✅ | ✅ | ✅ | ❌ |
| Arquivar projeto | ✅ | ❌ | ❌ | ❌ |

---

## 7. CRITÉRIOS DE ACEITE

- [ ] Projeto criado sem GitHub já nasce com quadro e cinco colunas padrão
- [ ] Projeto criado com GitHub registra webhook e importa issues
- [ ] Listagem devolve projetos onde o usuário é dono e onde é membro
- [ ] Listagem não devolve projetos arquivados por padrão
- [ ] Usuário sem vínculo com o projeto recebe 403 ao tentar acessá-lo
- [ ] Vincular repositório sem acesso de escrita retorna 403
- [ ] Vincular repositório já usado em outro projeto do usuário retorna 409
- [ ] Desvincular remove o webhook e mantém as tarefas no quadro
- [ ] Sincronização manual respeita o intervalo de 5 minutos
- [ ] Importação repetida não duplica tarefas já vinculadas a issues
- [ ] Arquivar projeto remove o webhook do GitHub

---

**Próxima spec**: `spec-board-kanban.md`
