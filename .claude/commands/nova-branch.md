---
description: Cria a branch de uma funcionalidade ou correção a partir da homolog atualizada.
argument-hint: <descrição da funcionalidade> [fix|hotfix]
---

Crie a branch para: **$ARGUMENTS**

1. Nome: `feature/<slug>` (padrão), `fix/<slug>` se o texto indicar correção, `hotfix/<slug>` se for
   correção urgente de produção. Slug em minúsculas, sem acentos, palavras separadas por hífen.
2. Base: `homolog` (feature e fix) ou `main` (hotfix).
3. Confira a worktree com `git status --short`. Há alterações? Pare e pergunte o que fazer com elas —
   não descarte nada.
4. Crie:

   ```bash
   git fetch origin
   git switch -c <branch> origin/<base>
   ```

5. Informe a branch criada e a base. Ao terminar o trabalho: `/gc`, `/pc` e `/abrir-pr`.
