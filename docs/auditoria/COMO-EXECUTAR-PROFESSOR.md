# Como executar a auditoria do devBoard

Este guia permite consultar e regenerar a auditoria sem instalar as dependências do backend ou do frontend.

## Opção mais simples: abrir o relatório pronto

1. Entre na pasta do projeto.
2. Abra `docs/auditoria/relatorio-devboard.html` em um navegador atualizado.
3. Use o menu lateral para navegar pelo checklist, problemas, testes e atlas de arquivos.
4. Use o botão **Tema** para alternar entre o tema claro e o escuro.

O PDF pronto está em `output/pdf/auditoria-devboard-v1.1.1.pdf`.

## Abrir por servidor local

Este modo também permite que os links de evidência apontem corretamente para os arquivos do código.

### Pré-requisitos

- Node.js 18 ou superior;
- Python 3 apenas para servir os arquivos por HTTP;
- Git, caso queira conferir o histórico analisado.

Na raiz do repositório, execute:

```powershell
python -m http.server 4173 --bind 127.0.0.1 --directory .
```

Depois abra:

```text
http://127.0.0.1:4173/docs/auditoria/relatorio-devboard.html
```

Para encerrar o servidor, volte ao terminal e pressione `Ctrl+C`.

## Regenerar a auditoria

Na raiz do projeto:

```powershell
node docs/auditoria/analisar-configuracao.mjs
node docs/auditoria/gerar-relatorio.mjs
```

O primeiro comando exibe métricas reproduzíveis de estrutura, nomenclatura, duplicação, Git e automação. O segundo recria o HTML usando essas métricas.

Nenhum dos dois comandos altera o banco de dados ou inicia a aplicação.

## O que verificar no relatório

- **Preparação:** responsáveis e acessos exigidos pela atividade.
- **Checklist obrigatório:** resposta aos 30 itens de auditoria de configuração.
- **Problemas e soluções:** problema, impacto, evidência e correção proposta.
- **Mapa técnico:** 37 achados comparados com as especificações.
- **Testes e evidências:** resultados de backend, frontend, build e cobertura.
- **Atlas:** inventário dos arquivos e responsabilidades.
- **Roteiro da banca:** sequência sugerida para a apresentação.

## Resultado da versão 1.1.1

- 13 itens conformes;
- 16 itens parcialmente conformes;
- 1 item não conforme;
- 37 achados técnicos;
- 182 testes automatizados executados;
- 13 migrations Liquibase validadas;
- índice ponderado de configuração: 70%.

Os arquivos dentro de `docs/auditoria/evidencias/` são locais e ignorados pelo Git. O relatório contém as conclusões necessárias mesmo quando esses logs não acompanham o repositório.
