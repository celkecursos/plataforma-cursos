# FORUM-004 — Fiação do fórum

- **Spec:** .sdd/specs/forum.spec.md (v1.0) · atende RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** FORUM-002, FORUM-003

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V12__criar_forum.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`

## Descrição técnica

`app.forum` com o limite por hora.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
