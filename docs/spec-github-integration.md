# spec-github-integration.md — Integração com o GitHub

> **Pré-requisito**: `claude.md` + `spec-projects.md` + `spec-tasks.md`

**Status**: MVP — Prioridade 5
**Dependências**: Projetos (repositório vinculado), Tarefas, Quadro (papéis semânticos de coluna)

---

## 1. OBJETIVO

Este é o módulo que diferencia o devBoard de um Kanban genérico. A premissa é simples: **o desenvolvedor não deveria precisar atualizar o quadro manualmente**. Commitar, abrir PR e fazer merge já são sinais suficientes do que está acontecendo — o quadro deve refletir isso sozinho.

O módulo escuta eventos do GitHub via webhook, identifica a tarefa correspondente e atualiza o quadro.

---

## 2. ESCOPO

### Dentro do escopo
- Registro e remoção automática de webhooks nos repositórios vinculados
- Validação de autenticidade dos webhooks recebidos
- Processamento assíncrono dos eventos
- Identificação da tarefa a partir de branch, mensagem de commit ou issue
- Movimentação automática de tarefas conforme os eventos
- Registro de commits no histórico da tarefa
- Sincronização de estado entre tarefa e issue
- Configuração das automações por projeto

### Fora do escopo
- Sincronização bidirecional de comentários
- Integração com GitHub Actions e status de build
- Suporte a GitLab, Bitbucket ou outros
- Criação de releases

---

## 3. WEBHOOK

### 3.1 Registro

Acontece automaticamente quando um repositório é vinculado a um projeto. O webhook é configurado para entregar payload em JSON e assinar as requisições com um segredo compartilhado.

**Eventos assinados**: `push`, `pull_request`, `issues`

Ao desvincular o repositório ou arquivar o projeto, o webhook é removido.

### 3.2 Endpoint de recebimento — `POST /webhook/github`

Endpoint público (não exige JWT), protegido pela assinatura do GitHub.

**Comportamento**
1. Valida a assinatura da requisição contra o segredo configurado. Assinatura inválida → 401, sem processar nada.
2. Identifica o projeto pelo repositório informado no payload. Sem projeto correspondente → 202, evento descartado.
3. Enfileira o evento para processamento assíncrono.
4. Responde 202 imediatamente.

**Regra fundamental**: o endpoint nunca processa de forma síncrona e nunca devolve erro por falha de processamento. O GitHub interpreta respostas de erro como falha de entrega e passa a reenviar ou desativar o webhook.

### 3.3 Idempotência

Cada entrega do GitHub traz um identificador único. O sistema registra os identificadores já processados e descarta reentregas, evitando que um reenvio duplique commits no histórico ou mova a tarefa duas vezes.

---

## 4. IDENTIFICAÇÃO DA TAREFA

Todo evento precisa ser associado a uma tarefa. A busca segue esta ordem de precedência, parando no primeiro acerto:

| Ordem | Origem | Padrão reconhecido |
|---|---|---|
| 1 | Nome da branch | `feature/task-{id}-...` ou qualquer branch já registrada em uma tarefa |
| 2 | Mensagem do commit ou título/corpo do PR | referência explícita no formato `#{id}` |
| 3 | Issue vinculada | issue id presente no evento corresponde ao registrado na tarefa |

**Regras**
- A tarefa encontrada precisa pertencer ao projeto do repositório que emitiu o evento. Caso contrário, o evento é descartado.
- Nenhuma correspondência encontrada → evento descartado silenciosamente e registrado em log. Isso é esperado e comum (commits diretos na main, por exemplo).
- Mais de uma tarefa referenciada na mesma mensagem de commit → todas recebem o commit no histórico, mas apenas a primeira sofre movimentação automática.

---

## 5. EVENTOS E COMPORTAMENTOS

### 5.1 Evento `push`

Recebido a cada conjunto de commits enviado a uma branch.

**Processamento**
1. Ignora eventos de criação e remoção de branch sem commits.
2. Identifica a tarefa pela branch ou pelas mensagens dos commits.
3. Registra cada commit no histórico da tarefa: hash abreviado, mensagem, autor e link para o commit no GitHub.
4. Aplica a movimentação automática conforme a tabela abaixo.

**Regras de movimentação por palavra-chave na mensagem do commit**

| Padrão na mensagem | Movimento |
|---|---|
| contém `closes #{id}`, `fixes #{id}` ou `resolves #{id}` | move para a coluna de papel `DONE` |
| começa com `WIP` | move para `IN_PROGRESS` (ou mantém, se já estiver) |
| qualquer outro commit em branch de tarefa | move para `IN_PROGRESS`, **apenas se** a tarefa estiver em `BACKLOG` ou `TODO` |

**Princípio de não regressão**: a automação nunca move uma tarefa para uma coluna anterior no fluxo. Um commit novo em uma tarefa que já está em `IN_REVIEW` registra o commit mas não a puxa de volta para `IN_PROGRESS`.

### 5.2 Evento `pull_request`

| Ação recebida | Comportamento |
|---|---|
| aberto | vincula o PR à tarefa; move para `IN_REVIEW`; notifica os revisores solicitados |
| sincronizado (novos commits) | registra os commits; não move a tarefa |
| fechado com merge | move para a coluna `DONE`; registra a conclusão e quem fez o merge; notifica o responsável |
| fechado sem merge | registra a atividade e mantém a tarefa onde está; não retrocede automaticamente |
| reaberto | move de volta para `IN_REVIEW` |

### 5.3 Evento `issues`

| Ação recebida | Comportamento |
|---|---|
| aberta | cria tarefa no `BACKLOG` do quadro padrão, do tipo `DEV`, já vinculada à issue |
| fechada | move a tarefa vinculada para `DONE` |
| reaberta | move a tarefa de `DONE` de volta para `TODO` |
| labels alteradas | sincroniza as labels da tarefa com as da issue |
| responsável alterado | atualiza o responsável, se houver membro do projeto com o GitHub correspondente |
| editada | atualiza título e descrição, apenas se a tarefa não tiver sido editada manualmente depois do vínculo |

### 5.4 Sincronização no sentido inverso

Quando uma tarefa vinculada a uma issue é movida no devBoard:

| Movimento no quadro | Ação no GitHub |
|---|---|
| entra em coluna de papel `DONE` | fecha a issue |
| sai de coluna de papel `DONE` | reabre a issue |
| demais movimentações | nenhuma ação |

Essa sincronização é assíncrona e tolerante a falha: se o GitHub estiver indisponível, o movimento no quadro permanece válido e a falha é registrada em log.

**Prevenção de laço**: alterações originadas no devBoard são marcadas para que o webhook resultante seja reconhecido e descartado, evitando ciclo infinito entre os dois sistemas.

---

## 6. CONFIGURAÇÃO POR PROJETO

Cada automação pode ser desligada individualmente. Todas nascem habilitadas.

| Configuração | Efeito quando desligada |
|---|---|
| mover ao receber commit | commits são registrados, mas a tarefa não se move |
| mover ao abrir PR | PR é vinculado, mas a tarefa não se move |
| mover ao mergear PR | merge é registrado, mas a tarefa não se move |
| importar issues novas | issues criadas no GitHub não viram tarefas |
| fechar issue ao concluir tarefa | movimentação no quadro não afeta a issue |
| padrão de nome de branch | permite substituir o padrão default por outro formato |

**Endpoints**
- `GET /api/projects/{projectId}/github-settings` — consulta as configurações
- `PUT /api/projects/{projectId}/github-settings` — atualiza; permissão: dono ou `ADMIN`

---

## 7. FLUXO COMPLETO DE REFERÊNCIA

Este é o cenário que o módulo existe para atender.

1. **Tarefa criada** — um membro cria a tarefa `#42`, "Validar telas de login", tipo `DEV`, no `Backlog`.
2. **Branch criada** — o responsável aciona a criação da branch. O sistema cria `feature/task-42-validar-telas-de-login` a partir da branch base e registra a atividade.
3. **Primeiro commit** — o desenvolvedor commita e faz push. O GitHub dispara `push`. O sistema identifica a tarefa pela branch, registra o commit no histórico e move a tarefa de `Backlog` para `In Progress`.
4. **Commits seguintes** — cada push registra novos commits. A tarefa permanece em `In Progress`.
5. **PR aberto** — o desenvolvedor abre o PR. O sistema vincula o PR à tarefa, move para `In Review` e notifica o revisor.
6. **Revisão** — novos commits em resposta à revisão são registrados sem mover a tarefa.
7. **Merge** — o revisor aprova e mergeia. O sistema move a tarefa para `Done`, registra a conclusão e quem fez o merge, e notifica o responsável.
8. **Issue fechada** — se havia issue vinculada, ela é fechada automaticamente no GitHub.

Ao final, o histórico da tarefa mostra a jornada completa — criação, branch, commits, PR, merge — sem que ninguém tenha atualizado o quadro manualmente.

---

## 8. RESILIÊNCIA E OBSERVABILIDADE

| Item | Regra |
|---|---|
| Processamento | assíncrono, em fila; falha de um evento não afeta os demais |
| Nova tentativa | até 3 tentativas com intervalo crescente para falhas transitórias |
| Falha definitiva | registrada com o payload original para diagnóstico posterior |
| Limite de chamadas do GitHub | monitorado; ao se aproximar do limite, as operações são espaçadas |
| Token inválido ou revogado | projeto marcado como precisando de reautenticação; dono é notificado |
| Log obrigatório | todo evento recebido, a tarefa identificada (ou a ausência dela) e a ação tomada |

---

## 9. SEGURANÇA

- A assinatura de **todo** webhook é validada antes de qualquer processamento.
- O segredo do webhook não é versionado; vem de variável de ambiente.
- O payload é confrontado com o repositório vinculado ao projeto — um evento não pode alterar tarefas de outro projeto.
- Tokens do GitHub permanecem criptografados em repouso e nunca aparecem em respostas de API ou em log.
- O endpoint de webhook tem limite de requisições para mitigar abuso.

---

## 10. CRITÉRIOS DE ACEITE

- [ ] Vincular repositório registra o webhook automaticamente
- [ ] Desvincular repositório e arquivar projeto removem o webhook
- [ ] Webhook com assinatura inválida é rejeitado com 401 e não processa nada
- [ ] Webhook de repositório desconhecido responde 202 e descarta o evento
- [ ] Endpoint responde 202 antes de processar, sempre
- [ ] Reentrega do mesmo evento não duplica commits nem movimentações
- [ ] Commit em branch `feature/task-{id}-...` move a tarefa de `Backlog` para `In Progress`
- [ ] Commit com `closes #{id}` move a tarefa para `Done`
- [ ] Commit em tarefa já em `In Review` registra o commit sem retroceder a coluna
- [ ] Commit sem referência a tarefa é descartado sem erro
- [ ] Abrir PR vincula e move a tarefa para `In Review`
- [ ] Mergear PR move a tarefa para `Done` e registra a conclusão
- [ ] Fechar PR sem merge não retrocede a tarefa
- [ ] Issue nova no GitHub cria tarefa no `Backlog`
- [ ] Concluir tarefa vinculada fecha a issue no GitHub
- [ ] Fechamento de issue originado do devBoard não dispara novo processamento
- [ ] Automação desligada nas configurações não executa a movimentação correspondente
- [ ] Quadro sem coluna do papel necessário ignora a automação sem quebrar
- [ ] Falha do GitHub não impede a movimentação manual no quadro

---

**Próxima spec**: `spec-members.md`
