---
description: Abre o Pull Request da branch atual para a homolog (ou para a main, num hotfix), com título no padrão Conventional Commits.
argument-hint: "[base, se não for a padrão]"
---

Abra o Pull Request da branch atual.

1. `git branch --show-current`. Em `homolog` ou `main`: pare — PR sai de uma branch de trabalho.
2. Base: a informada em **$ARGUMENTS**; senão `main` para `hotfix/*` e `homolog` para o resto.
3. `git status --short` limpo e a branch enviada (`git status -sb` sem "ahead"). Não está? Sugira
   `/gc` e `/pc` e pare.
4. Leia `git log --oneline origin/<base>..HEAD` e `git diff --stat origin/<base>...HEAD`.
5. Título: Conventional Commits em português (skill `conventional-commits`), descrevendo a mudança
   principal.
6. Corpo em português:
   - **Resumo** — o que muda para o usuário ou para a operação;
   - **Como testar** — comandos e passos;
   - **Atenção no deploy** — variáveis novas, migrations, ações manuais no servidor (ou "nada");
   - sem rodapé de ferramenta nem menção a IA.
7. `gh pr create --base <base> --title "<título>" --body-file -` com o corpo pela entrada.
8. Mostre o link. O merge é feito pelo usuário (ou quando ele pedir), depois do pipeline verde, com
   merge commit — nunca squash nem rebase nas branches `homolog` e `main`.
