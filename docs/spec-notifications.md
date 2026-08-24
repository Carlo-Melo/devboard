# spec-notifications.md — Notificações e Histórico de Atividades

> **Pré-requisito**: `claude.md` + todas as specs anteriores

**Status**: MVP — Prioridade 8
**Dependências**: Tarefas, Membros, Integração GitHub

---

## 1. OBJETIVO

Dois registros com propósitos distintos, frequentemente confundidos:

- **Notificação** é dirigida a *uma pessoa* e exige atenção. Responde "o que eu preciso saber?".
- **Atividade** é o registro histórico do projeto ou da tarefa, visível a todos. Responde "o que aconteceu aqui?".

Um mesmo evento normalmente gera uma atividade e zero, uma ou várias notificações.

---

## 2. ESCOPO

### Dentro do escopo
- Registro de atividades por projeto e por tarefa
- Notificações no aplicativo
- Contagem de não lidas
- Marcar como lida individualmente e em lote
- Preferências de notificação por usuário

### Fora do escopo
- Envio de notificações por email
- Integração com Slack ou Discord
- Notificações em tempo real por WebSocket
- Resumo diário ou semanal
- Horários de silêncio

---

## 3. MODELO DE DADOS

### 3.1 Notificação

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| destinatário | referência a usuário | obrigatório |
| autor da ação | referência a usuário | opcional; nulo quando originado do GitHub sem correspondência |
| tipo | enum | ver seção 4 |
| título | texto | gerado pelo sistema |
| descrição | texto | contexto adicional |
| projeto / tarefa / comentário | referências | opcionais, conforme o tipo |
| lida | booleano | default falso |
| criada em | timestamp | automático |
| lida em | timestamp | preenchido ao marcar como lida |

**Regras**
- O autor de uma ação **nunca** é notificado da própria ação.
- Um mesmo evento não gera duas notificações para a mesma pessoa. Se alguém é simultaneamente responsável e mencionado, recebe apenas a notificação de maior relevância (menção tem precedência).
- Notificações são geradas apenas para quem ainda é membro do projeto.

### 3.2 Atividade do projeto

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| projeto | referência | obrigatório |
| autor | referência a usuário | opcional |
| tipo | enum | ver seção 5 |
| descrição | texto | gerada pelo sistema |
| tarefa / comentário / membro | referências | opcionais |
| metadados | dados adicionais | específicos do evento |
| criada em | timestamp | automático |

**Regras**
- Registro imutável: não há edição nem exclusão pela API.
- Atividades permanecem mesmo quando a tarefa é arquivada.
- As atividades de tarefa descritas em `spec-tasks.md` são a visão filtrada deste mesmo histórico.

### 3.3 Preferências de notificação

Um conjunto de opções por usuário, todas habilitadas por padrão. Cada tipo de notificação pode ser desligado individualmente. Preferências valem globalmente, não por projeto.

---

## 4. TIPOS DE NOTIFICAÇÃO

| Tipo | Destinatários | Disparo |
|---|---|---|
| Tarefa atribuída | novo responsável | tarefa criada com responsável ou responsável alterado |
| Tarefa comentada | criador e responsável | comentário criado |
| Menção | usuários mencionados | comentário ou descrição contendo `@username` |
| Tarefa movida | responsável | movimentação manual feita por outra pessoa |
| Prazo próximo | responsável | 24 horas antes do prazo, para tarefa não concluída |
| Prazo vencido | responsável | no momento em que o prazo vence, para tarefa não concluída |
| Prioridade elevada | responsável | prioridade alterada para `HIGH` ou `URGENT` |
| PR aberto | responsável da tarefa | webhook de PR aberto |
| PR mergeado | responsável da tarefa | webhook de merge |
| Commit recebido | responsável da tarefa | commit de outra pessoa em tarefa atribuída a você |
| Novo membro | dono e admins | convite aceito |
| Removido do projeto | o próprio removido | remoção por outra pessoa |
| Papel alterado | o próprio membro | mudança de papel |
| GitHub desconectado | dono do projeto | token inválido ou revogado detectado |

**Precedência**: quando um evento se qualifica para mais de um tipo para o mesmo destinatário, prevalece, nesta ordem — menção, atribuição, movimentação, comentário.

---

## 5. TIPOS DE ATIVIDADE

| Categoria | Eventos registrados |
|---|---|
| Projeto | criado, editado, arquivado, GitHub vinculado, GitHub desvinculado, sincronizado |
| Quadro e colunas | quadro criado/editado/excluído, coluna criada/editada/excluída/reordenada |
| Tarefas | criada, editada, movida, atribuída, prioridade alterada, prazo alterado, label adicionada/removida, arquivada |
| Comentários | adicionado, editado, excluído |
| Membros | convite enviado, convite aceito, membro removido, papel alterado |
| GitHub | branch criada, commit recebido, PR aberto, PR mergeado, PR fechado, issue vinculada, issue importada |
| Alertas | limite WIP excedido por automação, falha de sincronização |

---

## 6. ENDPOINTS

Todos exigem autenticação.

### 6.1 Listar notificações — `GET /api/notifications`

**Entrada (query)**: `unreadOnly` (default falso), `projectId` (opcional), `page`, `size`

**Comportamento**: retorna as notificações do usuário autenticado, mais recentes primeiro.

**Saída (200)**: lista paginada com o total de não lidas

---

### 6.2 Contar não lidas — `GET /api/notifications/unread-count`

**Saída (200)**: número total de notificações não lidas

> Endpoint consultado com alta frequência pelo cliente para exibir o indicador. Deve ser leve.

---

### 6.3 Marcar como lida — `PUT /api/notifications/{notificationId}/read`

**Erros**
| Situação | Status |
|---|---|
| Notificação de outro usuário | 403 |
| Notificação inexistente | 404 |

---

### 6.4 Marcar todas como lidas — `PUT /api/notifications/read-all`

**Entrada (query)**: `projectId` (opcional — restringe a um projeto)

**Saída (200)**: quantidade marcada

---

### 6.5 Excluir notificação — `DELETE /api/notifications/{notificationId}`

Remove da lista do usuário. Não afeta a atividade correspondente.

---

### 6.6 Atividades do projeto — `GET /api/projects/{projectId}/activities`

**Entrada (query)**: `type` (opcional, múltiplos), `userId` (opcional), `taskId` (opcional), `since` / `until` (datas), `page`, `size`

**Comportamento**: histórico do projeto, mais recente primeiro. Disponível a qualquer participante, inclusive `VIEWER`.

**Saída (200)**: lista paginada de atividades

---

### 6.7 Preferências — `GET /api/notifications/preferences` · `PUT /api/notifications/preferences`

**GET**: retorna as preferências do usuário, com os defaults quando nunca alteradas.
**PUT**: atualiza. Envio parcial altera apenas o que foi informado.

---

## 7. FLUXOS

### 7.1 Tarefa atribuída
1. Um membro cria a tarefa e define outra pessoa como responsável.
2. Sistema registra a atividade de criação e a de atribuição no projeto.
3. Sistema gera notificação de atribuição para o responsável.
4. O criador não é notificado, por ser o autor da ação.
5. Se o criador atribuir a si mesmo, nenhuma notificação é gerada.

### 7.2 Comentário com menção
1. Alguém comenta mencionando dois membros, sendo um deles o responsável pela tarefa.
2. Sistema registra a atividade de comentário.
3. O membro apenas mencionado recebe notificação de menção.
4. O membro que é responsável **e** mencionado recebe apenas a notificação de menção, por precedência.
5. O criador da tarefa recebe notificação de comentário, se não for o autor.

### 7.3 Merge de PR
1. Webhook informa que o PR foi mergeado.
2. Sistema move a tarefa para a coluna de conclusão.
3. Sistema registra as atividades de merge e de movimentação, com autor nulo ou correspondente ao usuário GitHub, quando identificável.
4. Responsável recebe notificação de PR mergeado.
5. Não há notificação de movimentação, para evitar duplicidade sobre o mesmo fato.

### 7.4 Prazo vencendo
1. Rotina periódica identifica tarefas não concluídas com prazo nas próximas 24 horas.
2. Gera notificação de prazo próximo para cada responsável.
3. Cada tarefa gera esse aviso uma única vez, mesmo que a rotina rode várias vezes no intervalo.
4. Ao vencer, gera a notificação de prazo vencido, também uma única vez.

---

## 8. RETENÇÃO E DESEMPENHO

| Item | Regra |
|---|---|
| Notificações lidas | mantidas por 90 dias, depois removidas por rotina |
| Notificações não lidas | mantidas indefinidamente |
| Atividades | mantidas indefinidamente |
| Geração | assíncrona; falha ao notificar nunca impede a operação que a originou |
| Contagem de não lidas | otimizada para leitura frequente |
| Consultas | sempre paginadas, com limite máximo por página |

---

## 9. CRITÉRIOS DE ACEITE

- [ ] Autor de uma ação nunca recebe notificação dela
- [ ] Atribuir tarefa a si mesmo não gera notificação
- [ ] Menção e atribuição no mesmo evento geram uma única notificação, a de menção
- [ ] Menção a quem não é membro do projeto não gera notificação
- [ ] Ex-membro não recebe notificações do projeto
- [ ] Contagem de não lidas reflete o estado real após marcar como lida
- [ ] Marcar todas como lidas aceita restrição por projeto
- [ ] Tentar ler notificação de outro usuário retorna 403
- [ ] Aviso de prazo próximo é gerado uma única vez por tarefa
- [ ] Merge de PR gera notificação de merge, não de movimentação
- [ ] Atividades do projeto são visíveis inclusive para Viewer
- [ ] Atividades não podem ser editadas nem excluídas pela API
- [ ] Preferência desligada suprime a notificação correspondente
- [ ] Falha na geração de notificação não impede a ação que a originou

---

**Fim das especificações do MVP.**
