# spec-authentication.md — Módulo de Autenticação

> **Pré-requisito**: Leia `claude.md` (padrões de código, estrutura, stack).
> Esta spec define **o quê** deve ser construído. O **como** está no `claude.md`.

**Status**: MVP — Prioridade 1
**Dependências**: Nenhuma (módulo base)

---

## 1. OBJETIVO

Permitir que um usuário crie conta, acesse o sistema e recupere o acesso caso perca a senha. Suporta dois provedores de identidade: credenciais próprias (email/senha) e GitHub OAuth.

O GitHub OAuth é estratégico: o token obtido é reutilizado pelos módulos de Projetos e Integração GitHub para listar repositórios, criar branches e registrar webhooks.

---

## 2. ESCOPO

### Dentro do escopo
- Registro com email/senha
- Login com email **ou** username + senha
- Login via GitHub OAuth (cria usuário automaticamente)
- Emissão e validação de JWT
- Alteração de senha (usuário autenticado)
- Recuperação de senha por email
- Consulta e edição do próprio perfil

### Fora do escopo (fase futura)
- Refresh token
- Confirmação de email no registro
- Autenticação de dois fatores
- Blacklist de tokens no logout
- Login social além do GitHub

---

## 3. MODELO DE DADOS

### 3.1 Usuário

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| username | texto | único, 3–50 caracteres, alfanumérico + underscore |
| email | texto | único, formato de email válido |
| senha | texto | hash BCrypt; nulo quando origem = GITHUB |
| nome completo | texto | opcional |
| avatar | URL | opcional |
| origem da conta | enum | `TRADITIONAL` \| `GITHUB` |
| github id | numérico | único quando presente |
| github username | texto | opcional |
| github token | texto | criptografado em repouso; nunca retornado em API |
| criado em / atualizado em | timestamp | automático |

**Regras**
- Um mesmo email não pode existir duas vezes, independentemente da origem.
- Se um usuário `TRADITIONAL` fizer login via GitHub com o mesmo email, as contas são vinculadas (origem permanece `TRADITIONAL`, dados GitHub são preenchidos).
- Usuário com origem `GITHUB` e sem senha definida não pode usar login tradicional nem recuperação de senha — deve ser orientado a entrar pelo GitHub.

### 3.2 Token de Recuperação de Senha

| Campo | Tipo | Regra |
|---|---|---|
| id | identificador | gerado pelo sistema |
| usuário | referência | obrigatório |
| token | texto | único; valor aleatório criptograficamente seguro |
| hash do token | texto | o que fica persistido; o valor puro só vai no email |
| status | enum | `PENDING` \| `USED` \| `EXPIRED` |
| criado em | timestamp | automático |
| expira em | timestamp | criação + 24 horas |
| usado em | timestamp | preenchido no consumo |

**Regras**
- Ao solicitar nova recuperação, tokens `PENDING` anteriores do mesmo usuário são invalidados (`EXPIRED`).
- O token é de uso único.

---

## 4. ENDPOINTS

Todos sob `/api/auth`. Nenhum exige autenticação, exceto onde indicado.

### 4.1 Registro — `POST /register`

**Entrada**: username, email, senha, confirmação de senha, nome completo (opcional)

**Validações**
- username: obrigatório, 3–50 caracteres, único, sem espaços
- email: obrigatório, formato válido, único
- senha: obrigatória, mínimo 8 caracteres, ao menos uma maiúscula, uma minúscula e um número
- confirmação deve ser idêntica à senha

**Saída (201)**: token JWT, dados públicos do usuário, momento de expiração do token

**Erros**
| Situação | Status |
|---|---|
| Campo inválido ou senhas divergentes | 400 |
| Username já existe | 409 |
| Email já existe | 409 |

---

### 4.2 Login — `POST /login`

**Entrada**: email ou username, senha

**Comportamento**
- O campo aceita indistintamente email ou username; o sistema decide pela presença de `@`.
- Contas de origem `GITHUB` sem senha definida retornam mensagem orientando login via GitHub.

**Saída (200)**: token JWT, dados públicos do usuário, expiração

**Erros**
| Situação | Status |
|---|---|
| Campo obrigatório ausente | 400 |
| Credenciais inválidas (usuário inexistente **ou** senha errada) | 401 |

> A mensagem de erro deve ser idêntica nos dois casos, para não revelar quais emails existem.

---

### 4.3 Iniciar OAuth GitHub — `GET /github/login`

**Entrada**: `redirectUri` (opcional, query)

**Comportamento**: redireciona (302) para a tela de autorização do GitHub, solicitando os escopos `repo` e `user:email`.

---

### 4.4 Callback OAuth GitHub — `GET /github/callback`

**Entrada**: `code` (query, obrigatório)

**Comportamento**
1. Troca o code por um access token junto ao GitHub.
2. Busca perfil e email primário verificado do usuário.
3. Localiza usuário por github id; se não encontrar, localiza por email.
4. Se encontrou: atualiza dados GitHub e o token armazenado.
5. Se não encontrou: cria usuário novo com origem `GITHUB`, gerando username a partir do login do GitHub (com sufixo numérico em caso de colisão).
6. Emite JWT próprio do devBoard.

**Saída (200)**: token JWT, dados públicos do usuário, expiração

**Erros**
| Situação | Status |
|---|---|
| Code ausente, inválido ou expirado | 400 |
| Escopo insuficiente / autorização negada | 403 |
| GitHub indisponível | 503 |

---

### 4.5 Alterar senha — `POST /change-password` 🔒

**Entrada**: senha atual, nova senha, confirmação

**Validações**
- Senha atual deve conferir
- Nova senha segue as mesmas regras de força do registro
- Nova senha deve ser diferente da atual
- Confirmação deve conferir

**Saída (200)**: mensagem de sucesso

**Erros**
| Situação | Status |
|---|---|
| Nova senha inválida ou confirmação divergente | 400 |
| Não autenticado | 401 |
| Senha atual incorreta | 403 |

---

### 4.6 Solicitar recuperação — `POST /forgot-password`

**Entrada**: email

**Comportamento**
- Sempre responde 200 com a mesma mensagem, exista ou não o email.
- Quando o email existe e a conta tem senha: invalida tokens pendentes, gera novo token, dispara email.
- Quando a conta é `GITHUB` sem senha: não envia email de recuperação (a resposta segue idêntica).

**Limite de uso**: máximo 3 solicitações por hora por email e por IP. Excedido → 429.

**Saída (200)**: mensagem genérica de confirmação

---

### 4.7 Validar token de recuperação — `GET /reset-password/validate-token`

**Entrada**: `token` (query)

**Comportamento**: verifica existência, status `PENDING` e prazo. Se expirado, marca como `EXPIRED`.

**Saída (200)**: indicador de validade e data de expiração
**Saída (400)**: indicador de invalidade com motivo genérico

---

### 4.8 Redefinir senha — `POST /reset-password`

**Entrada**: token, nova senha, confirmação

**Validações**: token válido e pendente; senha atende às regras de força; confirmação confere.

**Comportamento**: atualiza a senha, marca o token como `USED` e registra o momento de uso.

**Limite de uso**: máximo 5 tentativas por token. Excedido → token invalidado e 429.

**Erros**
| Situação | Status |
|---|---|
| Senha inválida ou confirmação divergente | 400 |
| Token inexistente ou expirado | 400 |
| Token já utilizado | 409 |

---

### 4.9 Logout — `POST /logout` 🔒

**Comportamento**: no MVP, apenas confirma a operação. A remoção do token é responsabilidade do cliente.

**Saída (200)**: mensagem de sucesso

---

### 4.10 Perfil do usuário — `GET /me` 🔒 · `PUT /me` 🔒

**GET**: retorna dados públicos do usuário autenticado (inclui origem da conta e se o GitHub está conectado; nunca retorna senha nem token GitHub).

**PUT**: permite alterar nome completo e avatar. Email e username não são editáveis no MVP.

---

## 5. FLUXOS

### 5.1 Registro e primeiro acesso
1. Usuário preenche o formulário de registro.
2. Sistema valida unicidade e força da senha.
3. Conta é criada com senha em hash.
4. JWT é emitido imediatamente — não há etapa de confirmação de email no MVP.
5. Cliente armazena o token e passa a enviá-lo em todas as requisições.

### 5.2 Login via GitHub (usuário novo)
1. Usuário aciona "Entrar com GitHub".
2. Sistema redireciona ao GitHub com os escopos necessários.
3. Usuário autoriza.
4. GitHub devolve o code ao callback.
5. Sistema obtém access token e perfil.
6. Nenhum usuário corresponde ao github id nem ao email → conta é criada.
7. Access token do GitHub é armazenado criptografado, para uso posterior pelos módulos de Projetos e Integração.
8. JWT é emitido e o usuário entra no sistema.

### 5.3 Login via GitHub (conta já existente por email)
Idêntico ao anterior até o passo 5. No passo 6, o email do GitHub coincide com uma conta `TRADITIONAL`: os dados GitHub são anexados à conta existente e o usuário mantém a possibilidade de login por senha.

### 5.4 Recuperação de senha
1. Usuário informa o email na tela "Esqueci minha senha".
2. Sistema responde com mensagem genérica, independentemente do resultado.
3. Se aplicável, um email é enviado com link contendo o token.
4. Ao abrir o link, o cliente valida o token antes de exibir o formulário.
5. Token inválido ou expirado → tela orienta nova solicitação.
6. Token válido → usuário define nova senha.
7. Senha é atualizada, token é consumido e o usuário é direcionado ao login.

---

## 6. SEGURANÇA

| Item | Regra |
|---|---|
| Armazenamento de senha | BCrypt, nunca texto puro |
| Força da senha | mínimo 8 caracteres, com maiúscula, minúscula e número |
| JWT | expiração de 24h; claims: identificador, email e username |
| Enumeração de usuários | mensagens de erro genéricas em login e recuperação |
| Token de recuperação | aleatório seguro, persistido como hash, uso único, validade de 24h |
| Token GitHub | criptografado em repouso; nunca exposto em resposta de API |
| Rate limit | recuperação: 3/hora; redefinição: 5 tentativas por token; login: 10/hora por IP |
| Auditoria | registrar em log: login bem-sucedido, login falho, alteração e redefinição de senha |

---

## 7. NOTIFICAÇÕES POR EMAIL

### Recuperação de senha
- **Assunto**: recuperação de senha no devBoard
- **Conteúdo**: saudação com o nome do usuário, link com o token, aviso de validade de 24 horas e orientação para ignorar caso não tenha solicitado
- **Link**: aponta para a rota de redefinição do frontend, carregando o token

---

## 8. CRITÉRIOS DE ACEITE

- [ ] Registro cria conta e devolve JWT válido em uma única chamada
- [ ] Email e username duplicados são rejeitados com 409
- [ ] Senha fraca é rejeitada com mensagem clara sobre a regra violada
- [ ] Login funciona tanto com email quanto com username
- [ ] Credenciais inválidas retornam a mesma mensagem para usuário inexistente e senha errada
- [ ] Login via GitHub cria conta quando o usuário é novo
- [ ] Login via GitHub vincula à conta existente quando o email coincide
- [ ] Token GitHub fica disponível para os demais módulos e nunca aparece em resposta de API
- [ ] Requisição sem JWT a endpoint protegido retorna 401
- [ ] JWT expirado retorna 401
- [ ] Solicitação de recuperação responde igual para email existente e inexistente
- [ ] Token de recuperação expira em 24h e só funciona uma vez
- [ ] Redefinir senha permite login imediato com a nova senha
- [ ] Rate limits disparam 429 nos limites definidos

---

**Próxima spec**: `spec-projects.md`
