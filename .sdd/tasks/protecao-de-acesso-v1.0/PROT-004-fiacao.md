# PROT-004 — Fiação das proteções

- **Spec:** .sdd/specs/protecao-de-acesso.spec.md (v1.0) · atende RF-01, RF-02, RF-05, RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** PROT-001, PROT-002, PROT-003

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V4__criar_auditoria_login.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`
- `infra/.env.example`

## Descrição técnica

Negação por padrão com `@PreAuthorize`; filtro de limite antes da segurança; `AUTH_ORIGENS` e `server.forward-headers-strategy: native`.

## Restrições

Nenhuma rota nova pública sem método.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Registrar o filtro duas vezes conta cada requisição em dobro.
