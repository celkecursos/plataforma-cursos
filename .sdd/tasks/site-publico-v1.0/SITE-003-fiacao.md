# SITE-003 — Fiação do site

- **Spec:** .sdd/specs/site-publico.spec.md (v1.0) · atende RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** SITE-002

## Arquivos a criar/alterar

- `infra/caddy/sites/producao.caddy`
- `infra/caddy/sites/homolog.caddy`
- `.github/workflows/pipeline.yml` — Lighthouse

## Descrição técnica

Metas do Lighthouse no CI.

## Critérios de aceite

- [x] Lighthouse verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
