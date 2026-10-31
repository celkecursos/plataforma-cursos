# PROT-002 — Conferência de origem e auditoria de login

- **Spec:** .sdd/specs/protecao-de-acesso.spec.md (v1.0) · atende RF-05, RF-06, RF-07
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/api/VerificadorOrigem.java` — `Origin` e `Sec-Fetch-Site` em refresh e logout
- `backend/src/main/java/br/com/celke/cursos/auth/api/OrigemNaoPermitidaException.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/AuditoriaLoginService.java` — transação própria
- `backend/src/main/java/br/com/celke/cursos/auth/domain/AuditoriaLogin.java`
- `backend/src/main/java/br/com/celke/cursos/auth/domain/ResultadoLogin.java`
- `backend/src/main/java/br/com/celke/cursos/auth/infra/AuditoriaLoginRepository.java`

## Descrição técnica

Origens permitidas vêm de `AUTH_ORIGENS` (`https://aluno.celke.uno,https://adm.celke.uno`), normalizadas (minúsculas, sem barra final).

## Restrições

O IP vem do proxy interno (`forward-headers-strategy: native`).

## Critérios de aceite

- [x] `VerificadorOrigemTest` verde
- [x] `AuthOrigemIT`: outra aplicação do domínio recusada

## Armadilhas

Aceitar qualquer subdomínio de `celke.uno` abre CSRF pelas outras aplicações.
