# devBoard — Especificações

Documentação do projeto no padrão **Spec-Driven Development**.

---

## Princípio

A separação entre os dois tipos de documento é o que faz o padrão funcionar:

| Documento | Responde | Contém |
|---|---|---|
| `claude.md` | **como** implementar | stack, arquitetura, estrutura de pastas, convenções, exemplos de código |
| `spec-*.md` | **o que** implementar | objetivo, modelo de dados, endpoints, regras, fluxos, permissões, critérios de aceite |

As specs não contêm código. O padrão de implementação vive em um único lugar — `claude.md` — e por isso pode ser alterado sem tocar em nenhuma spec.

---

## Ordem de leitura

**Sempre**: `claude.md` primeiro. Depois, a spec do que você vai construir, junto com as specs das quais ela depende.

```
claude.md
   │
   ├── spec-authentication.md          ← base, sem dependências
   │
   └── spec-projects.md                ← depende de autenticação
          │
          ├── spec-board-kanban.md     ← depende de projetos
          │      │
          │      └── spec-tasks.md     ← depende de quadro
          │             │
          │             ├── spec-github-integration.md
          │             └── spec-labels-search.md
          │
          ├── spec-members.md
          │
          └── spec-notifications.md    ← depende de tarefas e membros
```

---

## Specs

| # | Spec | Escopo |
|---|---|---|
| 1 | `spec-authentication.md` | registro, login, GitHub OAuth, JWT, recuperação de senha |
| 2 | `spec-projects.md` | CRUD de projetos, vínculo com repositório, importação de issues |
| 3 | `spec-board-kanban.md` | quadros, colunas, papéis semânticos, limite WIP |
| 4 | `spec-tasks.md` | tarefas genéricas, tipos, movimentação, comentários, branches |
| 5 | `spec-github-integration.md` | webhooks, automações, sincronização bidirecional |
| 6 | `spec-members.md` | convites, papéis, matriz de permissões |
| 7 | `spec-labels-search.md` | labels, busca, filtros combinados |
| 8 | `spec-notifications.md` | notificações e histórico de atividades |

---

## Anatomia de uma spec

Todas seguem a mesma estrutura:

1. **Objetivo** — por que este módulo existe
2. **Escopo** — o que está dentro e, principalmente, o que está fora
3. **Modelo de dados** — campos, tipos e regras em formato de tabela
4. **Endpoints** — entrada, comportamento, saída e erros
5. **Fluxos** — sequências que atravessam vários endpoints
6. **Permissões** — quem pode o quê
7. **Critérios de aceite** — checklist verificável de conclusão

---

## Como implementar um módulo

1. Ler `claude.md`
2. Ler a spec do módulo e as das quais ela depende
3. Implementar seguindo a ordem: entidade → migration → repository → DTO → service → controller → testes
4. Validar contra os critérios de aceite da spec
5. Um módulo só está pronto quando todos os critérios passam

---

## Usando as specs com IA

O objetivo do padrão é que a IA nunca escreva código no escuro.

```
Prompt: "Implemente o módulo de autenticação seguindo docs/spec-authentication.md.
         Os padrões de código estão em docs/claude.md."
```

A IA tem o contexto completo: **o que** construir (spec) e **como** construir (claude.md). Sem ambiguidade, sem invenção de padrão, sem inconsistência entre módulos.

---

## Manutenção

- Mudou uma regra de negócio → altere a spec correspondente
- Mudou um padrão de código → altere apenas `claude.md`
- Novo módulo → crie `spec-{modulo}.md` seguindo a mesma estrutura e registre-o aqui

A spec é a fonte de verdade. Quando código e spec divergem, um dos dois está errado — decida qual antes de seguir.

---

## Ordem de implementação sugerida

**Sprint 1** — autenticação, projetos, quadro, tarefas
**Sprint 2** — integração GitHub, membros
**Sprint 3** — labels e busca, notificações

Ao final da Sprint 1 o devBoard já é um Kanban funcional. A Sprint 2 entrega o diferencial do produto.
