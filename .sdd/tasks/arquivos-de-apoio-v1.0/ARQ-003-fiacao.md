# ARQ-003 — Fiação dos arquivos

- **Spec:** .sdd/specs/arquivos-de-apoio.spec.md (v1.0) · atende RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** ARQ-001, ARQ-002

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V11__criar_arquivos_apoio.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`

## Descrição técnica

`CHECK` com `num_nonnulls` (aula ou módulo) e FKs `RESTRICT`.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
