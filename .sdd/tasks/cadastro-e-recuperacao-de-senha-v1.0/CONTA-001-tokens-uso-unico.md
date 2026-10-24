# CONTA-001 — Links de uso único

- **Spec:** .sdd/specs/cadastro-e-recuperacao-de-senha.spec.md (v1.0) · atende RF-07, RF-08
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/domain/TokenUsoUnico.java`
- `backend/src/main/java/br/com/celke/cursos/auth/domain/TipoToken.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/TokenUsoUnicoService.java` — novo invalida os anteriores; 3 por hora
- `backend/src/main/java/br/com/celke/cursos/auth/infra/TokenUsoUnicoRepository.java`

## Descrição técnica

Guarda só o hash (`TokenSeguro`). Um link novo do mesmo tipo invalida os anteriores; acima de 3 por hora por conta e tipo, nenhum e-mail sai.

## Restrições

Nunca guardar o código em texto.

## Critérios de aceite

- [x] `TokenUsoUnicoServiceTest` verde

## Armadilhas

Contar o limite por e-mail digitado em vez da conta.
