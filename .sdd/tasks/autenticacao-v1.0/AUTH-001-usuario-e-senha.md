# AUTH-001 — Usuário, papéis e hash de senha

- **Spec:** .sdd/specs/autenticacao.spec.md (v1.0) · atende RF-07, RF-10
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/usuario/domain/Usuario.java` — entidade com e-mail único em minúsculas e papéis
- `backend/src/main/java/br/com/celke/cursos/usuario/domain/Papel.java` — `ALUNO`, `ADMIN`
- `backend/src/main/java/br/com/celke/cursos/usuario/domain/PoliticaSenha.java` — tamanho por papel
- `backend/src/main/java/br/com/celke/cursos/usuario/application/SenhaConfig.java` — Argon2 e aceite do formato `$2y$` com regravação
- `backend/src/main/java/br/com/celke/cursos/usuario/application/UsuarioService.java` — busca por e-mail normalizado
- `backend/src/main/java/br/com/celke/cursos/usuario/infra/UsuarioRepository.java`

## Descrição técnica

Usuário com e-mail normalizado (minúsculas, sem espaços nas pontas). O codificador de senha grava em Argon2 e reconhece o hash `$2y$` do sistema anterior, sinalizando a regravação no login.

## Restrições

Nunca devolver o hash em DTO. A política do administrador é 12 a 128 caracteres.

## Critérios de aceite

- [x] `UsuarioTest`, `PoliticaSenhaTest` e `UsuarioServiceTest` verdes
- [x] senha `$2y$` aceita e regravada em Argon2 no login

## Armadilhas

Comparar e-mail sem normalizar gera contas duplicadas por maiúsculas.
