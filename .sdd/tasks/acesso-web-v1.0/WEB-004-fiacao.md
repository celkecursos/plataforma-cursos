# WEB-004 — Fiação dos fronts

- **Spec:** .sdd/specs/acesso-web.spec.md (v1.0) · atende RF-06, RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** WEB-002, WEB-003

## Arquivos a criar/alterar

- `web/package.json` — `@celke/kole-react` do GitHub com commit fixo
- `web/aluno/src/rotas.tsx`
- `web/adm/src/rotas.tsx`
- `web/Dockerfile`
- `web/Caddyfile` — CSP com `MIDIA_ORIGEM`, cache
- `infra/compose.prod.yml` — serviço `web`
- `.github/workflows/pipeline.yml` — Playwright

## Descrição técnica

Caddy interno serve aluno (:8081) e ADM (:8082); CSP libera só a mídia de `media.celke.uno`.

## Critérios de aceite

- [x] `npm test` e Playwright verdes
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Cache longo no `index.html` prende versão velha.
