# spec-design-system.md — Design System

> **Pré-requisito**: `claude.md`
> **Fonte**: `docs/DesingSystem.jpg` (referência visual única, extraída por inspeção)

**Status**: referência de UI — norteia o frontend, não é um módulo de backend
**Aplica-se a**: `devBoard-frontend/` (todas as telas)

---

## 1. OBJETIVO

Este documento traduz a referência visual `DesingSystem.jpg` em tokens, componentes e regras reaproveitáveis, para que qualquer tela nova do devBoard nasça visualmente consistente sem depender de decisões de design ad-hoc por desenvolvedor.

Onde a spec de um módulo (`spec-tasks.md`, `spec-board-kanban.md`, etc.) define **o que** uma tela mostra, este arquivo define **com o que ela é construída**: cor, tipografia, espaçamento, raio, componente.

Quando um valor não está literalmente visível na imagem de referência (ex.: cor de aviso, estado de foco), ele é marcado como **[extrapolado]** — inferido a partir do padrão visual existente, não copiado do original. Trate esses casos como ponto de partida, ajustável em revisão de design.

---

## 2. ESCOPO

### Dentro do escopo
- Paleta de cores, tipografia, espaçamento, raio, elevação
- Especificação dos componentes visíveis na referência (sidebar, top bar, cards, gráficos, badges, avatar)
- Tokens prontos para uso em SCSS
- Mapeamento dos componentes do design system para as telas reais do devBoard

### Fora do escopo
- Tema claro (a referência define apenas tema escuro; ver seção 10)
- Biblioteca de componentes Angular já implementada (este documento é a especificação; a implementação é trabalho de frontend subsequente)
- Ilustrações, ícones customizados de marca (usa-se biblioteca de ícones de linha padrão — ver 3.7)

---

## 3. ESTILO GERAL

Dashboard escuro, denso em dados, com um único acento de cor (verde-menta) usado com disciplina — nunca mais de uma cor de destaque compete por atenção na mesma tela. Cards em superfície ligeiramente mais clara que o fundo, cantos bem arredondados, bordas quase invisíveis (contraste vem de diferença de luminosidade, não de linhas fortes). Números grandes e brancos carregam a informação principal; texto de apoio é cinza-esverdeado discreto.

Elemento de marca: faixas diagonais em gradiente verde, decorativas, nos cantos da tela — usadas com moderação, nunca sobre área de conteúdo denso (ver 5.10).

---

## 4. FUNDAMENTOS

### 4.1 Paleta de cores

| Token | Hex / valor | Uso |
|---|---|---|
| `--color-bg-base` | `#0A0F0D` | fundo da aplicação |
| `--color-bg-shell` | `#101613` | sidebar, moldura externa dos cards |
| `--color-surface` | `#141B18` | superfície de card |
| `--color-surface-hover` | `#182420` | card/linha em hover |
| `--color-border` | `#233029` | borda de card, divisor |
| `--color-border-subtle` | `rgba(255,255,255,0.06)` | divisores internos discretos (ex.: separador entre KPIs) |
| `--color-primary-500` | `#1AEFA8` | acento de marca — texto de destaque, ícones ativos, gráfico primário, botão primário |
| `--color-primary-600` | `#12C98D` | hover/pressed do primário |
| `--color-primary-100` | `rgba(26,239,168,0.12)` | fundo suave (bolha de ícone, chip) |
| `--color-primary-contrast` | `#06120D` | texto sobre fundo `--color-primary-500` |
| `--color-danger` | `#FF5C6C` | queda/negativo, erro, badge de notificação |
| `--color-danger-100` | `rgba(255,92,108,0.12)` | fundo suave de erro |
| `--color-warning` **[extrapolado]** | `#FFB020` | alerta (ex.: prazo próximo, limite WIP) — não existe na referência, papel inferido da paleta semântica típica |
| `--color-info` **[extrapolado]** | `#4DA3FF` | informativo neutro (ex.: prioridade média) — não existe na referência |
| `--color-text-primary` | `#F5F7F6` | valores, títulos, texto principal |
| `--color-text-secondary` | `#9AA8A2` | rótulos, texto de apoio |
| `--color-text-muted` | `#5E6B66` | texto terciário (rótulo de grupo, placeholder) |
| `--color-text-on-primary` | `#06120D` | texto sobre botão/pill preenchido em verde |

**Regra de disciplina de cor**: o verde-menta (`--color-primary-500`) é o único acento "de marca". Vermelho é reservado exclusivamente para negativo/erro/contagem. Nenhuma outra cor viva aparece fora de gráfico de dados.

### 4.2 Tipografia

Fonte não identificável com certeza a partir de um raster, mas a geometria (baixo contraste de traço, terminais retos, números tabulares) é compatível com **Inter** — adotar como fonte padrão do frontend, com fallback de sistema:

```scss
--font-family-base: 'Inter', -apple-system, 'Segoe UI', Roboto, sans-serif;
```

| Token | Tamanho / altura de linha | Peso | Uso |
|---|---|---|---|
| `--text-display` | 32px / 40px | 700 | valor de KPI, número central do gráfico/donut |
| `--text-h1` | 26px / 32px | 700 | título de página (cor `--color-primary-500`) |
| `--text-h2` | 15px / 20px | 600 | título de card |
| `--text-body` | 14px / 20px | 400–500 | texto padrão, itens de lista, nav |
| `--text-label` | 12px / 16px | 500 | rótulo pequeno, meta-informação (duração, data) |
| `--text-micro` | 11px / 14px | 700 | número de badge, tag de status |
| `--text-group-label` | 12px / 16px | 500, letter-spacing 0.04em | rótulo de grupo na sidebar ("Podcasts", "Analytics") — sempre `--color-text-muted` |

Números (KPIs, percentuais) usam variante tabular (`font-variant-numeric: tabular-nums`) para não "tremer" ao atualizar.

### 4.3 Espaçamento

Escala base de 4px:

```
--space-1: 4px   --space-2: 8px   --space-3: 12px  --space-4: 16px
--space-5: 20px  --space-6: 24px  --space-8: 32px  --space-10: 40px
```

Padding interno de card: `--space-6` (24px). Gap entre cards de uma mesma linha: `--space-5` (20px). Gap entre item de nav e o próximo: `--space-2`.

### 4.4 Raio de borda

| Token | Valor | Uso |
|---|---|---|
| `--radius-sm` | 8px | input, bolha de ícone pequena |
| `--radius-md` | 12px | botão, tag, item de nav |
| `--radius-lg` | 20px | card |
| `--radius-full` | 999px | pill (busca, badge, nav ativo, status "Live") |

### 4.5 Elevação e borda

Sem sombras dramáticas — o tema escuro separa camadas por diferença de luminosidade (`--color-bg-base` → `--color-bg-shell` → `--color-surface`) mais uma borda de 1px quase invisível (`--color-border`). Único brilho: leve `box-shadow` verde suave atrás do botão primário e do anel de progresso, para reforçar o acento de marca.

```scss
--shadow-card: none; // separação por cor, não por sombra
--glow-primary: 0 0 24px rgba(26, 239, 168, 0.25);
```

### 4.6 Iconografia

Ícones de linha (outline), traço uniforme ~1.5–1.75px, grid de 20×20 ou 24×24, cantos levemente arredondados. Compatível com bibliotecas **Lucide** ou **Feather** — adotar uma delas para consistência; não misturar estilos de ícone (outline com filled) na mesma tela, exceto o próprio indicador de item ativo da sidebar (ver 5.1).

### 4.7 Movimento

Transições curtas e discretas — nada chama atenção sobre si mesmo:

```scss
--transition-fast: 120ms ease;
--transition-base: 200ms ease;
```

Aplicar em: hover de item de nav, hover de card, abertura de dropdown. Gráficos podem animar entrada (draw-in) uma única vez ao carregar; não reanimam em atualização de dado.

---

## 5. COMPONENTES

### 5.1 Sidebar de navegação

- Largura fixa: 260–280px. Fundo `--color-bg-shell`, ocupa a altura total, cantos externos arredondados em `--radius-lg` quando a sidebar é "flutuante" dentro do shell (como na referência) ou reta quando colada à borda da viewport.
- Título/logo no topo: `--text-h1`, cor `--color-primary-500`.
- Item de navegação: ícone (20px) + rótulo (`--text-body`), padding `--space-3 --space-4`, `--radius-md`.
  - **Default**: ícone e texto em `--color-text-secondary`.
  - **Hover**: fundo `--color-surface-hover`.
  - **Ativo**: fundo sólido `--color-primary-500`, texto `--color-text-on-primary`, com um pequeno indicador (quadrado/ponto) à esquerda do rótulo.
- Badge de contagem: círculo `--radius-full`, fundo `--color-danger`, texto branco `--text-micro`, ~18px de diâmetro, ancorado à direita do item.
- Rótulo de grupo ("Podcasts", "Analytics" na referência → em devBoard: agrupar por seção do produto): `--text-group-label`, com `--space-6` de margem superior para separar do grupo anterior.
- Rodapé da sidebar: item fixo no fim (ícone + nome do produto + versão), texto `--text-label` em `--color-text-muted`.

### 5.2 Barra superior

- Campo de busca: pill (`--radius-full`), fundo `--color-surface`, ícone de lupa à esquerda em `--color-text-muted`, placeholder em `--color-text-muted`, sem borda visível até o foco (ver 8, estado `:focus`).
- Bloco de usuário, alinhado à direita: avatar circular + nome (`--text-body`, `--color-text-primary`) + email/subtítulo (`--text-label`, `--color-text-secondary`) empilhados, chevron discreto para abrir menu.

### 5.3 Botões

| Variante | Fundo | Texto | Uso |
|---|---|---|---|
| Primário | `--color-primary-500` (hover: `--color-primary-600`) | `--color-text-on-primary`, peso 600 | ação principal da tela ("Criar tarefa", "Entrar") |
| Secundário | transparente, borda `--color-border` | `--color-text-primary` | ação alternativa |
| Ghost / ícone | transparente (hover: `--color-surface-hover`) | `--color-text-secondary` | ações de suporte (kebab menu, fechar) |
| Perigo **[extrapolado]** | transparente, borda `--color-danger` | `--color-danger` | ação destrutiva (arquivar, remover membro) |

Todos: `--radius-md`, padding `--space-3 --space-5`, altura mínima 40px (alvo de toque).

### 5.4 Cards

Base comum a todo card: fundo `--color-surface`, borda 1px `--color-border`, `--radius-lg`, padding `--space-6`.

- **Card de KPI (stat card)**: várias métricas lado a lado dentro do mesmo card, separadas por `--color-border-subtle` vertical. Cada métrica: rótulo `--text-label`/`--color-text-secondary` no topo, valor `--text-display` abaixo, e um indicador de variação (5.5) na base.
- **Card de gráfico**: título `--text-h2` + valor grande `--text-display` + variação (5.5) no cabeçalho; corpo com gráfico de linha/área (5.9); eixo inferior com rótulos `--text-label`/`--color-text-muted`.
- **Card de progresso circular**: título `--text-h2` em `--color-primary-500` + menu kebab no canto; donut central com percentual em `--text-display`; legenda min/max abaixo em `--text-label`.
- **Card de lista**: título + link "Ver tudo" (`--color-primary-500`) no cabeçalho; itens com bolha de ícone circular (fundo `--color-primary-100`, ícone `--color-primary-500`), título `--text-body`, subtítulo `--text-label`/`--color-text-secondary`, meta-informação alinhada à direita (duração + tag de status, ver 5.6).
- **Card promocional**: fundo `--color-surface`, título em destaque `--text-h2`/`--color-primary-500` (2–3 linhas), corpo `--text-body`/`--color-text-secondary`, link de ação `--color-primary-500` sem preenchimento.

### 5.5 Indicador de variação (delta)

Seta + percentual, cor semântica: `--color-primary-500` com seta para cima (positivo), `--color-danger` com seta para baixo (negativo). Tamanho `--text-label`, peso 600. Nunca depender só da cor — a seta (ou sinal `+`/`-`) sempre acompanha, por acessibilidade (ver seção 9).

### 5.6 Badges e tags

- **Badge de contagem**: ver 5.1 — círculo vermelho com número branco.
- **Tag de status ("Live")**: texto curto em `--color-primary-500`, sem fundo, `--text-micro`, ou opcionalmente pill com fundo `--color-primary-100`.
- **Tag de prioridade/label** **[extrapolado — não existe na referência, inferido para uso em devBoard]**: pill `--radius-full`, padding `--space-1 --space-3`, fundo tonal (100) da cor semântica + texto na cor sólida correspondente. Ver mapeamento em 8.3.

### 5.7 Avatar

Circular, três tamanhos: 24px (lista/tabela), 32px (padrão), 40px (perfil no topbar). Sem borda; se precisar destacar sobreposição em pilha de avatares (assignees), usar borda de 2px na cor `--color-bg-shell`.

### 5.8 Inputs e formulários **[extrapolado — a referência mostra apenas o campo de busca]**

- Fundo `--color-surface`, borda 1px `--color-border`, `--radius-sm`, padding `--space-3 --space-4`, texto `--color-text-primary`, placeholder `--color-text-muted`.
- Foco: borda `--color-primary-500` + anel externo `0 0 0 3px rgba(26,239,168,0.15)`.
- Erro: borda `--color-danger`, texto de ajuda abaixo em `--color-danger`/`--text-label`.
- Label do campo: `--text-label`, `--color-text-secondary`, `--space-2` de margem inferior.

### 5.9 Gráficos

- **Linha/área**: linha principal `--color-primary-500` com preenchimento em gradiente (`--color-primary-500` a 25% de opacidade no topo, transparente na base). Linha secundária (comparação) em `--color-danger` ou branco a 60% de opacidade, sem preenchimento. Grade horizontal discreta em `--color-border-subtle`. Ponto de destaque (pico/hover): círculo branco com halo da cor da linha.
- **Donut/progresso circular**: anel grosso (~10–12px de espessura), cor `--color-primary-500` sobre trilho `--color-border`, extremidades arredondadas, valor central em `--text-display`.

### 5.10 Decoração de marca

Faixas diagonais em gradiente verde (`--color-primary-500` esmaecendo para transparente), posicionadas nos cantos da tela. Uso restrito a telas de baixa densidade de dados — login, registro, estados vazios, onboarding. **Nunca** atrás do quadro Kanban, listas ou formulários densos: compete com o conteúdo e prejudica leitura.

---

## 6. GRID E LAYOUT

- Estrutura: sidebar fixa (260–280px) + conteúdo fluido, gutter de `--space-6` entre sidebar e conteúdo.
- Conteúdo principal: largura máxima confortável de leitura (~1200–1400px), centralizado em telas muito largas.
- Linha de KPIs: um único card dividido internamente (ver 5.4), não vários cards soltos.
- Abaixo dos KPIs: grid de 2 colunas, proporção aproximada 65/35 (card de gráfico maior + card de apoio menor). Em telas estreitas, empilha em 1 coluna.
- Breakpoint de colapso da sidebar em ícone-apenas: `[extrapolado]` < 1024px. Colapso para menu off-canvas: `[extrapolado]` < 768px.

---

## 7. ESTADOS

| Estado | Regra |
|---|---|
| Hover (card, item de lista, nav) | fundo sobe para `--color-surface-hover`, transição `--transition-fast` |
| Ativo/selecionado (nav) | fundo sólido `--color-primary-500`, ver 5.1 |
| Foco (input, botão, item navegável por teclado) | anel visível — nunca remover `outline` sem substituto; ver 5.8 |
| Desabilitado | opacidade 0.5, cursor `not-allowed`, sem hover |
| Carregando | skeleton na cor `--color-surface-hover` com leve pulso; não usar spinner central em card de conteúdo já visível |
| Vazio | ícone + texto `--color-text-muted` centralizado, sem borda de card adicional |

---

## 8. APLICAÇÃO NO devBoard

Este design system nasceu de um dashboard de podcast; a tabela abaixo traduz cada componente para as telas reais do devBoard, cruzando com as specs de produto.

### 8.1 Autenticação (`spec-authentication.md`)

Login e registro usam o card centralizado sobre `--color-bg-base`, com a decoração diagonal (5.10) nas bordas — única tela onde ela é apropriada além do dashboard vazio. Formulário segue 5.8. Botão de submissão é o botão primário (5.3).

> **Observação**: a tela de registro já implementada (`register.component.scss`) usa paleta clara (`#f4f5f7`, `#fff`) anterior a este design system. Deve ser re-skinada para o tema escuro como parte da adoção deste documento — não é um bug, é trabalho pendente.

### 8.2 Lista de projetos (`spec-projects.md`)

Cada projeto é um card de lista (5.4) — ícone/avatar do projeto, nome, meta-informação (nº de membros, última atividade). "Novo projeto" é botão primário no topo da página, mesmo padrão do header com título `--text-h1`.

### 8.3 Quadro Kanban e tarefas (`spec-board-kanban.md`, `spec-tasks.md`)

- **Coluna do quadro**: superfície `--color-surface`, cabeçalho com nome da coluna (`--text-h2`) + contador de tarefas; quando o limite WIP é atingido, o contador vira badge vermelho (5.6), reaproveitando o mesmo componente do badge de notificação da sidebar.
- **Card de tarefa**: variação do card de lista (5.4) — título `--text-body`, avatar do responsável (5.7, 24px), tags de label/prioridade (5.6).
- **Mapeamento de prioridade** (`TaskPriority` em `claude.md`) para cor semântica — `[extrapolado]`:

| Prioridade | Token de cor |
|---|---|
| `LOW` | `--color-text-secondary` (neutro, sem tag colorida) |
| `MEDIUM` | `--color-info` |
| `HIGH` | `--color-warning` |
| `URGENT` | `--color-danger` |

- Barra de busca/filtro do quadro reaproveita o componente de busca da top bar (5.2).

### 8.4 Detalhe de tarefa

Painel ou modal sobre `--color-surface`, mesma tipografia e espaçamento do card padrão. Comentários seguem o padrão de item de lista (5.4) com avatar (5.7).

### 8.5 Membros (`spec-members.md`)

Tabela/lista de membros usa o padrão de item de lista (5.4): avatar + nome + email + papel (tag, 5.6) + ações (botão ghost, 5.3).

### 8.6 Notificações (`spec-notifications.md`)

Centro de notificações é um card de lista (5.4); contagem de não lidas no ícone de sino da top bar usa o mesmo badge vermelho (5.6/5.1) já definido para a sidebar.

### 8.7 Labels e busca (`spec-labels-search.md`)

Labels do projeto são tags (5.6) com cor definida pelo usuário — nesse caso a cor é dado do domínio, não do design system; o componente de tag em si (forma, raio, padding) segue o padrão.

---

## 9. ACESSIBILIDADE

- Contraste: `--color-text-primary` sobre `--color-bg-base`/`--color-surface` atende AA confortavelmente (fundo muito escuro, texto quase branco). Verificar especificamente `--color-text-secondary` (`#9AA8A2`) e `--color-text-muted` (`#5E6B66`) sobre `--color-surface` — o segundo é o mais arriscado; reservar para texto não essencial (rótulo de grupo, timestamp), nunca para conteúdo que precise ser lido.
- Cor nunca é o único portador de significado: variação positiva/negativa sempre com seta ou sinal (5.5); prioridade/status sempre com texto na tag, não só cor.
- Estado de foco nunca é removido (ver 7) — navegação por teclado é obrigatória em formulário e quadro.
- Ícones de ação sem texto (kebab menu, fechar) sempre com `aria-label`.

---

## 10. TEMA CLARO

A referência define apenas tema escuro. O devBoard não exige tema claro no MVP; se vier a ser solicitado, a estratégia é inverter a escala de superfície (`--color-bg-base` → quase branco, `--color-surface` → branco, texto invertido) mantendo `--color-primary-500` como único acento — não redesenhar componentes, apenas reatribuir tokens. Fora do escopo desta versão.

---

## 11. CHECKLIST ANTES DE IMPLEMENTAR UMA TELA

- [ ] Usa os tokens de cor da seção 4.1 — nenhum hex novo hardcoded
- [ ] Tipografia vem da escala da seção 4.2, sem tamanho "solto"
- [ ] Espaçamento em múltiplos da escala de 4px
- [ ] Card segue a base comum (fundo, borda, raio, padding) antes de qualquer variação
- [ ] Variação positiva/negativa tem seta ou sinal, não só cor
- [ ] Estado de foco visível em todo elemento navegável por teclado
- [ ] Estado vazio e estado de carregamento definidos, não só o caminho feliz
- [ ] Decoração diagonal (5.10), se usada, está fora de área de conteúdo denso
- [ ] Cor de prioridade/tag segue o mapeamento da seção 8.3 (ou equivalente do módulo)

---

**Ver também**: `claude.md` (estrutura de pastas do frontend, convenção de nomenclatura de componentes)
