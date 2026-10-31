# CURSO-001 — Domínio de cursos, turmas, módulos e aulas

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-04
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/domain/Curso.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Turma.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Modulo.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Aula.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Slug.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Ordenacao.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/StatusCurso.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/StatusTurma.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/StatusConteudo.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/CursoRepository.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/TurmaRepository.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/ModuloRepository.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/AulaRepository.java`

## Descrição técnica

Ordem única por pai com restrição adiável; item novo no fim; o pai fica travado (`PESSIMISTIC_WRITE`) enquanto a ordem muda.

## Restrições

`ON DELETE RESTRICT` em toda a hierarquia.

## Critérios de aceite

- [x] `SlugTest`, `OrdenacaoTest` verdes
- [x] `ConteudoOrdemIT`: criação simultânea sem conflito

## Armadilhas

Reordenar posição por posição viola a restrição única no meio da troca.
