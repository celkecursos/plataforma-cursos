# PERFIL-003 — Fiação do perfil e dos termos

- **Spec:** .sdd/specs/perfil-e-termos.spec.md (v1.0) · atende RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** PERFIL-001, PERFIL-002

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V14__perfil_e_termos.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`

## Descrição técnica

`GET /api/termos/vigentes` público.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
