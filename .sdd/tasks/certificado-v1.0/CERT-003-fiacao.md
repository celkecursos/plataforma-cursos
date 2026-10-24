# CERT-003 — Fiação do certificado

- **Spec:** .sdd/specs/certificado.spec.md (v1.0) · atende RF-01
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CERT-002

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V13__criar_certificados.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/TurmaAdmController.java` — `PUT /api/adm/turmas/{id}/certificado`

## Descrição técnica

Validação pública só em GET.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
