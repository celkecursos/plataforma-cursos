# ENTREGA-001 — Stack de e2e e pipeline

- **Spec:** .sdd/specs/ambientes-e-entrega.spec.md (v1.0) · atende RF-01, RF-02
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `infra/compose.prod.yml` — stack de um ambiente, imagens de `${REGISTRO}`
- `infra/compose.e2e.yml`
- `infra/e2e.env` — só valores de teste
- `infra/caddy/Caddyfile` — só `/api/*` e `/actuator/health` chegam à API
- `.github/workflows/pipeline.yml` — testes → imagens → e2e → publicação em push
- `backend/src/e2e/` — `CadastroE2E`, `SessaoE2E`, `ProxyE2E`

## Descrição técnica

O pipeline roda em PR e em push para `homolog` e `main`; as imagens testadas recebem a tag do commit e vão para `ghcr.io/celkecursos` sem rebuild.

## Restrições

Prompt: `f01-07-e2e-e-pipeline.md`.

## Critérios de aceite

- [x] pipeline verde com as três jornadas
- [x] `docker compose logs` anexado em falha

## Armadilhas

Reconstruir a imagem para publicar publica algo que não foi testado.
