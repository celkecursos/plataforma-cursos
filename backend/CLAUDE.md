# Backend — guia do projeto Java

API e worker da Plataforma de Cursos: Java 25, Spring Boot 4.1, PostgreSQL 18, Flyway.
Os comandos abaixo rodam **dentro de `backend/`**.

## Definição de pronto

Toda tarefa só está concluída quando passa:

```bash
./mvnw spotless:apply      # formata (palantir-java-format)
./mvnw verify              # unitários + integração + cobertura mínima + spotless:check
./mvnw verify -Pe2e        # e2e, com a stack de e2e no ar (como subir: skill criar-teste-e2e)
```

O `verify` precisa do Docker rodando (Testcontainers) e do `ffmpeg`/`ffprobe` no PATH (a
transcodificação de vídeo é testada de verdade; o pipeline instala o ffmpeg no runner).

## Estrutura

```
src/main/java/br/com/celke/cursos/
  shared/          # config, security, erros, storage S3, fila, e-mail
  <modulo>/        # auth, usuario, curso, matricula, video, progresso, arquivo, forum, certificado, perfil
    api/           # controllers e DTOs (record)
    application/   # casos de uso
    domain/        # entidades, enums, regras
    infra/         # repositórios, integrações
src/main/resources/
  application.yaml            # padrão (perfil api)
  application-worker.yaml     # worker: sem servidor HTTP
  db/migration/               # Flyway
src/test/java/                # unitários (*Test) e integração (*IT)
src/e2e/java/                 # e2e caixa-preta (*E2E), perfil Maven e2e
Dockerfile                    # targets api e worker (com ffmpeg)
```

## Padrões de código

- **Idioma:** domínio em português (`Curso`, `Matricula`, `criarCurso`), sufixos técnicos em inglês (`Controller`, `Service`, `Repository`, `Policy`, `Test`, `IT`).
- **Controllers** só lidam com HTTP. Toda regra fica em `application/` ou `domain/`.
- **Um módulo não acessa o repositório de outro** — usa o serviço de `application/` exportado pelo outro módulo.
- **Rotas são privadas por padrão.** Rota pública é declarada em `shared/security/SecurityConfig` com o método HTTP; restrição por papel com `@PreAuthorize`.
- **Acesso a conteúdo do curso** (aula, vídeo, arquivo, fórum, progresso, certificado) passa **sempre** pela `AcessoAulaPolicy`.
- **Erros** no formato `ProblemDetail` (RFC 9457) pelo handler global; erro de validação traz a lista `erros` com `campo` e `mensagem`.
- **Data e hora** vêm de um `java.time.Clock` injetado — nunca `LocalDateTime.now()` direto em regra de negócio.
- **Status** são enums, nunca números soltos.
- **DTOs** são `record` com Bean Validation; toda rota documentada no OpenAPI.
- **Configuração:** variável nova → `application.yaml` com `${VARIAVEL}`, validação em `@ConfigurationProperties` quando a aplicação tiver propriedades próprias, e `infra/.env.example`.
- **Migrations** Flyway compatíveis com a versão anterior (expandir → depois contrair).

## Testes

Três níveis. **Antes de criar ou alterar um teste, leia o padrão do nível** — ele vale mesmo
quando a skill não é usada:

| Nível | O que cobre | Onde | Padrão | Skill |
|---|---|---|---|---|
| Unitário | regra de negócio, cálculo, política, validação de DTO — sem Spring, sem banco | `src/test/java/**/*Test.java` | `.claude/skills/criar-teste-unitario/padrao.md` | `/criar-teste-unitario <modulo>` |
| Integração | rotas, segurança, persistência, S3, fila, e-mail — Spring + Testcontainers | `src/test/java/**/*IT.java` | `.claude/skills/criar-teste-integracao/padrao.md` | `/criar-teste-integracao <modulo>` |
| e2e | jornadas que atravessam os containers (Caddy, worker, Garage, e-mail) — caixa-preta | `src/e2e/java/**/*E2E.java` | `.claude/skills/criar-teste-e2e/padrao.md` | `/criar-teste-e2e <jornada>` |

Funcionalidade nova vem com os níveis que se aplicam **no mesmo PR**.
