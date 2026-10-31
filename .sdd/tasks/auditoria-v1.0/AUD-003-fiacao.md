# AUD-003 — Fiação da auditoria e dos logs

- **Spec:** .sdd/specs/auditoria.spec.md (v1.0) · atende RF-03, RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** AUD-001, AUD-002

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V16__criar_auditoria.sql` — gatilho só de inclusão
- `backend/src/main/resources/application.yaml`
- `infra/compose.prod.yml` — volume `logs`

## Descrição técnica

Logs em arquivo, um por dia, 180 dias ou 2 GB por serviço.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
