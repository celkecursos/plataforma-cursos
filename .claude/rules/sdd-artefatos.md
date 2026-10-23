---
paths:
  - ".sdd/**"
description: Contrato dos artefatos do SDD (specs e tasks) — o que não pode mudar de forma ao editar uma spec ou uma task. Carrega ao tocar .sdd/.
---

# Artefatos do SDD

Os arquivos em `.sdd/` são lidos por agentes nas fases seguintes. Mudar um campo estruturado quebra
o pipeline longe daqui, sem erro na hora.

## `.sdd/specs/<slug>.spec.md` (Fase 1)

- **Nove seções, nesta ordem:** Resumo Executivo · Atores e Papéis · Requisitos Funcionais · Modelo de
  Domínio · Estados e Fluxos · Contratos de Interface · Casos de Borda · Premissas Validadas · Riscos.
- **Requisitos `RF-NN`** no formato "O sistema DEVE…". O número é a chave de rastreio spec → task →
  arquivo: **nunca renumere**. RF novo entra no fim; RF descartado vira "RF-NN — REMOVIDO", não some.
- **Agnóstica de tecnologia:** sem linguagem, framework, biblioteca, nome de arquivo, código ou SQL.
- **Frontmatter:** `feature`, `versao`, `status` (`rascunho` | `aprovada`), `autor`. Revisar uma spec
  aprovada: suba a `versao` e volte o `status` para `rascunho`.

## `.sdd/tasks/<slug>-v<versao>/` (Fase 2)

- **`Nível:`** é a chave do despacho da Fase 3: exatamente `Pleno` ou `Sênior`.
- **`Depende de:`** define ordem e paralelismo. Tasks sem dependência entre si rodam juntas e por isso
  **não podem listar o mesmo arquivo**.
- **Arquivos compartilhados** — `SecurityConfig`, `application.yaml`, `infra/.env.example`, os compose,
  `rotas.tsx` de cada front e a migration da fase — pertencem a **uma** task de fiação, que depende das
  outras e roda por último.
- **`Spec:` + `atende RF-NN`** em toda task.
- **Sem código nas tasks:** contratos em prosa e o arquivo exato a espelhar. Exceção: `record`, `enum`
  ou `type` de domínio que fixa o contrato.
- Mudou o escopo? Pasta nova `<slug>-v<nova-versao>/`; não reescreva tasks já implementadas.

## Não fazer

- Implementar a partir de uma spec sem passar por `/gerar-tasks`.
- Marcar uma spec como `aprovada` por conta própria: os portões são humanos.
- Apagar spec ou task antiga.
