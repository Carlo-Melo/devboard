# spec-members.md — Módulo de Membros e Permissões

> **Pré-requisito**: `claude.md` + `spec-projects.md` + `spec-authentication.md`

**Status**: MVP — Prioridade 6
**Dependências**: Projetos, Autenticação

---

## 1. OBJETIVO

Permitir que um projeto deixe de ser individual. Define quem participa, com qual nível de acesso, e por quais caminhos alguém entra em um projeto.

O modelo de papéis é deliberadamente enxuto — três níveis — porque o público-alvo são equipes de 2 a 10 pessoas, para quem a granularidade fina de ferramentas corporativas é mais atrito do que benefício.

---

## 2. ESCOPO

### Dentro do escopo
- Convite por email
- Link de convite compartilhável
- Importação de colaboradores do repositório GitHub vinculado
- Aceite de convite
- Listagem e gestão de membros
- Alteração de papel
- Remoção de membro e saída voluntária

### Fora do escopo
- Grupos ou times reutilizáveis entre projetos
- Papéis customizados
- Permissões por quadro ou por coluna
- Convite de usuários externos com acesso somente a tarefas específicas

---

## 3. MODELO DE DADOS

### 3.1 Membro do projeto

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| projeto | referência | obrigatório |
| usuário | referência | obrigatório |
| papel | enum | `ADMIN` \| `DEVELOPER` \| `VIEWER` |
| convidado por | referência a usuário | opcional |
| entrou em | timestamp | automático |

**Regras**
- A combinação projeto + usuário é única.
- O dono do projeto não é um registro de membro: sua autoridade vem da propriedade. Ele não pode ser removido nem ter o papel alterado.
- Um projeto pode existir sem nenhum membro além do dono.

### 3.2 Convite

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| projeto | referência | obrigatório |
| tipo | enum | `EMAIL` \| `LINK` |
| email | texto | obrigatório quando tipo é `EMAIL`; nulo quando `LINK` |
| papel concedido | enum | papel que o convidado recebe ao aceitar |
| token | texto | único; valor aleatório criptograficamente seguro |
| convidado por | referência a usuário | obrigatório |
| status | enum | `PENDING` \| `ACCEPTED` \| `EXPIRED` \| `REVOKED` |
| usos | numérico | quantas vezes foi aceito |
| máximo de usos | numérico | 1 para `EMAIL`; ilimitado para `LINK` |
| criado em | timestamp | automático |
| expira em | timestamp | criação + 7 dias |

**Regras**
- Convite por email é de uso único e destina-se àquele endereço específico.
- Convite por link pode ser aceito por várias pessoas até expirar ou ser revogado.
- Convidar um email que já é membro é rejeitado.
- Convidar um email com convite `PENDING` ativo reenvia o convite existente em vez de criar outro.

---

## 4. PAPÉIS E PERMISSÕES

### 4.1 Matriz consolidada

| Ação | Dono | Admin | Developer | Viewer |
|---|:---:|:---:|:---:|:---:|
| Visualizar projeto, quadros e tarefas | ✅ | ✅ | ✅ | ✅ |
| Comentar em tarefas | ✅ | ✅ | ✅ | ✅ |
| Criar tarefa | ✅ | ✅ | ✅ | ❌ |
| Editar qualquer tarefa | ✅ | ✅ | ❌ | ❌ |
| Editar tarefa própria ou atribuída | ✅ | ✅ | ✅ | ❌ |
| Mover tarefa | ✅ | ✅ | ✅ | ❌ |
| Arquivar tarefa | ✅ | ✅ | apenas próprias | ❌ |
| Atribuir responsável | ✅ | ✅ | ✅ | ❌ |
| Criar branch | ✅ | ✅ | ✅ | ❌ |
| Reordenar colunas | ✅ | ✅ | ✅ | ❌ |
| Criar / editar / excluir colunas e quadros | ✅ | ✅ | ❌ | ❌ |
| Gerenciar labels do projeto | ✅ | ✅ | ❌ | ❌ |
| Editar dados do projeto | ✅ | ✅ | ❌ | ❌ |
| Vincular / desvincular GitHub | ✅ | ✅ | ❌ | ❌ |
| Configurar automações | ✅ | ✅ | ❌ | ❌ |
| Sincronizar manualmente | ✅ | ✅ | ✅ | ❌ |
| Convidar membros | ✅ | ✅ | ❌ | ❌ |
| Alterar papel de membro | ✅ | ✅ | ❌ | ❌ |
| Remover membro | ✅ | ✅ | ❌ | ❌ |
| Arquivar projeto | ✅ | ❌ | ❌ | ❌ |

### 4.2 Restrições sobre papéis

- Um `ADMIN` não pode alterar o papel nem remover outro `ADMIN` — apenas o dono pode.
- Um `ADMIN` não pode se autopromover a dono.
- Qualquer membro pode sair voluntariamente do projeto; o dono não.
- O último `ADMIN` pode ser removido: o dono sempre permanece com autoridade total.

---

## 5. ENDPOINTS

Todos exigem autenticação, exceto o aceite de convite quando indicado.

### 5.1 Convidar por email — `POST /api/projects/{projectId}/members/invite`

**Entrada**: email, papel

**Comportamento**
1. Valida que o email não pertence ao dono nem a um membro atual.
2. Se já existe convite pendente para o email, reenvia o email do convite existente.
3. Caso contrário, cria o convite e envia o email.

**Permissão**: dono ou `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| Email inválido ou papel inexistente | 400 |
| Email já é membro ou é o dono | 409 |
| Sem permissão | 403 |

---

### 5.2 Gerar link de convite — `POST /api/projects/{projectId}/members/invite-link`

**Entrada**: papel concedido

**Comportamento**: cria (ou retorna) um convite do tipo link com validade de 7 dias. Só existe um link ativo por papel em cada projeto.

**Saída (201)**: URL de aceite e data de expiração

**Permissão**: dono ou `ADMIN`

---

### 5.3 Importar colaboradores do GitHub — `POST /api/projects/{projectId}/members/import-github`

**Entrada**: papel a conceder, lista de logins a importar (opcional — se omitido, importa todos)

**Comportamento**
1. Valida que o projeto tem repositório vinculado.
2. Consulta os colaboradores do repositório.
3. Para cada colaborador cujo email ou github id corresponda a um usuário do devBoard: adiciona como membro diretamente.
4. Para os demais: cria convite por email quando o email for público.
5. Ignora quem já é membro ou é o dono.

**Saída (200)**: resumo com quantidade adicionada, convidada e ignorada

**Permissão**: dono ou `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| Projeto sem repositório vinculado | 400 |
| Token GitHub inválido | 401 |
| GitHub indisponível | 503 |

---

### 5.4 Aceitar convite — `POST /api/invites/accept`

**Entrada**: token

**Comportamento**
1. Valida existência, status `PENDING`, prazo e usos disponíveis.
2. Exige usuário autenticado. Não autenticado → 401, para que o cliente direcione a login ou registro preservando o token.
3. Para convite do tipo `EMAIL`: exige que o email do usuário autenticado seja o mesmo do convite.
4. Cria o vínculo de membro com o papel do convite.
5. Incrementa os usos; convite de uso único passa a `ACCEPTED`.
6. Notifica quem convidou.

**Erros**
| Situação | Status |
|---|---|
| Token inexistente, expirado ou revogado | 400 |
| Não autenticado | 401 |
| Email do usuário diverge do convite | 403 |
| Já é membro do projeto | 409 |

---

### 5.5 Consultar convite — `GET /api/invites/{token}`

Endpoint público. Permite ao cliente exibir "Você foi convidado para o projeto X" antes do login.

**Saída (200)**: nome do projeto, nome de quem convidou, papel concedido e validade. Nunca expõe dados sensíveis do projeto.

---

### 5.6 Listar membros — `GET /api/projects/{projectId}/members`

**Saída (200)**: dono e membros com seus papéis e datas de entrada. Disponível a qualquer participante do projeto.

---

### 5.7 Listar convites — `GET /api/projects/{projectId}/invites`

**Entrada (query)**: `status` (opcional)

**Permissão**: dono ou `ADMIN`

---

### 5.8 Alterar papel — `PUT /api/projects/{projectId}/members/{memberId}`

**Entrada**: novo papel

**Permissão**: dono, ou `ADMIN` para membros que não sejam `ADMIN`

**Erros**
| Situação | Status |
|---|---|
| Tentativa de alterar o dono | 403 |
| `ADMIN` tentando alterar outro `ADMIN` | 403 |

---

### 5.9 Remover membro — `DELETE /api/projects/{projectId}/members/{memberId}`

**Comportamento**: remove o vínculo. Tarefas criadas ou atribuídas ao removido permanecem, mas o campo de responsável é esvaziado e uma atividade é registrada.

**Permissão**: dono, ou `ADMIN` para membros que não sejam `ADMIN`

---

### 5.10 Sair do projeto — `DELETE /api/projects/{projectId}/members/me`

**Comportamento**: o próprio membro encerra sua participação, com os mesmos efeitos da remoção.

**Erro**: dono tentando sair → 403

---

### 5.11 Revogar convite — `DELETE /api/invites/{inviteId}`

**Comportamento**: marca como `REVOKED`. Links revogados param de funcionar imediatamente.

**Permissão**: dono ou `ADMIN`

---

## 6. FLUXOS

### 6.1 Convite por email a usuário novo
1. Admin informa o email e escolhe o papel.
2. Sistema cria o convite e envia o email com o link.
3. Convidado abre o link; o cliente consulta o convite e exibe o contexto do projeto.
4. Sem conta, o convidado é levado ao registro com o email já preenchido e o token preservado.
5. Após criar a conta, o aceite é processado.
6. Convidado passa a ver o projeto na sua listagem; quem convidou é notificado.

### 6.2 Convite por email a usuário existente
Idêntico ao anterior, exceto que no passo 4 o convidado faz login. Se estiver autenticado com outro email, o sistema informa a divergência e orienta a trocar de conta.

### 6.3 Link compartilhável
1. Admin gera o link para um papel específico.
2. Link é distribuído pelo canal que a equipe usar.
3. Cada pessoa que abre e aceita entra com aquele papel.
4. O link permanece válido por 7 dias ou até ser revogado.

### 6.4 Importação de colaboradores do GitHub
1. Admin aciona a importação em um projeto com repositório vinculado.
2. Sistema lista os colaboradores do repositório.
3. Quem já tem conta no devBoard entra direto como membro.
4. Quem não tem recebe convite por email, quando o email for público no GitHub.
5. Sistema apresenta o resumo do que foi feito.

---

## 7. SEGURANÇA

| Item | Regra |
|---|---|
| Token de convite | aleatório criptograficamente seguro, impossível de adivinhar |
| Validade | 7 dias para qualquer tipo de convite |
| Convite por email | vinculado ao endereço; não aceita outro usuário |
| Consulta pública de convite | expõe apenas nome do projeto, quem convidou e papel |
| Escalada de privilégio | `ADMIN` não altera nem remove outro `ADMIN`, nem se promove a dono |
| Auditoria | registrar envio, aceite, revogação de convite e toda alteração de papel |

---

## 8. CRITÉRIOS DE ACEITE

- [ ] Convite por email cria registro pendente e dispara o email
- [ ] Convidar email que já é membro retorna 409
- [ ] Convidar email com convite pendente reenvia em vez de duplicar
- [ ] Link de convite aceita múltiplas pessoas até expirar
- [ ] Convite por email só é aceito pelo dono do endereço
- [ ] Convite expirado ou revogado é rejeitado
- [ ] Consulta pública do convite não vaza dados internos do projeto
- [ ] Aceite sem autenticação retorna 401 preservando o token no fluxo
- [ ] Importação do GitHub adiciona quem já tem conta e convida quem não tem
- [ ] Viewer visualiza e comenta, mas não cria nem move tarefas
- [ ] Developer não acessa endpoints de gestão de membros
- [ ] Admin não consegue alterar nem remover outro Admin
- [ ] Dono não pode ser removido, rebaixado nem sair do projeto
- [ ] Remover membro esvazia a atribuição das tarefas dele e registra atividade

---

**Próxima spec**: `spec-labels-search.md`
