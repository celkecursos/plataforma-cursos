# AUTH-002 — Credencial de acesso e renovação com troca

- **Spec:** .sdd/specs/autenticacao.spec.md (v1.0) · atende RF-01, RF-03, RF-04, RF-05, RF-08
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** AUTH-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/application/TokenAcessoService.java` — JWT HS256 de 10 min com `sub` e `papeis`
- `backend/src/main/java/br/com/celke/cursos/auth/application/JwtConfig.java` — Resource Server só com HS256, emissor e validade
- `backend/src/main/java/br/com/celke/cursos/auth/application/RefreshTokenService.java` — renovação opaca, troca, família e revogação
- `backend/src/main/java/br/com/celke/cursos/auth/application/TokenSeguro.java` — código aleatório e hash SHA-256
- `backend/src/main/java/br/com/celke/cursos/auth/domain/RefreshToken.java`
- `backend/src/main/java/br/com/celke/cursos/auth/infra/RefreshTokenRepository.java`

## Descrição técnica

A renovação guarda só o hash, troca a cada uso com bloqueio da linha (`SELECT ... FOR UPDATE`) e, na reutilização, revoga a família inteira numa transação própria (gravada mesmo com a sessão recusada). `JWT_SEGREDO` é validado ao subir.

## Restrições

Credencial de acesso nunca vai para cookie; renovação nunca vai para o corpo.

## Critérios de aceite

- [x] `TokenAcessoServiceTest`, `RefreshTokenServiceTest`, `TokenSeguroTest` verdes
- [x] `AuthTokenIT`: adulterado, vencido, outra chave, outro algoritmo, `alg: none` e outro emissor recusados
- [x] `AuthRefreshIT`: troca, reutilização revoga a família

## Armadilhas

Revogar a família na mesma transação que é revertida pelo erro apaga a revogação.
