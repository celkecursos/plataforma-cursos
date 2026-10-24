# PERFIL-002 — Termos versionados e aceite

- **Spec:** .sdd/specs/perfil-e-termos.spec.md (v1.0) · atende RF-04, RF-05, RF-06, RF-07
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/termo/domain/Termo.java`
- `backend/src/main/java/br/com/celke/cursos/termo/domain/AceiteTermo.java`
- `backend/src/main/java/br/com/celke/cursos/termo/domain/TipoTermo.java`
- `backend/src/main/java/br/com/celke/cursos/termo/application/TermoService.java`
- `backend/src/main/java/br/com/celke/cursos/termo/api/TermoController.java`
- `backend/src/main/java/br/com/celke/cursos/termo/api/TermoPendenteInterceptor.java`
- `backend/src/main/java/br/com/celke/cursos/termo/api/TermoWebConfig.java`
- `backend/src/main/java/br/com/celke/cursos/termo/infra/TermoRepository.java`
- `backend/src/main/java/br/com/celke/cursos/termo/infra/AceiteTermoRepository.java`

## Descrição técnica

Interceptor em `/api/aluno/**` e `/api/aulas/**` responde 403 com `termosPendentes`.

## Restrições

Perfil, sessão e termos continuam liberados.

## Critérios de aceite

- [x] `TermoServiceTest`, `TermoPendenteInterceptorTest` verdes
- [x] `TermoIT` e `PerfilTermosE2E` verdes

## Armadilhas

Bloquear também a rota de aceite trava o aluno.
