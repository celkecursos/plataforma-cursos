# MAT-003 — Meus cursos do aluno

- **Spec:** .sdd/specs/matricula-e-acesso.spec.md (v1.0) · atende RF-06, RF-07, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** MAT-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/matricula/api/AlunoController.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/application/AlunoCursosService.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/domain/FiltroMeusCursos.java`

## Descrição técnica

Abas `ATIVOS`, `ARQUIVADOS`, `EXPIRADOS`; turma e aula no escopo do token.

## Restrições

Matrícula de outro aluno responde 404.

## Critérios de aceite

- [x] `AlunoCursosServiceTest` verde
- [x] `AlunoCursosIT` e `MatriculaAcessoIT` (IDOR) verdes
- [x] `MatriculaE2E` verde

## Armadilhas

Usar id da URL sem conferir o dono.
