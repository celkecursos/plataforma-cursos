# PLAY-002 — Progresso da aula e da turma

- **Spec:** .sdd/specs/reproducao-e-progresso.spec.md (v1.0) · atende RF-04, RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/progresso/api/ProgressoController.java`
- `backend/src/main/java/br/com/celke/cursos/progresso/api/ProgressoRequests.java`
- `backend/src/main/java/br/com/celke/cursos/progresso/application/ProgressoService.java`
- `backend/src/main/java/br/com/celke/cursos/progresso/domain/ProgressoAula.java`
- `backend/src/main/java/br/com/celke/cursos/progresso/domain/PercentualConclusao.java`
- `backend/src/main/java/br/com/celke/cursos/progresso/infra/ProgressoAulaRepository.java`

## Descrição técnica

`GET`/`PATCH /api/aluno/aulas/{id}/progresso` e `GET /api/aluno/turmas/{id}/progresso`.

## Restrições

Sempre no escopo do token; turma exige matrícula vigente.

## Critérios de aceite

- [x] `PercentualConclusaoTest`, `ProgressoServiceTest` verdes
- [x] `ProgressoIT` verde

## Armadilhas

Arredondar para cima mostra 100% sem ver tudo.
