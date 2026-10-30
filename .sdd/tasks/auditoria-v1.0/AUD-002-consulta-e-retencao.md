# AUD-002 — Consulta no painel e retenção

- **Spec:** .sdd/specs/auditoria.spec.md (v1.0) · atende RF-04, RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** AUD-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auditoria/api/AuditoriaController.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/application/AuditoriaService.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/application/LimpezaAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/infra/RetencaoAuditoriaJdbc.java`
- `web/adm/src/paginas/auditoria/AuditoriaPagina.tsx`

## Descrição técnica

Filtros por usuário, e-mail, entidade, operação e período; antes e depois lado a lado.

## Critérios de aceite

- [x] `web/e2e/adm/auditoria.spec.ts` verde
