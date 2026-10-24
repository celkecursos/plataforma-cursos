# AUD-001 — Trilha pelos eventos do Hibernate

- **Spec:** .sdd/specs/auditoria.spec.md (v1.0) · atende RF-01, RF-02, RF-03
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/OuvinteAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/TrilhaAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/ContextoAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/DadoSensivel.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/SemAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/ValoresAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/domain/RegistroAuditoria.java`

## Descrição técnica

Grava na transação da mudança só o que mudou; `@DadoSensivel` vira `***`.

## Critérios de aceite

- [x] `ContextoAuditoriaTest`, `ValoresAuditoriaTest` verdes
- [x] `AuditoriaIT` verde

## Armadilhas

Gravar fora da transação registra mudanças revertidas.
