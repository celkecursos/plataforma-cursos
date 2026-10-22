---
name: gc
description: Gera UMA mensagem de commit (Conventional Commits, em português) a partir de tudo o que está na worktree — alterações com e sem stage e arquivos novos. Só escreve a mensagem; não roda git add nem commit. Use quando o usuário digitar /gc ou pedir "gera a mensagem de commit".
---

# /gc — mensagem de commit da worktree

Gera **uma** mensagem que descreve **tudo** o que está na worktree agora. Não altera nada: sem
`git add`, sem `git commit`, sem `git stash`.

## 1. Levantar as mudanças

```bash
git status --short
git diff --stat
git diff --staged --stat
git log --oneline -20
```

Leia o conteúdo para entender o **porquê**: `git diff` e `git diff --staged` (por arquivo, se o diff
for grande) e os arquivos novos (`??`), que o diff não mostra. Worktree limpa: diga isso e pare.

## 2. Escrever

Siga a skill `conventional-commits`. Mudanças sem relação entre si: gere mesmo assim **uma** mensagem
(tipo da mudança principal, as outras no corpo) e avise numa linha que daria para separar em commits.

## 3. Entregar

Só a mensagem, num **único** bloco de código, e abaixo:

> Para commitar e enviar: `/pc`.

Não execute o commit.
