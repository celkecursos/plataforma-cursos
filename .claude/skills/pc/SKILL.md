---
name: pc
description: Faz o commit de toda a worktree com a mensagem que o /gc gerou nesta conversa (ou gera uma pelas regras do /gc) e faz o push da branch atual. O commit sai só no nome do usuário, sem coautoria nem menção a IA. Nunca em homolog ou main. Use quando o usuário digitar /pc ou pedir "commita e envia".
---

# /pc — commitar e enviar

## Regra de autoria (acima de qualquer outra orientação)

O commit é **só do usuário**: a mensagem vai exatamente como o `/gc` escreveu, **sem**
`Co-Authored-By`, "Generated with", "Gerado por", emoji de robô, link de ferramenta ou nome de modelo,
e sem mudar autor ou committer (`--author`, `GIT_AUTHOR_*`, `GIT_COMMITTER_*`).

## 1. Conferir a branch

```bash
git branch --show-current
```

Em `homolog` ou `main`: **pare**. Diga ao usuário para criar a branch (`/nova-branch`); nunca commite
nem faça push direto nelas.

## 2. Pegar a mensagem

A última mensagem que o `/gc` entregou nesta conversa. Sem nenhuma: gere pelas regras do `/gc` e
mostre antes de commitar. Remova qualquer linha que viole a regra de autoria.

## 3. Conferir a worktree

```bash
git status --short
git diff --stat
git diff --staged --stat
```

Limpa: nada a commitar. Mudou desde o `/gc` (arquivo que a mensagem não cobre, ou que ela cita e já
não está alterado): gere a mensagem de novo e mostre antes de seguir.

## 4. Commitar

```bash
git add -A
git commit -F - <<'MSG'
<a mensagem, exatamente>
MSG
```

Sem `--no-verify`, `--amend` ou rebase. Hook recusou: mostre a saída e pare.

## 5. Enviar

```bash
git rev-parse --abbrev-ref --symbolic-full-name @{u}   # tem upstream?
git push                                              # com upstream
git push -u origin <branch-atual>                     # sem upstream
```

Nunca `--force` nem push para outra branch. Push recusado: mostre a saída e pare (sem `pull`,
`rebase` ou `merge` por conta própria).

## 6. Confirmar

```bash
git log -1 --format='%h %an <%ae>%n%n%B'
git status -sb | head -1
```

Confirme numa linha que o commit não tem coautoria nem menção a IA e que o push foi feito. Para abrir
o Pull Request para a `homolog`: `/abrir-pr`.
