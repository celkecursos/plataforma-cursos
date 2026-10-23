---
paths:
  - "docs/prompts/**"
  - "docs/prompts-iniciais/**"
description: Como escrever e manter os prompts do tutorial — atemporais, sem citar outros projetos, com o conteúdo exato. Carrega ao tocar docs/prompts ou docs/prompts-iniciais.
---

# Prompts do tutorial

- `docs/prompts/` tem um prompt por fase (especificação + verificação); `docs/prompts-iniciais/` é a
  sequência completa para criar o projeto do zero, com o conteúdo exato de cada arquivo. Mudou uma
  fase? Atualize **os dois**.
- **Atemporal:** o prompt descreve o projeto como se tivesse nascido assim. Sem datas, "versão 2",
  "atualizado", "correção", "antes era", histórico de mudanças.
- **Sem citar outros projetos**, caminhos de outras máquinas ou repositórios de onde algo foi
  aproveitado.
- Arquivo gerado por ferramenta (lockfile, wrapper do Maven, listas baixadas) não vai embutido: o
  prompt manda rodar o comando que o gera.
- Cada prompt termina com **Antes de executar** (branch a partir da `homolog`), **Verificação** (os
  comandos da definição de pronto) e **Depois de executar** (commit no padrão Conventional Commits,
  push e Pull Request para a `homolog`).
- O índice (`README.md` de cada pasta) lista os prompts na ordem de execução.
