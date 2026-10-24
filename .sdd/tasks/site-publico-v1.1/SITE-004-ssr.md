# SITE-004 — Renderização no servidor

- **Spec:** .sdd/specs/site-publico.spec.md (v1.1) · atende RF-07, RF-08, RF-09
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `web/site/app/root.tsx`
- `web/site/app/entry.server.tsx`
- `web/site/app/api.server.ts` — cache de `SITE_CACHE_SEGUNDOS`
- `web/site/app/config.server.ts`
- `web/site/app/nonce.ts`
- `web/site/app/routes.ts`
- `web/site/react-router.config.ts`

## Descrição técnica

React Router em modo framework; servidor Node na porta 3000; 404 de verdade.

## Critérios de aceite

- [x] `web/e2e/site/site.spec.ts` verde

## Armadilhas

CSP sem nonce bloqueia a hidratação.
