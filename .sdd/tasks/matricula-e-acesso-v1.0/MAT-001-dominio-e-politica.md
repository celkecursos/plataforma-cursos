# MAT-001 — Matrícula e política única de acesso

- **Spec:** .sdd/specs/matricula-e-acesso.spec.md (v1.0) · atende RF-03, RF-04, RF-05
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/matricula/domain/Matricula.java` — `situacao(agora)` pelo `Clock`
- `backend/src/main/java/br/com/celke/cursos/matricula/domain/SituacaoMatricula.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/domain/StatusMatricula.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/domain/FormaPagamento.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/domain/AcessoAulaPolicy.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/application/AcessoAulaService.java` — `exigirAcesso` e `exigirAcessoTurma`
- `backend/src/main/java/br/com/celke/cursos/matricula/infra/MatriculaRepository.java`

## Descrição técnica

A política é uma classe pura com `Clock`; o serviço é a porta de entrada de vídeo, arquivo, fórum, progresso e certificado.

## Restrições

Fim inclusivo; nenhuma tarefa agendada para vencer matrícula.

## Critérios de aceite

- [x] `MatriculaTest`, `AcessoAulaPolicyTest` (instante antes, exato e depois) verdes
- [x] `AcessoConteudoIT` com a matriz de acesso ao conteúdo

## Armadilhas

Cada módulo reimplementar a regra de acesso.
