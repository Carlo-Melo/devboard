# Auditoria técnica do devBoard

Este diretório mantém a auditoria técnica reproduzível do projeto. Ela compara o código com as especificações, registra lacunas, riscos, decisões de negócio e um roteiro de apresentação do TCC.

## Artefatos versionados

- `analisar-configuracao.mjs`: coleta métricas reproduzíveis de estrutura, nomenclatura, duplicação, manutenibilidade e Git.
- `gerar-relatorio.mjs`: fonte do relatório e dos dados consolidados da auditoria.
- `relatorio-devboard.html`: versão navegável do relatório, gerada pelo script.
- `CHANGELOG.md`: histórico das versões da auditoria.
- `COMO-EXECUTAR-PROFESSOR.md`: roteiro curto para abrir e regenerar a auditoria.
- `README.md`: orientação de manutenção deste material.

O PDF de entrega atual é exportado localmente para `output/pdf/auditoria-devboard-v1.1.1.pdf` e corresponde ao mesmo conteúdo do HTML. A pasta `output/` é ignorada pelo Git: o HTML e seus geradores são a documentação versionada, e os PDFs são arquivos de exportação local.

## Como regenerar

Na raiz do repositório, execute:

```powershell
node docs/auditoria/analisar-configuracao.mjs
node docs/auditoria/gerar-relatorio.mjs
```

Abra `docs/auditoria/relatorio-devboard.html` no navegador. O arquivo é autocontido e não depende de serviços externos. Para abrir os links relativos para o código por HTTP local:

```powershell
python -m http.server 4173 --bind 127.0.0.1 --directory .
```

Depois acesse `http://127.0.0.1:4173/docs/auditoria/relatorio-devboard.html`.

## Evidências locais

`evidencias/` é ignorado pelo Git. Essa pasta pode conter logs de builds, testes, execução local, caminhos da máquina e dados temporários de ferramentas. Nunca inclua credenciais, tokens, senhas ou bancos locais nela.

Ao mudar uma regra, uma spec, a arquitetura ou o resultado de testes, atualize o gerador e regenere o HTML no mesmo commit. Assim o relatório continua sendo uma fotografia verificável do projeto.

## Versionamento

A auditoria usa versionamento semântico próprio, independente da versão do produto:

- **patch** (`1.1.0` → `1.1.1`): correção textual, visual ou de evidência sem mudar o escopo;
- **minor** (`1.1.0` → `1.2.0`): nova rodada, checklist, análise ou conjunto relevante de evidências;
- **major** (`1.x` → `2.0.0`): mudança de método, estrutura ou objetivo da auditoria.

Uma solicitação de “rodar a auditoria novamente” gera uma nova versão somente quando produz uma nova fotografia verificável do projeto. Abrir, exportar ou corrigir um erro de digitação não cria automaticamente uma versão major/minor.
