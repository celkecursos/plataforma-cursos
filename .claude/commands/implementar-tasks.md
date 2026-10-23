---
description: Fase 3 do SDD — implementa as tasks, despachando cada uma ao implementador conforme o Nível (Pleno/Sênior).
argument-hint: <.sdd/tasks/…/README.md> <spec .sdd/specs/…spec.md>
---

Tasks: **$1** · Spec: **$2**

Você é o **orquestrador**: não implementa, despacha cada task ao subagente certo e revisa o resultado.

## Despacho

Leia `$1` (ordem e grafo). Para cada task, respeitando `Depende de:`:

1. Leia o `.md` da task e o campo `Nível:`.
2. Despache pela ferramenta Agent: `Pleno` → `dev-pleno`; `Sênior` → `dev-senior`.
3. No prompt: o conteúdo integral da task, o caminho da spec, o lembrete de ler `CLAUDE.md`,
   `backend/CLAUDE.md` e `.claude/rules/`, e o aviso de leva paralela quando for o caso.
4. Atualize o `**Status:**` da task: `Em andamento` antes de despachar, `Concluída` ou `Falhou` depois.

## Paralelismo

Tasks sem dependência pendente rodam juntas (vários subagentes numa mensagem). Duas coisas quebram em
silêncio e é você quem previne:

1. **Arquivo repetido = escrita perdida.** Compare os "Arquivos a criar/alterar" da leva; caminho
   repetido → serialize.
2. **Build concorrente se atrapalha.** `./mvnw verify` sobe containers de teste e grava em `target/`;
   `npm run build` grava em `dist/`. Na leva paralela, diga a cada agente para rodar só os testes da
   task (`./mvnw test -Dtest=...`, `npm test`, `npm run lint`, `npm run typecheck`); você roda a
   verificação completa quando a leva voltar.

## Depois de cada leva

Relate arquivos tocados, resultado da verificação e premissas assumidas. Falhou (teste vermelho,
conflito de arquitetura, escalonamento)? **Pare e consulte o usuário.**

## No fim (Portão 3)

- Backend: `./mvnw spotless:apply && ./mvnw verify`. Front: `npm run format:check && npm run lint &&
  npm run typecheck && npm test && npm run build`. Jornada que atravessa containers: e2e (`web/e2e/README.md`).
- Rode `/revisar-dod` e relate o que ele acusar.
- Resuma tasks, arquivos e resultados; aponte divergências entre spec e implementação.
- **Pare** para a revisão humana. Commit só quando o usuário pedir (`/gc` e `/pc`).
