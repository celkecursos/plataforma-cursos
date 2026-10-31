# PROT-001 — Limite por IP e bloqueio por falhas

- **Spec:** .sdd/specs/protecao-de-acesso.spec.md (v1.0) · atende RF-02, RF-03, RF-04
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/api/LimitePorIpFilter.java` — Bucket4j antes da segurança, 429 com `Retry-After`
- `backend/src/main/java/br/com/celke/cursos/auth/application/BloqueioLoginService.java` — 5 por e-mail+IP, 20 por e-mail, 15 min
- `backend/src/main/java/br/com/celke/cursos/auth/application/LimitesProperties.java`

## Descrição técnica

O filtro conta toda requisição às rotas de sessão e conta. O bloqueio é verificado antes de calcular o hash e zera com login correto ou 1 h sem falhas.

## Restrições

Erro de filtro sai pelo `ProblemDetailJson`, no mesmo formato do handler global.

## Critérios de aceite

- [x] `LimitePorIpFilterTest`, `BloqueioLoginServiceTest` verdes
- [x] `AuthBloqueioIT` verde

## Armadilhas

Contar só as falhas no filtro deixa a força bruta lenta passar.
