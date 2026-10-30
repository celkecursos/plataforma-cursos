# OPER-004 — Fiação da operação

- **Spec:** .sdd/specs/operacao.spec.md (v1.0) · atende RF-03
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** OPER-001, OPER-002, OPER-003

## Arquivos a criar/alterar

- `backend/src/main/resources/application.yaml`
- `infra/.env.example`
- `infra/scripts/gerar-env-servidor.sh` — `ALERTA_EMAIL`

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
