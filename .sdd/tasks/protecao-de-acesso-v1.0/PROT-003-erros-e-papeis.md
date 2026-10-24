# PROT-003 — Erros padronizados e matriz de papéis

- **Spec:** .sdd/specs/protecao-de-acesso.spec.md (v1.0) · atende RF-01, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/shared/erro/ManipuladorErros.java`
- `backend/src/main/java/br/com/celke/cursos/shared/erro/ProblemDetailJson.java`
- `backend/src/main/java/br/com/celke/cursos/shared/security/RespostasSeguranca.java`
- `backend/src/main/resources/messages.properties` — mensagens em português

## Descrição técnica

Todo erro sai em ProblemDetail com `instance` e `requestId`; validação traz `erros` (`campo`, `mensagem`).

## Restrições

Erro inesperado só com o id da requisição, sem stack trace.

## Critérios de aceite

- [x] `ManipuladorErrosIT` verde
- [x] 401/403/429 no mesmo formato

## Armadilhas

Mensagens do Spring em inglês vazando sem tradução.
