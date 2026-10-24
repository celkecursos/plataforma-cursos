# CURSO-005 — Fiação do catálogo

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CURSO-002, CURSO-003, CURSO-004

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V6__criar_cursos.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`

## Descrição técnica

Migration V6 com os `CHECK` de status e do formato do slug; catálogo público só em GET.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Restrição de ordem não adiável.
