# ADM-004 — Fiação do painel

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-01
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** ADM-001, ADM-002, ADM-003

## Arquivos a criar/alterar

- `web/adm/src/rotas.tsx`

## Descrição técnica

Rotas `cursos`, `cursos/:cursoId`, turmas, aulas e `termos`.

## Critérios de aceite

- [x] `npm test` e Playwright verdes
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
