# ENTREGA-002 — Preparação da VPS e dos dois ambientes

- **Spec:** .sdd/specs/ambientes-e-entrega.spec.md (v1.0) · atende RF-06, RF-07, RF-08, RF-09
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** ENTREGA-001

## Arquivos a criar/alterar

- `infra/scripts/preparar-vps.sh`
- `infra/scripts/gerar-env-servidor.sh` — segredos aleatórios, `REGISTRO` e SMTP para preencher
- `infra/compose.homolog.yml`
- `infra/caddy/compose.yml`
- `infra/caddy/sites/producao.caddy`
- `infra/caddy/sites/homolog.caddy`
- `docs/vps.md`

## Descrição técnica

Um Caddy nas portas 80/443 ligado às redes `plataforma-cursos-producao` e `plataforma-cursos-homolog`; homologação e `adm.celke.uno` restritos por `HOMOLOG_IPS` e `ADM_IPS`; homologação com `noindex`.

## Restrições

Prompt: `f01-08-vps.md`. IPs e senhas só no servidor.

## Critérios de aceite

- [x] `shellcheck` sem avisos
- [x] e2e usando o mesmo `Caddyfile`

## Armadilhas

Senha básica no Caddy ocupa o cabeçalho `Authorization` do JWT.
