# CONTA-002 — Cadastro e confirmação de e-mail

- **Spec:** .sdd/specs/cadastro-e-recuperacao-de-senha.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-04, RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CONTA-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/application/CadastroService.java`
- `backend/src/main/java/br/com/celke/cursos/auth/api/ContaController.java` — `POST /api/auth/cadastro|confirmar-email|reenviar-confirmacao`
- `backend/src/main/java/br/com/celke/cursos/auth/api/ContaRequests.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/EmailNaoConfirmadoException.java`
- `backend/src/main/resources/templates/email/confirmacao-email.html`
- `backend/src/main/resources/templates/email/cadastro-existente.html`

## Descrição técnica

Resposta igual para e-mail novo e existente; conta ativa recebe aviso (1 por hora). Login de conta pendente só recebe 403 próprio com a senha certa.

## Restrições

Links apontam para `FRONT_ALUNO_URL` (`https://aluno.celke.uno`).

## Critérios de aceite

- [x] `CadastroServiceTest`, `ContaRequestsTest` verdes
- [x] `CadastroIT` com o e-mail lido no Mailpit

## Armadilhas

Responder 409 para e-mail existente revela a conta.
