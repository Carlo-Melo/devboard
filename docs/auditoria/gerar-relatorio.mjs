import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const output = path.join(here, 'relatorio-devboard.html');

const findings = [
  {
    id: 'SEC-01', severity: 'critical', type: 'bug', module: 'Segurança',
    title: 'Vínculo por email permite sequestro de identidade entre senha e GitHub',
    summary: 'O cadastro tradicional aceita qualquer email sem verificá-lo. Depois, o OAuth procura a conta por email e anexa o GitHub preservando a senha. Quem pré-cadastrar o email de outra pessoa mantém acesso quando a vítima entrar pelo GitHub. A importação de colaboradores repete a associação por email público.',
    scenario: 'Atacante registra vitima@empresa.com com uma senha. A vítima entra pelo GitHub com esse email verificado. O sistema anexa o GitHub à conta criada pelo atacante; ambos passam a acessar a mesma identidade.',
    impact: 'Tomada de conta, acesso aos projetos da vítima e possibilidade de importação indevida de membros. É o defeito mais grave para uma demonstração pública.',
    fix: 'Adicionar emailVerifiedAt e fluxo de verificação. Vincular OAuth a conta tradicional apenas após reautenticação/prova da conta existente. Na importação, adicionar diretamente somente por githubId verificado; correspondência apenas por email deve gerar convite.',
    verify: 'Teste de integração: conta local não verificada com email coincidente não pode ser vinculada nem adicionada diretamente. Conta verificada exige confirmação explícita antes do vínculo.',
    confidence: 'Confirmado por leitura e encadeamento de fluxo',
    evidence: [
      ['devBoard-backend/src/main/java/com/devboard/service/AuthService.java', 38, 'Registro persiste email sem estado de verificação.'],
      ['devBoard-backend/src/main/java/com/devboard/service/github/GithubOAuthService.java', 76, 'OAuth resolve primeiro por githubId e depois por email, preservando a senha.'],
      ['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 217, 'Importação também associa colaborador a usuário local pelo email público.'],
      ['devBoard-backend/src/main/java/com/devboard/entity/User.java', 34, 'Modelo não possui emailVerifiedAt.']
    ],
    specs: [['docs/spec-authentication.md', 258, 'A própria regra de vínculo por email precisa ser endurecida na spec.'], ['docs/spec-members.md', 159, 'Importação direta por email também precisa exigir identidade verificada.']]
  },
  {
    id: 'DEP-01', severity: 'high', type: 'bug', module: 'Deploy',
    title: 'Build de produção aponta para localhost:8080',
    summary: 'environment.prod.ts existe, mas angular.json não configura fileReplacements. Todos os serviços importam environment.ts e o bundle produzido contém http://localhost:8080/api.',
    scenario: 'Ao publicar o frontend, o navegador do avaliador tenta chamar a API na própria máquina do avaliador.',
    impact: 'A versão publicada fica inutilizável mesmo com o backend disponível.',
    fix: 'Adicionar fileReplacements na configuração production ou migrar para configuração de runtime. Validar o bundle em CI e usar uma URL real configurável.',
    verify: 'Executar ng build --configuration production e garantir que nenhum chunk contenha localhost:8080.',
    confidence: 'Confirmado por build de produção',
    evidence: [['devBoard-frontend/angular.json', 40, 'Configuração production não substitui o environment.'], ['devBoard-frontend/src/environments/environment.ts', 1, 'API de desenvolvimento.'], ['docs/auditoria/evidencias/frontend-build.txt', 1, 'Build executado com sucesso; inspeção do chunk encontrou localhost.']],
    specs: [['AGENTS.md', 630, 'O guia define environment.prod.ts para a API de produção.']]
  },
  {
    id: 'AUTH-01', severity: 'high', type: 'bug', module: 'Autenticação',
    title: 'Invalidação de token de recuperação sofre rollback',
    summary: 'O serviço marca o token como EXPIRED e logo lança uma RuntimeException dentro de @Transactional. O Spring faz rollback por padrão, então a invalidação por expiração ou excesso de tentativas não é persistida.',
    scenario: 'Na sexta tentativa, o código altera o status e lança 429; a transação desfaz a alteração e o banco continua com PENDING.',
    impact: 'A regra “estourar o limite invalida o token” não é garantida e o estado auditável fica incorreto.',
    fix: 'Persistir a transição em uma transação REQUIRES_NEW separada, ou configurar noRollbackFor apenas para a exceção depois de garantir que nenhuma outra escrita deva ser revertida.',
    verify: 'Teste de integração real: após 429, consultar diretamente o registro e exigir status EXPIRED.',
    confidence: 'Confirmado por leitura e semântica oficial do Spring',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/PasswordResetService.java', 73, 'Status alterado e exceção runtime lançada na mesma transação.']],
    specs: [['docs/spec-authentication.md', 197, 'Token expirado deve ser marcado como EXPIRED.'], ['docs/spec-authentication.md', 212, 'Cinco tentativas devem invalidar o token.']]
  },
  {
    id: 'PROJ-01', severity: 'high', type: 'divergence', module: 'Projetos',
    title: 'Criação aceita githubRepoId, responde 201 e ignora o repositório',
    summary: 'O DTO recebe githubRepoId e branches. O service só verifica se existe token e não valida o repositório, não persiste o vínculo, não registra webhook e não agenda importação.',
    scenario: 'Usuário cria projeto selecionando um repositório. A resposta é de sucesso, mas o projeto volta como GitHub não vinculado.',
    impact: 'Sucesso enganoso no principal diferencial do TCC.',
    fix: 'Até implementar o fluxo completo, rejeitar githubRepoId com 501/400 explícito. Na entrega final, resolver o repositório, validar push, persistir campos, registrar webhook e publicar evento assíncrono de importação.',
    verify: 'Teste de integração com cliente GitHub mockado cobrindo persistência, webhook e publicação do job.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/dto/project/CreateProjectRequest.java', 18, 'Entrada declara githubRepoId.'], ['devBoard-backend/src/main/java/com/devboard/service/ProjectService.java', 53, 'Só confere token; linhas seguintes não usam repo id nem branches.']],
    specs: [['docs/spec-projects.md', 70, 'Entrada e fluxo exigem vínculo, webhook e importação.']]
  },
  {
    id: 'BOARD-01', severity: 'high', type: 'bug', module: 'Quadro', reproduced: true,
    title: 'Excluir quadro com tarefas resulta em 500',
    summary: 'deleteBoard apaga colunas sem consultar tarefas. A FK tasks.column_id impede a exclusão e o handler converte a violação em erro interno.',
    scenario: 'Foi criado segundo quadro, uma tarefa permaneceu no primeiro e DELETE /api/boards/{id} retornou 500.',
    impact: 'Falha visível na demo, contrato HTTP quebrado e stack trace no servidor.',
    fix: 'Antes de excluir, consultar existência de tarefas ativas e arquivadas em qualquer coluna do quadro e lançar ConflictException. Não tentar apagar colunas enquanto houver referência.',
    verify: 'Teste de integração reproduzindo quadro com tarefa ativa e outro apenas com tarefa arquivada; ambos devem retornar 409.',
    confidence: 'Reproduzido contra PostgreSQL isolado',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 151, 'Exclui colunas sem validar tarefas.'], ['devBoard-backend/src/main/resources/db/changelog/V4__create_tasks_tables.sql', 24, 'FK impede exclusão.']],
    specs: [['docs/spec-board-kanban.md', 130, 'Quadro com tarefas deve retornar 409.']]
  },
  {
    id: 'BOARD-02', severity: 'high', type: 'bug', module: 'Quadro', reproduced: true,
    title: 'Exclusão de coluna aceita destino igual e ignora tarefas arquivadas',
    summary: 'moveTasksTo pode ser a própria coluna. A busca considera somente tarefas ativas; tarefas arquivadas continuam referenciando a coluna. O movimento em massa também ignora WIP, completedAt e histórico.',
    scenario: 'DELETE /api/columns/{id}?moveTasksTo={mesmo-id} retornou 500 por FK. Uma coluna com somente tarefa arquivada falha pelo mesmo motivo.',
    impact: 'Operação administrativa instável e perda de semântica ao mover tarefas para ou de DONE.',
    fix: 'Rejeitar destino igual; contar todas as tarefas; definir política para arquivadas; reutilizar uma rotina de movimentação em lote que aplique conclusão, WIP ou exceção administrativa e publique atividades.',
    verify: 'Testes de integração para destino igual, tarefa arquivada, destino DONE e WIP cheio.',
    confidence: 'Destino igual reproduzido; demais efeitos confirmados por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 235, 'Consulta apenas archived=false e não rejeita o próprio id.'], ['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 248, 'Altera coluna/posição diretamente, sem regras de move.']],
    specs: [['docs/spec-board-kanban.md', 164, 'Exclusão deve mover tarefas de forma válida ou rejeitar.']]
  },
  {
    id: 'TASK-01', severity: 'high', type: 'bug', module: 'Comentários', reproduced: true,
    title: 'Ex-membro continua editando e excluindo comentário próprio',
    summary: 'update e delete conferem autoria, mas pulam PermissionService quando o usuário é o autor. O vínculo atual com o projeto deixa de ser verificado.',
    scenario: 'Membro comentou, saiu do projeto e depois recebeu 200 ao editar e 204 ao excluir o comentário.',
    impact: 'Autorização residual depois da revogação de acesso.',
    fix: 'Sempre chamar requireRole(projectId, userId, VIEWER) antes da regra de autoria; depois aplicar autor para editar e autor/Admin para excluir.',
    verify: 'Teste de integração deve remover o membro e exigir 404 conforme a política anti-enumeração do AGENTS.md.',
    confidence: 'Reproduzido contra a API em execução',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/TaskCommentService.java', 79, 'Update só compara author.id.'], ['devBoard-backend/src/main/java/com/devboard/service/TaskCommentService.java', 93, 'Delete só chama permissão para terceiros.'], ['devBoard-backend/src/test/java/com/devboard/service/TaskCommentServiceTest.java', 146, 'Teste atual codifica a ausência da verificação.']],
    specs: [['docs/spec-tasks.md', 110, 'Todos os endpoints exigem vínculo com o projeto.']]
  },
  {
    id: 'FRONT-01', severity: 'high', type: 'bug', module: 'Frontend',
    title: 'Drag com filtro envia índice visual como posição global',
    summary: 'O quadro filtrado contém apenas um subconjunto das tarefas. O drop envia event.currentIndex desse subconjunto ao backend, que o interpreta como posição na lista completa.',
    scenario: 'Filtre por prioridade, mova o único card visível para a primeira posição e remova o filtro: tarefas ocultas foram reordenadas inesperadamente.',
    impact: 'Ordem persistida diverge da intenção do usuário.',
    fix: 'Desabilitar reordenação enquanto há filtros ou traduzir o índice visível para uma posição global estável usando vizinhos não filtrados/versionamento.',
    verify: 'E2E com três cards, um filtro que esconda o card intermediário e comparação da ordem após limpar o filtro.',
    confidence: 'Confirmado por leitura do cliente e contrato do backend',
    evidence: [['devBoard-frontend/src/app/features/board/board-view/board-view.component.ts', 81, 'Servidor recebe quadro filtrado.'], ['devBoard-frontend/src/app/features/board/board-view/board-view.component.ts', 125, 'currentIndex filtrado é enviado como posição.'], ['devBoard-backend/src/main/java/com/devboard/service/TaskService.java', 187, 'Backend reindexa a lista completa de tarefas ativas.']],
    specs: [['docs/spec-tasks.md', 279, 'Movimento deve manter posições corretas e sem lacunas.']]
  },
  {
    id: 'FRONT-02', severity: 'high', type: 'bug', module: 'Frontend',
    title: 'Predicado visual de WIP libera qualquer destino e apaga o erro do backend',
    summary: 'drop.data sempre é a mesma referência de column.tasks no destino, então a condição retorna true antes de comparar o limite. Se o backend retorna 409, errorMessage é definido e load() o limpa imediatamente.',
    scenario: 'Arrastar para coluna cheia parece permitido; o card salta, a API rejeita, o quadro recarrega e a mensagem desaparece.',
    impact: 'Interação confusa justamente na regra central do Kanban.',
    fix: 'Comparar a origem do drag com o destino, ou usar drag.dropContainer; preservar a mensagem durante o reload e reverter otimisticamente de forma controlada.',
    verify: 'Teste de componente/E2E deve impedir entrada em destino cheio e manter mensagem em resposta 409.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-frontend/src/app/features/board/board-view/board-view.component.ts', 117, 'Comparação drop.data === column.tasks é verdadeira para o destino.'], ['devBoard-frontend/src/app/features/board/board-view/board-view.component.ts', 142, 'Erro é atribuído e logo load() zera errorMessage.']],
    specs: [['docs/spec-board-kanban.md', 207, 'Usuário deve receber erro específico quando o WIP foi atingido.']]
  },
  {
    id: 'TASK-02', severity: 'high', type: 'bug', module: 'Tarefas',
    title: 'Tarefa arquivada continua aceitando edição, movimento e comentário',
    summary: 'findByIdWithDetails não filtra archived e os métodos não bloqueiam o estado. move remove a tarefa de uma lista que já a exclui, adiciona-a à lista ativa para reindexar e mantém archived=true.',
    scenario: 'Uma chamada direta à API move uma tarefa já arquivada; ela segue invisível, mas altera as posições das tarefas visíveis.',
    impact: 'Corrupção silenciosa da ordem e alteração de recurso que deveria estar fora do fluxo ativo.',
    fix: 'Criar findActiveByIdWithDetails ou validação central assertActive para mutações. Se houver restauração futura, criar endpoint explícito.',
    verify: 'Após arquivar, PUT, move e POST comentário devem retornar 409/404 e não mudar posições.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/repository/TaskRepository.java', 26, 'Consulta por id não filtra archived.'], ['devBoard-backend/src/main/java/com/devboard/service/TaskService.java', 169, 'Move não valida estado arquivado.'], ['devBoard-backend/src/main/java/com/devboard/service/TaskCommentService.java', 48, 'Comentário também não valida.']],
    specs: [['AGENTS.md', 282, 'Soft delete deve tirar tarefas das consultas operacionais por padrão.']]
  },
  {
    id: 'MEM-01', severity: 'high', type: 'bug', module: 'Membros',
    title: 'Dono não aparece no endpoint de membros nem nas opções de responsável',
    summary: 'listMembers retorna somente project_members. No detalhe de tarefa, o frontend copia somente project.members; o owner é um campo separado e nunca entra nos selects.',
    scenario: 'O dono cria o projeto e tenta atribuir uma tarefa a si mesmo: seu nome não aparece na lista, embora o backend o reconheça como ADMIN válido.',
    impact: 'Fluxo comum de equipes pequenas fica bloqueado pela interface e o endpoint viola seu contrato.',
    fix: 'Definir DTO unificado de participante com owner=true e role efetiva ADMIN, incluindo o dono na listagem. No frontend, combinar owner + members sem duplicidade.',
    verify: 'Teste de contrato e componente exigindo o dono exatamente uma vez nas opções.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 132, 'Retorna apenas registros ProjectMember.'], ['devBoard-frontend/src/app/features/board/task-detail/task-detail.component.ts', 80, 'Usa somente project.members.']],
    specs: [['docs/spec-members.md', 206, 'Saída deve conter dono e membros.']]
  },
  {
    id: 'GH-01', severity: 'high', type: 'gap', module: 'GitHub',
    title: 'Integração GitHub de negócio ainda não existe',
    summary: 'Há OAuth para login e leitura inicial de colaboradores. Não há endpoints de repositórios, vínculo/desvínculo, sync, branches/issues, webhook, assinatura HMAC, idempotência, automações, prevenção de laço ou retry.',
    scenario: 'Commit, PR ou issue no GitHub não altera nenhuma tarefa. Criar branch pela tarefa não existe.',
    impact: 'O diferencial declarado “Kanban integrado ao GitHub” ainda não pode ser apresentado como entregue.',
    fix: 'Implementar em fatias verticais: vínculo + webhook seguro; idempotência; importação de issues; branch; push/PR; sync inverso; retry/observabilidade.',
    verify: 'Testes de contrato do webhook e cenários de ponta a ponta da spec, incluindo reentrega e prevenção de laço.',
    confidence: 'Confirmado por inventário integral de controllers/services/entities/migrations',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/github/GithubClient.java', 17, 'Cliente atual cobre OAuth e colaboradores.'], ['devBoard-backend/src/main/java/com/devboard/controller/ProjectController.java', 25, 'Não há endpoints GitHub do projeto.'], ['devBoard-backend/src/main/java/com/devboard/controller/TaskController.java', 30, 'Não há branch ou vínculo de issue.']],
    specs: [['docs/spec-github-integration.md', 38, 'Webhook e automações completos.'], ['docs/spec-projects.md', 133, 'Endpoints de repositório/vínculo/sync.'], ['docs/spec-tasks.md', 197, 'Branch e issue.']]
  },
  {
    id: 'OAUTH-01', severity: 'high', type: 'risk', module: 'Segurança',
    title: 'state OAuth é global e não está vinculado ao navegador que iniciou o login',
    summary: 'O state é aleatório e de uso único, mas fica somente em um cache global. Qualquer navegador que possua state+code válidos consegue consumir o callback; não há cookie/sessão para provar que foi o iniciador.',
    scenario: 'Login CSRF: atacante inicia OAuth com a própria conta e induz a vítima a abrir o callback, autenticando o navegador da vítima na conta do atacante.',
    impact: 'Confusão de identidade e ações da vítima na conta errada.',
    fix: 'Emitir cookie HttpOnly, Secure e SameSite=Lax com nonce; armazenar hash/associação state↔nonce e validar ambos no callback. Considerar PKCE.',
    verify: 'Callback com state válido em navegador sem o cookie original deve falhar.',
    confidence: 'Risco confirmado por desenho; exploração deve ser testada em ambiente OAuth real',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/github/GithubOAuthStateService.java', 21, 'Cache associa state apenas ao redirectUri.'], ['devBoard-backend/src/main/java/com/devboard/controller/AuthController.java', 58, 'Callback não recebe vínculo de navegador.']],
    specs: [['docs/spec-authentication.md', 272, 'Segurança OAuth não detalha o vínculo; decisão deve ser acrescentada.']]
  },
  {
    id: 'GH-02', severity: 'medium', type: 'bug', module: 'GitHub',
    title: 'Importação lê só a primeira página e faz N+1 chamadas dentro da transação',
    summary: 'fetchCollaborators não segue Link nem envia per_page. Para cada item faz nova chamada /users/{login}; todo o ciclo ocorre dentro de @Transactional e da requisição HTTP.',
    scenario: 'Repositório com mais de 30 colaboradores importa apenas parte da equipe e mantém conexão de banco aberta durante dezenas de chamadas externas.',
    impact: 'Resultado incompleto, latência alta e risco de esgotar pool/rate limit.',
    fix: 'Paginar até rel=last, buscar somente selecionados, separar coleta externa da transação curta e mover importação para executor GitHub com job/status.',
    verify: 'Cliente mockado com duas páginas e 31 colaboradores; medir que a transação de escrita começa depois da coleta.',
    confidence: 'Confirmado por leitura e comportamento documentado da API GitHub',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/github/GithubClient.java', 119, 'Uma única chamada sem paginação.'], ['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 192, 'Método transacional envolve chamadas externas.']],
    specs: [['docs/spec-members.md', 152, 'Importar todos os colaboradores selecionados.'], ['AGENTS.md', 322, 'Serviço externo não deve bloquear resposta HTTP.']]
  },
  {
    id: 'BOARD-03', severity: 'medium', type: 'divergence', module: 'Quadro',
    title: 'Listagem de quadros carrega e devolve todas as tarefas',
    summary: 'listBoards agrega ids de todas as colunas, carrega tarefas e comentários e usa o mesmo mapper da visão completa.',
    scenario: 'Abrir detalhe de projeto com vários quadros transfere centenas de cards que a tela usa apenas para listar quadros.',
    impact: 'Contrato maior que a spec, custo desnecessário e possibilidade de exposição acidental de dados.',
    fix: 'Criar BoardSummaryResponse para a listagem e query de contagem agrupada por coluna, sem TaskResponse.',
    verify: 'Teste JSON deve garantir ausência de tasks e verificar quantidade de consultas.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 82, 'Carrega tasksByColumn e commentCounts.'], ['devBoard-backend/src/main/java/com/devboard/mapper/BoardMapper.java', 29, 'Mapper inclui tasks.']],
    specs: [['docs/spec-board-kanban.md', 104, 'Listagem deve trazer contagem, sem tarefas.']]
  },
  {
    id: 'FRONT-03', severity: 'medium', type: 'gap', module: 'Frontend',
    title: 'Interface não aplica papel: Viewer vê ações proibidas',
    summary: 'Só existe AuthGuard. Não existe RoleGuard nem diretiva hasRole; quadro e detalhe exibem criar, mover, editar, excluir e arquivar para qualquer participante.',
    scenario: 'Viewer abre o quadro, tenta editar coluna ou arrastar tarefa e recebe erro do backend.',
    impact: 'Backend permanece protegido, mas a experiência contradiz a matriz de permissões e prejudica a demo.',
    fix: 'Disponibilizar role efetiva no contexto do board/task, implementar diretiva *hasRole e condicionar controles. O backend continua sendo a autoridade.',
    verify: 'Testes de componente para Owner/Admin/Developer/Viewer e E2E de Viewer.',
    confidence: 'Confirmado por inventário e templates',
    evidence: [['devBoard-frontend/src/app/app.routes.ts', 1, 'Rotas usam somente authGuard.'], ['devBoard-frontend/src/app/features/board/board-view/board-view.component.html', 42, 'Controles e drag não dependem de papel.'], ['devBoard-frontend/src/app/features/board/task-detail/task-detail.component.ts', 94, 'Edição não verifica role no cliente.']],
    specs: [['AGENTS.md', 221, 'Guard protege a rota e diretiva esconde controle.'], ['docs/spec-members.md', 82, 'Matriz consolidada.']]
  },
  {
    id: 'FRONT-04', severity: 'medium', type: 'bug', module: 'Frontend',
    title: 'Erro ao aceitar convite fica invisível',
    summary: 'Quando invite já foi carregado, o template fica no primeiro ng-container. O alerta de errorMessage só existe no template alternativo usado quando invite é nulo.',
    scenario: 'Usuário autenticado com email diferente clica em aceitar; API retorna 403, botão volta ao normal e nenhuma mensagem aparece.',
    impact: 'Falha sem feedback em um fluxo de entrada importante.',
    fix: 'Renderizar o alerta fora dos dois estados ou dentro de ambos e limpar mensagens no início de cada ação.',
    verify: 'Teste de componente com invite preenchido + acceptInvite retornando 403.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-frontend/src/app/features/members/invite-accept/invite-accept.component.ts', 35, 'Erro é gravado no estado.'], ['devBoard-frontend/src/app/features/members/invite-accept/invite-accept.component.html', 1, 'Alerta só aparece no else quando invite é nulo.']],
    specs: [['docs/spec-members.md', 188, 'Fluxo prevê erros 403/409 que precisam de feedback.']]
  },
  {
    id: 'AUTH-02', severity: 'medium', type: 'divergence', module: 'Autenticação',
    title: 'Rate limit de recuperação não usa email + IP e validação não conta tentativa',
    summary: 'forgot-password usa somente request.email. reset-password não possui anotação; o contador interno só roda depois do @Valid do controller, então senhas fracas/confirmacão divergente não consomem tentativa.',
    scenario: 'Atacante distribui solicitações para muitos emails a partir do mesmo IP ou testa payloads inválidos sem consumir as cinco tentativas.',
    impact: 'Proteção inferior à especificada e comportamento difícil de explicar na banca.',
    fix: 'Criar resolvedor de chave composto email normalizado + IP confiável; para reset, decidir na spec o que é tentativa e aplicar limitador antes da validação de senha sem vazar o token.',
    verify: 'Testes de aspecto com emails/IPs distintos e payload inválido repetido.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/controller/PasswordResetController.java', 25, 'Chave contém apenas email.'], ['devBoard-backend/src/main/java/com/devboard/controller/PasswordResetController.java', 37, '@Valid ocorre antes de resetPassword.']],
    specs: [['docs/spec-authentication.md', 187, 'Limite é por email e por IP.'], ['docs/spec-authentication.md', 212, 'Máximo de cinco tentativas por token.']]
  },
  {
    id: 'AUTH-03', severity: 'medium', type: 'bug', module: 'Autenticação',
    title: 'Expiração de recuperação e convite não é persistida',
    summary: 'findPendingTokenOrThrow e assertInviteUsable marcam EXPIRED e lançam exceção runtime dentro da mesma transação, repetindo o rollback de AUTH-01.',
    scenario: 'Cada consulta de item vencido recalcula a expiração, mas o banco continua registrando PENDING indefinidamente.',
    impact: 'Auditoria e filtros por status ficam incorretos.',
    fix: 'Centralizar transição de expiração em REQUIRES_NEW ou rotina agendada idempotente; manter a validação temporal mesmo se o status ainda não foi limpo.',
    verify: 'Consultar o registro depois de validar token/convite vencido e exigir EXPIRED.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/PasswordResetService.java', 97, 'Altera status e lança InvalidRequestException.'], ['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 283, 'Mesmo padrão em convite.']],
    specs: [['docs/spec-authentication.md', 197, 'Marca token vencido como EXPIRED.'], ['docs/spec-members.md', 180, 'Valida status e prazo do convite.']]
  },
  {
    id: 'NOTIF-01', severity: 'high', type: 'gap', module: 'Notificações',
    title: 'Módulo de notificações não foi iniciado',
    summary: 'Não existem entidade, migration, repository, service, controller, preferências, scheduler nem tela de notificações.',
    scenario: 'Atribuições, menções, prazos e mudanças de papel não avisam ninguém.',
    impact: '14 critérios da spec ficam sem cobertura e vários fluxos de tarefas/membros estão incompletos.',
    fix: 'Implementar modelo + preferências, deduplicação/precedência centralizada, eventos AFTER_COMMIT, endpoints e scheduler idempotente de prazo/limpeza.',
    verify: 'Matriz de testes por tipo, autor, ex-membro, preferências e idempotência.',
    confidence: 'Confirmado por inventário integral',
    evidence: [['docs/spec-notifications.md', 122, 'Endpoints esperados não têm correspondentes no código.']],
    specs: [['docs/spec-notifications.md', 229, '14 critérios pendentes.']]
  },
  {
    id: 'LABEL-01', severity: 'high', type: 'gap', module: 'Labels e busca',
    title: 'Labels, busca paginada e filtros combinados não existem',
    summary: 'Não há entidade/migration/endpoints de label nem endpoint /tasks/search. O quadro implementa somente type, priority e search por título.',
    scenario: 'Não é possível classificar tarefas, buscar na descrição, filtrar sem responsável/atrasadas/PR ou usar atalhos.',
    impact: '15 critérios da spec pendentes; usabilidade cai rapidamente com volume de cards.',
    fix: 'Implementar Label/TaskLabel, queries Specification/Criteria, ordenação semântica de prioridade, paginação e filtro compartilhado com board.',
    verify: 'Testes combinatórios focados em AND, labelMatch all, overdue e limite 100.',
    confidence: 'Confirmado por inventário integral',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 105, 'Filtro atual cobre somente quatro campos e só título.'], ['docs/spec-labels-search.md', 38, 'Modelo e endpoints ausentes.']],
    specs: [['docs/spec-labels-search.md', 192, '15 critérios pendentes.']]
  },
  {
    id: 'ACT-01', severity: 'medium', type: 'gap', module: 'Atividades',
    title: 'Histórico cobre parte das tarefas, não o projeto inteiro',
    summary: 'Existe TaskActivity e evento AFTER_COMMIT para criação/edição/movimento/arquivamento e comentário criado. Editar/excluir comentário não publica evento; projetos, quadros, membros e GitHub não compartilham histórico de projeto.',
    scenario: 'Alterar papel, revogar convite, editar quadro ou excluir comentário não aparece no histórico pedido pela spec.',
    impact: 'Auditoria fragmentada e apresentação do histórico incompleta.',
    fix: 'Evoluir para Activity de projeto com referências opcionais e listeners por eventos de domínio; a visão da tarefa vira filtro do mesmo histórico.',
    verify: 'Teste por categoria da tabela da spec e garantia de imutabilidade.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/ActivityService.java', 25, 'Serviço atual é específico de TaskActivity.'], ['devBoard-backend/src/main/java/com/devboard/service/TaskCommentService.java', 79, 'Editar/excluir não publica atividade.']],
    specs: [['docs/spec-notifications.md', 61, 'Atividade deve ser do projeto e a de tarefa é uma visão filtrada.']]
  },
  {
    id: 'FRONT-05', severity: 'medium', type: 'gap', module: 'Frontend',
    title: 'Fluxos de projeto e quadro existem no backend, mas não na UI',
    summary: 'A interface não lista repositórios/branches, não cria quadros, não edita/exclui quadro e não reordena colunas. O formulário de projeto só possui nome e descrição.',
    scenario: 'A banca pergunta como criar um segundo quadro ou escolher repositório; não há caminho visual.',
    impact: 'Recursos implementados parcialmente ficam inacessíveis ao usuário final.',
    fix: 'Adicionar gestão de quadros e drag de colunas com papel; estender formulário após os endpoints GitHub existirem.',
    verify: 'E2E de criar/editar/excluir quadro e reordenar colunas.',
    confidence: 'Confirmado por inventário de rotas e templates',
    evidence: [['devBoard-frontend/src/app/features/projects/project-form/project-form.component.ts', 37, 'Formulário tem só name/description.'], ['devBoard-frontend/src/app/app.routes.ts', 4, 'Não há telas/rotas de gestão de quadro.']],
    specs: [['docs/spec-board-kanban.md', 94, 'CRUD/reordenação do quadro.'], ['docs/spec-projects.md', 133, 'Fluxos GitHub do projeto.']]
  },
  {
    id: 'TEST-01', severity: 'medium', type: 'gap', module: 'Qualidade',
    title: 'Cobertura fica abaixo dos 70% em backend e frontend',
    summary: 'JaCoCo mediu 63,56% de linhas e 54,93% de branches no backend. Karma mediu 66,88% de linhas, 58,38% de branches e 49,21% de funções no frontend.',
    scenario: 'As áreas sem teste incluem rate limiting, controllers, interceptors/services e vários caminhos de erro.',
    impact: 'A definição de pronto do próprio projeto não foi atingida.',
    fix: 'Priorizar testes que reproduzam os achados, controllers com MockMvc e services HTTP com HttpTestingController. Configurar limiar no JaCoCo/Karma/CI.',
    verify: 'Pipeline falha abaixo de 70% e publica relatórios de cobertura.',
    confidence: 'Confirmado por execução automatizada',
    evidence: [['docs/auditoria/evidencias/backend-verify.txt', 1, '121 testes passaram; relatório JaCoCo gerado.'], ['docs/auditoria/evidencias/frontend-tests.txt', 1, '61 testes passaram; cobertura registrada.']],
    specs: [['AGENTS.md', 660, 'Cobertura mínima definida como 70%.']]
  },
  {
    id: 'TEST-02', severity: 'medium', type: 'risk', module: 'Qualidade',
    title: 'Testes verdes escondem falhas de contrato e erros no console',
    summary: 'O teste de comentário afirma explicitamente que o autor não precisa passar por PermissionService. Os testes Angular terminam verdes enquanto registram NG04002 de navegação para rotas ausentes. Não existe suíte E2E/Cypress.',
    scenario: 'A equipe vê 182 testes passando, mas os comportamentos errados continuam protegidos como regressão desejada.',
    impact: 'Falsa confiança antes da apresentação.',
    fix: 'Derivar testes dos critérios de aceite, falhar em console.error/unhandled navigation, usar RouterTestingHarness e criar três E2E essenciais: login, tarefa, movimento.',
    verify: 'Os três bugs reproduzidos devem primeiro gerar testes vermelhos e depois verdes com a correção.',
    confidence: 'Confirmado por testes e logs',
    evidence: [['devBoard-backend/src/test/java/com/devboard/service/TaskCommentServiceTest.java', 146, 'Teste codifica a falha.'], ['docs/auditoria/evidencias/frontend-tests.txt', 1, 'Log contém NG04002 apesar de TOTAL: 61 SUCCESS.'], ['devBoard-frontend/package.json', 5, 'Não há script E2E.']],
    specs: [['AGENTS.md', 650, 'Testes devem cobrir critérios, erros e permissões.']]
  },
  {
    id: 'DATA-01', severity: 'high', type: 'risk', module: 'Concorrência',
    title: 'Posições e WIP não têm controle de concorrência',
    summary: 'Mover tarefa lê listas, confere WIP e regrava posições sem @Version, lock ou constraint única. Duas requisições simultâneas podem aceitar a última vaga ou produzir posições duplicadas/perdidas.',
    scenario: 'Dois devs soltam cards ao mesmo tempo em uma coluna com uma vaga; ambos contam antes do commit e entram.',
    impact: 'Limite WIP violado e ordem não determinística em uso real de equipe.',
    fix: 'Adicionar versionamento otimista ou lock pessimista nas colunas/tarefas durante movimento; constraint/normalização de posição e resposta 409 em conflito.',
    verify: 'Teste de concorrência com barreira executando dois moves simultâneos.',
    confidence: 'Risco forte por leitura; requer teste concorrente para reprodução',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/TaskService.java', 187, 'Read-check-write sem lock.'], ['devBoard-backend/src/main/java/com/devboard/entity/Task.java', 24, 'Entidade não possui @Version.']],
    specs: [['docs/spec-board-kanban.md', 72, 'WIP deve ser uma regra efetiva.']]
  },
  {
    id: 'DATA-02', severity: 'medium', type: 'risk', module: 'Banco',
    title: 'Invariantes importantes existem só no Java',
    summary: 'Não há garantia de exatamente um board padrão, unicidade de papel semântico, posição ou github_repo_id. Faltam índices em task_activities.author_id e project_invites.invited_by_id, contrariando a convenção de indexar FKs.',
    scenario: 'Concorrência ou manutenção manual pode criar dois boards padrão/roles repetidos; consultas de auditoria degradam com volume.',
    impact: 'Dados válidos dependem de uma única execução serial do service.',
    fix: 'Criar nova migration com índices/constraints apropriados, sem editar migrations aplicadas. Avaliar índices parciais PostgreSQL para default e semantic_role != NONE.',
    verify: 'Testes de migration tentando inserir violações e EXPLAIN das consultas frequentes.',
    confidence: 'Confirmado por leitura das migrations',
    evidence: [['devBoard-backend/src/main/resources/db/changelog/V3__create_projects_tables.sql', 60, 'boards não garante um padrão por projeto.'], ['devBoard-backend/src/main/resources/db/changelog/V4__create_tasks_tables.sql', 68, 'FK author_id sem índice.'], ['devBoard-backend/src/main/resources/db/changelog/V5__create_member_invites_tables.sql', 25, 'FK invited_by_id sem índice.']],
    specs: [['AGENTS.md', 440, 'Toda FK e filtro frequente deve ter índice.'], ['docs/spec-board-kanban.md', 46, 'Exatamente um quadro padrão.']]
  },
  {
    id: 'FRONT-06', severity: 'medium', type: 'divergence', module: 'Frontend',
    title: '401 remove token, mas não redireciona para login',
    summary: 'O ErrorInterceptor normaliza o erro e limpa localStorage. A rota atual continua renderizada até outra navegação e cada componente precisa lidar com o erro.',
    scenario: 'JWT expira enquanto usuário está no quadro: a tela mostra erro genérico, mas não abre login com returnUrl.',
    impact: 'Sessão expirada tem recuperação ruim e diverge do padrão escrito.',
    fix: 'Injetar Router no interceptor, evitar redirecionar chamadas públicas de login e navegar para /login com returnUrl uma única vez.',
    verify: 'Teste do interceptor com 401 e teste E2E de JWT expirado.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-frontend/src/app/core/interceptors/error.interceptor.ts', 16, 'Só remove o token.']],
    specs: [['AGENTS.md', 614, '401 deve limpar sessão e redirecionar.']]
  },
  {
    id: 'SPEC-01', severity: 'medium', type: 'conflict', module: 'Specs',
    title: 'Contrato 403 versus 404 está contraditório',
    summary: 'spec-projects exige 403 para usuário sem vínculo; AGENTS.md exige 404 para não revelar a existência. PermissionService implementa 404.',
    scenario: 'Um teste escrito a partir da spec falha; outro escrito a partir da constituição passa.',
    impact: 'Impossível afirmar conformidade sem decidir a fonte de verdade do comportamento.',
    fix: 'Escolher 404 como política anti-enumeração ou 403 como transparência e atualizar todos os documentos/testes. Minha recomendação é manter 404.',
    verify: 'Uma única tabela de autorização compartilhada por todas as specs e testes de contrato.',
    confidence: 'Conflito documental confirmado',
    evidence: [['AGENTS.md', 214, 'Sem vínculo → 404.'], ['docs/spec-projects.md', 105, 'Sem vínculo → 403.'], ['devBoard-backend/src/main/java/com/devboard/security/PermissionService.java', 48, 'Código segue 404.']],
    specs: [['docs/spec-projects.md', 238, 'Critério também exige 403.']]
  },
  {
    id: 'SPEC-02', severity: 'medium', type: 'conflict', module: 'Specs',
    title: '“Tarefa própria” versus “tarefa atribuída” no arquivamento',
    summary: 'A seção do endpoint permite criador ou responsável; a matriz diz Developer “apenas próprias”; o critério diz que Developer não arquiva tarefa de outro membro. PermissionService aceita criador ou responsável.',
    scenario: 'Developer responsável, mas não criador, arquiva; não está claro se o comportamento é correto.',
    impact: 'Teste e apresentação podem defender regras diferentes.',
    fix: 'Definir “própria” formalmente. Recomendo: criada OU atualmente atribuída, alinhado ao endpoint e edição; reescrever o critério.',
    verify: 'Casos criador, responsável, colaborador e terceiro na matriz de testes.',
    confidence: 'Conflito documental confirmado',
    evidence: [['docs/spec-tasks.md', 172, 'Endpoint permite criador ou responsável.'], ['docs/spec-tasks.md', 256, 'Matriz usa “apenas próprias”.'], ['devBoard-backend/src/main/java/com/devboard/security/PermissionService.java', 70, 'Código considera criador ou responsável.']],
    specs: [['docs/spec-tasks.md', 289, 'Critério usa “de outro membro”.']]
  },
  {
    id: 'SPEC-03', severity: 'low', type: 'divergence', module: 'Specs',
    title: 'Callback OAuth documentado como 200 JSON, implementado como 302 + fragmento',
    summary: 'A implementação é coerente com SPA, mas a spec descreve resposta 200 com JWT. O frontend depende do contrato de redirecionamento não documentado.',
    scenario: 'Uma integração/teste gerado pela spec espera JSON e falha.',
    impact: 'Contrato implícito e difícil de manter.',
    fix: 'Atualizar a spec para 302 ao callback frontend, detalhar fragmento e returnUrl permitido; ou mudar implementação para popup/JSON.',
    verify: 'Teste de contrato do Location e componente callback.',
    confidence: 'Confirmado por leitura',
    evidence: [['docs/spec-authentication.md', 134, 'Spec declara saída 200.'], ['devBoard-backend/src/main/java/com/devboard/controller/AuthController.java', 58, 'Controller devolve redirect 302.'], ['devBoard-frontend/src/app/features/auth/github-callback/github-callback.component.ts', 22, 'Cliente lê JWT do fragmento.']],
    specs: [['docs/spec-authentication.md', 146, 'Saída precisa ser corrigida.']]
  },
  {
    id: 'BOARD-04', severity: 'medium', type: 'gap', module: 'Quadro',
    title: 'Vocabulário de filtros do quadro está incompleto',
    summary: 'Endpoint aceita assigneeId, priority, type e search; não aceita label. A busca verifica somente título, enquanto a spec de busca compartilhada inclui descrição e filtros combinados.',
    scenario: 'Buscar palavra existente apenas na descrição não retorna a tarefa; filtros avançados não podem ser usados no board.',
    impact: 'Contrato parcial e diferença futura entre busca e quadro.',
    fix: 'Criar objeto TaskFilters único e query reutilizável; manter colunas vazias ao mapear resultado.',
    verify: 'Testes parametrizados do mesmo filtro no endpoint plano e no board.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/controller/BoardController.java', 47, 'Query expõe apenas quatro filtros.'], ['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 105, 'Busca somente task.title.']],
    specs: [['docs/spec-board-kanban.md', 110, 'Inclui label.'], ['docs/spec-labels-search.md', 147, 'Board compartilha vocabulário de filtros.']]
  },
  {
    id: 'BOARD-05', severity: 'medium', type: 'bug', module: 'Quadro',
    title: 'Excluir o quadro padrão não promove um substituto',
    summary: 'deleteBoard permite apagar o default quando há outro board e não marca nenhum restante como padrão.',
    scenario: 'Projeto com dois quadros exclui Main Board; ambos passam a ter is_default=false.',
    impact: 'Viola “exatamente um quadro padrão” e deixa automações sem destino canônico.',
    fix: 'Ao excluir default, promover deterministicamente o quadro mais antigo na mesma transação; reforçar com constraint.',
    verify: 'Teste de integração exige exatamente um default após exclusão.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 151, 'Não trata isDefault antes de excluir.']],
    specs: [['docs/spec-board-kanban.md', 46, 'Exatamente um padrão por projeto.']]
  },
  {
    id: 'OPS-01', severity: 'low', type: 'gap', module: 'Operação',
    title: 'Health check é liberado na segurança, mas não existe',
    summary: 'SecurityConfig permite /api/health, porém não há controller nem Actuator no pom.',
    scenario: 'Plataforma de deploy consulta o endpoint e recebe 404.',
    impact: 'Readiness/liveness e diagnóstico de apresentação ficam piores.',
    fix: 'Adicionar Actuator com exposição mínima ou HealthController que valide apenas a saúde essencial sem vazar configuração.',
    verify: 'GET /api/health retorna 200 e teste quando banco está indisponível.',
    confidence: 'Confirmado por inventário',
    evidence: [['devBoard-backend/src/main/java/com/devboard/config/SecurityConfig.java', 56, 'Rota está permitida.'], ['devBoard-backend/pom.xml', 1, 'Sem actuator.']],
    specs: [['AGENTS.md', 168, 'Lista /api/health como endpoint público.']]
  },
  {
    id: 'DOC-01', severity: 'low', type: 'conflict', module: 'Documentação',
    title: 'Guia aponta para docs/claude.md, mas o arquivo está na raiz',
    summary: 'docs/README.md e a árvore dentro de claude.md instruem uma localização inexistente. AGENTS.md está correto.',
    scenario: 'Aluno ou agente segue a ordem de leitura e não encontra o arquivo.',
    impact: 'Onboarding confuso e risco de implementar sem a constituição.',
    fix: 'Escolher um local único; recomendo raiz para AGENTS.md/claude.md e corrigir docs/README.md e a árvore.',
    verify: 'Validador de links Markdown no CI.',
    confidence: 'Confirmado por inventário',
    evidence: [['docs/README.md', 22, 'Menciona docs/claude.md.'], ['claude.md', 21, 'Árvore também coloca o próprio arquivo em docs.']],
    specs: []
  },
  {
    id: 'DESIGN-01', severity: 'low', type: 'divergence', module: 'Design system',
    title: 'Um estilo introduz cor hexadecimal fora dos tokens',
    summary: 'member-list usa #166534 diretamente, contrariando o checklist de nenhum hex novo e dificultando consistência de tema.',
    scenario: 'Alerta de sucesso fica com contraste inesperado no tema escuro ou futuro tema claro.',
    impact: 'Pequena dívida visual e de acessibilidade.',
    fix: 'Criar --color-success e variação suave no design system; medir contraste WCAG.',
    verify: 'Lint de cores e auditoria visual claro/escuro.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-frontend/src/app/features/members/member-list/member-list.component.scss', 7, 'Hex hardcoded.']],
    specs: [['docs/spec-design-system.md', 294, 'Checklist proíbe hex solto.']]
  },
  {
    id: 'VALID-01', severity: 'low', type: 'risk', module: 'Validação',
    title: 'Cor e limite WIP não têm validação de domínio',
    summary: 'DTOs aceitam qualquer string de cor e qualquer inteiro, inclusive negativo. O banco também não possui CHECK.',
    scenario: 'wipLimit=-1 faz qualquer entrada manual ser considerada cheia; cor inválida quebra a indicação visual.',
    impact: 'Estados impossíveis entram por chamada direta à API.',
    fix: 'Adicionar @Pattern para #RRGGBB, @Positive para WIP e CHECKs em migration nova.',
    verify: 'Testes de controller com valores negativos e cores inválidas retornando 400.',
    confidence: 'Confirmado por leitura',
    evidence: [['devBoard-backend/src/main/java/com/devboard/dto/board/CreateColumnRequest.java', 15, 'Sem @Pattern/@Positive.'], ['devBoard-backend/src/main/java/com/devboard/dto/board/UpdateColumnRequest.java', 15, 'Mesmo na edição.']],
    specs: [['docs/spec-board-kanban.md', 53, 'Cor hexadecimal e WIP numérico são dados de domínio.']]
  }
];

const modules = [
  { name: 'Autenticação', score: 70, status: 'Parcial com bloqueio de segurança', verified: 9, partial: 3, missing: 2, done: ['Registro/login JWT', 'BCrypt e senha forte', 'OAuth GitHub', 'recuperação por email', 'token GitHub AES-GCM'], todo: ['verificação de email', 'corrigir transações de expiração', 'vincular state ao browser', 'rate limit composto'] },
  { name: 'Projetos', score: 35, status: 'CRUD local funcional', verified: 3, partial: 1, missing: 7, done: ['criar/listar/detalhar/editar/arquivar', 'quadro padrão'], todo: ['repositórios', 'link/unlink', 'sync', 'webhook/importação', 'remover webhook ao arquivar'] },
  { name: 'Quadro', score: 65, status: 'Usável com falhas destrutivas', verified: 6, partial: 3, missing: 2, done: ['colunas e papéis', 'WIP no backend', 'visão agrupada', 'reordenação de tarefas'], todo: ['exclusões seguras', 'default invariável', 'filtros completos', 'concorrência'] },
  { name: 'Tarefas', score: 55, status: 'Núcleo local parcial', verified: 7, partial: 3, missing: 5, done: ['CRUD principal', 'movimento', 'comentários', 'atividade de tarefa', 'soft delete'], todo: ['bloquear arquivadas', 'branch/issues/PR', 'labels', 'notificações/menções', 'histórico completo'] },
  { name: 'Membros', score: 65, status: 'Convites funcionais, import parcial', verified: 8, partial: 4, missing: 2, done: ['email/link', 'aceite/revogação', 'papéis', 'remoção/saída', 'import inicial'], todo: ['incluir owner no contrato', 'paginação GitHub', 'auditoria/notificação', 'concorrência no aceite'] },
  { name: 'GitHub', score: 8, status: 'Somente autenticação e colaboradores', verified: 0, partial: 0, missing: 19, done: ['OAuth login', 'token cifrado', 'primeira página de colaboradores'], todo: ['todo o webhook', 'issues', 'branches', 'commits/PRs', 'sync/retry/idempotência'] },
  { name: 'Labels e busca', score: 10, status: 'Não iniciado; filtros primitivos', verified: 0, partial: 2, missing: 13, done: ['filtro board por tipo/prioridade/título'], todo: ['modelo/endpoints labels', 'busca paginada', 'filtros combinados', 'sync GitHub'] },
  { name: 'Notificações', score: 8, status: 'Não iniciado; atividade parcial', verified: 0, partial: 1, missing: 13, done: ['atividade de tarefa assíncrona'], todo: ['notificações', 'preferências', 'precedência', 'prazos', 'limpeza', 'atividade do projeto'] },
  { name: 'Design system', score: 78, status: 'Boa base visual', verified: 7, partial: 1, missing: 1, done: ['tokens', 'layout responsivo', 'foco', 'reduced motion', 'componentes consistentes'], todo: ['eliminar hex solto', 'testar contraste', 'tema claro futuro'] }
];

const flows = [
  { title: '1. Boot e segurança da requisição', text: 'Spring carrega configuração, Liquibase valida o schema e SecurityFilterChain instala o filtro JWT. O filtro extrai Bearer, valida assinatura/expiração, busca o usuário e popula SecurityContext; o controller recebe SecurityUser.', files: [['devBoard-backend/src/main/java/com/devboard/DevBoardApplication.java', 1], ['devBoard-backend/src/main/resources/application.yml', 1], ['devBoard-backend/src/main/java/com/devboard/config/SecurityConfig.java', 49], ['devBoard-backend/src/main/java/com/devboard/security/JwtAuthenticationFilter.java', 1]] },
  { title: '2. Registro, login e JWT', text: 'AuthController valida o DTO. AuthService aplica unicidade, BCrypt e emite JWT com userId/email/username. AuthInterceptor injeta o token nas chamadas Angular; 401 é normalizado pelo ErrorInterceptor.', files: [['devBoard-backend/src/main/java/com/devboard/controller/AuthController.java', 40], ['devBoard-backend/src/main/java/com/devboard/service/AuthService.java', 37], ['devBoard-backend/src/main/java/com/devboard/security/JwtTokenProvider.java', 32], ['devBoard-frontend/src/app/core/services/auth.service.ts', 27], ['devBoard-frontend/src/app/core/interceptors/auth.interceptor.ts', 5]] },
  { title: '3. OAuth GitHub', text: 'Frontend navega para /github/login. Backend cria state, GitHub devolve code, o cliente troca por token/perfil/email, resolve o usuário e redireciona à SPA com JWT no fragmento. Aqui estão SEC-01 e OAUTH-01.', files: [['devBoard-frontend/src/app/core/services/auth.service.ts', 37], ['devBoard-backend/src/main/java/com/devboard/service/github/GithubOAuthStateService.java', 25], ['devBoard-backend/src/main/java/com/devboard/service/github/GithubOAuthService.java', 48], ['devBoard-frontend/src/app/features/auth/github-callback/github-callback.component.ts', 22]] },
  { title: '4. Projeto nasce com quadro', text: 'ProjectService persiste Project com owner fora de project_members, chama BoardService.createDefaultBoard e retorna ProjectResponse. O mapper agrega membros e resumos dos quadros.', files: [['devBoard-backend/src/main/java/com/devboard/service/ProjectService.java', 48], ['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 42], ['devBoard-backend/src/main/java/com/devboard/mapper/ProjectMapper.java', 36], ['devBoard-frontend/src/app/features/projects/project-detail/project-detail.component.ts', 41]] },
  { title: '5. Abrir o Kanban', text: 'BoardController envia filtros ao service. Colunas são carregadas ordenadas; uma consulta traz tarefas de todas as colunas e outra agrega comentários. BoardMapper monta cards resumidos; Angular renderiza drop lists.', files: [['devBoard-backend/src/main/java/com/devboard/controller/BoardController.java', 47], ['devBoard-backend/src/main/java/com/devboard/service/BoardService.java', 100], ['devBoard-backend/src/main/java/com/devboard/repository/TaskRepository.java', 17], ['devBoard-backend/src/main/java/com/devboard/mapper/BoardMapper.java', 29], ['devBoard-frontend/src/app/features/board/board-view/board-view.component.ts', 77]] },
  { title: '6. Criar, editar, mover e arquivar tarefa', text: 'TaskService valida coluna e PermissionService, resolve participantes, persiste e publica eventos. move lê origem/destino, aplica WIP, reindexa, controla completedAt e publica MOVED. archive aplica soft delete.', files: [['devBoard-backend/src/main/java/com/devboard/service/TaskService.java', 56], ['devBoard-backend/src/main/java/com/devboard/security/PermissionService.java', 70], ['devBoard-backend/src/main/java/com/devboard/service/TaskService.java', 169], ['devBoard-backend/src/main/java/com/devboard/service/TaskService.java', 225], ['devBoard-frontend/src/app/features/board/task-detail/task-detail.component.ts', 124]] },
  { title: '7. Comentário e atividade depois do commit', text: 'Comentário é salvo e publica TaskActivityEvent. O listener só executa AFTER_COMMIT em pool próprio. ActivityService abre sua transação, converte metadata em JSON e captura qualquer exceção para não afetar a ação principal.', files: [['devBoard-backend/src/main/java/com/devboard/service/TaskCommentService.java', 48], ['devBoard-backend/src/main/java/com/devboard/async/TaskActivityListener.java', 21], ['devBoard-backend/src/main/java/com/devboard/service/ActivityService.java', 45], ['devBoard-backend/src/main/java/com/devboard/config/AsyncConfig.java', 25]] },
  { title: '8. Convite e participação', text: 'Admin cria convite aleatório, email é enviado em outro executor, consulta pública mostra contexto mínimo e o aceite cria ProjectMember. Na remoção/saída, atribuições ativas são limpas e eventos são publicados.', files: [['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 61], ['devBoard-backend/src/main/java/com/devboard/service/EmailService.java', 49], ['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 102], ['devBoard-backend/src/main/java/com/devboard/service/MemberService.java', 300], ['devBoard-frontend/src/app/features/members/member-list/member-list.component.ts', 50]] },
  { title: '9. Banco e migrations', text: 'Entidades são LAZY e migrations V1–V5 criam usuários, recuperação, projetos/quadros, tarefas/atividades e convites. ddl-auto=validate impede que Hibernate invente alterações fora do Liquibase.', files: [['devBoard-backend/src/main/resources/db/changelog/db.changelog-master.xml', 1], ['devBoard-backend/src/main/resources/db/changelog/V1__create_users_table.sql', 1], ['devBoard-backend/src/main/resources/db/changelog/V5__create_member_invites_tables.sql', 1], ['devBoard-backend/src/main/resources/application.yml', 1]] },
  { title: '10. Angular de rota a template', text: 'Rotas standalone são lazy-loaded e protegidas pelo AuthGuard. Components containers chamam services tipados, mantêm estado de loading/error e templates exibem o design system compartilhado.', files: [['devBoard-frontend/src/app/app.routes.ts', 4], ['devBoard-frontend/src/app/app.config.ts', 9], ['devBoard-frontend/src/app/core/guards/auth.guard.ts', 5], ['devBoard-frontend/src/app/core/services/task.service.ts', 1], ['devBoard-frontend/src/styles.scss', 1]] }
];

const strengths = [
  ['Arquitetura reconhecível', 'Controllers finos, regras nos services, repositories isolados e DTOs na fronteira.'],
  ['Segurança básica bem escolhida', 'BCrypt 10, JWT 24h, token de recuperação com SHA-256 e token GitHub em AES-GCM.'],
  ['Permissão central', 'PermissionService resolve owner como ADMIN e diferencia ausência de vínculo de papel insuficiente.'],
  ['Banco dirigido por migration', 'Liquibase V1–V5 aplicou e Hibernate validou o schema em PostgreSQL real.'],
  ['Leitura eficiente do board', 'Tarefas de todas as colunas vêm em uma consulta; contagem de comentários é agregada.'],
  ['Pós-commit assíncrono', 'Atividade é criada após commit e falha não reverte a operação principal.'],
  ['Frontend consistente', 'Standalone components, lazy loading, modelos espelhados, tokens visuais, foco visível e layout responsivo.'],
  ['Boa base de testes', '121 testes backend e 61 frontend passaram; são uma base útil, embora a cobertura e os contratos precisem crescer.']
];

const roadmap = [
  { phase: 'P0 — antes da banca', time: '1–2 dias', items: ['Corrigir SEC-01 ou desabilitar vínculo automático por email', 'Corrigir DEP-01 e validar deploy', 'Corrigir BOARD-01/02, TASK-01 e AUTH-01', 'Corrigir drag/WIP e owner nas opções', 'Adicionar testes de regressão dos casos reproduzidos'] },
  { phase: 'P1 — Kanban confiável', time: '3–5 dias', items: ['Bloquear mutações de arquivadas', 'Completar UI por papel', 'Promover novo board padrão', 'Locks/versionamento de move', 'Subir cobertura acima de 70% e 3 E2E'] },
  { phase: 'P2 — diferencial GitHub', time: '1–2 semanas', items: ['Vínculo de repositório', 'Webhook HMAC + delivery id', 'Import issues idempotente', 'Branch/push/PR', 'Retry e prevenção de laço'] },
  { phase: 'P3 — escala de uso', time: '4–6 dias', items: ['Labels e tabela de associação', 'Busca paginada', 'Filtros combinados', 'Atalhos da spec e sync de labels'] },
  { phase: 'P4 — comunicação', time: '5–7 dias', items: ['NotificationService central', 'Preferências e precedência', 'Menções/atribuições', 'Schedulers de prazo e retenção', 'Histórico único do projeto'] },
  { phase: 'P5 — entrega', time: '2–4 dias', items: ['CI, cobertura e lint', 'Health check', 'Ambiente de demo seedado', 'Revisar specs conflitantes', 'Roteiro gravado de contingência'] }
];

const decisions = [
  ['Sem vínculo: 404 ou 403?', 'Recomendação: 404 para evitar enumeração; atualizar spec-projects que hoje pede 403.'],
  ['O que significa tarefa “própria”?', 'Recomendação: criada OU atribuída. Colaborador não ganha poder de arquivar.'],
  ['PUT integral ou atualização parcial?', 'O código implementa substituição integral. Escrever isso claramente na spec ou trocar para PATCH.'],
  ['Tarefas arquivadas podem ser consultadas?', 'Recomendação: leitura apenas em endpoint explícito; nenhuma mutação sem restauração.'],
  ['Excluir coluna move arquivadas?', 'Recomendação: manter referência histórica movendo todas para destino, com evento administrativo.'],
  ['Callback OAuth 200 ou 302?', 'Recomendação para SPA atual: documentar 302 e validar returnUrl interno.']
];

const demo = [
  ['0:00–0:45', 'Problema', '“Equipes pequenas dividem o trabalho entre Kanban e GitHub; o devBoard centraliza contexto.”'],
  ['0:45–1:30', 'Arquitetura', 'Angular → REST → Service/Permission → JPA/PostgreSQL; eventos AFTER_COMMIT.'],
  ['1:30–4:30', 'Demo segura', 'Registrar/login → criar projeto → abrir Main Board → criar tarefa → editar → mover → comentar → convidar membro.'],
  ['4:30–5:30', 'Decisões técnicas', 'Soft delete, DTOs, owner fora de members, papéis semânticos, WIP, token cifrado.'],
  ['5:30–6:30', 'Qualidade', '182 testes verdes, migrations e build; explicar que a auditoria encontrou gaps e gerou roadmap.'],
  ['6:30–7:00', 'Limites honestos', 'Integração GitHub de negócio, labels e notificações são próximas entregas; OAuth não equivale a webhook.']
];

function escapeHtml(value = '') {
  return String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char]);
}

function sourceHref(file, line = 1) {
  return '../../' + file.split(path.sep).join('/') + '#L' + line;
}

function walk(dir, result = []) {
  if (!fs.existsSync(dir)) return result;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (['node_modules', 'target', 'dist', '.angular', 'coverage', '.git'].includes(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, result);
    else result.push(full);
  }
  return result;
}

const selected = [
  path.join(root, 'AGENTS.md'), path.join(root, 'claude.md'), path.join(root, 'README.md'),
  path.join(root, 'devBoard-backend', 'pom.xml'), path.join(root, 'devBoard-frontend', 'package.json'),
  path.join(root, 'devBoard-frontend', 'angular.json'), path.join(root, 'devBoard-frontend', 'tsconfig.json'),
  ...walk(path.join(root, 'docs')).filter(f => /spec-.*\.md$|README\.md$/.test(f) && !f.includes(`${path.sep}auditoria${path.sep}`)),
  ...walk(path.join(root, 'devBoard-backend', 'src')).filter(f => /\.(java|sql|xml|ya?ml)$/.test(f)),
  ...walk(path.join(root, 'devBoard-frontend', 'src')).filter(f => /\.(ts|html|scss)$/.test(f))
].filter(f => fs.existsSync(f));

function classify(relative) {
  const p = relative.replaceAll('\\', '/');
  if (p.startsWith('docs/spec-')) return ['Spec', 'Fonte de verdade do comportamento e critérios de aceite.'];
  if (p.includes('/controller/')) return ['Controller', 'Fronteira HTTP: rota, validação de entrada e status.'];
  if (p.includes('/service/github/')) return ['GitHub', 'Cliente/orquestração da integração externa com GitHub.'];
  if (p.includes('/service/')) return ['Service', 'Regra de negócio, permissão, transação e orquestração.'];
  if (p.includes('/repository/')) return ['Repository', 'Acesso JPA e consultas do domínio.'];
  if (p.includes('/entity/enums/')) return ['Enum', 'Vocabulário fechado e comportamento do domínio.'];
  if (p.includes('/entity/')) return ['Entity', 'Modelo persistente JPA e relacionamentos.'];
  if (p.includes('/dto/')) return ['DTO', 'Contrato de entrada/saída da API.'];
  if (p.includes('/mapper/')) return ['Mapper', 'Converte entidades em DTOs sem vazar JPA.'];
  if (p.includes('/security/')) return ['Security', 'JWT, principal autenticado e autorização.'];
  if (p.includes('/ratelimit/')) return ['Rate limit', 'Limite de uso com aspecto e cache local.'];
  if (p.includes('/validation/')) return ['Validation', 'Validações Bean Validation reutilizáveis.'];
  if (p.includes('/async/')) return ['Async', 'Listener assíncrono executado depois do commit.'];
  if (p.includes('/config/')) return ['Config', 'Configuração Spring de segurança e executores.'];
  if (p.includes('/db/changelog/')) return ['Migration', 'Evolução versionada do schema PostgreSQL.'];
  if (p.includes('/src/test/') || p.endsWith('.spec.ts')) return ['Test', 'Especificação executável/regressão automatizada.'];
  if (p.endsWith('.component.ts')) return ['Component', 'Container Angular: estado, eventos e chamadas de serviço.'];
  if (p.endsWith('.component.html')) return ['Template', 'Estrutura e binding visual do componente.'];
  if (p.endsWith('.component.scss') || p.includes('/styles/')) return ['Style', 'Apresentação, tokens e responsividade.'];
  if (p.includes('/core/services/')) return ['Front service', 'Cliente HTTP tipado para a API.'];
  if (p.includes('/core/models/')) return ['Front model', 'Contrato TypeScript espelhado do backend.'];
  if (p.includes('/guards/')) return ['Guard', 'Controle de navegação Angular.'];
  if (p.includes('/interceptors/')) return ['Interceptor', 'Tratamento transversal das requisições HTTP.'];
  if (p.endsWith('.ts')) return ['TypeScript', 'Código Angular/TypeScript de suporte.'];
  if (p.endsWith('.scss')) return ['Style', 'Folha de estilo SCSS.'];
  return ['Config/docs', 'Configuração, documentação ou ponto de entrada.'];
}

function symbols(text, ext) {
  const found = [];
  const patterns = ext === '.java'
    ? [/\b(?:class|interface|enum|record)\s+(\w+)/g, /\b(?:public|protected|private)\s+(?:static\s+)?(?:[\w<>?,.\[\]]+\s+)+(\w+)\s*\(/g]
    : [/\b(?:class|interface|type|enum)\s+(\w+)/g, /\b(?:export\s+)?(?:const|function)\s+(\w+)/g];
  for (const regex of patterns) {
    for (const match of text.matchAll(regex)) {
      if (!found.includes(match[1]) && !['if', 'for', 'while', 'switch'].includes(match[1])) found.push(match[1]);
      if (found.length >= 7) return found;
    }
  }
  return found;
}

const atlas = [...new Set(selected.map(f => path.resolve(f)))].map(full => {
  const text = fs.readFileSync(full, 'utf8');
  const relative = path.relative(root, full).replaceAll('\\', '/');
  const [layer, purpose] = classify(relative);
  return { relative, lines: text.split(/\r?\n/).length, layer, purpose, symbols: symbols(text, path.extname(full)) };
}).sort((a, b) => a.layer.localeCompare(b.layer) || a.relative.localeCompare(b.relative));

const counts = findings.reduce((acc, item) => { acc[item.severity]++; return acc; }, { critical: 0, high: 0, medium: 0, low: 0 });
const sourceLines = atlas.reduce((sum, item) => sum + item.lines, 0);
const criteria = modules.reduce((acc, item) => ({ verified: acc.verified + item.verified, partial: acc.partial + item.partial, missing: acc.missing + item.missing }), { verified: 0, partial: 0, missing: 0 });
const totalCriteria = criteria.verified + criteria.partial + criteria.missing;

function evidenceList(items = []) {
  return items.map(([file, line, note]) => `<li><a class="source-link" href="${sourceHref(file, line)}"><code>${escapeHtml(file)}:${line}</code></a><span>${escapeHtml(note)}</span></li>`).join('');
}

function findingCard(f) {
  const spec = f.specs?.length ? `<div class="finding-block"><h4>Base de comparação</h4><ul class="evidence">${evidenceList(f.specs)}</ul></div>` : '';
  return `<article class="finding-card severity-${f.severity}" data-severity="${f.severity}" data-type="${f.type}" data-module="${escapeHtml(f.module)}" data-search="${escapeHtml([f.id, f.title, f.summary, f.module, f.type].join(' ').toLowerCase())}">
    <div class="finding-top"><div><span class="finding-id">${f.id}</span><span class="tag">${escapeHtml(f.module)}</span><span class="tag tag-type">${escapeHtml(f.type)}</span>${f.reproduced ? '<span class="tag tag-reproduced">● reproduzido</span>' : ''}</div><span class="severity-badge">${{critical:'crítica',high:'alta',medium:'média',low:'baixa'}[f.severity]}</span></div>
    <h3>${escapeHtml(f.title)}</h3><p class="finding-summary">${escapeHtml(f.summary)}</p>
    <details><summary>Diagnóstico, impacto e correção</summary><div class="finding-grid">
      <div class="finding-block"><h4>Cenário</h4><p>${escapeHtml(f.scenario)}</p></div>
      <div class="finding-block"><h4>Impacto</h4><p>${escapeHtml(f.impact)}</p></div>
      <div class="finding-block fix"><h4>Como corrigir</h4><p>${escapeHtml(f.fix)}</p></div>
      <div class="finding-block"><h4>Como provar a correção</h4><p>${escapeHtml(f.verify)}</p></div>
      <div class="finding-block full"><h4>Evidências</h4><ul class="evidence">${evidenceList(f.evidence)}</ul></div>${spec}
      <div class="confidence full">Confiança: ${escapeHtml(f.confidence)}</div>
    </div></details>
  </article>`;
}

const html = `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Auditoria técnica devBoard — TCC</title>
<style>
:root{color-scheme:dark;--bg:#07100d;--shell:#0c1713;--card:#111e19;--card2:#14251e;--border:#263b32;--text:#f3f8f5;--muted:#9fb2a9;--dim:#70837a;--accent:#21e6a1;--accent2:#82ffd1;--danger:#ff6475;--warning:#ffbd52;--info:#65a9ff;--purple:#b59cff;--shadow:0 24px 70px rgba(0,0,0,.25);--radius:18px;--sidebar:274px}
[data-theme="light"]{color-scheme:light;--bg:#edf5f1;--shell:#e2eee8;--card:#fff;--card2:#f4faf7;--border:#cbded4;--text:#102019;--muted:#536a5f;--dim:#71877c;--accent:#087d58;--accent2:#075f44;--danger:#c52d45;--warning:#956000;--info:#195faf;--purple:#6548b5;--shadow:0 18px 50px rgba(17,54,38,.11)}
*{box-sizing:border-box}html{scroll-behavior:smooth;max-width:100%;overflow-x:hidden}body{margin:0;max-width:100%;overflow-x:hidden;background:radial-gradient(circle at 75% -10%,rgba(33,230,161,.12),transparent 30%),var(--bg);color:var(--text);font-family:Inter,ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;line-height:1.58}a{color:var(--accent2)}button,input,select{font:inherit}button{cursor:pointer}.skip{position:fixed;left:12px;top:-80px;background:var(--accent);color:#06120d;padding:10px 14px;border-radius:10px;z-index:999}.skip:focus{top:12px}:focus-visible{outline:3px solid var(--accent);outline-offset:3px}.layout{display:grid;grid-template-columns:var(--sidebar) minmax(0,1fr);min-height:100vh;max-width:100%}.sidebar{position:fixed;inset:0 auto 0 0;width:var(--sidebar);padding:24px 18px;background:rgba(12,23,19,.94);border-right:1px solid var(--border);backdrop-filter:blur(18px);overflow:auto;z-index:10}.brand{display:flex;align-items:center;gap:11px;margin-bottom:24px}.brand-mark{width:38px;height:38px;border-radius:12px;background:linear-gradient(145deg,var(--accent),#0ca86f);color:#06120d;display:grid;place-items:center;font-weight:900;box-shadow:0 0 30px rgba(33,230,161,.25)}.brand strong{display:block}.brand small{color:var(--muted)}.nav-group{font-size:11px;text-transform:uppercase;letter-spacing:.12em;color:var(--dim);margin:22px 10px 8px}.nav a{display:flex;align-items:center;gap:10px;padding:9px 11px;border-radius:10px;color:var(--muted);text-decoration:none;font-size:13px}.nav a:hover,.nav a.active{background:var(--card2);color:var(--text)}.nav-dot{width:7px;height:7px;border-radius:50%;background:var(--border)}.nav a.active .nav-dot{background:var(--accent);box-shadow:0 0 10px var(--accent)}.main{grid-column:2;min-width:0;max-width:100%}.top-actions{position:sticky;top:0;z-index:8;display:flex;justify-content:flex-end;gap:8px;padding:14px 28px;background:linear-gradient(var(--bg) 60%,transparent);pointer-events:none}.icon-button{pointer-events:auto;flex:0 0 auto;border:1px solid var(--border);background:var(--card);color:var(--text);padding:8px 12px;border-radius:11px;white-space:nowrap}.content{width:min(1240px,calc(100% - 56px));max-width:100%;margin:0 auto 100px}.hero{padding:60px 0 34px}.eyebrow{margin:0 0 14px;color:var(--accent);font-size:12px;font-weight:800;letter-spacing:.16em;text-transform:uppercase}.hero h1{font-size:clamp(38px,6vw,78px);line-height:1.02;letter-spacing:-.055em;margin:0;max-width:900px;overflow-wrap:anywhere}.hero .lead{max-width:850px;color:var(--muted);font-size:18px;margin:24px 0}.audit-meta{display:flex;flex-wrap:wrap;gap:10px}.tag,.meta-chip{display:inline-flex;align-items:center;border:1px solid var(--border);background:var(--card2);color:var(--muted);border-radius:999px;padding:4px 9px;font-size:11px}.meta-chip{padding:8px 12px;font-size:12px}.section{padding:42px 0;scroll-margin-top:40px;min-width:0}.section-head{display:flex;justify-content:space-between;gap:20px;align-items:end;margin-bottom:20px}.section h2{font-size:clamp(26px,4vw,42px);letter-spacing:-.035em;margin:0}.section-intro{color:var(--muted);max-width:760px;margin:8px 0 0}.metric-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}.metric{background:linear-gradient(160deg,var(--card2),var(--card));border:1px solid var(--border);border-radius:var(--radius);padding:18px;min-height:122px}.metric-label{color:var(--muted);font-size:12px}.metric-value{display:block;font-size:34px;line-height:1.2;font-weight:800;letter-spacing:-.04em;margin-top:9px}.metric small{color:var(--dim)}.metric.critical .metric-value{color:var(--danger)}.metric.high .metric-value{color:var(--warning)}.metric.good .metric-value{color:var(--accent)}.verdict{margin-top:16px;border-left:4px solid var(--warning);background:var(--card);padding:18px 20px;border-radius:0 var(--radius) var(--radius) 0}.verdict strong{color:var(--warning)}.card-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.card{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:20px;box-shadow:var(--shadow)}.strength{display:flex;gap:14px}.strength-icon{width:34px;height:34px;flex:0 0 auto;border-radius:11px;background:rgba(33,230,161,.12);display:grid;place-items:center;color:var(--accent)}.strength h3,.module h3{margin:0 0 5px;font-size:15px}.strength p{margin:0;color:var(--muted);font-size:13px}.module-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:13px}.module{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:18px}.module-top{display:flex;align-items:start;justify-content:space-between;gap:12px}.score{font-weight:800;color:var(--accent)}.bar{height:7px;background:var(--shell);border-radius:99px;overflow:hidden;margin:13px 0}.bar span{display:block;height:100%;background:linear-gradient(90deg,var(--accent),var(--info));border-radius:inherit}.module-status{color:var(--muted);font-size:12px}.criteria-line{display:flex;gap:8px;font-size:11px;margin:10px 0}.criteria-line .ok{color:var(--accent)}.criteria-line .partial{color:var(--warning)}.criteria-line .miss{color:var(--danger)}.mini-list{margin:9px 0 0;padding-left:18px;color:var(--muted);font-size:12px}.mini-list strong{color:var(--text)}.filters{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 16px}.filters input,.filters select,.atlas-controls input,.atlas-controls select{border:1px solid var(--border);background:var(--card);color:var(--text);padding:10px 12px;border-radius:11px;min-width:0;max-width:100%}.filters input{flex:1;min-width:220px}.filter-button{border:1px solid var(--border);background:var(--card);color:var(--muted);border-radius:11px;padding:9px 11px}.filter-button.active{background:var(--accent);border-color:var(--accent);color:#06120d}.finding-list{display:grid;gap:12px}.finding-card{background:var(--card);border:1px solid var(--border);border-left:5px solid var(--dim);border-radius:var(--radius);padding:19px 21px;min-width:0}.finding-card.hidden{display:none}.severity-critical{border-left-color:var(--danger)}.severity-high{border-left-color:var(--warning)}.severity-medium{border-left-color:var(--info)}.severity-low{border-left-color:var(--dim)}.finding-top{display:flex;justify-content:space-between;align-items:center;gap:14px}.finding-id{font:700 12px ui-monospace,SFMono-Regular,Consolas,monospace;color:var(--text);margin-right:7px}.tag-type{color:var(--info)}.tag-reproduced{color:var(--danger)}.severity-badge{font-size:11px;font-weight:800;text-transform:uppercase;letter-spacing:.08em}.severity-critical .severity-badge{color:var(--danger)}.severity-high .severity-badge{color:var(--warning)}.severity-medium .severity-badge{color:var(--info)}.finding-card h3{font-size:19px;margin:12px 0 6px}.finding-summary{color:var(--muted);margin:0}.finding-card details{margin-top:13px}.finding-card summary{cursor:pointer;color:var(--accent);font-size:13px}.finding-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:15px}.finding-block{background:var(--card2);border-radius:13px;padding:13px;min-width:0}.finding-block.full,.confidence.full{grid-column:1/-1}.finding-block h4{font-size:11px;text-transform:uppercase;letter-spacing:.09em;color:var(--dim);margin:0 0 6px}.finding-block p{margin:0;color:var(--muted);font-size:13px}.finding-block.fix{border:1px solid rgba(33,230,161,.28)}.evidence{list-style:none;margin:0;padding:0;display:grid;gap:7px}.evidence li{display:flex;gap:10px;align-items:start;font-size:12px;color:var(--muted);min-width:0}.evidence code{font-size:11px;overflow-wrap:anywhere}.source-link{color:var(--accent2);text-decoration:none;overflow-wrap:anywhere;min-width:0}.confidence{font-size:11px;color:var(--dim)}.flow-list{display:grid;gap:12px}.flow{display:grid;grid-template-columns:280px 1fr;gap:20px;background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:18px}.flow h3{margin:0;font-size:16px}.flow p{margin:0 0 10px;color:var(--muted);font-size:13px}.file-pills{display:flex;flex-wrap:wrap;gap:6px}.file-pills a{font:11px ui-monospace,SFMono-Regular,Consolas,monospace;background:var(--card2);border:1px solid var(--border);padding:4px 7px;border-radius:8px;text-decoration:none;overflow-wrap:anywhere}.roadmap{display:grid;gap:12px}.phase{display:grid;grid-template-columns:190px 100px 1fr;gap:16px;align-items:start;background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:17px}.phase h3{margin:0;font-size:15px}.phase-time{color:var(--accent);font-size:12px}.phase ul{margin:0;padding-left:18px;color:var(--muted);font-size:13px}.decision-table,.demo-table{width:100%;border-collapse:separate;border-spacing:0;background:var(--card);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden}.decision-table th,.decision-table td,.demo-table th,.demo-table td{text-align:left;padding:13px 15px;border-bottom:1px solid var(--border);font-size:13px;vertical-align:top}.decision-table tr:last-child td,.demo-table tr:last-child td{border-bottom:0}.decision-table th,.demo-table th{color:var(--dim);font-size:11px;text-transform:uppercase;letter-spacing:.08em}.decision-table td:last-child,.demo-table td:last-child{color:var(--muted)}.atlas-controls{display:flex;gap:8px;margin-bottom:12px}.atlas-controls input{flex:1}.atlas-wrap{border:1px solid var(--border);border-radius:var(--radius);overflow:auto;max-height:680px;background:var(--card);max-width:100%}.atlas{width:100%;border-collapse:collapse}.atlas th{position:sticky;top:0;background:var(--shell);z-index:1;text-align:left;color:var(--dim);font-size:11px;text-transform:uppercase;letter-spacing:.08em;padding:11px}.atlas td{padding:10px 11px;border-top:1px solid var(--border);font-size:12px;vertical-align:top}.atlas tr.hidden{display:none}.atlas-path{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;white-space:nowrap}.atlas-purpose{color:var(--muted);min-width:260px}.symbols{color:var(--dim);font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:11px}.test-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.test-card{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:19px}.test-card h3{margin:0 0 12px}.coverage{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.coverage div{background:var(--card2);padding:10px;border-radius:10px}.coverage strong{display:block;font-size:22px}.coverage span{font-size:11px;color:var(--muted)}.warning-box{background:rgba(255,189,82,.08);border:1px solid rgba(255,189,82,.28);padding:14px;border-radius:13px;color:var(--muted);font-size:13px;margin-top:12px}.footer{color:var(--dim);font-size:12px;border-top:1px solid var(--border);padding:28px 0;overflow-wrap:anywhere}.mobile-menu{display:none}
@media(max-width:1080px){.metric-grid{grid-template-columns:repeat(3,1fr)}.module-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:820px){.layout{display:block}.sidebar{transform:translateX(-100%);transition:.2s}.sidebar.open{transform:none}.main{display:block;width:100%}.mobile-menu{display:inline-flex}.content{width:calc(100% - 30px);max-width:1240px}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.module-grid,.card-grid,.test-grid{grid-template-columns:minmax(0,1fr)}.flow,.phase{grid-template-columns:minmax(0,1fr)}.finding-grid{grid-template-columns:minmax(0,1fr)}.finding-block.full,.confidence.full{grid-column:auto}.top-actions{padding:10px 15px;flex-wrap:wrap}.hero{padding-top:38px}.atlas-controls{flex-direction:column}.decision-table,.demo-table{display:block;overflow-x:auto;max-width:100%}}
@media(max-width:520px){.metric-grid{grid-template-columns:minmax(0,1fr)}.finding-top,.section-head{align-items:start;flex-direction:column}.hero h1{font-size:clamp(34px,11vw,42px)}.coverage{grid-template-columns:1fr}.evidence li{display:block}.top-actions{gap:6px}.icon-button{font-size:13px;padding:7px 9px}}
@media print{.sidebar,.top-actions,.filters,.atlas-controls{display:none!important}.layout{display:block}.main{display:block}.content{width:100%;margin:0}.section{break-inside:avoid}.finding-card{break-inside:avoid;box-shadow:none}.finding-card details{display:block}.finding-card details>div{display:grid}.atlas-wrap{max-height:none;overflow:visible}.atlas th{position:static}body{background:#fff;color:#111}a{color:#0645ad}}
</style></head>
<body><a class="skip" href="#main">Ir para o conteúdo</a><div class="layout">
<aside class="sidebar" id="sidebar"><div class="brand"><div class="brand-mark">dB</div><div><strong>devBoard Audit</strong><small>Spec-driven · TCC</small></div></div><nav class="nav">
${[['resumo','Resumo executivo'],['maturidade','Maturidade por módulo'],['achados','Mapa de falhas'],['arquitetura','Ponta a ponta'],['testes','Testes e evidências'],['pendencias','O que falta'],['decisoes','Conflitos de regra'],['estudo','Guia de estudo'],['atlas','Atlas de cada arquivo'],['apresentacao','Roteiro da banca']].map(([id,label])=>`<a href="#${id}"><span class="nav-dot"></span>${label}</a>`).join('')}
</nav><div class="nav-group">Legenda</div><div class="mini-list"><div><span style="color:var(--danger)">●</span> crítica</div><div><span style="color:var(--warning)">●</span> alta</div><div><span style="color:var(--info)">●</span> média</div><div><span style="color:var(--dim)">●</span> baixa</div></div></aside>
<main class="main" id="main"><div class="top-actions"><button class="icon-button mobile-menu" id="menuButton" aria-label="Abrir menu">☰</button><button class="icon-button" id="themeButton">◐ Tema</button><button class="icon-button" onclick="window.print()">↗ Imprimir / PDF</button></div><div class="content">
<header class="hero"><p class="eyebrow">Auditoria técnica independente · ${new Date().toLocaleDateString('pt-BR')}</p><h1>O devBoard, sem pontos cegos.</h1><p class="lead">Leitura ponta a ponta do código, comparação com 9 especificações, execução de builds e 182 testes, validação em PostgreSQL real e reprodução de falhas pela API. O objetivo é você dominar o projeto e saber exatamente o que pode defender na apresentação.</p><div class="audit-meta"><span class="meta-chip">${atlas.length} arquivos catalogados</span><span class="meta-chip">${sourceLines.toLocaleString('pt-BR')} linhas inventariadas</span><span class="meta-chip">122 critérios de aceite</span><span class="meta-chip">PostgreSQL isolado</span><span class="meta-chip">HTML offline</span></div></header>

<section class="section" id="resumo"><div class="section-head"><div><p class="eyebrow">01 · Diagnóstico</p><h2>Resumo executivo</h2><p class="section-intro">O núcleo de Kanban local existe e é demonstrável. O MVP descrito nas specs ainda não está concluído, e quatro correções devem preceder qualquer publicação.</p></div></div>
<div class="metric-grid"><div class="metric critical"><span class="metric-label">Críticas</span><span class="metric-value">${counts.critical}</span><small>segurança</small></div><div class="metric high"><span class="metric-label">Altas</span><span class="metric-value">${counts.high}</span><small>bloqueiam entrega</small></div><div class="metric"><span class="metric-label">Médias</span><span class="metric-value">${counts.medium}</span><small>regras e qualidade</small></div><div class="metric"><span class="metric-label">Baixas</span><span class="metric-value">${counts.low}</span><small>dívida controlável</small></div><div class="metric good"><span class="metric-label">Testes verdes</span><span class="metric-value">182</span><small>121 back + 61 front</small></div><div class="metric"><span class="metric-label">Critérios provados</span><span class="metric-value">${Math.round(criteria.verified/totalCriteria*100)}%</span><small>${criteria.verified}/${totalCriteria}; ${criteria.partial} parciais</small></div></div>
<div class="verdict"><strong>Veredito para a banca:</strong> apresente como “Kanban colaborativo local com autenticação, projetos, quadros, tarefas, comentários, atividades e membros; OAuth e importação inicial de colaboradores GitHub em andamento”. Não apresente ainda como integração bidirecional com GitHub. Antes de disponibilizar publicamente, trate SEC-01, DEP-01, AUTH-01 e as exclusões 500.</div>
<div class="section-head" style="margin-top:28px"><div><h2 style="font-size:25px">O que está bem construído</h2></div></div><div class="card-grid">${strengths.map(([title,text],i)=>`<article class="card strength"><div class="strength-icon">${i+1}</div><div><h3>${escapeHtml(title)}</h3><p>${escapeHtml(text)}</p></div></article>`).join('')}</div></section>

<section class="section" id="maturidade"><div class="section-head"><div><p class="eyebrow">02 · Rastreabilidade</p><h2>Maturidade por módulo</h2><p class="section-intro">Percentuais são uma avaliação técnica baseada em código + critérios, não cobertura de testes. “Verificado” exige evidência direta; “parcial” não conta como concluído.</p></div></div><div class="module-grid">${modules.map(m=>`<article class="module"><div class="module-top"><div><h3>${escapeHtml(m.name)}</h3><span class="module-status">${escapeHtml(m.status)}</span></div><span class="score">${m.score}%</span></div><div class="bar"><span style="width:${m.score}%"></span></div><div class="criteria-line"><span class="ok">✓ ${m.verified}</span><span class="partial">◐ ${m.partial}</span><span class="miss">× ${m.missing}</span></div><ul class="mini-list"><li><strong>Existe:</strong> ${escapeHtml(m.done.join(', '))}</li><li><strong>Falta:</strong> ${escapeHtml(m.todo.join(', '))}</li></ul></article>`).join('')}</div></section>

<section class="section" id="achados"><div class="section-head"><div><p class="eyebrow">03 · Findings</p><h2>Mapa de falhas e soluções</h2><p class="section-intro">Cada item liga sintoma, impacto, correção, teste e linha de código. Use os filtros para montar sua pauta de correção.</p></div><span id="findingCount" class="meta-chip">${findings.length} visíveis</span></div><div class="filters"><input id="findingSearch" type="search" placeholder="Buscar por id, módulo ou palavra…" aria-label="Buscar achados"><button class="filter-button active" data-filter="all">Todos</button><button class="filter-button" data-filter="critical">Crítica</button><button class="filter-button" data-filter="high">Alta</button><button class="filter-button" data-filter="medium">Média</button><button class="filter-button" data-filter="low">Baixa</button><select id="typeFilter" aria-label="Filtrar por tipo"><option value="all">Todos os tipos</option>${[...new Set(findings.map(f=>f.type))].map(t=>`<option value="${t}">${t}</option>`).join('')}</select></div><div class="finding-list" id="findingList">${findings.map(findingCard).join('')}</div></section>

<section class="section" id="arquitetura"><div class="section-head"><div><p class="eyebrow">04 · Como o sistema funciona</p><h2>Leitura ponta a ponta</h2><p class="section-intro">Siga estes fluxos na ordem. Eles cobrem a passagem de uma requisição pela arquitetura e mostram onde cada regra mora.</p></div></div><div class="flow-list">${flows.map(flow=>`<article class="flow"><h3>${escapeHtml(flow.title)}</h3><div><p>${escapeHtml(flow.text)}</p><div class="file-pills">${flow.files.map(([file,line])=>`<a href="${sourceHref(file,line)}">${escapeHtml(path.basename(file))}:${line}</a>`).join('')}</div></div></article>`).join('')}</div></section>

<section class="section" id="testes"><div class="section-head"><div><p class="eyebrow">05 · Evidência executável</p><h2>Testes, build e reproduções</h2><p class="section-intro">Tudo abaixo foi executado nesta auditoria em 18/09/2026, sem usar o banco normal do projeto.</p></div></div><div class="test-grid"><article class="test-card"><h3>Backend · Maven + PostgreSQL</h3><div class="coverage"><div><strong>121</strong><span>testes passaram</span></div><div><strong>63,56%</strong><span>linhas</span></div><div><strong>54,93%</strong><span>branches</span></div></div><div class="warning-box">Build SUCCESS, Liquibase com 13 changesets e Hibernate validate. A cobertura está abaixo dos 70% exigidos.</div><p><a class="source-link" href="evidencias/backend-verify.txt">Abrir log completo</a></p></article><article class="test-card"><h3>Frontend · Angular/Karma</h3><div class="coverage"><div><strong>61</strong><span>testes passaram</span></div><div><strong>66,88%</strong><span>linhas</span></div><div><strong>49,21%</strong><span>funções</span></div></div><div class="warning-box">Build production passou, porém o bundle contém localhost e o runner registrou NG04002 mesmo terminando verde.</div><p><a class="source-link" href="evidencias/frontend-tests.txt">Log de testes</a> · <a class="source-link" href="evidencias/frontend-build.txt">Log do build</a></p></article></div><div class="card" style="margin-top:14px"><h3 style="margin-top:0">Reprodução pela API real</h3><table class="demo-table"><thead><tr><th>Caso</th><th>Esperado</th><th>Obtido</th><th>Resultado</th></tr></thead><tbody><tr><td>Excluir quadro com tarefa</td><td>409</td><td>500</td><td style="color:var(--danger)">Falhou</td></tr><tr><td>Excluir coluna para ela mesma</td><td>400/409</td><td>500</td><td style="color:var(--danger)">Falhou</td></tr><tr><td>Ex-membro edita comentário</td><td>404/403</td><td>200</td><td style="color:var(--danger)">Falhou</td></tr><tr><td>Ex-membro exclui comentário</td><td>404/403</td><td>204</td><td style="color:var(--danger)">Falhou</td></tr></tbody></table></div></section>

<section class="section" id="pendencias"><div class="section-head"><div><p class="eyebrow">06 · Plano de entrega</p><h2>O que precisa ser desenvolvido</h2><p class="section-intro">Sequência orientada por risco e dependência. Os tempos são estimativas para uma pessoa com foco e testes.</p></div></div><div class="roadmap">${roadmap.map(p=>`<article class="phase"><h3>${escapeHtml(p.phase)}</h3><span class="phase-time">${escapeHtml(p.time)}</span><ul>${p.items.map(i=>`<li>${escapeHtml(i)}</li>`).join('')}</ul></article>`).join('')}</div></section>

<section class="section" id="decisoes"><div class="section-head"><div><p class="eyebrow">07 · Spec-driven</p><h2>Conflitos que exigem decisão</h2><p class="section-intro">Aqui não existe “conserto de código” até a regra ser decidida. Atualize a spec primeiro, depois os testes e a implementação.</p></div></div><table class="decision-table"><thead><tr><th>Decisão</th><th>Recomendação</th></tr></thead><tbody>${decisions.map(([q,a])=>`<tr><td><strong>${escapeHtml(q)}</strong></td><td>${escapeHtml(a)}</td></tr>`).join('')}</tbody></table></section>

<section class="section" id="estudo"><div class="section-head"><div><p class="eyebrow">08 · Domine o código</p><h2>Como estudar cada linha sem se perder</h2><p class="section-intro">Não memorize arquivos isolados. Para cada caso de uso, percorra a mesma cadeia e explique por que cada camada existe.</p></div></div><div class="card-grid"><article class="card"><h3>Roteiro vertical</h3><ol class="mini-list"><li>Comece na rota Angular e no component.</li><li>Veja o service HTTP e o modelo TypeScript.</li><li>Ache o Controller e o DTO Java.</li><li>Leia o Service: permissão → validação → mutação → evento.</li><li>Confira Repository/query, Entity e migration.</li><li>Termine no Mapper e nos testes do critério.</li></ol></article><article class="card"><h3>Perguntas para cada método</h3><ul class="mini-list"><li>Quem pode chamar?</li><li>Qual estado entra e qual sai?</li><li>O que acontece em concorrência?</li><li>Qual exceção/status representa cada falha?</li><li>Há chamada externa dentro da transação?</li><li>Qual critério de aceite prova o comportamento?</li></ul></article><article class="card"><h3>Mapa mental backend</h3><p class="section-intro">Controller traduz HTTP. Service protege invariantes. Repository expressa acesso. Entity representa estado. Mapper impede vazamento. Listener separa efeitos após commit.</p></article><article class="card"><h3>Mapa mental frontend</h3><p class="section-intro">Route escolhe tela. Guard decide navegação. Component mantém estado. Service fala HTTP. Model garante contrato. Interceptor aplica token/erro. Template e SCSS apresentam.</p></article></div></section>

<section class="section" id="atlas"><div class="section-head"><div><p class="eyebrow">09 · Inventário navegável</p><h2>Atlas de cada arquivo</h2><p class="section-intro">${atlas.length} arquivos relevantes, com camada, tamanho, responsabilidade e símbolos principais. Clique no caminho para abrir a fonte.</p></div><span id="atlasCount" class="meta-chip">${atlas.length} arquivos</span></div><div class="atlas-controls"><input id="atlasSearch" type="search" placeholder="Filtrar caminho, símbolo ou responsabilidade…" aria-label="Filtrar arquivos"><select id="layerFilter"><option value="all">Todas as camadas</option>${[...new Set(atlas.map(a=>a.layer))].sort().map(l=>`<option value="${escapeHtml(l)}">${escapeHtml(l)}</option>`).join('')}</select></div><div class="atlas-wrap"><table class="atlas"><thead><tr><th>Arquivo</th><th>Camada</th><th>Linhas</th><th>Responsabilidade</th><th>Símbolos</th></tr></thead><tbody id="atlasBody">${atlas.map(a=>`<tr data-layer="${escapeHtml(a.layer)}" data-search="${escapeHtml((a.relative+' '+a.purpose+' '+a.symbols.join(' ')).toLowerCase())}"><td class="atlas-path"><a class="source-link" href="${sourceHref(a.relative,1)}">${escapeHtml(a.relative)}</a></td><td><span class="tag">${escapeHtml(a.layer)}</span></td><td>${a.lines}</td><td class="atlas-purpose">${escapeHtml(a.purpose)}</td><td class="symbols">${escapeHtml(a.symbols.join(' · '))}</td></tr>`).join('')}</tbody></table></div></section>

<section class="section" id="apresentacao"><div class="section-head"><div><p class="eyebrow">10 · Defesa do TCC</p><h2>Roteiro de 7 minutos</h2><p class="section-intro">Um caminho seguro, tecnicamente honesto e fácil de recuperar se a rede ou GitHub falhar.</p></div></div><table class="demo-table"><thead><tr><th>Tempo</th><th>Parte</th><th>Mensagem</th></tr></thead><tbody>${demo.map(([t,p,m])=>`<tr><td>${escapeHtml(t)}</td><td><strong>${escapeHtml(p)}</strong></td><td>${escapeHtml(m)}</td></tr>`).join('')}</tbody></table><div class="card-grid" style="margin-top:14px"><article class="card"><h3>Frase que você pode defender</h3><p class="section-intro">“As specs são a fonte de verdade do comportamento; a arquitetura define como implementar. Minha auditoria rastreia os critérios até código e testes, e deixa explícito o que é entregue, parcial ou pendente.”</p></article><article class="card"><h3>Evite dizer</h3><p class="section-intro">“A integração GitHub está pronta”, “todos os critérios passam” ou “182 testes provam que não há bugs”. Diga exatamente quais fatias existem e mostre o roadmap.</p></article></div></section>

<footer class="footer"><p>Relatório gerado por análise estática, builds, testes e reprodução em ambiente isolado. Não foram modificadas regras da aplicação. Referências técnicas externas: <a href="https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html">rollback transacional do Spring</a>, <a href="https://angular.dev/tools/cli/environments">ambientes Angular</a>, <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-10.12">OAuth 2.0 CSRF</a> e <a href="https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api">paginação GitHub REST</a>.</p><p>Gerado em ${new Date().toLocaleString('pt-BR')} · fonte regenerável: <a href="gerar-relatorio.mjs">gerar-relatorio.mjs</a></p></footer>
</div></main></div>
<script>
const root=document.documentElement;const saved=localStorage.getItem('devboard-audit-theme');if(saved)root.dataset.theme=saved;
document.getElementById('themeButton').addEventListener('click',()=>{const next=root.dataset.theme==='light'?'dark':'light';root.dataset.theme=next;localStorage.setItem('devboard-audit-theme',next)});
const sidebar=document.getElementById('sidebar');document.getElementById('menuButton').addEventListener('click',()=>sidebar.classList.toggle('open'));document.querySelectorAll('.nav a').forEach(a=>a.addEventListener('click',()=>sidebar.classList.remove('open')));
let severity='all';const findingSearch=document.getElementById('findingSearch');const typeFilter=document.getElementById('typeFilter');const findingCards=[...document.querySelectorAll('.finding-card')];
function filterFindings(){const q=findingSearch.value.trim().toLowerCase();let visible=0;findingCards.forEach(card=>{const show=(severity==='all'||card.dataset.severity===severity)&&(typeFilter.value==='all'||card.dataset.type===typeFilter.value)&&(!q||card.dataset.search.includes(q));card.classList.toggle('hidden',!show);if(show)visible++});document.getElementById('findingCount').textContent=visible+' visíveis'}
document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{document.querySelectorAll('[data-filter]').forEach(b=>b.classList.remove('active'));button.classList.add('active');severity=button.dataset.filter;filterFindings()}));findingSearch.addEventListener('input',filterFindings);typeFilter.addEventListener('change',filterFindings);
const atlasSearch=document.getElementById('atlasSearch'),layerFilter=document.getElementById('layerFilter'),atlasRows=[...document.querySelectorAll('#atlasBody tr')];function filterAtlas(){const q=atlasSearch.value.trim().toLowerCase();let visible=0;atlasRows.forEach(row=>{const show=(layerFilter.value==='all'||row.dataset.layer===layerFilter.value)&&(!q||row.dataset.search.includes(q));row.classList.toggle('hidden',!show);if(show)visible++});document.getElementById('atlasCount').textContent=visible+' arquivos'}atlasSearch.addEventListener('input',filterAtlas);layerFilter.addEventListener('change',filterAtlas);
const navLinks=[...document.querySelectorAll('.nav a')];const observed=[...document.querySelectorAll('.section')];const observer=new IntersectionObserver(entries=>{const current=entries.filter(e=>e.isIntersecting).sort((a,b)=>b.intersectionRatio-a.intersectionRatio)[0];if(!current)return;navLinks.forEach(a=>a.classList.toggle('active',a.getAttribute('href')==='#'+current.target.id))},{rootMargin:'-15% 0px -70% 0px',threshold:[0,.2,.6]});observed.forEach(s=>observer.observe(s));
</script></body></html>`;

fs.writeFileSync(output, html, 'utf8');
console.log(`Relatório gerado: ${output}`);
console.log(`Achados: ${findings.length}; arquivos no atlas: ${atlas.length}; linhas: ${sourceLines}`);
