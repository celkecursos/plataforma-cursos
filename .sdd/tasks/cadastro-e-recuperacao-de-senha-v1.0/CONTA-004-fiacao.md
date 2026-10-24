# CONTA-004 — Fiação do cadastro

- **Spec:** .sdd/specs/cadastro-e-recuperacao-de-senha.spec.md (v1.0) · atende RF-10
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CONTA-002, CONTA-003

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V5__confirmacao_email_e_tokens.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`
- `infra/.env.example`
- `backend/src/main/java/br/com/celke/cursos/shared/fila/BatimentoWorker.java` — healthcheck do worker por arquivo de batimento

## Descrição técnica

Rotas da conta públicas só em POST e no limite por IP; `FRONT_ALUNO_URL` e SMTP no `.env.example`; contas existentes marcadas como confirmadas na migration.

## Restrições

SMTP sem valor no repositório.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Migration que não confirma as contas antigas bloqueia todos.
