# PAG-005 — Fiação dos pagamentos

- **Spec:** .sdd/specs/pagamentos.spec.md (v1.0) · atende RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** PAG-004

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V15__criar_pagamentos.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`
- `infra/.env.example`
- `infra/scripts/gerar-env-servidor.sh` — `PAGAMENTOS_CHAVE`
- `infra/caddy/sites/homolog.caddy` — só o webhook aceita fora dos IPs
- `web/aluno/src/rotas.tsx`
- `web/adm/src/rotas.tsx`

## Critérios de aceite

- [x] `./mvnw verify` e Playwright verdes
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
