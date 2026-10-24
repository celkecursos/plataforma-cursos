# SITE-006 — Fiação do site no servidor

- **Spec:** .sdd/specs/site-publico.spec.md (v1.1) · atende RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** SITE-004, SITE-005

## Arquivos a criar/alterar

- `web/Dockerfile`
- `infra/compose.prod.yml` — `site` na porta 3000
- `infra/caddy/sites/producao.caddy`
- `infra/caddy/sites/homolog.caddy`

## Critérios de aceite

- [x] Playwright e Lighthouse verdes
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
