# Auditoria técnica do devBoard

Este diretório mantém a auditoria técnica reproduzível do projeto. Ela compara o código com as especificações, registra lacunas, riscos, decisões de negócio e um roteiro de apresentação do TCC.

## Artefatos versionados

- `gerar-relatorio.mjs`: fonte do relatório e dos dados consolidados da auditoria.
- `relatorio-devboard.html`: versão navegável do relatório, gerada pelo script.
- `README.md`: orientação de manutenção deste material.

## Como regenerar

Na raiz do repositório, execute:

```powershell
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
