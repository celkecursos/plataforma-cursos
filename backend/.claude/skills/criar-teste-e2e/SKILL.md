---
name: criar-teste-e2e
description: Cria ou amplia os testes e2e caixa-preta (REST Assured + Awaitility) de uma jornada contra a stack Docker completa — Caddy, API, worker, PostgreSQL, Garage e Mailpit. Use quando pedirem "teste e2e", "teste ponta a ponta", "testar a jornada" ou "/criar-teste-e2e <jornada>".
argument-hint: <jornada>
---

# Criar teste e2e de uma jornada

Argumento: `$ARGUMENTS` — a jornada (ex.: `cadastro`, `sessao`, `video`, `proxy`).

O **padrão** (o que é jornada e2e, organização, espera assíncrona, o que conferir) está em
[padrao.md](padrao.md) — leia antes de escrever. Esta skill é o **processo**.

## 1. Confirmar que é e2e

Descreva a jornada passo a passo e aponte **qual container ou peça de infraestrutura** só o
e2e alcança (Caddy, worker com ffmpeg, URL pré-assinada usada de fora, e-mail real). Se
nenhum passo depende disso, a jornada é teste de integração — diga isso ao usuário e use
`/criar-teste-integracao`.

## 2. Entender a stack

Leia:

1. `infra/compose.prod.yml` e `infra/compose.e2e.yml` — serviços, hosts, healthchecks,
   variáveis do profile `e2e`.
2. `infra/caddy/Caddyfile` — hosts, proxy de `/api`, headers.
3. Os controllers das rotas da jornada e o seed do profile `e2e`.
4. `backend/src/e2e/java/.../support/` e o perfil `e2e` do `backend/pom.xml` — o que já existe.

## 3. Propor

Apresente ao usuário: a classe (`<Jornada>E2E`), os passos da jornada, o que cada passo
confere e os tempos máximos de espera (Awaitility). Espere o ok antes de escrever.

## 4. Completar o que faltar

- Perfil Maven `e2e` no `backend/pom.xml`, se não existir: adiciona `src/e2e/java` e
  `src/e2e/resources` como fontes de teste, pula unitários e integração e roda só `*E2E`
  no Failsafe.
- `support/`:
  - `E2eConfig.java` — URLs base de `aluno`, `adm` e `media` por propriedade do sistema
    (padrão `http://aluno.localhost`, `http://adm.localhost`, `http://media.localhost`).
  - `Api.java` — `RequestSpecification` por host, `login(email, senha)`, `bearer(token)`,
    credenciais do seed do profile `e2e`.
  - `Mailpit.java` — buscar o último e-mail de um destinatário e extrair links.
  - `Dados.java` — `stamp()` com prefixo `e2e-`.
  - `Arquivos.java` — leitura das amostras em `src/e2e/resources/amostras/`.

## 5. Escrever e verificar

Na raiz do repositório:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml up -d --build --wait
```

Dentro de `backend/`:

```bash
./mvnw verify -Pe2e -Dit.test=<Jornada>E2E
./mvnw verify -Pe2e
./mvnw spotless:apply
```

Se falhar, leia `docker compose logs` antes de mexer no teste. Bug real não se contorna no
teste — avise o usuário com a jornada, o passo, o esperado e o obtido.

Por fim, resuma: classe criada, passos cobertos, tempo de execução e achados.
