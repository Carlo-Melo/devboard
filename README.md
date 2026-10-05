# devBoard

**Gestão de projetos e tarefas para pequenas equipes de desenvolvimento, conectada ao GitHub.**

O devBoard reúne o planejamento do trabalho e os sinais que já acontecem no repositório. A equipe acompanha tarefas em um quadro Kanban e, quando commits, pull requests ou issues mudam, as automações podem atualizar o quadro sem depender de uma segunda atualização manual.

> Projeto acadêmico em desenvolvimento. O escopo funcional e os critérios de aceite da versão 1.0 estão descritos em [`docs/`](docs/README.md).

## A dor que o devBoard resolve

Equipes pequenas costumam dividir o trabalho entre um board e o GitHub. Essa separação cria tarefas desatualizadas, dificulta saber o que está bloqueado e exige que a mesma informação seja mantida em mais de um lugar. O resultado é perda de contexto: o board diz uma coisa, enquanto branches, commits e pull requests contam outra.

O devBoard aproxima essas duas rotinas. O board organiza o fluxo de trabalho; a integração associa cada board a um repositório e usa eventos do GitHub para refletir atividade de desenvolvimento nas tarefas relacionadas. Projetos, quadros, membros e atividades ficam no mesmo espaço de trabalho, com acesso controlado por papéis.

## O que o produto oferece

- **Projetos e equipes:** espaços de trabalho com membros, convites e papéis de acesso.
- **Quadros Kanban:** vários boards por projeto, colunas configuráveis, papéis semânticos e limites de trabalho em progresso.
- **Tarefas:** responsáveis, prioridade, prazo, labels, comentários, histórico e movimentação entre colunas.
- **Integração com GitHub:** vínculo de repositórios por board, webhooks e automações ligadas a branches, commits, pull requests e issues.
- **Autenticação:** cadastro, login, GitHub OAuth, recuperação de senha e sessões com JWT.
- **Visão de trabalho:** dashboard com tarefas, prazos, prioridades e atividades relevantes para a pessoa autenticada.
- **Ciclo de vida dos dados:** arquivamento e restauração de projetos, tarefas e quadros conforme as regras do domínio.

As funcionalidades e limitações exatas são definidas pelas [especificações do produto](docs/README.md); esta lista não substitui seus critérios de aceite.

## Como a integração GitHub ajuda

Cada board pode ser ligado a um repositório. As tarefas podem ser relacionadas a referências de branch, commit, pull request e issue; eventos recebidos por webhook podem registrar commits, atualizar o fluxo da tarefa ou sincronizar o estado de uma issue, conforme a configuração do board. O processamento externo é assíncrono para que indisponibilidade do GitHub não impeça as operações locais do Kanban.

## Tecnologias

| Camada | Tecnologias |
|---|---|
| Backend | Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security, Maven |
| Banco e migrations | PostgreSQL 14+, Liquibase |
| Autenticação e segurança | JWT, BCrypt, OAuth GitHub, criptografia de credenciais sensíveis |
| Integração | GitHub API, webhooks e processamento assíncrono com Spring |
| Frontend | Angular 17, TypeScript, RxJS, Reactive Forms, SCSS |
| Testes | JUnit 5, Mockito, Jasmine e Karma |

## Arquitetura

O backend usa camadas para separar transporte HTTP, regras de negócio, persistência e modelos da API:

```text
Angular → REST API (Spring controllers) → services → repositories → PostgreSQL
                                            ↘ eventos assíncronos → GitHub / email
```

No frontend, componentes de página coordenam estado e chamadas; componentes de apresentação cuidam da interface; services concentram HTTP e estado compartilhado. As entidades JPA permanecem internas ao backend e os contratos entre camadas HTTP são DTOs.

### Estrutura principal

```text
devBoard-backend/   API Spring Boot, domínio, migrations e testes
devBoard-frontend/  Aplicação Angular
docs/               Especificações funcionais, auditorias e guias
AGENTS.md           Convenções e regras de desenvolvimento do repositório
claude.md           Guia complementar de desenvolvimento
```

## Requisitos para executar localmente

- Java 17+
- Maven 3.8+
- Node.js 18+ e npm
- PostgreSQL 14+
- Credenciais de OAuth do GitHub para testar a autenticação e a integração

## Configuração

Crie o banco local, por exemplo `devboard`, e disponibilize as variáveis abaixo no ambiente em que o backend será executado. Liquibase aplica as migrations ao iniciar a aplicação.

| Variável | Uso |
|---|---|
| `DB_URL` | JDBC do PostgreSQL; por padrão `jdbc:postgresql://localhost:5432/devboard` |
| `DB_USER`, `DB_PASSWORD` | Credenciais do banco |
| `JWT_SECRET` | Segredo de assinatura JWT; use um valor forte com pelo menos 64 caracteres |
| `ENCRYPTION_KEY` | Chave usada para proteger tokens e outros dados sensíveis |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | OAuth do GitHub |
| `GITHUB_OAUTH_CALLBACK_URL` | Callback OAuth; padrão local `http://localhost:8080/api/auth/github/callback` |
| `GITHUB_WEBHOOK_SECRET` | Segredo para validar a assinatura dos webhooks |
| `GITHUB_WEBHOOK_URL` | URL pública de recebimento dos webhooks |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` | Configuração do servidor de email |
| `MAIL_FROM` | Endereço remetente; usa `MAIL_USER` por padrão |
| `APP_BASE_URL` | URL do frontend usada em links enviados por email |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas; padrão local `http://localhost:4200` |

Não versione segredos nem coloque credenciais reais em exemplos, logs ou arquivos de configuração.

## Executar em desenvolvimento

Em um terminal, inicie o backend:

```bash
cd devBoard-backend
mvn spring-boot:run
```

Em outro terminal, instale as dependências e inicie o frontend:

```bash
cd devBoard-frontend
npm install
npm start
```

A aplicação Angular fica em `http://localhost:4200` e a API Spring Boot em `http://localhost:8080`. O frontend local aponta para `http://localhost:8080/api`.

## Build e testes

```bash
# Backend
cd devBoard-backend
mvn clean package
mvn test

# Frontend — em outro terminal
cd devBoard-frontend
npm run build
npm test -- --watch=false
```

## Documentação do produto

- [Índice das especificações](docs/README.md)
- [Autenticação](docs/spec-authentication.md)
- [Projetos](docs/spec-projects.md)
- [Quadros Kanban](docs/spec-board-kanban.md)
- [Tarefas](docs/spec-tasks.md)
- [Integração GitHub](docs/spec-github-integration.md)
- [Membros e permissões](docs/spec-members.md)
- [Labels e busca](docs/spec-labels-search.md)
- [Notificações](docs/spec-notifications.md)
- [Guia de desenvolvimento](AGENTS.md)

## Status

O devBoard está em desenvolvimento. A versão 1.0 contempla a entrega progressiva dos módulos descritos nas specs; funcionalidades implementadas no código ainda precisam ser verificadas contra todos os critérios de aceite antes de serem consideradas concluídas.
