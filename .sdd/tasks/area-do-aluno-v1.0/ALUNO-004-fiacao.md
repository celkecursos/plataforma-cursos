# ALUNO-004 — Fiação da área do aluno

- **Spec:** .sdd/specs/area-do-aluno.spec.md (v1.0) · atende RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** ALUNO-001, ALUNO-002, ALUNO-003

## Arquivos a criar/alterar

- `web/aluno/src/rotas.tsx`

## Descrição técnica

Rotas `turmas/:turmaId`, aulas, fórum, `certificados` e `perfil`.

## Critérios de aceite

- [x] `npm test` e Playwright verdes
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
