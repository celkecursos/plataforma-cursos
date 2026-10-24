# AUTH-003 — Rotas de login, renovação, saída e quem sou eu

- **Spec:** .sdd/specs/autenticacao.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-05, RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** AUTH-002

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/api/AuthController.java` — `POST /api/auth/login|refresh|logout|sair-de-todos`, `GET /api/auth/me`
- `backend/src/main/java/br/com/celke/cursos/auth/api/CookieRefresh.java` — cookie `refresh_token` HttpOnly, Secure, SameSite=Strict, Path=/api/auth, sem Domain
- `backend/src/main/java/br/com/celke/cursos/auth/api/LoginRequest.java`
- `backend/src/main/java/br/com/celke/cursos/auth/api/SessaoResponse.java`
- `backend/src/main/java/br/com/celke/cursos/auth/api/UsuarioResponse.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/AuthService.java` — mesmo tempo para e-mail inexistente e senha errada

## Descrição técnica

Para e-mail inexistente, o serviço calcula um hash falso para igualar o tempo de resposta.

## Restrições

Os hosts que recebem o cookie são `aluno.celke.uno` e `adm.celke.uno`, cada um só o seu.

## Critérios de aceite

- [x] `CookieRefreshTest`, `LoginRequestTest`, `AuthServiceTest` verdes
- [x] `AuthLoginIT` e `AuthAcessoIT` (matriz de papéis) verdes

## Armadilhas

Colocar `Domain` no cookie entrega a sessão às outras aplicações do domínio.
