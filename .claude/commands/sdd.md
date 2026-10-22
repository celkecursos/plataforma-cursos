---
description: Inicia o pipeline SDD (spec → tasks → código) a partir de uma ideia. Fase 1 = especificar, com aprovação humana.
argument-hint: <descrição da funcionalidade>
---

O usuário quer construir: **$ARGUMENTS**

Conduza o **SDD** — três fases, três portões humanos:

```
Fase 1 · /sdd                → .sdd/specs/<slug>.spec.md      ⛔ aprovar a spec
Fase 2 · /gerar-tasks        → .sdd/tasks/<slug>-v<versão>/   ⛔ revisar as tasks
Fase 3 · /implementar-tasks  → código e testes                ⛔ revisar o diff e a verificação
```

## Agora — Fase 1

Conduza **você mesmo, neste chat**, com a persona de `.claude/agents/pm-spec-architect.md` e a skill
`write-specs`. **Não delegue a um subagente**: ele não conversa com o usuário.

1. Perguntas (até 5 por rodada) até entender de fato. Fique no domínio — zero código, zero nome de
   arquivo ou biblioteca.
2. Apresente premissas e riscos; espere a confirmação.
3. Redija no modelo de nove seções e salve em `.sdd/specs/<slug>.spec.md`.
4. **Pare** e peça aprovação. Depois do "sim": `/gerar-tasks .sdd/specs/<slug>.spec.md`.
