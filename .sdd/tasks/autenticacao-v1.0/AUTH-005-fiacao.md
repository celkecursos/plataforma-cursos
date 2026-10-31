# AUTH-005 — Fiação da autenticação

- **Spec:** .sdd/specs/autenticacao.spec.md (v1.0) · atende RF-01, RF-03, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** AUTH-003, AUTH-004

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V2__criar_usuarios.sql`
- `backend/src/main/resources/db/migration/V3__criar_refresh_tokens.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`
- `infra/.env.example`

## Descrição técnica

Migrations de `usuarios`, `usuario_papeis` e `refresh_tokens`; rotas de sessão públicas só no método declarado; `JWT_SEGREDO` obrigatório no `.env.example`.

## Restrições

Nenhum segredo com valor no repositório.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Esquecer o método na rota pública libera outros métodos.
