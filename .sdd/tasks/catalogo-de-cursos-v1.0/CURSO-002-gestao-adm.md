# CURSO-002 — Gestão pelo administrador

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-04, RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CURSO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/api/CursoAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/TurmaAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/ModuloAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/AulaAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/CursoRequests.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/CursoResponses.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/CursoAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/TurmaAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/ModuloAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/AulaAdmService.java`

## Descrição técnica

CRUD em `/api/adm/**`, `PATCH .../status` e `PUT .../ordem` com a lista completa de ids.

## Restrições

ADMIN conferido no `SecurityConfig` e com `@PreAuthorize`.

## Critérios de aceite

- [x] serviços `*AdmServiceTest` verdes
- [x] `CursoCrudIT` e `CursoAcessoIT` (matriz de papéis) verdes

## Armadilhas

Excluir item com filhos em vez de responder 409.
