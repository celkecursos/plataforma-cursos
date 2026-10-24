# WEB-001 — Cliente HTTP e sessão

- **Spec:** .sdd/specs/acesso-web.spec.md (v1.0) · atende RF-03, RF-04, RF-05
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `web/compartilhado/src/api/cliente.ts` — token em memória, renovação silenciosa e fila
- `web/compartilhado/src/api/erros.ts`
- `web/compartilhado/src/sessao/ProvedorSessao.tsx`
- `web/compartilhado/src/sessao/RotaPrivada.tsx`

## Descrição técnica

Requisições feitas durante a renovação esperam e repetem com o token novo.

## Restrições

Token nunca em `localStorage`.

## Critérios de aceite

- [x] Vitest do cliente e da sessão verdes

## Armadilhas

Duas renovações paralelas reutilizam o cookie e derrubam a sessão.
