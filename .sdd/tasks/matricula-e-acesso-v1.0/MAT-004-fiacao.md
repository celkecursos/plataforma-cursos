# MAT-004 — Fiação da matrícula

- **Spec:** .sdd/specs/matricula-e-acesso.spec.md (v1.0) · atende RF-02, RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** MAT-002, MAT-003

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V7__criar_matriculas.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`

## Descrição técnica

Índices únicos parciais (uma ativa por aluno e turma; transação por forma de pagamento) e FK `RESTRICT` na turma.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Índice único total impede rematricular após cancelar.
