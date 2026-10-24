# Auditoria das ações e logs persistentes

**Fase:** 17
**Branch:** `feature/auditoria`

> **Configuração do projeto:** troque os marcadores `<dominio>`, `<sub-site>`, `<sub-aluno>`,
> `<sub-adm>`, `<sub-media>` e `<dono-github>` pelos valores de `docs/configuracao.md` (criado no
> primeiro prompt) em todo o conteúdo deste prompt, inclusive nos testes. Se o arquivo não existir,
> pergunte esses valores ao usuário antes de começar.

## Prompt

### Especificação e tasks (SDD)

Antes do código, registre a funcionalidade no SDD: crie a spec e as tasks abaixo com **exatamente** este conteúdo (troque os marcadores pelos valores de `docs/configuracao.md`). As tasks nascem com `Status: Pronta`; ao fim da **Verificação**, passam para `Concluída`.

`.sdd/specs/auditoria.spec.md`:

`````markdown
---
feature: auditoria
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Auditoria

## 1. Resumo Executivo

Toda alteração de dados fica registrada — quem mudou, o quê, antes e depois, de onde e quando — numa
trilha que não pode ser editada nem apagada, e o administrador a consulta pelo painel. Os registros
técnicos da API e do worker também ficam guardados em arquivo.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Consultar a trilha com filtros e ver o antes e depois lado a lado |
| Sistema | Registrar cada alteração e apagar o que passou do prazo |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE registrar, na mesma transação da mudança, o usuário, a operação, a entidade
  e o identificador, o antes e o depois (só o que mudou), a origem (rota ou tarefa), o IP, o navegador,
  o identificador da requisição e a data.
- **RF-02** — O sistema DEVE mascarar os dados sensíveis e deixar fora da trilha as entidades de registro
  e de sessão.
- **RF-03** — O sistema DEVE impedir no banco a alteração e a exclusão dos registros, só permitindo
  inclusão.
- **RF-04** — O sistema DEVE apagar os registros com mais de 5 anos.
- **RF-05** — O sistema DEVE oferecer a consulta com filtros (usuário, entidade, operação, período) e o
  antes e depois lado a lado.
- **RF-06** — O sistema DEVE guardar os registros técnicos da API e do worker em arquivo, um por dia, por
  180 dias ou 2 GB por serviço.

## 4. Modelo de Domínio

- **Registro de auditoria:** usuário, operação, entidade, identificador, antes, depois, origem, IP,
  navegador, requisição, data.

## 5. Estados e Fluxos

- **Mudança:** operação → registro na mesma transação → consulta no painel.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Consultar | filtros, página | registros |
| Detalhe | registro | antes e depois |

## 7. Casos de Borda

- Mudança desfeita (transação revertida): nenhum registro.
- Senha e segredos: aparecem como `***`.
- Tentativa de apagar registro direto no banco: recusada.

## 8. Premissas Validadas

- O prazo de 5 anos atende às obrigações de guarda dos registros financeiros e de matrícula.

## 9. Riscos

- **Crescimento da trilha:** só o que mudou e retenção de 5 anos.
- **Dado pessoal na trilha:** mascaramento dos campos sensíveis.
`````

`.sdd/tasks/auditoria-v1.0/README.md`:

`````markdown
# Tasks — Auditoria (v1.0)

Spec: [`.sdd/specs/auditoria.spec.md`](../../specs/auditoria.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f17-01-auditoria-e-logs.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [AUD-001](AUD-001-trilha.md) | Trilha pelos eventos do Hibernate | Sênior | — |
| 2 | [AUD-002](AUD-002-consulta-e-retencao.md) | Consulta no painel e retenção | Pleno | AUD-001 |
| 3 | [AUD-003](AUD-003-fiacao.md) | Fiação da auditoria e dos logs | Pleno | AUD-001, AUD-002 |

## Grafo

```
AUD-001 (início)
AUD-001 → AUD-002
AUD-001 + AUD-002 → AUD-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | AUD-001 |
| RF-02 | AUD-001 |
| RF-03 | AUD-001, AUD-003 |
| RF-04 | AUD-002 |
| RF-05 | AUD-002 |
| RF-06 | AUD-003 |

## Resumo por nível

- Pleno: 2
- Sênior: 1
`````

`.sdd/tasks/auditoria-v1.0/AUD-001-trilha.md`:

`````markdown
# AUD-001 — Trilha pelos eventos do Hibernate

- **Spec:** .sdd/specs/auditoria.spec.md (v1.0) · atende RF-01, RF-02, RF-03
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/OuvinteAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/TrilhaAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/ContextoAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/DadoSensivel.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/SemAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/shared/auditoria/ValoresAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/domain/RegistroAuditoria.java`

## Descrição técnica

Grava na transação da mudança só o que mudou; `@DadoSensivel` vira `***`.

## Critérios de aceite

- [ ] `ContextoAuditoriaTest`, `ValoresAuditoriaTest` verdes
- [ ] `AuditoriaIT` verde

## Armadilhas

Gravar fora da transação registra mudanças revertidas.
`````

`.sdd/tasks/auditoria-v1.0/AUD-002-consulta-e-retencao.md`:

`````markdown
# AUD-002 — Consulta no painel e retenção

- **Spec:** .sdd/specs/auditoria.spec.md (v1.0) · atende RF-04, RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** AUD-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auditoria/api/AuditoriaController.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/application/AuditoriaService.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/application/LimpezaAuditoria.java`
- `backend/src/main/java/br/com/celke/cursos/auditoria/infra/RetencaoAuditoriaJdbc.java`
- `web/adm/src/paginas/auditoria/AuditoriaPagina.tsx`

## Descrição técnica

Filtros por usuário, e-mail, entidade, operação e período; antes e depois lado a lado.

## Critérios de aceite

- [ ] `web/e2e/adm/auditoria.spec.ts` verde
`````

`.sdd/tasks/auditoria-v1.0/AUD-003-fiacao.md`:

`````markdown
# AUD-003 — Fiação da auditoria e dos logs

- **Spec:** .sdd/specs/auditoria.spec.md (v1.0) · atende RF-03, RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** AUD-001, AUD-002

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V16__criar_auditoria.sql` — gatilho só de inclusão
- `backend/src/main/resources/application.yaml`
- `infra/compose.prod.yml` — volume `logs`

## Descrição técnica

Logs em arquivo, um por dia, 180 dias ou 2 GB por serviço.

## Critérios de aceite

- [ ] `./mvnw verify` verde
- [ ] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
`````

Acrescente ao fim da tabela do índice `.sdd/README.md` a linha:

```text
| [auditoria](specs/auditoria.spec.md) | 1.0 | [v1.0](tasks/auditoria-v1.0/README.md) | `f17-01-auditoria-e-logs.md` |
```

Vamos registrar quem fez o quê nos dados da plataforma e guardar o log técnico por meses. Siga o
`backend/CLAUDE.md` e os padrões do `web/`. Não faça commit.

Duas entregas:

1. **Trilha de auditoria**: uma tabela `auditoria`, só de inclusão, com usuário, operação, entidade, id,
   antes e depois (sem dados sensíveis), origem (rota ou job), IP, navegador, id da requisição e data,
   gravada em toda mudança feita pela aplicação nos dados (ADM, área do aluno, cadastro, worker), com
   uma tela de consulta no ADM.
2. **Log persistente**: a API e o worker gravam o log também em arquivo, num volume, com retenção.

### 0. Por que sem biblioteca de auditoria

- **Hibernate Envers** cria uma tabela `_aud` por entidade: a consulta "o que aconteceu" fica espalhada e
  não guarda ação, IP nem rota sem código extra.
- **JaVers** guarda snapshots e diffs em tabelas e modelo próprios: dependência grande e trabalho com
  relacionamentos lazy.
- **Spring Data Auditing** (`@CreatedBy`, `@LastModifiedBy`) só guarda quem alterou por último na linha:
  não é trilha.

Use o mesmo mecanismo que o Envers usa por baixo: os eventos `PostInsert`, `PostUpdate` e `PostDelete`
do Hibernate, gravando numa tabela única, na mesma transação da mudança (o que foi desfeito não aparece).
Mudanças feitas direto em SQL (fila, notificações de pagamento, atualizações em lote) não passam por
ali, e está tudo bem: são do sistema e já têm registro próprio.

### 1. O que entra

- Migration `V16__criar_auditoria.sql`: tabela `auditoria` e o gatilho que só aceita inclusão (a
  retenção liga `app.auditoria_retencao` na transação para poder apagar).
- `shared/auditoria`: `@DadoSensivel`, `@SemAuditoria`, `ContextoAuditoria` (usuário, rota, IP,
  navegador, id da requisição; job da fila pelo MDC), `ValoresAuditoria`, `TrilhaAuditoria`,
  `OuvinteAuditoria` e o registro dele nos eventos do Hibernate. O `ProcessadorFila` marca o job no MDC.
- Módulo `auditoria`: leitura (`RegistroAuditoria`), filtros, retenção de 5 anos (worker, domingo 5h) e
  as rotas do ADM `GET /api/adm/auditoria`, `/entidades` e `/{id}`.
- Logs da API e do worker também em arquivo, no volume `logs` (um por dia de Brasília, 180 dias ou 2 GB
  por serviço).
- ADM: Segurança → Auditoria, com filtros e o antes/depois lado a lado.
- `README.md` com o passo a passo para executar o projeto (Docker e desenvolvimento), os testes e a
  documentação.

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos alterados (substitua o conteúdo inteiro)

`README.md`:

`````markdown
# Plataforma de Cursos

Plataforma de cursos online com área do aluno, painel administrativo (ADM) e site público, desenvolvida
passo a passo como tutorial.

## Índice

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Requisitos](#requisitos)
- [Executar com Docker (recomendado)](#executar-com-docker-recomendado)
- [Executar para desenvolvimento](#executar-para-desenvolvimento)
- [Testes](#testes)
- [Comandos úteis](#comandos-úteis)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Branches](#branches)
- [Documentação](#documentação)

## Funcionalidades

- **Área do aluno:** meus cursos (ativos, arquivados e expirados), turma com módulos e progresso, aula
  com vídeo HLS (retoma de onde parou), arquivos de apoio, fórum por aula e por turma, certificado em PDF
  com código de validação, perfil (nome, foto e senha), compra de cursos.
- **ADM:** cursos, turmas, módulos e aulas; envio de vídeo em partes com conversão para HLS; capa,
  arquivos de apoio, matrículas, moderação do fórum, regras do certificado e termos; meios de pagamento,
  ofertas, pedidos e notificações recebidas; trilha de auditoria.
- **Site público:** home, catálogo, página do curso com preço e botão de compra, validação de
  certificado, termos e privacidade, com SEO (Open Graph, dados estruturados, sitemap).
- **Conta e segurança:** cadastro com confirmação de e-mail, login com JWT e refresh em cookie
  `HttpOnly`, recuperação de senha, bloqueio por tentativas, limite por IP, termos versionados com aceite.
- **Pagamentos:** Hotmart (webhook 2.0.0) numa estrutura que aceita outros meios de pagamento.
- **Operação:** fila de jobs no PostgreSQL (worker), alertas por e-mail, backup e restauração, logs em
  JSON no horário de Brasília guardados em arquivo, auditoria de todas as alterações nos dados.

## Tecnologias

| Parte | Tecnologias |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL 18 |
| Vídeos e arquivos | Garage (S3) com URLs pré-assinadas, ffmpeg (HLS) |
| Frontend | React 19, TypeScript, Vite, `@celke/kole-react`, hls.js |
| Site | Páginas HTML geradas a partir da API (Node) |
| Testes | JUnit 5, Mockito, Testcontainers, REST Assured, Vitest, Testing Library, Playwright |
| Infraestrutura | Docker Compose, Caddy, GitHub Actions, deploy numa VPS |

## Requisitos

**Para executar com Docker:** Docker com o Docker Compose e Git.

**Para desenvolver:** além do Docker, JDK 25 e ffmpeg (para os testes do backend) e Node.js 24 (para os
fronts).

Portas usadas:

| Porta | Serviço |
|---|---|
| 8088 | Stack completa (Caddy): aluno, ADM, site e mídia |
| 8026 | Mailpit da stack completa (e-mails enviados) |
| 8080 | API (desenvolvimento) |
| 5173 e 5174 | Área do aluno e ADM (desenvolvimento, Vite) |
| 5432, 3900 e 8025 | PostgreSQL, Garage e Mailpit (desenvolvimento) |

## Executar com Docker (recomendado)

Sobe tudo como em produção — Caddy, API, worker, fronts, site, PostgreSQL, Garage e Mailpit — com contas
de exemplo já criadas. Os valores de `infra/e2e.env` são só para uso local.

**1. Clonar o repositório**

```bash
git clone https://github.com/<dono-github>/plataforma-cursos.git
cd plataforma-cursos
```

**2. Subir os serviços** (a primeira vez leva alguns minutos para construir as imagens)

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

Este comando:

- cria o banco e aplica as migrations;
- sobe a API, o worker (fila, e-mails e conversão de vídeo), os fronts e o gerador do site;
- cria o bucket no Garage e as contas de exemplo;
- deixa tudo atrás do Caddy na porta 8088.

**3. Acessar**

| Endereço | O quê |
|---|---|
| http://aluno.localhost:8088 | Área do aluno |
| http://adm.localhost:8088 | ADM |
| http://cursos.localhost:8088 | Site público (atualizado a cada 3 segundos a partir da API) |
| http://localhost:8026 | Mailpit: e-mails de confirmação, recuperação de senha e matrícula |

Os navegadores atuais resolvem `*.localhost` sozinhos; não é preciso mexer no `hosts`.

**4. Contas de exemplo**

| Conta | E-mail | Senha |
|---|---|---|
| Administrador | `admin@<dominio>` | `123456A#b123` |
| Aluno | `aluno@<dominio>` | `123456A#b123` |

Para ver um curso no site e na área do aluno: entre no ADM, crie o curso, uma turma (ative-a), um módulo
e uma aula, publique o curso e matricule o aluno na aba **Matrículas** da turma.

**5. Parar**

```bash
# Para os serviços e mantém os dados
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env stop
# Para e apaga tudo (banco, vídeos, logs)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

## Executar para desenvolvimento

API e worker no Docker (com Swagger e Mailpit) e os fronts no Vite, com recarga ao salvar.

**1. Gerar o `infra/.env`** com segredos aleatórios (uma vez)

```bash
./infra/scripts/gerar-env.sh
```

Para usar o ADM no Vite, acrescente no `infra/.env` a porta dele nas origens:

```bash
AUTH_ORIGENS=http://localhost:8080,http://localhost:5173,http://localhost:5174
S3_CORS_ORIGENS=http://localhost:5173,http://localhost:5174
```

**2. Subir o backend**

```bash
docker compose -f infra/compose.yml up -d --build --wait
```

| Endereço | O quê |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentação da API (Swagger) |
| http://localhost:8025 | Mailpit |
| http://localhost:8080/actuator/health | Saúde da API |

**3. Criar o administrador** (pede a senha no terminal; mínimo de 12 caracteres)

```bash
docker compose -f infra/compose.yml run --rm api criar-admin admin@exemplo.com "Administrador"
```

Alunos criam a conta pela tela de cadastro; o link de confirmação chega no Mailpit.

**4. Subir os fronts** (cada um num terminal)

```bash
cd web
npm ci
npm run dev:aluno   # http://localhost:5173
npm run dev:adm     # http://localhost:5174
```

O Vite manda `/api` para a API em `http://localhost:8080` (outra API: `VITE_API_PROXY`).

## Testes

**Backend** (dentro de `backend/`, com o Docker rodando e o ffmpeg no PATH):

```bash
./mvnw spotless:apply        # formata
./mvnw verify                # unitários, integração (Testcontainers), cobertura mínima e formatação
```

**Jornadas e2e** (com a stack completa no ar, como em "Executar com Docker"):

```bash
(cd backend && ./mvnw verify -Pe2e)    # API pelo Caddy, worker, Garage e e-mail
(cd web && npm run e2e)                # navegador real (Playwright): aluno, ADM e site
```

Como escolher o que rodar, ver os testes na tela (janela aberta, câmera lenta, modo UI), contas, dados de
teste e o que cada spec cobre: [`web/e2e/README.md`](web/e2e/README.md).

**Fronts** (dentro de `web/`):

```bash
npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

## Comandos úteis

```bash
# Situação dos containers (desenvolvimento)
docker compose -f infra/compose.yml ps

# Logs da API e do worker
docker compose -f infra/compose.yml logs -f api worker

# Reconstruir depois de mudar o backend
docker compose -f infra/compose.yml up -d --build --wait

# Parar o ambiente de desenvolvimento (com -v, apaga os dados)
docker compose -f infra/compose.yml down

# Abrir o banco de desenvolvimento
docker compose -f infra/compose.yml exec postgres psql -U plataforma_cursos plataforma_cursos
```

## Estrutura do projeto

```
backend/   API e worker (Java 25, Spring Boot 4.1): módulos por funcionalidade, testes e e2e
web/       Área do aluno e ADM (React + Vite), site público gerado a partir da API, testes Playwright
infra/     Docker Compose (local, servidores e e2e), Caddy, scripts de VPS, deploy e backup
docs/      Plano, roteiro dos capítulos, VPS, operação, pagamentos e prompts
```

## Branches

| Branch | Uso |
|---|---|
| `main` | Produção |
| `homolog` | Homologação |
| `feature/nome` | Nova funcionalidade (PR para `homolog`) |
| `fix/nome` | Correção (PR para `homolog`) |
| `hotfix/nome` | Correção urgente a partir da `main` |

## Documentação

- [Plano de implementação](docs/plano-implementacao.md)
- [Roteiro da VPS](docs/vps.md)
- [Operação: backup, alertas, logs, auditoria e segurança](docs/operacao.md)
- [Pagamentos (Hotmart)](docs/pagamentos.md)
- [Roteiro do tutorial (capítulos)](docs/capitulos.md)
- [Prompts para recriar o projeto do zero](docs/prompts-iniciais/README.md)
- [Prompts por fase](docs/prompts/README.md)
`````

`backend/Dockerfile`:

`````dockerfile
# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true \
    && java -Djarmode=tools -jar target/plataforma-cursos-*.jar extract --layers --launcher --destination target/extracted

# ---- Base de execução (sem root; curl para o healthcheck) ----
FROM eclipse-temurin:25-jre-noble AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --no-create-home app \
    && mkdir -p /var/log/plataforma-cursos && chown app:app /var/log/plataforma-cursos
WORKDIR /app

# ---- API ----
FROM runtime AS api
COPY --from=build /app/target/extracted/dependencies/ ./
COPY --from=build /app/target/extracted/spring-boot-loader/ ./
COPY --from=build /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /app/target/extracted/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=api
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ---- Worker (com ffmpeg) ----
FROM runtime AS worker
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/extracted/dependencies/ ./
COPY --from=build /app/target/extracted/spring-boot-loader/ ./
COPY --from=build /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /app/target/extracted/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=worker
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/AuditoriaLogin.java`:

`````java
package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@SemAuditoria
@Table(name = "auditoria_login")
public class AuditoriaLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResultadoLogin resultado;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AuditoriaLogin() {}

    public AuditoriaLogin(
            String email, Long usuarioId, ResultadoLogin resultado, String ip, String userAgent, Instant agora) {
        this.email = email;
        this.usuarioId = usuarioId;
        this.resultado = resultado;
        this.ip = ip;
        this.userAgent = userAgent;
        this.criadoEm = agora;
    }

    public String getEmail() {
        return email;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public ResultadoLogin getResultado() {
        return resultado;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/RefreshToken.java`:

`````java
package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refresh token gravado só pelo hash. Um token pertence a uma família (uma sessão de login). */
@Entity
@SemAuditoria
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private UUID familia;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RefreshToken() {}

    public RefreshToken(Long usuarioId, UUID familia, String hash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.familia = familia;
        this.hash = hash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    public boolean foiUsado() {
        return usadoEm != null;
    }

    public boolean estaRevogado() {
        return revogadoEm != null;
    }

    /** Expira no instante exato de {@code expiraEm}. */
    public boolean estaExpirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public UUID getFamilia() {
        return familia;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/TokenUsoUnico.java`:

`````java
package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Token de link enviado por e-mail, gravado só pelo hash. Vale uma vez e até expirar. */
@Entity
@SemAuditoria
@Table(name = "tokens_uso_unico")
public class TokenUsoUnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoToken tipo;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    /** Preenchido quando o token é usado ou invalidado por um token mais novo. */
    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected TokenUsoUnico() {}

    public TokenUsoUnico(Long usuarioId, TipoToken tipo, String hash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.hash = hash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    /** Ainda não usado, do tipo certo e dentro da validade (expira no instante exato). */
    public boolean podeSerUsado(TipoToken tipoEsperado, Instant agora) {
        return usadoEm == null && tipo == tipoEsperado && agora.isBefore(expiraEm);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public TipoToken getTipo() {
        return tipo;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/EventoRecebido.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notificação gravada (tabela {@code eventos_pagamento}). A inclusão é feita com
 * {@code INSERT ... ON CONFLICT DO NOTHING} pelo repositório (idempotência); o corpo e o evento
 * normalizado só são escritos ali e apagados pela retenção.
 */
@Entity
@SemAuditoria
@Table(name = "eventos_pagamento")
public class EventoRecebido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "id_externo", nullable = false, length = 150, updatable = false)
    private String idExterno;

    @Column(name = "tipo_original", nullable = false, length = 60, updatable = false)
    private String tipoOriginal;

    @Column(name = "recebido_em", nullable = false, updatable = false)
    private Instant recebidoEm;

    @Column(name = "ocorrido_em", updatable = false)
    private Instant ocorridoEm;

    @Column(length = 100, updatable = false)
    private String transacao;

    /** Corpo recebido (mascarado), em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(insertable = false, updatable = false)
    private String payload;

    /** {@link EventoPagamento} em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(insertable = false, updatable = false)
    private String dados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEvento status;

    private String erro;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "processado_em")
    private Instant processadoEm;

    protected EventoRecebido() {}

    public void processado(Long pedidoId, Instant agora) {
        this.status = StatusEvento.PROCESSADO;
        this.pedidoId = pedidoId;
        this.erro = null;
        this.processadoEm = agora;
    }

    public void ignorado(Long pedidoId, String motivo, Instant agora) {
        this.status = StatusEvento.IGNORADO;
        this.pedidoId = pedidoId;
        this.erro = motivo;
        this.processadoEm = agora;
    }

    public void falhou(String motivo, Instant agora) {
        this.status = StatusEvento.ERRO;
        this.erro = motivo;
        this.processadoEm = agora;
    }

    /** Volta para a fila (reprocessamento pelo painel). */
    public void reabrir() {
        this.status = StatusEvento.RECEBIDO;
        this.erro = null;
        this.processadoEm = null;
    }

    public Long getId() {
        return id;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getTipoOriginal() {
        return tipoOriginal;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public String getTransacao() {
        return transacao;
    }

    public String getPayload() {
        return payload;
    }

    public String getDados() {
        return dados;
    }

    public StatusEvento getStatus() {
        return status;
    }

    public String getErro() {
        return erro;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Gateway.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.shared.auditoria.DadoSensivel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Meio de pagamento configurado pelo painel. Os campos comuns ficam em {@code configuracao} (JSON);
 * os secretos, cifrados em {@code segredos} (quem cifra e decifra é a aplicação).
 */
@Entity
@Table(name = "gateways_pagamento")
public class Gateway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoGateway tipo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo;

    /** JSON com os campos não secretos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String configuracao;

    /** IV + texto cifrado (AES-256-GCM) do JSON com os campos secretos; nulo sem segredos. */
    @DadoSensivel
    private byte[] segredos;

    @DadoSensivel
    @Column(name = "webhook_token", nullable = false, length = 64, updatable = false)
    private String webhookToken;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Gateway() {}

    /** Gateway novo nasce inativo: o admin ativa depois de testar. */
    public Gateway(TipoGateway tipo, String nome, String webhookToken, Instant agora) {
        this.tipo = tipo;
        this.nome = nome.strip();
        this.webhookToken = webhookToken;
        this.configuracao = "{}";
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void configurar(String nome, String configuracao, byte[] segredos, Instant agora) {
        this.nome = nome.strip();
        this.configuracao = configuracao;
        this.segredos = segredos == null ? null : segredos.clone();
        this.atualizadoEm = agora;
    }

    public void ativar(Instant agora) {
        this.ativo = true;
        this.atualizadoEm = agora;
    }

    public void desativar(Instant agora) {
        this.ativo = false;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public TipoGateway getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getConfiguracao() {
        return configuracao;
    }

    public byte[] getSegredos() {
        return segredos == null ? null : segredos.clone();
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/domain/ProgressoAula.java`:

`````java
package br.com.celke.cursos.progresso.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Progresso de um aluno numa aula. Guarda só os ids, sem relacionamento JPA com outros módulos. */
@Entity
@SemAuditoria
@Table(name = "progresso_aulas")
public class ProgressoAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    /** Onde o aluno parou no vídeo. */
    @Column(name = "posicao_segundos", nullable = false)
    private int posicaoSegundos;

    @Column(nullable = false)
    private boolean vista;

    @Column(name = "vista_em")
    private Instant vistaEm;

    /** Marcada pelo aluno para ver de novo. */
    @Column(nullable = false)
    private boolean revisar;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ProgressoAula() {}

    public ProgressoAula(Long usuarioId, Long aulaId, Instant agora) {
        this.usuarioId = usuarioId;
        this.aulaId = aulaId;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Aplica só o que veio preenchido; desmarcar a aula vista apaga a data. */
    public void atualizar(Integer posicaoSegundos, Boolean vista, Boolean revisar, Instant agora) {
        if (posicaoSegundos != null) {
            if (posicaoSegundos < 0) {
                throw new IllegalArgumentException("A posição não pode ser negativa.");
            }
            this.posicaoSegundos = posicaoSegundos;
        }
        if (vista != null && vista != this.vista) {
            this.vista = vista;
            this.vistaEm = vista ? agora : null;
        }
        if (revisar != null) {
            this.revisar = revisar;
        }
        this.atualizadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public int getPosicaoSegundos() {
        return posicaoSegundos;
    }

    public boolean isVista() {
        return vista;
    }

    public Instant getVistaEm() {
        return vistaEm;
    }

    public boolean isRevisar() {
        return revisar;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ProcessadorFila.java`:

`````java
package br.com.celke.cursos.shared.fila;

import br.com.celke.cursos.shared.auditoria.ContextoAuditoria;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Reserva um job, entrega ao manipulador do tipo e registra o resultado na fila. */
@Component
public class ProcessadorFila {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessadorFila.class);

    private final FilaService fila;
    private final Map<String, ManipuladorJob> manipuladores;
    private final Clock clock;

    public ProcessadorFila(FilaService fila, List<ManipuladorJob> manipuladores, Clock clock) {
        this.fila = fila;
        this.manipuladores =
                manipuladores.stream().collect(Collectors.toUnmodifiableMap(ManipuladorJob::tipo, Function.identity()));
        this.clock = clock;
    }

    /** Processa um job; devolve {@code false} quando não havia job disponível. */
    public boolean processarProximo() {
        var reservado = fila.reservar(manipuladores.keySet(), clock.instant());
        if (reservado.isEmpty()) {
            return false;
        }
        Job job = reservado.get();
        ManipuladorJob manipulador = manipuladores.get(job.tipo());
        if (job.excedeuTentativas()) {
            desistir(manipulador, job, "Tentativas esgotadas (o processamento foi interrompido).");
            return true;
        }
        // A trilha de auditoria registra o job como origem das mudanças que ele fizer
        MDC.put(ContextoAuditoria.CHAVE_JOB, job.tipo());
        try {
            manipulador.processar(job);
            fila.concluir(job, clock.instant());
        } catch (ErroPermanenteException e) {
            LOG.error("Job {} ({}) falhou sem nova tentativa: {}", job.id(), job.tipo(), e.getMessage());
            desistir(manipulador, job, e.getMessage());
        } catch (Exception e) {
            LOG.warn("Job {} ({}) falhou na tentativa {}: {}", job.id(), job.tipo(), job.tentativas(), e.toString());
            fila.falhar(job, e.toString(), clock.instant());
            if (job.ultimaTentativa()) {
                avisar(manipulador, job, e.toString());
            }
        } finally {
            MDC.remove(ContextoAuditoria.CHAVE_JOB);
        }
        return true;
    }

    private void desistir(ManipuladorJob manipulador, Job job, String motivo) {
        fila.falharDefinitivo(job, motivo, clock.instant());
        avisar(manipulador, job, motivo);
    }

    /** O estado da fila já foi gravado: uma falha ao avisar o manipulador só vai para o log. */
    private static void avisar(ManipuladorJob manipulador, Job job, String motivo) {
        try {
            manipulador.desistir(job, motivo);
        } catch (RuntimeException e) {
            LOG.error("Job {} ({}): falha ao registrar a desistência", job.id(), job.tipo(), e);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/domain/Usuario.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import br.com.celke.cursos.shared.auditoria.DadoSensivel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    /** Nulo na conta criada por uma compra, até o dono criar a senha pelo link do e-mail. */
    @DadoSensivel
    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 20)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    /** Nulo enquanto o dono não confirmar o e-mail. */
    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    /** Foto de perfil no bucket ({@code fotos/<usuario>/<uuid>.jpg}); nula sem foto. */
    @Column(name = "foto_chave", length = 200)
    private String fotoChave;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {}

    public Usuario(
            String nome, String email, String senhaHash, Set<Papel> papeis, boolean emailConfirmado, Instant agora) {
        this.nome = nome.strip();
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.ativo = true;
        this.papeis = EnumSet.copyOf(papeis);
        this.emailConfirmadoEm = emailConfirmado ? agora : null;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** E-mail sempre em minúsculas e sem espaços nas pontas (o banco também exige minúsculas). */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Confirma o e-mail; confirmar de novo não muda a data da primeira confirmação. */
    public void confirmarEmail(Instant agora) {
        if (emailConfirmadoEm == null) {
            this.emailConfirmadoEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public boolean possuiSenha() {
        return senhaHash != null;
    }

    public boolean isEmailConfirmado() {
        return emailConfirmadoEm != null;
    }

    public void trocarHashSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    /** Troca a foto (nula remove) e devolve a chave da anterior, a apagar do bucket. */
    public String trocarFoto(String chave, Instant agora) {
        String anterior = this.fotoChave;
        this.fotoChave = chave;
        this.atualizadoEm = agora;
        return anterior;
    }

    public String getFotoChave() {
        return fotoChave;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Papel> getPapeis() {
        return Set.copyOf(papeis);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/AcessoVideo.java`:

`````java
package br.com.celke.cursos.video.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Abertura do vídeo de uma aula por um usuário (log de acesso, com retenção limitada). */
@Entity
@SemAuditoria
@Table(name = "acessos_video")
public class AcessoVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    @Column(nullable = false, length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "acessado_em", nullable = false, updatable = false)
    private Instant acessadoEm;

    protected AcessoVideo() {}

    public AcessoVideo(Long usuarioId, Long aulaId, String ip, String userAgent, Instant acessadoEm) {
        this.usuarioId = usuarioId;
        this.aulaId = aulaId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.acessadoEm = acessadoEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getAcessadoEm() {
        return acessadoEm;
    }
}
`````

`backend/src/main/resources/application.yaml`:

`````yaml
spring:
  application:
    name: plataforma-cursos
  threads:
    virtual:
      enabled: true
  # Sem usuário embutido: a autenticação é feita pelos usuários do banco
  autoconfigure:
    exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
  # Sem valor padrão: sem as credenciais a aplicação não sobe (o compose exige as variáveis)
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
  web:
    # Mensagens de validação em português
    locale: pt_BR
    locale-resolver: fixed
  mail:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: ${SMTP_AUTH:true}
      mail.smtp.starttls.enable: ${SMTP_STARTTLS:true}
      mail.smtp.starttls.required: ${SMTP_STARTTLS:true}
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 10000

logging:
  # Horário de Brasília nos logs: o @timestamp do JSON (ECS) e o texto do console do desenvolvimento
  structured:
    json:
      customizer: br.com.celke.cursos.shared.config.FusoHorarioLogs
  pattern:
    dateformat: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Sao_Paulo"
  # Arquivo de log (ligado nos servidores por LOGGING_FILE_NAME, num volume): um arquivo por dia de
  # Brasília, comprimido, guardado por 180 dias ou até 2 GB por serviço, o que vier primeiro
  logback:
    rollingpolicy:
      file-name-pattern: "${LOG_FILE}.%d{yyyy-MM-dd,America/Sao_Paulo}.%i.gz"
      max-file-size: 50MB
      max-history: 180
      total-size-cap: 2GB

server:
  shutdown: graceful
  # A API roda atrás do Caddy. Com "native", o Tomcat só aceita X-Forwarded-* vindo de proxy interno
  # (rede privada) e lê o X-Forwarded-For da direita para a esquerda: IP forjado pelo cliente é ignorado
  forward-headers-strategy: native
  error:
    include-stacktrace: never
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    # O SMTP fora do ar não deve tirar a API do ar: o envio é pela fila, com novas tentativas
    mail:
      enabled: false

# Swagger: ligado só no desenvolvimento e na homologação (DOCS_ENABLED=true)
springdoc:
  api-docs:
    enabled: ${DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${DOCS_ENABLED:false}

app:
  auth:
    # Segredo do JWT em Base64, com pelo menos 32 bytes (openssl rand -base64 48)
    jwt-segredo: ${JWT_SEGREDO:}
    emissor: plataforma-cursos
    validade-acesso: PT10M
    validade-refresh: P14D
    cookie-seguro: true
    # Origens do front que podem renovar e encerrar a sessão (separadas por vírgula)
    origens-permitidas: ${AUTH_ORIGENS:}
  conta:
    # Área do aluno: base dos links enviados por e-mail
    url-aluno: ${FRONT_ALUNO_URL:}
    validade-confirmacao: PT48H
    validade-recuperacao: PT1H
    emails-por-hora: 3
  limites:
    # Rotas de autenticação e conta: requisições por IP a cada minuto
    requisicoes-por-ip-por-minuto: 30
    # Falhas de login seguidas que bloqueiam o par e-mail + IP, e o e-mail vindo de qualquer IP
    falhas-por-email-e-ip: 5
    falhas-por-email: 20
    # Contadores de falha zeram depois de uma hora sem novas falhas
    janela-falhas: PT1H
    bloqueio: PT15M
  armazenamento:
    # Endereço usado pela aplicação (rede interna)
    endpoint: ${S3_ENDPOINT:}
    # Endereço que vai nas URLs pré-assinadas (o que o navegador acessa)
    endpoint-publico: ${S3_ENDPOINT_PUBLICO:}
    regiao: ${S3_REGIAO:garage}
    bucket: ${S3_BUCKET:}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    cors-origens: ${S3_CORS_ORIGENS:}
    dias-uploads-abandonados: 2
  email:
    remetente: ${EMAIL_REMETENTE:}
    nome-remetente: Plataforma de Cursos
  fila:
    max-tentativas: 5
    lease: PT10M
    backoff-base: PT30S
    backoff-maximo: PT1H
    intervalo: PT2S
    consumidor-ativo: false
  video:
    tamanho-maximo: 5GB
    # Validade das URLs de envio das partes (um vídeo grande numa conexão lenta)
    validade-envio: PT6H
    ffmpeg: ffmpeg
    ffprobe: ffprobe
    # veryfast: na VPS (1,5 vCPU para o worker) o ganho de compressão dos presets lentos não compensa o tempo
    preset: veryfast
    crf: 24
    diretorio-trabalho: ${java.io.tmpdir}/plataforma-cursos-videos
    # Validade das URLs dos segmentos = duração do vídeo + margem (pausas e idas e voltas no player)
    margem-playlist: PT2H
    # Log de acesso aos vídeos: 6 meses (prazo do Marco Civil para registros de acesso a aplicações)
    retencao-log-acesso: P180D
  forum:
    # Perguntas e respostas por aluno dentro da janela (o instrutor não tem limite)
    mensagens-por-janela: 20
    janela: PT1H
    anexos-por-mensagem: 3
  pagamentos:
    # Chave AES-256 dos segredos dos meios de pagamento: 32 bytes em Base64 (openssl rand -base64 32).
    # Em branco, a API sobe, mas o painel não grava credenciais
    chave: ${PAGAMENTOS_CHAVE:}
    webhook-tamanho-maximo: 256KB
    webhook-requisicoes-por-ip-por-minuto: 600
    # Corpo dos eventos processados ou ignorados é apagado depois disso (o resto fica para auditoria)
    retencao-payload: P180D
    # Disputa aberta mantém o acesso; true tira o acesso até a disputa terminar
    disputa-suspende: false
    # Cobrança atrasada mantém o acesso; com app.pagamentos.atraso-suspende-apos (ex.: P7D), o acesso
    # é tirado depois desse prazo
    reconciliacao:
      # Consulta diária na API do meio de pagamento (precisa das credenciais da API)
      ativa: false
      pendente-apos: PT24H
      sem-evento-apos: P3D
      janela: P60D
    hotmart:
      url-token: https://api-sec-vlc.hotmart.com/security/oauth/token
      url-api: https://developers.hotmart.com/payments/api/v1
      url-api-sandbox: https://sandbox.hotmart.com/payments/api/v1
      timeout: PT10S
  auditoria:
    # Trilha de auditoria guardada por 5 anos (prazo de prescrição das ações do consumidor); a limpeza é
    # semanal, no worker
    retencao: P1826D
  alertas:
    # Destinatário dos alertas do worker (jobs com erro, disco); em branco, desligados
    email: ${ALERTA_EMAIL:}
    disco-minimo-percentual: 15
    diretorio-disco: /
    repeticao-disco: PT6H
`````

`docs/operacao.md`:

`````markdown
# Operação

Rotina da plataforma depois que a VPS está no ar ([roteiro da VPS](vps.md)): backup e restauração,
monitoramento e alertas, logs, espaço em disco, saída para um S3 externo e a revisão de segurança.

## 1. Backup diário

`infra/scripts/backup.sh <ambiente>` guarda, com o [restic](https://restic.net), num repositório S3
**fora da VPS** (Cloudflare R2 ou Backblaze B2):

- o dump do PostgreSQL (`pg_dump --format=custom`), por stdin, sem arquivo temporário no disco;
- os arquivos do Garage, com o snapshot consistente dos metadados (`garage meta snapshot`); o SQLite em
  uso fica de fora.

Retenção: 7 diários, 4 semanais e 6 mensais por ambiente. O restic cifra tudo com a `RESTIC_PASSWORD`.

### Configuração (uma vez, na VPS)

1. Crie um bucket privado no R2 (ou B2) só para os backups e uma chave com acesso apenas a ele.
2. Na VPS, como `deploy`:

   ```bash
   sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
   install -m 600 /dev/null /opt/plataforma-cursos/backup.env
   nano /opt/plataforma-cursos/backup.env
   ```

   ```bash
   RESTIC_REPOSITORY=s3:https://<conta>.r2.cloudflarestorage.com/plataforma-cursos-backup
   RESTIC_PASSWORD=          # openssl rand -base64 48 — guarde também no gerenciador de senhas
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   ```

3. Agendamento (`crontab -e` do `deploy`), fora do horário de aula:

   ```cron
   30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
   45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1
   ```

> Sem a `RESTIC_PASSWORD` o backup não abre. Ela fica em dois lugares: no `backup.env` da VPS e no
> gerenciador de senhas do responsável.

## 2. Restauração

`infra/scripts/restaurar.sh <ambiente> [snapshot-postgres] [snapshot-garage]` para a API e o worker,
recria o banco a partir do dump, troca os arquivos do Garage pelos do backup (com os metadados do
snapshot) e sobe tudo de novo. Pede o nome do ambiente para confirmar (ou `CONFIRMAR=sim`).

```bash
docker run --rm --env-file /opt/plataforma-cursos/backup.env restic/restic:0.19.1 snapshots   # escolher
restaurar.sh homolog                                                                         # o mais recente
```

Se algo falhar no meio, a API e o worker **continuam parados de propósito** (subir com o banco pela
metade é pior): corrija e rode de novo.

### Teste de restauração (feito e repetido a cada trimestre)

Testado na stack de e2e, com um repositório restic local:

```bash
export MSYS_NO_PATHCONV=1 ENV_BACKUP=<backup.env com RESTIC_REPOSITORY=/repo> \
       PROJETO=plataforma-cursos-e2e RESTIC_EXTRA="-v restic-teste:/repo"
infra/scripts/backup.sh e2e
# estrago: UPDATE cursos SET titulo = 'ESTRAGADO'; e apagar /garage/data do volume
CONFIRMAR=sim infra/scripts/restaurar.sh e2e
```

Resultado: os títulos dos 7 cursos voltaram, os 11 objetos do bucket baixaram pela API S3 e a API e o
worker subiram saudáveis. O procedimento trimestral repete o teste na **homologação** com o backup da
produção mais recente.

## 3. Monitoramento e alertas

| O quê | Como |
|---|---|
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://<sub-aluno>.<dominio>/actuator/health` e `https://<sub-site>.<dominio>`, com aviso por e-mail e celular |
| Job que desistiu (conversão de vídeo, por exemplo) | O worker verifica a cada 15 minutos os jobs que foram para `ERRO` e envia um e-mail para `ALERTA_EMAIL` com o resumo |
| Pagamento contestado ou notificação com erro | Chargeback, disputa aberta e notificação de pagamento que foi para `ERRO` geram um e-mail para `ALERTA_EMAIL` na hora (veja `docs/pagamentos.md`) |
| Disco quase cheio | O mesmo verificador do worker mede o disco da VPS (o sistema de arquivos do container é o do host) e alerta abaixo de 15% livre, repetindo a cada 6 horas enquanto não melhorar |
| Container parado | `restart: unless-stopped` e o healthcheck de cada serviço; o deploy só conclui com tudo saudável |

`ALERTA_EMAIL` fica no `.env` de cada ambiente; em branco, os alertas do worker ficam desligados.
Falha no envio de e-mail não gera alerta por e-mail (iria pela mesma fila): aparece no log do worker.

## 4. Logs e limpeza

- **Logs do Docker** com rotação em todos os serviços (`json-file`, 5 arquivos de 10 MB por container):
  no máximo ~250 MB por ambiente, poucos dias de histórico. Os logs da API e do worker são JSON (ECS) com o id de correlação e o `@timestamp` no horário de Brasília (`-03:00`); o gerador do site e o `deploy.log` usam o mesmo fuso.
- **Logs persistentes da API e do worker**: além do console, cada um grava o mesmo JSON em
  `/var/log/plataforma-cursos/api.log` e `worker.log`, no volume `logs` do ambiente (sobrevive a deploy e
  recriação do container). Um arquivo por dia de Brasília, comprimido (`api.log.2026-10-22.0.gz`), novo
  arquivo a cada 50 MB, guardado por **180 dias** ou até **2 GB por serviço**, o que vier primeiro. Para
  ler, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api ls -lh /var/log/plataforma-cursos
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api tail -n 100 /var/log/plataforma-cursos/api.log
  # Um dia antigo, procurando um id de requisição (funciona com o container parado)
  docker run --rm -v plataforma-cursos-producao_logs:/logs:ro alpine \
    sh -c 'zcat /logs/api.log.2026-10-22.*.gz | grep 7f1c2a90'
  ```

  O volume não entra no backup diário (o que importa guardar por anos está no banco: a auditoria).
- **Uploads abandonados** (`uploads/` do bucket): a regra de expiração do bucket apaga em 2 dias, e os
  uploads em partes não concluídos em 1 dia. Os envios aceitos saem de `uploads/` na confirmação.
- **Log de acesso aos vídeos**: o worker apaga todo dia o que passou de 180 dias.
- **Dados da homologação**: quando acumular lixo de testes,
  `docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml down -v`
  e o próximo deploy da `homolog` recria tudo vazio (crie o admin de novo com `criar-admin`).

## 4.1 Auditoria

A tabela `auditoria` guarda cada inclusão, alteração e exclusão feita pela aplicação nos dados (ADM, área
do aluno, cadastro e worker): **quem** (usuário do token; vazio para visitante e sistema), **o quê**
(entidade e id), a **operação**, o **antes e o depois** dos campos (na alteração, só os que mudaram),
**de onde** (rota da requisição, como `PUT /api/adm/cursos/{id}`, ou o job da fila, como
`job pagamento.processar-evento`), **IP**, navegador, id da requisição e a data.

- Consulta no ADM: **Segurança → Auditoria**, com filtro por entidade, id, operação e e-mail do usuário.
- Dados sensíveis (hash de senha, segredos do meio de pagamento, token do webhook) aparecem como `***`:
  a trilha mostra que mudaram, nunca o valor.
- Ficam fora: progresso das aulas (muda a cada poucos segundos), log de acesso a vídeo, auditoria de
  login, notificações de pagamento (já são registros próprios) e tokens de sessão e de e-mail. Mudanças
  feitas direto em SQL (fila, atualizações em lote) também não passam pela trilha.
- O registro entra na mesma transação da mudança: o que foi desfeito não aparece.
- **Só inclusão**: o banco recusa `UPDATE` e `DELETE` na tabela (gatilho). A única exclusão é a retenção,
  feita pelo worker aos domingos, que apaga o que passou de **5 anos** (`app.auditoria.retencao`).
- Login e tentativas de login continuam na `auditoria_login`; acesso a vídeo na `acessos_video`.

## 5. Quanto vídeo cabe no disco

VPS de 100 GB NVMe. Reservas: sistema e imagens Docker (~15 GB), PostgreSQL e logs (~5 GB), uploads em
andamento (~10 GB) e os 15% livres do alerta (15 GB). Sobram **~55 GB para o HLS** (produção e
homologação juntas).

Cada hora de aula guarda as duas variantes (720p e 1080p) mais o áudio de cada uma:

| Conteúdo | Taxa média (720p + 1080p + áudio) | GB por hora | Horas em 55 GB |
|---|---|---|---|
| Tela de código, slides (o típico do curso) | ~1,1 Mbps | ~0,5 | ~110 h |
| Câmera e tela misturadas | ~3,9 Mbps | ~1,8 | ~30 h |
| Pior caso (sempre no teto: 2,8 + 5 Mbps) | ~8,1 Mbps | ~3,6 | ~15 h |

A conta do capítulo de limites da VPS usa o `segundosProcessamentoPorMinuto` e o `tamanhoFinal` que o
ADM mostra para cada vídeo: medir com os vídeos reais e atualizar esta tabela.

## 6. Saída para um S3 externo

Quando o HLS passar de ~40 GB, ou se a banda da VPS virar gargalo, os vídeos vão para o Cloudflare R2
(sem taxa de saída) ou o Backblaze B2. Como a aplicação só fala S3 pelo AWS SDK:

1. Criar o bucket no R2, a chave da aplicação e o CORS para `<sub-aluno>.<dominio>` e `<sub-adm>.<dominio>`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `<sub-media>.<dominio>` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; bloqueio após falhas por e-mail e por e-mail + IP; limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR) |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção |

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-<sub-aluno>.<dominio>/api/cursos -I
  ```
`````

`docs/vps.md`:

`````markdown
# Roteiro da VPS

Preparação da VPS da Hostinger (Ubuntu 24.04 ou mais novo; testado também no 26.04) para a produção e
a homologação. Feito uma vez; as
versões novas são implantadas pelo pipeline (merge na `homolog` → homologação; merge na `main` →
produção), pelo script `/usr/local/bin/plataforma-cursos-deploy`.

## Como fica a VPS

```
/opt/plataforma-cursos/            (dono: deploy, permissão 750)
├── caddy/                         Caddy único nas portas 80/443 (projeto plataforma-cursos-caddy)
│   ├── compose.yml
│   ├── Caddyfile
│   ├── sites/producao.caddy
│   ├── sites/homolog.caddy
│   └── .env                       hosts, e-mail do Let's Encrypt, IPs liberados
├── producao/                      projeto plataforma-cursos-producao, rede plataforma-cursos-producao
│   ├── compose.prod.yml
│   ├── garage/garage.toml
│   ├── .env                       segredos da produção (TAG = versão no ar)
│   ├── versoes/<sha>/             arquivos de compose das últimas versões (rollback)
│   ├── backups/                   dump do banco antes de cada deploy
│   └── deploy.log                 histórico dos deploys
└── homolog/                       projeto plataforma-cursos-homolog, rede plataforma-cursos-homolog
    ├── compose.prod.yml
    ├── compose.homolog.yml
    ├── garage/garage.toml
    └── .env                       segredos da homologação (diferentes dos da produção)
```

Só o Caddy publica portas. Ele chega à API, aos fronts e ao Garage de cada ambiente pela rede do
ambiente, pelos apelidos `producao-api`, `producao-web`, `producao-garage`, `homolog-api`, `homolog-web`
e `homolog-garage`. Em `aluno` e `adm`, `/api/*` e o health check vão para a API; o resto vai para o
container `web` (área do aluno na porta 8081, ADM na 8082). Em `cursos`, só as leituras públicas da
API (catálogo, termos vigentes, validação de certificado) vão para ela; o resto é o site público, que o
container `site` gera a partir da API ao subir e a cada 5 minutos (`SITE_INTERVALO`), servido pelo
`web` na porta 8083.

| Host | Ambiente | Acesso |
|---|---|---|
| `<sub-aluno>.<dominio>` | produção | público |
| `<sub-adm>.<dominio>` | produção | só `ADM_IPS` |
| `<sub-site>.<dominio>` | produção | público (site gerado a partir da API) |
| `<sub-media>.<dominio>` | produção | público (URLs assinadas) |
| `homolog-<sub-aluno>.<dominio>`, `homolog-<sub-adm>.<dominio>`, `homolog-<sub-media>.<dominio>` | homologação | só `HOMOLOG_IPS`, `noindex`, com Swagger |

## 1. Contratar a VPS na Hostinger

1. Contrate a VPS pelo link https://celke.com.br/page/hostinger e use o cupom **CELKE** no pagamento.
   Recomendado: plano **KVM 4** (4 vCPU, 16 GB de RAM, 200 GB de disco NVMe), com folga para a
   produção e a homologação na mesma VPS e para a conversão dos vídeos. Sistema **Ubuntu 24.04**, sem
   painel de controle; defina uma senha forte para o root (ela só vale até o passo 4).
2. No painel da Hostinger, anote o IP da VPS.
3. Firewall da VPS: libere só 22, 80 e 443 (TCP) e 443 (UDP). O `ufw` faz o mesmo dentro da VPS; o
   painel é a segunda camada.
4. Ative o backup/snapshot semanal do painel (não substitui o backup fora da VPS da fase de operação).

## 2. DNS de <dominio>

Crie registros **A** apontando para o IP da VPS (só subdomínios; o domínio principal continua com as
outras aplicações):

```
cursos          A   <ip-da-vps>
aluno           A   <ip-da-vps>
adm             A   <ip-da-vps>
media           A   <ip-da-vps>
homolog-cursos  A   <ip-da-vps>
homolog-aluno   A   <ip-da-vps>
homolog-adm     A   <ip-da-vps>
homolog-media   A   <ip-da-vps>
```

Confira antes de seguir: `dig +short <sub-aluno>.<dominio>` responde o IP da VPS.

## 3. Chaves SSH

São **duas chaves diferentes**, as duas geradas no **seu computador** (nunca na VPS: a chave privada do
deploy ficaria guardada no próprio servidor que ela abre). De cada uma, só a parte pública (`.pub`) vai
para a VPS.

| Chave | Quem usa | Pública (`.pub`) vai para | Privada fica |
|---|---|---|---|
| administrador | você, para entrar na VPS | `ADMIN_CHAVE` | no seu computador |
| deploy | o GitHub Actions, para publicar | `DEPLOY_CHAVE` | no secret `VPS_SSH_KEY` (passo 8) |

Não use a mesma chave nas duas: a do deploy só executa o script de deploy (sem terminal), e a do
administrador dá acesso completo com sudo.

No Linux, macOS ou Git Bash:

```bash
# Sua chave (administrador). Use a que já tem, se houver (id_ed25519.pub ou id_rsa.pub):
cat ~/.ssh/id_ed25519.pub

# Chave só do deploy automático
ssh-keygen -t ed25519 -f ~/.ssh/plataforma-cursos-deploy -C "github-deploy" -N ""
cat ~/.ssh/plataforma-cursos-deploy.pub
```

No PowerShell do Windows:

```powershell
Get-Content $HOME\.ssh\id_ed25519.pub     # ou id_rsa.pub; sem nenhuma: ssh-keygen -t ed25519
ssh-keygen -t ed25519 -f $HOME\.ssh\plataforma-cursos-deploy -C "github-deploy" -N '""'
Get-Content $HOME\.ssh\plataforma-cursos-deploy.pub
```

Cada `.pub` é **uma linha inteira**: `ssh-ed25519 AAAAC3Nza...` (cerca de 80 caracteres) ou
`ssh-rsa AAAAB3Nza...` (centenas de caracteres). Copie a linha toda; uma chave cortada deixa você fora
da VPS no fim do passo 4.

## 4. Preparar a VPS

O script recebe três valores:

- `ADMIN_USUARIO`: o nome do **seu** usuário Linux na VPS (ex.: `cesar`). Ele ainda não existe: o script
  cria, com sudo e acesso ao Docker. Não é o usuário do painel da Hostinger nem o do GitHub. Depois do
  script, é com ele que você entra (`ssh cesar@<ip-da-vps>`), porque o root deixa de entrar por SSH.
- `ADMIN_CHAVE`: a linha do `.pub` da sua chave de administrador.
- `DEPLOY_CHAVE`: a linha do `.pub` da chave do deploy. O usuário `deploy` tem nome fixo.

Do seu computador, na raiz do repositório:

```bash
scp infra/scripts/preparar-vps.sh root@<ip-da-vps>:/root/
ssh root@<ip-da-vps>
```

Na VPS, como root. Primeiro garanta o fim de linha LF (um checkout do Windows feito antes do
`.gitattributes` pode ter trazido CRLF, e o bash falha com `set: pipefail: invalid option name`):

```bash
cd /root
sed -i 's/\r$//' preparar-vps.sh
grep -c $'\r' preparar-vps.sh          # 0
```

Depois rode o script, **numa linha só** e com as chaves entre aspas:

```bash
ADMIN_USUARIO=cesar ADMIN_CHAVE="ssh-ed25519 AAAA... sua chave" DEPLOY_CHAVE="ssh-ed25519 AAAA... github-deploy" bash preparar-vps.sh
```

Para quebrar em várias linhas, termine cada uma com `\` (sem espaço depois dela) e não esqueça a última,
`bash preparar-vps.sh`: sem ela as variáveis são definidas e nada é executado. As linhas com `#` no
começo do script são só o exemplo de uso; não precisa editar o arquivo.

O script pede a senha do sudo do administrador (o login continua só por chave) e termina com
**"Pronto."**. A chave do deploy fica presa ao script de deploy (`command=` no `authorized_keys`): ela
não abre shell, terminal nem túnel, mesmo se vazar.

**Não feche a sessão de root** até confirmar, em outro terminal:

```bash
ssh cesar@<ip-da-vps>      # entra sem senha
sudo -v                    # aceita a senha definida pelo script
sudo ufw status            # ativo, com OpenSSH, 80/tcp, 443/tcp e 443/udp
swapon --show              # /swapfile de 2G
```

Depois disso, `ssh root@<ip-da-vps>` e login por senha passam a ser recusados.

### Se algo der errado no passo 4

- **O script parou no meio**: ele pode ser rodado de novo, com os mesmos valores; o que já existe é
  mantido e só a senha do sudo é pedida outra vez. Se ele parou depois de `==> SSH só por chave e sem
  root`, o root pode já não entrar por SSH (no Ubuntu 24.04+ o SSH sobe por socket e cada conexão nova
  já lê a configuração). Entre como administrador, copie o script para a pasta dele
  (`scp infra/scripts/preparar-vps.sh cesar@<ip-da-vps>:~/`) e rode como root com `sudo -i`.
- **`sudo -i` e os comandos seguintes colados juntos**: as linhas coladas se perdem enquanto o shell do
  root abre. Rode o `sudo -i` sozinho e os comandos depois, um de cada vez.
- **`ssh cesar@...` recusado** (`Permission denied (publickey)`), quase sempre por chave incompleta no
  `ADMIN_CHAVE`: entre pelo **terminal do navegador** no painel da Hostinger (VPS → Terminal), que não
  passa pelo SSH, desfaça o bloqueio com `rm /etc/ssh/sshd_config.d/01-plataforma.conf` e rode o script
  de novo com a chave completa.
- **`scp` roda no seu computador**, não dentro da VPS: na VPS ele tenta conectar nela mesma e é recusado.

## 5. Copiar os arquivos da infraestrutura

Do seu computador, na raiz do repositório:

```bash
VPS=cesar@<ip-da-vps>
ssh $VPS 'mkdir -p /tmp/plataforma && cd /tmp/plataforma && mkdir -p caddy/sites producao/garage homolog/garage'
scp infra/caddy/compose.yml infra/caddy/Caddyfile infra/caddy/.env.example $VPS:/tmp/plataforma/caddy/
scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/plataforma/caddy/sites/
scp infra/compose.prod.yml $VPS:/tmp/plataforma/producao/
scp infra/compose.prod.yml infra/compose.homolog.yml $VPS:/tmp/plataforma/homolog/
scp infra/garage/garage.toml $VPS:/tmp/plataforma/producao/garage/
scp infra/garage/garage.toml $VPS:/tmp/plataforma/homolog/garage/
scp infra/scripts/gerar-env-servidor.sh infra/scripts/deploy.sh $VPS:/tmp/plataforma/
ssh $VPS 'sudo install -o root -g root -m 755 /tmp/plataforma/deploy.sh /usr/local/bin/plataforma-cursos-deploy \
  && rm /tmp/plataforma/deploy.sh \
  && sudo cp -r /tmp/plataforma/. /opt/plataforma-cursos/ && sudo chown -R deploy:deploy /opt/plataforma-cursos \
  && rm -rf /tmp/plataforma'
```

## 6. Configuração de cada ambiente

Na VPS, como `deploy` (`sudo -iu deploy`):

```bash
cd /opt/plataforma-cursos
bash gerar-env-servidor.sh producao producao/.env
bash gerar-env-servidor.sh homolog homolog/.env
cp caddy/.env.example caddy/.env && chmod 600 caddy/.env
```

Preencha:

- `producao/.env` e `homolog/.env`: `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` (painel da Iagente; crie a conta pelo link https://celke.com.br/page/iagente, que dá
  alguns e-mails gratuitos para enviar).
  Na homologação, use um remetente identificado ou restrinja os envios a e-mails de teste.
- `caddy/.env`: `ACME_EMAIL`, `ADM_IPS` e `HOMOLOG_IPS` (seus IPs, separados por espaço).

Os segredos de cada `.env` são gerados aleatoriamente e são diferentes entre os ambientes: um token de
homologação nunca vale na produção.

## 7. Acesso às imagens (GHCR)

Crie no GitHub um token clássico só com `read:packages` e, na VPS, como `deploy`:

```bash
echo "<token>" | docker login ghcr.io -u <usuario-github> --password-stdin
```

No `.env` de cada ambiente, preencha `REGISTRO=ghcr.io/<dono-do-repositorio>` (em minúsculas): é
onde o pipeline publica as imagens e de onde o deploy baixa a API, o worker e os fronts.

## 8. Deploy automático no GitHub

Feito com a conta dona do repositório (colaboradores não veem as configurações). Em **Settings →
Secrets and variables → Actions**, crie os secrets do repositório (servem aos dois ambientes: a VPS e a
chave são as mesmas):

| Secret | Valor |
|---|---|
| `VPS_HOST` | IP da VPS |
| `VPS_SSH_KEY` | conteúdo de `~/.ssh/plataforma-cursos-deploy` (a chave **privada** do deploy) |
| `VPS_KNOWN_HOSTS` | saída de `ssh-keyscan -t ed25519 <ip-da-vps>` (conferida com o painel da Hostinger) |

Na aba **Variables** da mesma página, crie a variável `DEPLOY_ATIVO` com o valor `true`. Sem ela, o
pipeline pula os deploys (testes, e2e e publicação das imagens continuam rodando).

Os jobs de deploy usam os environments `homolog` e `production`, criados automaticamente na primeira
execução. Se o plano da conta permitir environments em repositório privado, ative em **Settings →
Environments → production** a opção **Required reviewers**: o deploy da produção passa a esperar a sua
aprovação.

## 9. Primeira subida

1. Abra um PR de `feature/base-projeto` para `homolog`, espere o pipeline ficar verde e faça o merge.
   O pipeline publica as imagens no GHCR e implanta a homologação (é a primeira subida dela: sem backup
   e sem versão anterior).
2. Suba o Caddy na VPS, como `deploy`. Ele se liga às redes dos dois ambientes, e a da produção só nasce
   no deploy dela: na primeira vez, crie essa rede com os rótulos do Docker Compose (o deploy da produção
   a adota depois). Até lá, os endereços da produção respondem 502.

   ```bash
   cd /opt/plataforma-cursos
   docker network inspect plataforma-cursos-producao > /dev/null 2>&1 || docker network create \
     --label com.docker.compose.network=default --label com.docker.compose.project=plataforma-cursos-producao \
     plataforma-cursos-producao
   docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d
   ```

3. Valide a homologação (passo 10, nos endereços `homolog-*`) e abra o PR de `homolog` para `main`:
   o pipeline implanta a produção.

4. Crie o administrador de cada ambiente (a senha é pedida no terminal):

   ```bash
   docker compose -p plataforma-cursos-producao -f producao/compose.prod.yml --env-file producao/.env \
     --project-directory producao run --rm api criar-admin admin@<dominio> "Administrador"
   docker compose -p plataforma-cursos-homolog -f homolog/compose.prod.yml -f homolog/compose.homolog.yml \
     --env-file homolog/.env --project-directory homolog run --rm api criar-admin admin@<dominio> "Administrador"
   ```

## 10. Verificação

Do seu computador:

```bash
curl -s https://<sub-aluno>.<dominio>/actuator/health            # {"groups":[...],"status":"UP"}
curl -sI https://<sub-aluno>.<dominio>/actuator/health | grep -i strict-transport
curl -s https://<sub-aluno>.<dominio>/ | grep -o '<title>.*</title>'   # a área do aluno
curl -s https://<sub-aluno>.<dominio>/v3/api-docs | head -c 15        # <!doctype html> (o front, não a API)
curl -s -o /dev/null -w "%{http_code}\n" https://<sub-adm>.<dominio>/actuator/health # 403 fora de ADM_IPS
nc -zv -w 3 <ip-da-vps> 5432 ; nc -zv -w 3 <ip-da-vps> 3900 ; nc -zv -w 3 <ip-da-vps> 8080   # recusadas
ssh root@<ip-da-vps>                                        # recusado
```

Na VPS: `docker stats --no-stream` mostra cada container dentro do limite de memória.

## 11. Operação

**Histórico dos deploys**: `tail -n 50 /opt/plataforma-cursos/<ambiente>/deploy.log`. Um deploy que não
fica saudável volta sozinho para a versão anterior e o job do pipeline falha.

**Voltar para uma versão antiga** (uma das guardadas em `versoes/`), como `deploy`:

```bash
tar -C /opt/plataforma-cursos/producao/versoes/<sha> -cz compose.prod.yml garage/garage.toml \
  | plataforma-cursos-deploy producao <sha>
```

O rollback não desfaz migrations: por isso toda migration precisa ser compatível com a versão anterior.

**Atualizar o Caddy** (é compartilhado pelos dois ambientes, então não entra no deploy automático).
Do seu computador, na raiz do repositório, com a `main` atualizada (`git switch main && git pull`): os
arquivos têm que ser os da versão no ar, não os de uma branch de trabalho antiga. Como na etapa 5, o
`scp` roda no seu computador; o `deploy` não tem terminal e `/opt/plataforma-cursos` só ele lê, por isso
a cópia passa pelo `/tmp` do administrador e o `docker compose` roda com `sudo -u deploy`:

```bash
VPS=cesar@<ip-da-vps>
ssh $VPS 'rm -rf /tmp/caddy && mkdir -p /tmp/caddy/sites'
scp infra/caddy/compose.yml infra/caddy/Caddyfile $VPS:/tmp/caddy/
scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/caddy/sites/
ssh -t $VPS 'C=/opt/plataforma-cursos/caddy \
  && sudo cp -p $C/Caddyfile $C/Caddyfile.bak-$(date +%F-%H%M) \
  && sudo cp -r /tmp/caddy/. $C/ && sudo chown -R deploy:deploy $C && rm -rf /tmp/caddy \
  && sudo -u deploy docker compose -f $C/compose.yml --env-file $C/.env --project-directory $C up -d --force-recreate'
curl -s https://<sub-aluno>.<dominio>/actuator/health   # {"groups":[...],"status":"UP"}
```

O `Caddyfile` anterior fica em `caddy/Caddyfile.bak-<data>`; para voltar, copie-o por cima do
`Caddyfile` e rode de novo o `docker compose ... up -d --force-recreate`. Sem SSH do seu computador (só
o terminal do navegador da Hostinger, como root), o repositório privado não pode ser baixado na VPS:
edite `/opt/plataforma-cursos/caddy/Caddyfile` lá mesmo, igual ao `infra/caddy/Caddyfile` da `main`, e
rode o mesmo `docker compose` com `sudo -u deploy`.

**Site público (Fase 14):** além do DNS de `cursos` e `homolog-cursos` (etapa 2), o `caddy/.env`
precisa de `PRODUCAO_HOST_SITE` e `HOMOLOG_HOST_SITE` (veja `infra/caddy/.env.example`). O endereço
público do site vem de `SITE_URL` no `.env` do ambiente; em branco, é derivado de `FRONT_ALUNO_URL`
(`aluno.` → `cursos.`). A homologação sai com `noindex` e `robots.txt` bloqueando tudo.

Quando a mudança do Caddy depende de um container novo (como na Fase 11, que passou a mandar as
páginas para o `web`), atualize o Caddy **depois** do deploy dos dois ambientes com esse container: antes
disso o Caddy novo não teria para onde mandar as páginas (502), enquanto o antigo continua respondendo
404 fora da API.

**Pagamentos (Fase 16):** o `gerar-env-servidor.sh` já cria a `PAGAMENTOS_CHAVE` nos ambientes novos.
Nos `.env` que já existem, acrescente uma chave por ambiente (diferentes entre si), antes do deploy:

```bash
echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/producao/.env
echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/homolog/.env
```

Trocar a chave depois exige informar de novo os segredos do meio de pagamento no painel. A
reconciliação fica desligada até `PAGAMENTOS_RECONCILIACAO=true`. Atualize também o Caddy (acima): a
homologação continua restrita por IP, mas deixa passar `/api/pagamentos/webhooks/*`, para a Hotmart de
teste notificar `https://homolog-<sub-aluno>.<dominio>/api/pagamentos/webhooks/<token>` (o endereço completo
aparece no painel). Configuração da Hotmart: `docs/pagamentos.md`.

**Logs persistentes e auditoria (Fase 17):** nada a configurar. O deploy cria o volume `logs` de cada
ambiente; a API e o worker gravam nele com retenção (180 dias ou 2 GB por serviço). Como ler:
`docs/operacao.md`, seção 4.

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

`infra/compose.prod.yml`:

`````yaml
# Stack de um ambiente dos servidores (produção ou homologação): API, worker, fronts (web), gerador
# do site público (site), PostgreSQL e Garage.
# Imagens publicadas no GHCR com a tag do commit (TAG); nenhuma porta publicada. O Caddy (projeto
# próprio, em caddy/) chega à API, aos fronts e ao Garage pela rede plataforma-cursos-<AMBIENTE>,
# pelos apelidos <AMBIENTE>-api, <AMBIENTE>-web e <AMBIENTE>-garage. Todas as variáveis vêm do .env do ambiente.
#
#   Produção:    docker compose -p plataforma-cursos-producao -f compose.prod.yml up -d --wait
#   Homologação: docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml up -d --wait

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO}
  AUTH_ORIGENS: ${AUTH_ORIGENS:?defina AUTH_ORIGENS}
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
  S3_ENDPOINT: http://garage:3900
  S3_ENDPOINT_PUBLICO: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:?defina S3_CORS_ORIGENS}
  SMTP_HOST: ${SMTP_HOST:?defina SMTP_HOST}
  SMTP_PORT: ${SMTP_PORT:-587}
  SMTP_USERNAME: ${SMTP_USERNAME:-}
  SMTP_PASSWORD: ${SMTP_PASSWORD:-}
  SMTP_AUTH: ${SMTP_AUTH:-true}
  SMTP_STARTTLS: ${SMTP_STARTTLS:-true}
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE}
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
  # Log também em arquivo, no volume logs: um por dia, guardado por 180 dias ou 2 GB por serviço
  LOGGING_STRUCTURED_FORMAT_FILE: ecs
  # Alertas do worker por e-mail (jobs com erro, disco quase cheio, chargeback); em branco, desligados
  ALERTA_EMAIL: ${ALERTA_EMAIL:-}
  # Chave dos segredos dos meios de pagamento; sem ela, o painel não grava credenciais
  PAGAMENTOS_CHAVE: ${PAGAMENTOS_CHAVE:-}
  APP_PAGAMENTOS_RECONCILIACAO_ATIVA: ${PAGAMENTOS_RECONCILIACAO:-false}

# Logs do Docker com rotação: no máximo 5 arquivos de 10 MB por container
x-logging: &logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  api:
    logging: *logging
    image: ${REGISTRO:?defina REGISTRO}/plataforma-cursos-api:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
      LOGGING_FILE_NAME: /var/log/plataforma-cursos/api.log
    volumes:
      - logs:/var/log/plataforma-cursos
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-api
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  worker:
    logging: *logging
    image: ${REGISTRO:?defina REGISTRO}/plataforma-cursos-worker:${TAG:?defina TAG}
    restart: unless-stopped
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx768m
      LOGGING_FILE_NAME: /var/log/plataforma-cursos/worker.log
    volumes:
      - logs:/var/log/plataforma-cursos
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check). A carência
      # cobre a partida do Spring com pouca CPU (meia CPU na homologação passa de um minuto)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 120s
      retries: 3
    deploy:
      resources:
        limits:
          # A transcodificação (ffmpeg) não pode tomar a CPU toda da VPS
          cpus: "1.5"
          memory: 2G

  # Área do aluno (porta 8081) e ADM (8082), estáticos; a API é chamada no mesmo domínio (/api)
  web:
    logging: *logging
    image: ${REGISTRO:?defina REGISTRO}/plataforma-cursos-web:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      # Origem dos vídeos e arquivos na Content-Security-Policy dos fronts
      MIDIA_ORIGEM: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
    volumes:
      - site:/srv/site:ro
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-web
    deploy:
      resources:
        limits:
          memory: 64M

  # Gera o site público (cursos.*) a partir da API ao subir e a cada SITE_INTERVALO segundos
  site:
    logging: *logging
    image: ${REGISTRO:?defina REGISTRO}/plataforma-cursos-site:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      API_URL: http://api:8080
      FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
      # Em branco, deriva do FRONT_ALUNO_URL (<sub-aluno>.<dominio> → <sub-site>.<dominio>)
      SITE_URL: ${SITE_URL:-}
      SITE_INDEXAVEL: ${SITE_INDEXAVEL:-true}
      SITE_INTERVALO: ${SITE_INTERVALO:-300}
    volumes:
      - site:/site
    depends_on:
      api:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 128M

  postgres:
    logging: *logging
    image: postgres:18.6-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
    volumes:
      - postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  garage:
    logging: *logging
    image: dxflrs/garage:v2.4.1
    restart: unless-stopped
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-garage
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 768M

networks:
  default:
    name: plataforma-cursos-${AMBIENTE:?defina AMBIENTE}

volumes:
  postgres:
  garage:
  site:
  # Logs da API e do worker em arquivo (o log do Docker roda em poucos dias)
  logs:
`````

`web/adm/src/caminhos.ts`:

`````ts
export const CAMINHOS = {
  entrar: '/entrar',
  inicio: '/',
  cursos: '/cursos',
  termos: '/termos',
  meiosPagamento: '/pagamentos',
  pedidos: '/pagamentos/pedidos',
  eventos: '/pagamentos/eventos',
  auditoria: '/auditoria',
} as const;

export const caminhoPedido = (pedidoId: number | string) => `/pagamentos/pedidos/${pedidoId}`;

export const caminhoCurso = (cursoId: number | string) => `/cursos/${cursoId}`;

export const caminhoTurma = (cursoId: number | string, turmaId: number | string) =>
  `/cursos/${cursoId}/turmas/${turmaId}`;

export const caminhoAula = (
  cursoId: number | string,
  turmaId: number | string,
  moduloId: number | string,
  aulaId: number | string,
) => `/cursos/${cursoId}/turmas/${turmaId}/modulos/${moduloId}/aulas/${aulaId}`;
`````

`web/adm/src/layout/LayoutAdm.tsx`:

`````tsx
import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export const NAV: SecaoNav[] = [
  { itens: [{ rotulo: 'Painel', icone: 'house', rota: CAMINHOS.inicio }] },
  {
    rotulo: 'Conteúdo',
    itens: [
      { rotulo: 'Cursos', icone: 'graduation-cap', rota: CAMINHOS.cursos },
      { rotulo: 'Termos', icone: 'file-text', rota: CAMINHOS.termos },
    ],
  },
  {
    rotulo: 'Pagamentos',
    itens: [
      { rotulo: 'Meios de pagamento', icone: 'credit-card', rota: CAMINHOS.meiosPagamento },
      { rotulo: 'Pedidos', icone: 'receipt', rota: CAMINHOS.pedidos },
      { rotulo: 'Eventos recebidos', icone: 'inbox', rota: CAMINHOS.eventos },
    ],
  },
  {
    rotulo: 'Segurança',
    itens: [{ rotulo: 'Auditoria', icone: 'shield-check', rota: CAMINHOS.auditoria }],
  },
];

export function LayoutAdm() {
  const { estado, sair } = useSessao();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';

  return (
    <Estrutura
      marca={MARCA}
      nav={NAV}
      rotaAtual={pathname}
      aoNavegar={navigate}
      renderLink={(p) => (
        <Link
          to={p.href}
          className={p.className}
          onClick={p.onClick}
          aria-current={p['aria-current']}
        >
          {p.children}
        </Link>
      )}
      usuario={{ nome, cargo: 'Administrador' }}
      menuUsuario={[{ id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true }]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <Outlet />
    </Estrutura>
  );
}
`````

`web/adm/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { AuditoriaPagina } from './paginas/auditoria/AuditoriaPagina.tsx';
import { LayoutAdm } from './layout/LayoutAdm.tsx';
import { SemPermissao } from './layout/SemPermissao.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CursoPagina } from './paginas/curso/CursoPagina.tsx';
import { CursosPagina } from './paginas/cursos/CursosPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { PainelPagina } from './paginas/PainelPagina.tsx';
import { EventosPagina } from './paginas/pagamentos/EventosPagina.tsx';
import { MeiosPagamentoPagina } from './paginas/pagamentos/MeiosPagamentoPagina.tsx';
import { PedidoPagina } from './paginas/pagamentos/PedidoPagina.tsx';
import { PedidosPagina } from './paginas/pagamentos/PedidosPagina.tsx';
import { TermosPagina } from './paginas/termos/TermosPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar} papel="ADMIN" semPermissao={<SemPermissao />}>
        <LayoutAdm />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <PainelPagina /> },
      { path: 'cursos', element: <CursosPagina /> },
      { path: 'cursos/:cursoId', element: <CursoPagina /> },
      { path: 'cursos/:cursoId/turmas/:turmaId', element: <TurmaPagina /> },
      {
        path: 'cursos/:cursoId/turmas/:turmaId/modulos/:moduloId/aulas/:aulaId',
        element: <AulaPagina />,
      },
      { path: 'termos', element: <TermosPagina /> },
      { path: 'pagamentos', element: <MeiosPagamentoPagina /> },
      { path: 'pagamentos/pedidos', element: <PedidosPagina /> },
      { path: 'pagamentos/pedidos/:pedidoId', element: <PedidoPagina /> },
      { path: 'pagamentos/eventos', element: <EventosPagina /> },
      { path: 'auditoria', element: <AuditoriaPagina /> },
    ],
  },
  { path: '*', element: <SemPermissao titulo="Página não encontrada" /> },
]);
`````

`web/adm/src/tipos.ts`:

`````ts
import type { Severidade } from '@celke/kole-react';

/** Respostas da API de gestão (/api/adm), espelhando os records do backend. */

export type StatusCurso = 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO';
export type StatusTurma = 'PREPARACAO' | 'ATIVA' | 'ENCERRADA';
export type StatusConteudo = 'ATIVO' | 'INATIVO';
export type StatusVideo = 'AGUARDANDO_ENVIO' | 'PENDENTE' | 'PROCESSANDO' | 'PRONTO' | 'ERRO';
export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FormaPagamento = 'MANUAL' | 'CORTESIA' | 'PIX' | 'CARTAO' | 'BOLETO' | 'OUTRO';
export type TipoTermo = 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';

export interface Curso {
  id: number;
  titulo: string;
  slug: string;
  resumo: string;
  descricao: string | null;
  status: StatusCurso;
  capaUrl: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Turma {
  id: number;
  cursoId: number;
  nome: string;
  status: StatusTurma;
  percentualConclusao: number;
  cargaHorariaHoras: number | null;
}

export interface Modulo {
  id: number;
  turmaId: number;
  titulo: string;
  ordem: number;
  status: StatusConteudo;
}

export interface Aula {
  id: number;
  moduloId: number;
  titulo: string;
  descricao: string | null;
  ordem: number;
  gratuita: boolean;
  status: StatusConteudo;
}

export interface Video {
  aulaId: number;
  status: StatusVideo;
  publicado: boolean;
  nomeOriginal: string | null;
  tamanhoOriginal: number | null;
  tamanhoFinal: number | null;
  reducaoPercentual: number | null;
  duracaoSegundos: number | null;
  largura: number | null;
  altura: number | null;
  variantes: string[];
  segundosProcessamentoPorMinuto: number | null;
  thumbnailUrl: string | null;
  erro: string | null;
  processadoEm: string | null;
  atualizadoEm: string;
}

export interface EnvioVideo {
  tamanhoParte: number;
  partes: { numero: number; url: string }[];
  expiraEm: string;
}

/** Envio de um arquivo só (capa, arquivo de apoio): PUT na url com o Content-Type informado. */
export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
}

export interface ArquivoApoio {
  id: number;
  aulaId: number | null;
  moduloId: number | null;
  titulo: string;
  nomeArquivo: string;
  contentType: string;
  tamanho: number;
  criadoEm: string;
}

export interface Matricula {
  id: number;
  turmaId: number;
  usuarioId: number;
  nome: string;
  email: string;
  inicio: string;
  fim: string | null;
  status: 'ATIVA' | 'CANCELADA';
  situacao: SituacaoMatricula;
  formaPagamento: FormaPagamento;
  transacao: string | null;
}

export interface TopicoResumo {
  id: number;
  turmaId: number;
  aulaId: number | null;
  titulo: string;
  autorNome: string;
  fixado: boolean;
  respondidoInstrutor: boolean;
  respostas: number;
  oculto: boolean | null;
  criadoEm: string;
}

export interface Anexo {
  id: number;
  url: string;
}

export interface Resposta {
  id: number;
  corpoHtml: string;
  autorNome: string;
  instrutor: boolean;
  oculto: boolean | null;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Termo {
  id: number;
  tipo: TipoTermo;
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

export type StatusPedido =
  | 'CRIADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'APROVADO'
  | 'CONCLUIDO'
  | 'EM_DISPUTA'
  | 'ATRASADO'
  | 'REEMBOLSADO'
  | 'CHARGEBACK'
  | 'CANCELADO'
  | 'EXPIRADO';
export type StatusEvento = 'RECEBIDO' | 'PROCESSADO' | 'IGNORADO' | 'ERRO';

/** Campo da configuração de um meio de pagamento (o formulário é montado a partir daqui). */
export interface CampoConfiguracao {
  chave: string;
  rotulo: string;
  tipo: 'TEXTO' | 'URL' | 'SELECAO';
  secreto: boolean;
  obrigatorio: boolean;
  dica: string | null;
  padrao: string | null;
  opcoes: string[];
}

export interface TipoGateway {
  tipo: string;
  nome: string;
  campos: CampoConfiguracao[];
  capacidades: string[];
}

export interface TiposGateway {
  cifraDisponivel: boolean;
  tipos: TipoGateway[];
}

export interface SegredoResumo {
  configurado: boolean;
  final4: string | null;
}

export interface Gateway {
  id: number;
  tipo: string;
  nomeTipo: string;
  nome: string;
  ativo: boolean;
  valores: Record<string, string>;
  segredos: Record<string, SegredoResumo>;
  segredosLegiveis: boolean;
  webhookUrl: string;
  criadoEm: string;
  atualizadoEm: string;
}

export interface ResultadoTeste {
  ok: boolean;
  mensagem: string;
}

export interface Oferta {
  id: number;
  turmaId: number;
  gatewayId: number;
  gatewayNome: string | null;
  gatewayAtivo: boolean;
  produtoExterno: string;
  ofertaExterna: string;
  codigoCheckout: string | null;
  titulo: string;
  precoCentavos: number;
  moeda: string;
  diasAcesso: number | null;
  ativa: boolean;
  criadoEm: string;
}

export interface Pedido {
  id: number;
  codigo: string;
  status: StatusPedido;
  origem: 'CHECKOUT' | 'EXTERNO';
  email: string;
  nome: string | null;
  usuarioId: number | null;
  gatewayId: number;
  ofertaId: number;
  ofertaTitulo: string | null;
  turmaId: number | null;
  transacaoExterna: string | null;
  valorCentavos: number | null;
  moeda: string | null;
  formaPagamento: FormaPagamento | null;
  matriculaId: number | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface EventoPagamento {
  id: number;
  gatewayId: number;
  idExterno: string;
  tipoOriginal: string;
  status: StatusEvento;
  recebidoEm: string;
  ocorridoEm: string | null;
  transacao: string | null;
  erro: string | null;
  pedidoId: number | null;
  processadoEm: string | null;
}

export interface HistoricoPedido {
  id: number;
  de: StatusPedido | null;
  para: StatusPedido;
  eventoId: number | null;
  motivo: string | null;
  em: string;
}

export interface PedidoDetalhe {
  pedido: Pedido;
  historico: HistoricoPedido[];
  eventos: EventoPagamento[];
}

export interface EventoDetalhe {
  evento: EventoPagamento;
  payload: string | null;
}

export type OperacaoAuditoria = 'INCLUSAO' | 'ALTERACAO' | 'EXCLUSAO';

/** Uma linha da trilha de auditoria; `antes` e `depois` são JSON com os campos. */
export interface RegistroAuditoria {
  id: number;
  em: string;
  operacao: OperacaoAuditoria;
  entidade: string;
  entidadeId: string | null;
  usuarioId: number | null;
  usuarioNome: string | null;
  usuarioEmail: string | null;
  origem: string;
  ip: string | null;
  userAgent: string | null;
  requestId: string | null;
  antes: string | null;
  depois: string | null;
}

interface Rotulo {
  rotulo: string;
  severidade: Severidade;
}

export const STATUS_CURSO: Record<StatusCurso, Rotulo> = {
  RASCUNHO: { rotulo: 'Rascunho', severidade: 'neutral' },
  PUBLICADO: { rotulo: 'Publicado', severidade: 'ok' },
  ARQUIVADO: { rotulo: 'Arquivado', severidade: 'warn' },
};

export const STATUS_TURMA: Record<StatusTurma, Rotulo> = {
  PREPARACAO: { rotulo: 'Em preparação', severidade: 'neutral' },
  ATIVA: { rotulo: 'Ativa', severidade: 'ok' },
  ENCERRADA: { rotulo: 'Encerrada', severidade: 'warn' },
};

export const STATUS_CONTEUDO: Record<StatusConteudo, Rotulo> = {
  ATIVO: { rotulo: 'Ativo', severidade: 'ok' },
  INATIVO: { rotulo: 'Inativo', severidade: 'neutral' },
};

export const STATUS_VIDEO: Record<StatusVideo, Rotulo> = {
  AGUARDANDO_ENVIO: { rotulo: 'Aguardando envio', severidade: 'neutral' },
  PENDENTE: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSANDO: { rotulo: 'Processando', severidade: 'info' },
  PRONTO: { rotulo: 'Pronto', severidade: 'ok' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const SITUACAO_MATRICULA: Record<SituacaoMatricula, Rotulo> = {
  AGUARDANDO_INICIO: { rotulo: 'Aguardando início', severidade: 'info' },
  VIGENTE: { rotulo: 'Vigente', severidade: 'ok' },
  VENCIDA: { rotulo: 'Vencida', severidade: 'warn' },
  CANCELADA: { rotulo: 'Cancelada', severidade: 'err' },
};

export const FORMAS_PAGAMENTO: Record<FormaPagamento, string> = {
  MANUAL: 'Manual',
  CORTESIA: 'Cortesia',
  PIX: 'Pix',
  CARTAO: 'Cartão',
  BOLETO: 'Boleto',
  OUTRO: 'Outro',
};

export const STATUS_PEDIDO: Record<StatusPedido, Rotulo> = {
  CRIADO: { rotulo: 'Criado', severidade: 'neutral' },
  AGUARDANDO_PAGAMENTO: { rotulo: 'Aguardando pagamento', severidade: 'info' },
  APROVADO: { rotulo: 'Aprovado', severidade: 'ok' },
  CONCLUIDO: { rotulo: 'Concluído', severidade: 'ok' },
  EM_DISPUTA: { rotulo: 'Em disputa', severidade: 'warn' },
  ATRASADO: { rotulo: 'Atrasado', severidade: 'warn' },
  REEMBOLSADO: { rotulo: 'Reembolsado', severidade: 'err' },
  CHARGEBACK: { rotulo: 'Chargeback', severidade: 'err' },
  CANCELADO: { rotulo: 'Cancelado', severidade: 'err' },
  EXPIRADO: { rotulo: 'Expirado', severidade: 'neutral' },
};

export const STATUS_EVENTO: Record<StatusEvento, Rotulo> = {
  RECEBIDO: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSADO: { rotulo: 'Processado', severidade: 'ok' },
  IGNORADO: { rotulo: 'Ignorado', severidade: 'warn' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const OPERACAO_AUDITORIA: Record<OperacaoAuditoria, Rotulo> = {
  INCLUSAO: { rotulo: 'Inclusão', severidade: 'ok' },
  ALTERACAO: { rotulo: 'Alteração', severidade: 'info' },
  EXCLUSAO: { rotulo: 'Exclusão', severidade: 'err' },
};

export const TIPOS_TERMO: Record<TipoTermo, string> = {
  TERMOS_USO: 'Termos de uso',
  POLITICA_PRIVACIDADE: 'Política de privacidade',
};

export function opcoes<V extends string>(mapa: Record<V, Rotulo | string>) {
  return (Object.keys(mapa) as V[]).map((valor) => {
    const item = mapa[valor];
    return { valor, rotulo: typeof item === 'string' ? item : item.rotulo };
  });
}
`````

`web/package.json`:

`````json
{
  "name": "plataforma-cursos-web",
  "private": true,
  "version": "0.1.0",
  "description": "Frontends da Plataforma de Cursos: área do aluno e ADM (React + @celke/kole-react).",
  "type": "module",
  "scripts": {
    "dev:aluno": "vite --config aluno/vite.config.ts",
    "dev:adm": "vite --config adm/vite.config.ts",
    "build": "tsc -b && vite build --config aluno/vite.config.ts && vite build --config adm/vite.config.ts",
    "typecheck": "tsc -b",
    "lint": "eslint .",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "test": "vitest run",
    "e2e": "playwright test",
    "e2e:headed": "playwright test --headed",
    "e2e:ui": "playwright test --ui",
    "e2e:debug": "playwright test --debug",
    "e2e:report": "playwright show-report",
    "e2e:trace": "playwright show-trace",
    "lighthouse": "node e2e/lighthouse.ts"
  },
  "dependencies": {
    "@celke/kole-react": "github:celkecursos/kole-react#fc87d5c4409e1e65e8c571321c08d56871d2a4d3",
    "hls.js": "^1.7.3",
    "lucide-react": "^1.61.0",
    "react": "^19.3.1",
    "react-dom": "^19.3.1",
    "react-router": "^8.5.0"
  },
  "devDependencies": {
    "@eslint/js": "^10.0.1",
    "@playwright/test": "^1.64.1",
    "@testing-library/jest-dom": "^7.0.1",
    "@testing-library/react": "^16.3.3",
    "@testing-library/user-event": "^14.6.8",
    "@types/node": "^24.13.3",
    "@types/react": "^19.2.18",
    "@types/react-dom": "^19.2.7",
    "@vitejs/plugin-react": "^6.1.2",
    "eslint": "^10.13.0",
    "eslint-plugin-react-hooks": "^7.1.1",
    "globals": "^17.13.0",
    "jsdom": "^30.1.3",
    "lighthouse": "^13.5.0",
    "prettier": "^3.9.10",
    "typescript": "~6.0.3",
    "typescript-eslint": "^8.72.0",
    "vite": "^8.3.5",
    "vitest": "^5.0.5"
  },
  "engines": {
    "node": ">=22"
  }
}
`````

### Arquivos novos

`backend/src/main/java/br/com/celke/cursos/auditoria/api/AuditoriaController.java`:

`````java
package br.com.celke.cursos.auditoria.api;

import br.com.celke.cursos.auditoria.api.AuditoriaResponses.Registro;
import br.com.celke.cursos.auditoria.application.AuditoriaService;
import br.com.celke.cursos.auditoria.application.AuditoriaService.FiltroAuditoria;
import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    @Operation(
            summary = "Trilha de auditoria",
            description = "Mais recentes primeiro. Filtros: usuário (id ou e-mail), entidade e id, operação e"
                    + " período (de inclusivo, até exclusivo; ISO-8601).")
    public Pagina<Registro> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) OperacaoAuditoria operacao,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            @ParameterObject Pageable pagina) {
        return Pagina.de(auditoriaService
                .listar(new FiltroAuditoria(usuarioId, email, entidade, entidadeId, operacao, de, ate), pagina)
                .map(Registro::de));
    }

    @GetMapping("/entidades")
    @Operation(summary = "Entidades que aparecem na trilha", description = "Para o filtro da tela.")
    public List<String> entidades() {
        return auditoriaService.entidades();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Registro da trilha")
    public Registro buscar(@PathVariable Long id) {
        return Registro.de(auditoriaService.buscar(id));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/api/AuditoriaResponses.java`:

`````java
package br.com.celke.cursos.auditoria.api;

import br.com.celke.cursos.auditoria.application.AuditoriaService.RegistroComUsuario;
import br.com.celke.cursos.auditoria.domain.RegistroAuditoria;
import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import java.time.Instant;

/** Respostas da trilha de auditoria (ADM). */
public final class AuditoriaResponses {

    private AuditoriaResponses() {}

    /**
     * Uma ação registrada.
     *
     * @param antes campos antes da mudança, em JSON (nulo na inclusão)
     * @param depois campos depois da mudança, em JSON (nulo na exclusão)
     * @param usuarioNome nulo quando foi o sistema (worker, agendamento) ou ação sem login
     */
    public record Registro(
            Long id,
            Instant em,
            OperacaoAuditoria operacao,
            String entidade,
            String entidadeId,
            Long usuarioId,
            String usuarioNome,
            String usuarioEmail,
            String origem,
            String ip,
            String userAgent,
            String requestId,
            String antes,
            String depois) {

        public static Registro de(RegistroComUsuario item) {
            RegistroAuditoria r = item.registro();
            return new Registro(
                    r.getId(),
                    r.getEm(),
                    r.getOperacao(),
                    r.getEntidade(),
                    r.getEntidadeId(),
                    r.getUsuarioId(),
                    item.usuario() == null ? null : item.usuario().getNome(),
                    item.usuario() == null ? null : item.usuario().getEmail(),
                    r.getOrigem(),
                    r.getIp(),
                    r.getUserAgent(),
                    r.getRequestId(),
                    r.getAntes(),
                    r.getDepois());
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/application/AuditoriaProperties.java`:

`````java
package br.com.celke.cursos.auditoria.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Trilha de auditoria.
 *
 * @param retencao registros mais antigos que isso são apagados pela limpeza semanal do worker
 */
@Validated
@ConfigurationProperties("app.auditoria")
public record AuditoriaProperties(@NotNull Duration retencao) {}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/application/AuditoriaService.java`:

`````java
package br.com.celke.cursos.auditoria.application;

import br.com.celke.cursos.auditoria.domain.RegistroAuditoria;
import br.com.celke.cursos.auditoria.infra.RegistroAuditoriaRepository;
import br.com.celke.cursos.auditoria.infra.RetencaoAuditoriaJdbc;
import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta da trilha de auditoria pelo ADM e a retenção. */
@Service
@EnableConfigurationProperties(AuditoriaProperties.class)
public class AuditoriaService {

    private final RegistroAuditoriaRepository registros;
    private final RetencaoAuditoriaJdbc retencao;
    private final UsuarioService usuarioService;
    private final AuditoriaProperties props;
    private final Clock clock;

    public AuditoriaService(
            RegistroAuditoriaRepository registros,
            RetencaoAuditoriaJdbc retencao,
            UsuarioService usuarioService,
            AuditoriaProperties props,
            Clock clock) {
        this.registros = registros;
        this.retencao = retencao;
        this.usuarioService = usuarioService;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Registros mais recentes primeiro. O filtro por e-mail procura o usuário; e-mail sem conta não
     * encontra nada.
     */
    @Transactional(readOnly = true)
    public Page<RegistroComUsuario> listar(FiltroAuditoria filtro, Pageable pagina) {
        PageRequest ordenada =
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "em", "id"));
        Long usuarioId = filtro.usuarioId();
        if (filtro.email() != null && !filtro.email().isBlank()) {
            Optional<Usuario> usuario = usuarioService.buscarPorEmail(filtro.email());
            if (usuario.isEmpty()) {
                return Page.empty(ordenada);
            }
            usuarioId = usuario.get().getId();
        }
        Page<RegistroAuditoria> page = registros.findAll(especificacao(filtro, usuarioId), ordenada);
        Map<Long, Usuario> usuarios = usuarioService.buscarPorIds(page.getContent().stream()
                .map(RegistroAuditoria::getUsuarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return page.map(
                r -> new RegistroComUsuario(r, r.getUsuarioId() == null ? null : usuarios.get(r.getUsuarioId())));
    }

    @Transactional(readOnly = true)
    public RegistroComUsuario buscar(Long id) {
        RegistroAuditoria registro =
                registros.findById(id).orElseThrow(() -> new NaoEncontradoException("Registro não encontrado."));
        Usuario usuario = registro.getUsuarioId() == null
                ? null
                : usuarioService.buscarPorId(registro.getUsuarioId()).orElse(null);
        return new RegistroComUsuario(registro, usuario);
    }

    @Transactional(readOnly = true)
    public List<String> entidades() {
        return registros.entidades();
    }

    /** Apaga o que passou da retenção; devolve quantos registros saíram. */
    public int aplicarRetencao() {
        return retencao.apagarAnteriores(clock.instant().minus(props.retencao()));
    }

    private static Specification<RegistroAuditoria> especificacao(FiltroAuditoria filtro, Long usuarioId) {
        Specification<RegistroAuditoria> spec = Specification.unrestricted();
        if (usuarioId != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("usuarioId"), usuarioId));
        }
        if (filtro.entidade() != null && !filtro.entidade().isBlank()) {
            spec = spec.and((raiz, consulta, cb) ->
                    cb.equal(raiz.get("entidade"), filtro.entidade().strip()));
        }
        if (filtro.entidadeId() != null && !filtro.entidadeId().isBlank()) {
            spec = spec.and((raiz, consulta, cb) ->
                    cb.equal(raiz.get("entidadeId"), filtro.entidadeId().strip()));
        }
        if (filtro.operacao() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("operacao"), filtro.operacao()));
        }
        if (filtro.de() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("em"), filtro.de()));
        }
        if (filtro.ate() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.lessThan(raiz.get("em"), filtro.ate()));
        }
        return spec;
    }

    /** Filtros opcionais: usuário (id ou e-mail), entidade e id, operação e período ({@code ate} exclusivo). */
    public record FiltroAuditoria(
            Long usuarioId,
            String email,
            String entidade,
            String entidadeId,
            OperacaoAuditoria operacao,
            Instant de,
            Instant ate) {}

    /** @param usuario nulo quando a ação foi do sistema ou o usuário não existe mais */
    public record RegistroComUsuario(RegistroAuditoria registro, Usuario usuario) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/application/LimpezaAuditoria.java`:

`````java
package br.com.celke.cursos.auditoria.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção da trilha de auditoria, toda semana, só no worker. */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class LimpezaAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(LimpezaAuditoria.class);

    private final AuditoriaService auditoriaService;

    public LimpezaAuditoria(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Scheduled(cron = "0 0 5 * * SUN", zone = "America/Sao_Paulo")
    public void limpar() {
        LOG.info("Retenção da auditoria: {} registro(s) apagado(s)", auditoriaService.aplicarRetencao());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/domain/RegistroAuditoria.java`:

`````java
package br.com.celke.cursos.auditoria.domain;

import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Uma linha da trilha de auditoria, só para leitura: quem grava é o {@code TrilhaAuditoria} e o banco
 * recusa alteração e exclusão (só a retenção apaga).
 */
@Entity
@Immutable
@SemAuditoria
@Table(name = "auditoria")
public class RegistroAuditoria {

    @Id
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    private OperacaoAuditoria operacao;

    private String entidade;

    @Column(name = "entidade_id")
    private String entidadeId;

    /** Campos antes da mudança, em JSON; nulo na inclusão. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String antes;

    /** Campos depois da mudança, em JSON; nulo na exclusão. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String depois;

    private String origem;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "request_id")
    private String requestId;

    private Instant em;

    protected RegistroAuditoria() {}

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public OperacaoAuditoria getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getAntes() {
        return antes;
    }

    public String getDepois() {
        return depois;
    }

    public String getOrigem() {
        return origem;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getEm() {
        return em;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/infra/RegistroAuditoriaRepository.java`:

`````java
package br.com.celke.cursos.auditoria.infra;

import br.com.celke.cursos.auditoria.domain.RegistroAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RegistroAuditoriaRepository
        extends JpaRepository<RegistroAuditoria, Long>, JpaSpecificationExecutor<RegistroAuditoria> {

    /** Entidades que já aparecem na trilha (filtro da tela). */
    @Query("select distinct r.entidade from RegistroAuditoria r order by r.entidade")
    List<String> entidades();
}
`````

`backend/src/main/java/br/com/celke/cursos/auditoria/infra/RetencaoAuditoriaJdbc.java`:

`````java
package br.com.celke.cursos.auditoria.infra;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga a trilha antiga. O banco só aceita a exclusão com {@code app.auditoria_retencao} ligado na
 * transação: nenhuma outra parte da aplicação consegue apagar registros.
 */
@Repository
public class RetencaoAuditoriaJdbc {

    private final JdbcClient jdbc;

    public RetencaoAuditoriaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int apagarAnteriores(Instant limite) {
        jdbc.sql("SELECT set_config('app.auditoria_retencao', 'on', true)")
                .query()
                .singleValue();
        return jdbc.sql("DELETE FROM auditoria WHERE em < :limite")
                .param("limite", limite.atOffset(ZoneOffset.UTC))
                .update();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/ContextoAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import br.com.celke.cursos.shared.web.IdRequisicao;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Quem fez a mudança e de onde: o usuário do token e o job da fila em processamento ({@link #CHAVE_JOB}
 * no MDC), ou a requisição (rota, IP, user-agent, id de correlação), ou "sistema" (agendamentos).
 *
 * @param usuarioId nulo sem usuário logado
 */
public record ContextoAuditoria(Long usuarioId, String origem, String ip, String userAgent, String requestId) {

    /** Chave do MDC com o tipo do job em processamento (posta pelo processador da fila). */
    public static final String CHAVE_JOB = "job";

    static final int TAMANHO_MAXIMO_ORIGEM = 200;

    /** Contexto da thread atual. */
    public static ContextoAuditoria atual() {
        Long usuarioId = usuario(SecurityContextHolder.getContext().getAuthentication());
        String requestId = MDC.get(IdRequisicao.CHAVE_MDC);
        String job = MDC.get(CHAVE_JOB);
        if (job != null) {
            return new ContextoAuditoria(usuarioId, "job " + job, null, null, requestId);
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            OrigemRequisicao origem = OrigemRequisicao.de(request);
            return new ContextoAuditoria(usuarioId, rota(request), origem.ip(), origem.userAgent(), requestId);
        }
        return new ContextoAuditoria(usuarioId, "sistema", null, null, requestId);
    }

    /** Método e rota com as variáveis ({@code PUT /api/adm/cursos/{id}}); sem rota mapeada, o caminho. */
    static String rota(HttpServletRequest request) {
        Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String caminho = padrao == null ? request.getRequestURI() : padrao.toString();
        String rota = request.getMethod() + " " + caminho;
        return rota.length() <= TAMANHO_MAXIMO_ORIGEM ? rota : rota.substring(0, TAMANHO_MAXIMO_ORIGEM);
    }

    private static Long usuario(Authentication autenticacao) {
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            try {
                return Long.valueOf(jwt.getToken().getSubject());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/DadoSensivel.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Campo que nunca vai para a auditoria: a trilha registra que ele mudou, com o valor trocado por
 * {@code "***"} (hash de senha, segredos cifrados, tokens).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DadoSensivel {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/OperacaoAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

public enum OperacaoAuditoria {
    INCLUSAO,
    ALTERACAO,
    EXCLUSAO
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/OuvinteAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.util.Map;
import org.hibernate.Hibernate;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

/**
 * Ouve as inclusões, alterações e exclusões que o Hibernate grava e registra cada uma na trilha de
 * auditoria, dentro da mesma transação. Na alteração, só os campos que mudaram, com o antes e o
 * depois. Entidades {@link SemAuditoria} ficam de fora. Mudanças feitas direto em SQL (fila,
 * notificações de pagamento, atualizações em lote) não passam por aqui.
 */
@Component
public class OuvinteAuditoria implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final TrilhaAuditoria trilha;

    public OuvinteAuditoria(TrilhaAuditoria trilha) {
        this.trilha = trilha;
    }

    @Override
    public void onPostInsert(PostInsertEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        if (auditada(tipo)) {
            registrar(
                    OperacaoAuditoria.INCLUSAO,
                    tipo,
                    evento.getId(),
                    null,
                    ValoresAuditoria.campos(tipo, nomes(evento.getPersister()), evento.getState(), null));
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        int[] alterados = evento.getDirtyProperties();
        if (!auditada(tipo) || (alterados != null && alterados.length == 0)) {
            return;
        }
        String[] nomes = nomes(evento.getPersister());
        registrar(
                OperacaoAuditoria.ALTERACAO,
                tipo,
                evento.getId(),
                ValoresAuditoria.campos(tipo, nomes, evento.getOldState(), alterados),
                ValoresAuditoria.campos(tipo, nomes, evento.getState(), alterados));
    }

    @Override
    public void onPostDelete(PostDeleteEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        if (auditada(tipo)) {
            registrar(
                    OperacaoAuditoria.EXCLUSAO,
                    tipo,
                    evento.getId(),
                    ValoresAuditoria.campos(tipo, nomes(evento.getPersister()), evento.getDeletedState(), null),
                    null);
        }
    }

    /** O registro entra na transação da mudança; nada fica pendente para depois do commit. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    static boolean auditada(Class<?> tipo) {
        return !tipo.isAnnotationPresent(SemAuditoria.class);
    }

    private void registrar(
            OperacaoAuditoria operacao,
            Class<?> tipo,
            Object id,
            Map<String, Object> antes,
            Map<String, Object> depois) {
        trilha.registrar(
                operacao,
                tipo.getSimpleName(),
                id == null ? null : String.valueOf(ValoresAuditoria.simples(id)),
                antes,
                depois,
                ContextoAuditoria.atual());
    }

    private static String[] nomes(EntityPersister persister) {
        return persister.getPropertyNames();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/RegistroOuvinteAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.stereotype.Component;

/** Liga o {@link OuvinteAuditoria} aos eventos do Hibernate (API e worker). */
@Component
public class RegistroOuvinteAuditoria {

    public RegistroOuvinteAuditoria(EntityManagerFactory emf, OuvinteAuditoria ouvinte) {
        EventListenerRegistry registro =
                emf.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
        registro.appendListeners(EventType.POST_INSERT, ouvinte);
        registro.appendListeners(EventType.POST_UPDATE, ouvinte);
        registro.appendListeners(EventType.POST_DELETE, ouvinte);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/SemAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entidade fora da trilha de auditoria: registros que já são um log (acesso a vídeo, auditoria de
 * login, notificações recebidas), dados técnicos de sessão (refresh token, tokens de e-mail) ou
 * mudanças frequentes demais para valer a trilha (progresso da aula, a cada poucos segundos).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SemAuditoria {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/TrilhaAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava uma linha da trilha de auditoria. Usa a conexão da transação em andamento: se a mudança é
 * desfeita, o registro também é.
 */
@Component
public class TrilhaAuditoria {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public TrilhaAuditoria(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void registrar(
            OperacaoAuditoria operacao,
            String entidade,
            String entidadeId,
            Map<String, Object> antes,
            Map<String, Object> depois,
            ContextoAuditoria contexto) {
        jdbc.sql("""
                        INSERT INTO auditoria (usuario_id, operacao, entidade, entidade_id, antes, depois, origem, ip,
                                               user_agent, request_id, em)
                        VALUES (:usuario, :operacao, :entidade, :entidadeId, CAST(:antes AS JSONB), CAST(:depois AS JSONB),
                                :origem, :ip, :userAgent, :requestId, :em)
                        """)
                .param("usuario", contexto.usuarioId())
                .param("operacao", operacao.name())
                .param("entidade", entidade)
                .param("entidadeId", entidadeId)
                .param("antes", antes == null ? null : jsonMapper.writeValueAsString(antes))
                .param("depois", depois == null ? null : jsonMapper.writeValueAsString(depois))
                .param("origem", contexto.origem())
                .param("ip", contexto.ip())
                .param("userAgent", contexto.userAgent())
                .param("requestId", contexto.requestId())
                .param("em", utc(clock.instant()))
                .update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/ValoresAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.hibernate.proxy.HibernateProxy;

/**
 * Converte o estado de uma entidade (como o Hibernate entrega no evento) em valores simples para o JSON
 * da trilha: entidade relacionada vira o id, data vira texto ISO-8601, binário vira um marcador e
 * campo {@link DadoSensivel} vira {@code "***"}.
 */
public final class ValoresAuditoria {

    public static final String OCULTO = "***";
    static final String BINARIO = "[binário]";

    private static final Map<Class<?>, Set<String>> SENSIVEIS = new ConcurrentHashMap<>();

    private ValoresAuditoria() {}

    /**
     * Campos escolhidos do estado.
     *
     * @param indices posições a incluir; nulo, todas
     */
    public static Map<String, Object> campos(Class<?> tipo, String[] nomes, Object[] estado, int[] indices) {
        if (estado == null) {
            return null;
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        if (indices == null) {
            for (int i = 0; i < nomes.length; i++) {
                campos.put(nomes[i], valor(tipo, nomes[i], estado[i]));
            }
        } else {
            for (int i : indices) {
                campos.put(nomes[i], valor(tipo, nomes[i], estado[i]));
            }
        }
        return campos;
    }

    static Object valor(Class<?> tipo, String campo, Object valor) {
        if (valor == null) {
            return null;
        }
        if (sensiveis(tipo).contains(campo)) {
            return OCULTO;
        }
        return simples(valor);
    }

    static Object simples(Object valor) {
        if (valor == null || valor instanceof String || valor instanceof Number || valor instanceof Boolean) {
            return valor;
        }
        if (valor instanceof Enum<?> constante) {
            return constante.name();
        }
        if (valor instanceof TemporalAccessor || valor instanceof UUID) {
            return valor.toString();
        }
        if (valor instanceof byte[]) {
            return BINARIO;
        }
        if (valor instanceof Collection<?> colecao) {
            return colecao.stream().map(ValoresAuditoria::simples).toList();
        }
        if (valor instanceof HibernateProxy proxy) {
            return simples(proxy.getHibernateLazyInitializer().getIdentifier());
        }
        if (valor.getClass().isAnnotationPresent(Entity.class)) {
            return idDe(valor);
        }
        return valor.toString();
    }

    /** Campos {@link DadoSensivel} da classe e das superclasses (lidos uma vez por classe). */
    static Set<String> sensiveis(Class<?> tipo) {
        return SENSIVEIS.computeIfAbsent(tipo, classe -> {
            java.util.stream.Stream.Builder<Field> campos = java.util.stream.Stream.builder();
            for (Class<?> c = classe; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field campo : c.getDeclaredFields()) {
                    campos.add(campo);
                }
            }
            return campos.build()
                    .filter(campo -> campo.isAnnotationPresent(DadoSensivel.class))
                    .map(Field::getName)
                    .collect(Collectors.toUnmodifiableSet());
        });
    }

    private static Object idDe(Object entidade) {
        try {
            Method getId = entidade.getClass().getMethod("getId");
            return simples(getId.invoke(entidade));
        } catch (ReflectiveOperationException e) {
            return entidade.getClass().getSimpleName();
        }
    }
}
`````

`backend/src/main/resources/db/migration/V16__criar_auditoria.sql`:

`````sql
-- Trilha de auditoria: cada inclusão, alteração e exclusão feita pela aplicação, com quem fez, de onde e
-- o antes e o depois dos campos (dados sensíveis mascarados). Sem chave estrangeira: o registro não
-- pode impedir nada nem depender de outra linha continuar existindo.
CREATE TABLE auditoria (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Nulo: ação sem usuário logado (cadastro, worker, agendamento)
    usuario_id   BIGINT,
    operacao     VARCHAR(20)  NOT NULL,
    entidade     VARCHAR(60)  NOT NULL,
    entidade_id  VARCHAR(64),
    antes        JSONB,
    depois       JSONB,
    -- Rota da requisição ("PUT /api/adm/cursos/{id}") ou o job/agendamento que fez a mudança
    origem       VARCHAR(200) NOT NULL,
    ip           VARCHAR(45),
    user_agent   VARCHAR(255),
    request_id   VARCHAR(64),
    em           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_auditoria_operacao CHECK (operacao IN ('INCLUSAO', 'ALTERACAO', 'EXCLUSAO'))
);

CREATE INDEX ix_auditoria_em ON auditoria (em DESC, id DESC);
CREATE INDEX ix_auditoria_entidade ON auditoria (entidade, entidade_id, em DESC);
CREATE INDEX ix_auditoria_usuario ON auditoria (usuario_id, em DESC);

-- Só inclusão: alterar nunca; excluir só pela retenção, que liga app.auditoria_retencao na transação
CREATE FUNCTION auditoria_somente_inclusao() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.auditoria_retencao', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'A auditoria só aceita inclusão (% recusado).', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_auditoria_somente_inclusao
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_somente_inclusao();
`````

`backend/src/test/java/br/com/celke/cursos/auditoria/AuditoriaIT.java`:

`````java
package br.com.celke.cursos.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.auditoria.application.AuditoriaService;
import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.pagamento.PagamentoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: trilha de auditoria — inclusão e alteração pelo ADM (quem, rota, IP, só os campos que
 * mudaram), ação do aluno (perfil e troca de senha com o hash oculto), segredo do meio de pagamento
 * oculto, operação recusada não deixa rastro, entidade fora da trilha (progresso), mudança feita pelo
 * worker com o job como origem, filtros, só inclusão no banco, retenção e matriz de papéis.
 * Não cobre: conversão de valores caso a caso (ValoresAuditoriaTest).
 * Dado criado: cursos e contas it-<stamp>, uma Hotmart e um registro antigo da retenção.
 */
class AuditoriaIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AuditoriaService auditoriaService;

    private CursoITHelper adm;
    private Long adminId;

    @BeforeEach
    void login() {
        String token = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        adm = new CursoITHelper(mvc, bearer(token));
        adminId = ((Number) JsonPath.read(
                        CursoITHelper.corpo(mvc.get()
                                .uri("/api/perfil")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .exchange()),
                        "$.id"))
                .longValue();
    }

    /** Antes e depois dos registros, juntos e sem espaços (o jsonb volta com espaço depois dos dois-pontos). */
    private static String mudancas(List<Map<String, Object>> registros) {
        return registros.stream()
                .map(r -> r.get("antes") + " " + r.get("depois"))
                .collect(Collectors.joining())
                .replace(" ", "");
    }

    private List<Map<String, Object>> trilha(String filtro) {
        return JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/auditoria?size=100&" + filtro, null)), "$.itens");
    }

    @Test
    @DisplayName("ADM cria e altera um curso: inclusão completa e alteração só do que mudou, com quem, rota e IP")
    void cursoPeloAdm() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/cursos/" + curso,
                        "{\"titulo\":\"Novo título\",\"slug\":\"%s\",\"resumo\":\"Resumo de IT Curso %s\"}"
                                .formatted(slug, slug)))
                .hasStatusOk();

        List<Map<String, Object>> registros = trilha("entidade=Curso&entidadeId=" + curso);
        assertThat(registros).extracting(r -> r.get("operacao")).containsExactly("ALTERACAO", "INCLUSAO");
        Map<String, Object> alteracao = registros.get(0);
        assertThat(alteracao)
                .containsEntry("usuarioEmail", UsuariosSeed.ADMIN_EMAIL)
                .containsEntry("origem", "PUT /api/adm/cursos/{id}")
                .containsEntry("ip", "127.0.0.1");
        assertThat(alteracao.get("requestId")).isNotNull();
        assertThat(JsonPath.<Map<String, Object>>read((String) alteracao.get("antes"), "$"))
                .containsEntry("titulo", "IT Curso " + slug)
                .doesNotContainKey("slug");
        assertThat(JsonPath.<Map<String, Object>>read((String) alteracao.get("depois"), "$"))
                .containsEntry("titulo", "Novo título")
                .containsKey("atualizadoEm")
                .doesNotContainKey("resumo");
        Map<String, Object> inclusao = registros.get(1);
        assertThat(inclusao).containsEntry("origem", "POST /api/adm/cursos").containsEntry("antes", null);
        assertThat(JsonPath.<Map<String, Object>>read((String) inclusao.get("depois"), "$"))
                .containsEntry("slug", slug)
                .containsEntry("status", "RASCUNHO");

        assertThat(trilha("usuarioId=" + adminId + "&entidade=Curso&entidadeId=" + curso))
                .hasSize(2);
        assertThat(trilha(
                        "email=" + UsuariosSeed.ADMIN_EMAIL + "&operacao=INCLUSAO&entidade=Curso&entidadeId=" + curso))
                .hasSize(1);
        assertThat(trilha("email=ninguem-" + stamp() + "@teste.<dominio>")).isEmpty();
        assertThat(trilha("entidade=Curso&entidadeId=" + curso + "&de="
                        + Instant.now().plusSeconds(3600)))
                .isEmpty();
    }

    @Test
    @DisplayName("aluno troca nome e senha: registrado com o hash da senha oculto")
    void acaoDoAluno() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());
        assertThat(mvc.put()
                        .uri("/api/perfil")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Novo\"}"))
                .hasStatusOk();
        assertThat(mvc.put()
                        .uri("/api/perfil/senha")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"senhaAtual\":\"%s\",\"novaSenha\":\"outra-senha-forte\"}".formatted(aluno.senha())))
                .hasStatus2xxSuccessful();

        List<Map<String, Object>> registros =
                trilha("entidade=Usuario&entidadeId=" + aluno.id() + "&operacao=ALTERACAO");
        assertThat(registros).extracting(r -> r.get("usuarioEmail")).containsOnly(aluno.email());
        String tudo = mudancas(registros);
        assertThat(tudo).contains("NomeNovo").contains("\"senhaHash\":\"***\"").doesNotContain("argon2");
    }

    @Test
    @DisplayName("segredo do meio de pagamento oculto; operação recusada não deixa rastro")
    void segredoERecusa() {
        String hottok = "hottok-" + stamp();
        long gateway = new PagamentoITHelper(mvc, adm).hotmart(hottok, false).id();
        List<Map<String, Object>> registros = trilha("entidade=Gateway&entidadeId=" + gateway);
        assertThat(registros).isNotEmpty();
        assertThat(mudancas(registros)).contains("\"segredos\":\"***\"").doesNotContain(hottok);

        String slug = stamp();
        adm.criarCurso("IT Curso " + slug, slug);
        int antes = trilha("entidade=Curso").size();
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/cursos",
                        "{\"titulo\":\"Repetido\",\"slug\":\"%s\",\"resumo\":\"x\"}".formatted(slug)))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(trilha("entidade=Curso")).hasSize(antes);
    }

    @Test
    @DisplayName("mudança feita pelo worker fica com o job como origem e sem usuário; progresso fica fora")
    void worker() {
        CursoITHelper admin = adm;
        var estrutura = new MatriculaITHelper(admin).cursoPublicado(stamp());
        PagamentoITHelper pagamentos = new PagamentoITHelper(mvc, admin);
        var gateway = pagamentos.hotmart("hottok-" + stamp(), true);
        String produto = stamp();
        pagamentos.oferta(estrutura.turma(), produto, "k2", null);
        String email = stamp() + "@teste.<dominio>";
        pagamentos.enviar(
                gateway,
                pagamentos
                        .notificacao("PURCHASE_APPROVED")
                        .xcod(null)
                        .transacao("HP-" + stamp())
                        .produto(produto, "k2")
                        .comprador(email, "Compradora")
                        .json(),
                "recebido");
        processarFila();

        List<Map<String, Object>> matriculas = trilha("entidade=Matricula&operacao=INCLUSAO");
        Map<String, Object> daCompra = matriculas.stream()
                .filter(r ->
                        ((String) r.get("depois")).replace(" ", "").contains("\"turmaId\":" + estrutura.turma() + ","))
                .findFirst()
                .orElseThrow();
        assertThat(daCompra)
                .containsEntry("origem", "job pagamento.processar-evento")
                .containsEntry("usuarioId", null)
                .containsEntry("ip", null);
        assertThat(trilha("entidade=ProgressoAula")).isEmpty();
        assertThat(CursoITHelper.corpo(adm.enviar("GET", "/api/adm/auditoria/entidades", null)))
                .contains("\"Matricula\"", "\"Curso\"")
                .doesNotContain("RefreshToken");
        int id = (Integer) daCompra.get("id");
        assertThat(adm.enviar("GET", "/api/adm/auditoria/" + id, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.entidade")
                .isEqualTo("Matricula");
        assertThat(adm.enviar("GET", "/api/adm/auditoria/999999999", null)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("o banco só aceita inclusão; a retenção apaga só o que passou do prazo")
    void somenteInclusaoERetencao() {
        Instant velho = Instant.now().minus(3000, ChronoUnit.DAYS);
        long antigo = jdbc.sql("""
                        INSERT INTO auditoria (operacao, entidade, entidade_id, origem, em)
                        VALUES ('INCLUSAO', 'Teste', :id, 'teste', :em) RETURNING id
                        """)
                .param("id", stamp())
                .param("em", velho.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();

        assertThatThrownBy(() -> jdbc.sql("UPDATE auditoria SET origem = 'x' WHERE id = :id")
                        .param("id", antigo)
                        .update())
                .hasMessageContaining("A auditoria só aceita inclusão");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM auditoria WHERE id = :id")
                        .param("id", antigo)
                        .update())
                .hasMessageContaining("A auditoria só aceita inclusão");

        String slug = stamp();
        long recente = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(auditoriaService.aplicarRetencao()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM auditoria WHERE id = :id")
                        .param("id", antigo)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(trilha("entidade=Curso&entidadeId=" + recente)).hasSize(1);
    }

    @Test
    @DisplayName("só o ADMIN consulta: anônimo 401, aluno 403")
    void papeis() {
        String aluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        for (String rota : List.of("/api/adm/auditoria", "/api/adm/auditoria/entidades", "/api/adm/auditoria/1")) {
            assertThat(mvc.get().uri(rota)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(mvc.get().uri(rota).header(HttpHeaders.AUTHORIZATION, bearer(aluno)))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
        assertThat(adm.enviar("GET", "/api/adm/auditoria", null)).hasStatusOk();
        assertThat(adm.enviar("GET", "/api/adm/auditoria/entidades", null)).hasStatusOk();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/auditoria/ContextoAuditoriaTest.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class ContextoAuditoriaTest {

    @AfterEach
    void limpar() {
        MDC.clear();
    }

    @Test
    @DisplayName("rota com as variáveis quando o Spring MVC já escolheu o mapeamento; senão o caminho")
    void rota() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/adm/cursos/7");
        assertThat(ContextoAuditoria.rota(request)).isEqualTo("PUT /api/adm/cursos/7");

        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/adm/cursos/{id}");
        assertThat(ContextoAuditoria.rota(request)).isEqualTo("PUT /api/adm/cursos/{id}");

        MockHttpServletRequest longa = new MockHttpServletRequest("GET", "/" + "a".repeat(300));
        assertThat(ContextoAuditoria.rota(longa)).hasSize(200);
    }

    @Test
    @DisplayName("sem requisição: sistema; com o job da fila marcado, o job")
    void foraDaRequisicao() {
        assertThat(ContextoAuditoria.atual().origem()).isEqualTo("sistema");
        assertThat(ContextoAuditoria.atual().usuarioId()).isNull();

        MDC.put(ContextoAuditoria.CHAVE_JOB, "pagamento.processar-evento");
        assertThat(ContextoAuditoria.atual().origem()).isEqualTo("job pagamento.processar-evento");
        assertThat(ContextoAuditoria.atual().ip()).isNull();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/auditoria/ValoresAuditoriaTest.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValoresAuditoriaTest {

    @Test
    @DisplayName("valores simples: enum pelo nome, data e UUID em texto, binário marcado, entidade pelo id")
    void simples() {
        assertThat(ValoresAuditoria.simples("texto")).isEqualTo("texto");
        assertThat(ValoresAuditoria.simples(42L)).isEqualTo(42L);
        assertThat(ValoresAuditoria.simples(true)).isEqualTo(true);
        assertThat(ValoresAuditoria.simples(StatusTurma.ATIVA)).isEqualTo("ATIVA");
        assertThat(ValoresAuditoria.simples(Instant.parse("2026-10-22T13:00:00Z")))
                .isEqualTo("2026-10-22T13:00:00Z");
        UUID uuid = UUID.randomUUID();
        assertThat(ValoresAuditoria.simples(uuid)).isEqualTo(uuid.toString());
        assertThat(ValoresAuditoria.simples(new byte[] {1, 2})).isEqualTo("[binário]");
        assertThat(ValoresAuditoria.simples(List.of(Papel.ALUNO))).isEqualTo(List.of("ALUNO"));
        assertThat(ValoresAuditoria.simples(curso(7, "java"))).isEqualTo(7L);
        assertThat(ValoresAuditoria.simples(new StringBuilder("x"))).isEqualTo("x");
        assertThat(ValoresAuditoria.simples(null)).isNull();
    }

    @Test
    @DisplayName("campos sensíveis viram *** (mesmo nas superclasses), nulos ficam nulos")
    void sensiveis() {
        assertThat(ValoresAuditoria.sensiveis(Usuario.class)).containsExactly("senhaHash");
        assertThat(ValoresAuditoria.sensiveis(Gateway.class)).containsExactlyInAnyOrder("segredos", "webhookToken");
        assertThat(ValoresAuditoria.valor(Usuario.class, "senhaHash", "{argon2}abc"))
                .isEqualTo("***");
        assertThat(ValoresAuditoria.valor(Usuario.class, "senhaHash", null)).isNull();
        assertThat(ValoresAuditoria.valor(Usuario.class, "nome", "Ana")).isEqualTo("Ana");
    }

    @Test
    @DisplayName("campos escolhidos pelo índice (alteração) ou todos (inclusão e exclusão); sem estado, nulo")
    void campos() {
        String[] nomes = {"nome", "senhaHash", "papeis"};
        Object[] estado = {"Ana", "{argon2}abc", Set.of(Papel.ADMIN)};

        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("nome", "Ana");
        todos.put("senhaHash", "***");
        todos.put("papeis", List.of("ADMIN"));
        assertThat(ValoresAuditoria.campos(Usuario.class, nomes, estado, null)).containsExactlyEntriesOf(todos);
        assertThat(ValoresAuditoria.campos(Usuario.class, nomes, estado, new int[] {1}))
                .containsExactly(Map.entry("senhaHash", "***"));
        assertThat(ValoresAuditoria.campos(Usuario.class, nomes, null, null)).isNull();
    }

    @Test
    @DisplayName("entidades de log e de sessão ficam fora da trilha")
    void foraDaTrilha() {
        assertThat(OuvinteAuditoria.auditada(br.com.celke.cursos.progresso.domain.ProgressoAula.class))
                .isFalse();
        assertThat(OuvinteAuditoria.auditada(br.com.celke.cursos.auth.domain.RefreshToken.class))
                .isFalse();
        assertThat(OuvinteAuditoria.auditada(turma(1, curso(1, "x")).getClass()))
                .isTrue();
    }
}
`````

`web/adm/src/auditoria.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { mudancas } from './paginas/auditoria/AuditoriaPagina.tsx';

describe('campos da auditoria', () => {
  it('alteração: antes e depois lado a lado', () => {
    expect(
      mudancas('{"titulo": "Antigo", "ativo": true}', '{"titulo": "Novo", "ativo": false}'),
    ).toEqual([
      { campo: 'titulo', antes: 'Antigo', depois: 'Novo' },
      { campo: 'ativo', antes: 'true', depois: 'false' },
    ]);
  });

  it('inclusão e exclusão: o lado que falta vira travessão; nulo vira "vazio"', () => {
    expect(mudancas(null, '{"fim": null, "papeis": ["ALUNO"]}')).toEqual([
      { campo: 'fim', antes: '—', depois: 'vazio' },
      { campo: 'papeis', antes: '—', depois: '["ALUNO"]' },
    ]);
    expect(mudancas('{"senhaHash": "***"}', null)).toEqual([
      { campo: 'senhaHash', antes: '***', depois: '—' },
    ]);
  });

  it('JSON inválido não quebra a tela', () => {
    expect(mudancas('quebrado', null)).toEqual([]);
  });
});
`````

`web/adm/src/paginas/auditoria/AuditoriaPagina.tsx`:

`````tsx
import {
  CabecalhoPagina,
  CampoSelect,
  CampoTexto,
  Detalhes,
  Dialogo,
  Status,
  Tabela,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import {
  OPERACAO_AUDITORIA,
  opcoes,
  type OperacaoAuditoria,
  type RegistroAuditoria,
} from '../../tipos.ts';

const TAMANHO = 20;

/**
 * Trilha de auditoria: quem incluiu, alterou ou excluiu o quê, de onde e quando. Só leitura (o banco
 * não aceita alteração da trilha).
 */
export function AuditoriaPagina() {
  const [pagina, setPagina] = useState(1);
  const [entidade, setEntidade] = useState<string | null>(null);
  const [operacao, setOperacao] = useState<OperacaoAuditoria | null>(null);
  const [email, setEmail] = useState('');
  const [entidadeId, setEntidadeId] = useState('');
  const [aberto, setAberto] = useState<RegistroAuditoria | null>(null);
  const filtroEmail = email.trim();
  const filtroId = entidadeId.trim();

  const entidades = useCarregar(() => api.get<string[]>('/api/adm/auditoria/entidades'), []);
  const registros = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (entidade) filtro.set('entidade', entidade);
    if (operacao) filtro.set('operacao', operacao);
    if (filtroEmail) filtro.set('email', filtroEmail);
    if (filtroId) filtro.set('entidadeId', filtroId);
    return api.get<Pagina<RegistroAuditoria>>(`/api/adm/auditoria?${filtro}`);
  }, [pagina, entidade, operacao, filtroEmail, filtroId]);

  const mudar =
    <T,>(definir: (valor: T) => void) =>
    (valor: T) => {
      definir(valor);
      setPagina(1);
    };

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Auditoria" icone="shield-check">
        Cada inclusão, alteração e exclusão feita na plataforma, com quem fez, de onde e o antes e o
        depois dos campos. Senhas, tokens e segredos aparecem como ***.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoSelect<string>
          rotulo="Entidade"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={(entidades.dados ?? []).map((e) => ({ valor: e, rotulo: e }))}
          valor={entidade}
          aoMudar={mudar(setEntidade)}
        />
        <CampoSelect
          rotulo="Operação"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={opcoes(OPERACAO_AUDITORIA)}
          valor={operacao}
          aoMudar={mudar(setOperacao)}
        />
        <CampoTexto rotulo="Id" valor={entidadeId} aoMudar={mudar(setEntidadeId)} maxLength={64} />
        <CampoTexto
          rotulo="E-mail do usuário"
          valor={email}
          aoMudar={mudar(setEmail)}
          maxLength={254}
        />
      </div>
      <Tabela<RegistroAuditoria>
        rotulo="Trilha de auditoria"
        colunas={[
          { campo: 'em', rotulo: 'Quando', formato: (v) => dataHora(v as string) },
          {
            campo: 'operacao',
            rotulo: 'Operação',
            render: (r) => (
              <Status
                rotulo={OPERACAO_AUDITORIA[r.operacao].rotulo}
                severidade={OPERACAO_AUDITORIA[r.operacao].severidade}
              />
            ),
          },
          {
            campo: 'entidade',
            rotulo: 'Registro',
            destaque: true,
            formato: (v, r) => `${v as string}${r.entidadeId ? ` #${r.entidadeId}` : ''}`,
          },
          { campo: 'usuarioEmail', rotulo: 'Usuário', formato: (_, r) => quem(r) },
          { campo: 'origem', rotulo: 'Origem' },
        ]}
        registros={registros.dados?.itens ?? []}
        carregando={registros.carregando && !registros.dados}
        atualizando={registros.carregando && !!registros.dados}
        erro={registros.erro}
        aoRecarregar={registros.recarregar}
        vazioTitulo="Nada registrado"
        vazioDescricao="Nenhuma ação com estes filtros."
        vazioIcone="shield-check"
        aoClicarLinha={setAberto}
        paginacao={{
          total: registros.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto && <DialogoRegistro registro={aberto} aoFechar={() => setAberto(null)} />}
    </div>
  );
}

function quem(registro: RegistroAuditoria): string {
  if (registro.usuarioEmail) return registro.usuarioEmail;
  if (registro.usuarioId) return `Usuário #${registro.usuarioId}`;
  return registro.origem.startsWith('job ') ? 'Sistema (worker)' : 'Sistema ou visitante';
}

export interface Mudanca {
  campo: string;
  antes: string;
  depois: string;
}

/** Campos do antes e do depois lado a lado, na ordem em que aparecem. */
export function mudancas(antes: string | null, depois: string | null): Mudanca[] {
  const a = objeto(antes);
  const d = objeto(depois);
  const campos = [...new Set([...Object.keys(a), ...Object.keys(d)])];
  return campos.map((campo) => ({
    campo,
    antes: campo in a ? texto(a[campo]) : '—',
    depois: campo in d ? texto(d[campo]) : '—',
  }));
}

function objeto(json: string | null): Record<string, unknown> {
  if (!json) return {};
  try {
    const valor: unknown = JSON.parse(json);
    return valor && typeof valor === 'object' ? (valor as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function texto(valor: unknown): string {
  if (valor === null || valor === undefined) return 'vazio';
  return typeof valor === 'string' ? valor : JSON.stringify(valor);
}

function DialogoRegistro({
  registro,
  aoFechar,
}: {
  registro: RegistroAuditoria;
  aoFechar: () => void;
}) {
  const linhas = mudancas(registro.antes, registro.depois);
  return (
    <Dialogo
      aberto
      titulo={`${OPERACAO_AUDITORIA[registro.operacao].rotulo}: ${registro.entidade}${registro.entidadeId ? ` #${registro.entidadeId}` : ''}`}
      subtitulo={dataHora(registro.em)}
      tamanho="grande"
      aoFechar={aoFechar}
    >
      <div className="pc-pilha">
        <div className="pc-grade-detalhes">
          <Detalhes rotulo="Usuário" valor={registro.usuarioNome ?? quem(registro)} />
          <Detalhes rotulo="E-mail" valor={registro.usuarioEmail} />
          <Detalhes rotulo="Origem" valor={registro.origem} />
          <Detalhes rotulo="IP" valor={registro.ip} />
          <Detalhes rotulo="Id da requisição" valor={registro.requestId} />
          <Detalhes rotulo="Navegador" valor={registro.userAgent} />
        </div>
        <Tabela<Mudanca>
          rotulo="Campos"
          colunas={[
            { campo: 'campo', rotulo: 'Campo', destaque: true },
            { campo: 'antes', rotulo: 'Antes' },
            { campo: 'depois', rotulo: 'Depois' },
          ]}
          registros={linhas}
          chave={(m) => m.campo}
          vazioTitulo="Sem campos"
          vazioDescricao="O registro não guardou valores."
        />
      </div>
    </Dialogo>
  );
}
`````

`web/e2e/README.md`:

`````markdown
# Testes E2E dos fronts (Playwright)

Testes ponta a ponta: dirigem um navegador real contra a **stack de e2e rodando de verdade** — Caddy,
API, worker (fila, e-mail e conversão de vídeo), fronts, gerador do site, PostgreSQL, Garage e Mailpit,
com as mesmas imagens que vão para produção. Não há mock de API: dependem dos containers no ar.

**Nunca contra um servidor.** A stack de e2e é descartável (`down -v` apaga tudo) e as contas do seed só
existem nela; `support/config.ts` recusa qualquer endereço que não seja `*.localhost`.

**Neste documento:** [Começo rápido](#começo-rápido) · [O que é testado aqui](#o-que-é-e-o-que-não-é-testado-aqui) ·
[Pré-requisitos](#pré-requisitos-uma-vez-por-máquina) · [Como rodar](#como-rodar) ·
[Contas e sessão](#contas-e-sessão) · [Dado de teste e limpeza](#dado-de-teste-e-limpeza) ·
[Os specs](#os-specs) · [Lighthouse do site](#lighthouse-do-site) · [Manutenção](#manutenção)

## Começo rápido

Depois dos pré-requisitos (uma vez por máquina, abaixo), na **raiz do repositório**:

**1. Subir a stack de e2e** (a primeira vez constrói as imagens e leva alguns minutos):

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

**2. Rodar os testes**, dentro de `web/`:

```bash
cd web
npm run e2e                                  # todos (aluno, ADM e site)
E2E_SLOWMO=500 npm run e2e:headed -- e2e/adm/ # só o ADM, com a janela aberta e em câmera lenta
```

**3. Derrubar a stack** quando terminar (apaga banco, vídeos e e-mails):

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- Troque `e2e/adm/` por `e2e/aluno/`, `e2e/site/` ou um arquivo (`e2e/aluno/compra.spec.ts`): apontar
  para uma pasta roda todos os specs dela.
- `:headed` e `E2E_SLOWMO` servem só para **acompanhar na tela**; para rodar rápido, sem janela,
  use `npm run e2e`.
- Mudou o backend ou os fronts? Suba a stack de novo com o `--build` para os testes verem o código novo.

O resto deste documento é referência: não é preciso ler tudo para rodar o primeiro teste.

## O que é e o que não é testado aqui

| Camada             | Cobre                                                                                                  | Onde                                                                            |
| :----------------- | :----------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------ |
| Unidade (front)    | funções puras, componentes, cliente HTTP                                                               | `{aluno,adm,compartilhado}/src/**/*.test.ts(x)` e `site/*.test.ts` — `npm test` |
| Backend            | regra, rotas, segurança, banco, fila                                                                   | `backend/src/test` — `./mvnw verify`                                            |
| Jornadas da API    | API pelo Caddy, worker, Garage e e-mail, sem navegador                                                 | `backend/src/e2e` — `./mvnw verify -Pe2e`                                       |
| **E2E dos fronts** | **telas de verdade**: navegação, formulários, mensagens, sessão por cookie, vídeo tocando, site gerado | **aqui**                                                                        |

**Regra de negócio não sobe para cá.** Se o caso é de cálculo, validação ou permissão, ele tem teste no
backend ou de unidade; aqui fica o que só o navegador prova (a tela mostra o erro certo, o vídeo toca, o
cookie mantém a sessão, o link do e-mail funciona).

## Pré-requisitos (uma vez por máquina)

1. Docker com o Docker Compose (a stack sobe com ele).
2. Node.js 24 e as dependências: `cd web && npm ci`
3. O navegador do Playwright: `npx playwright install chromium`
4. As portas **8088** (Caddy) e **8026** (Mailpit) livres.

Os navegadores atuais resolvem `*.localhost` sozinhos: não é preciso mexer no `hosts`.

## Como rodar

Todos os comandos dentro de `web/`, com a stack de e2e no ar.

**Escolher o que rodar** — uma pasta, um arquivo, um teste pelo nome ou um projeto:

```bash
npm run e2e                                    # tudo
npm run e2e -- e2e/aluno/                      # só a área do aluno
npm run e2e -- e2e/aluno/compra.spec.ts        # só um spec
npm run e2e -- -g "reembolso"                  # só os testes cujo nome tem o texto
npm run e2e -- --project=site                  # projeto: aluno, adm ou site
```

**Ver o teste rodando** — combine com qualquer seleção acima:

```bash
npm run e2e:ui                                 # melhor opção: linha do tempo, volta no tempo, rede
npm run e2e:headed                             # janela maximizada
E2E_SLOWMO=500 npm run e2e:headed              # câmera lenta (ms entre ações): dá para ler a tela
npm run e2e:debug -- e2e/adm/turma.spec.ts     # passo a passo, com o Inspector
```

**Depois de rodar:**

```bash
npm run e2e:report                             # abre o último relatório HTML
npm run e2e:trace test-results/<pasta>/trace.zip   # revê uma falha gravada (o relatório mostra o caminho)
```

Quando um teste falha, ficam em `test-results/` o trace, o vídeo e a captura da tela. A janela só
maximiza com `--headed`; na execução normal o viewport é fixo (Desktop Chrome), igual em qualquer
máquina.

Os testes rodam **um de cada vez** (`workers: 1`): escrevem no mesmo banco e gastam o mesmo limite de
requisições por IP.

**Variáveis** (padrões de `infra/e2e.env`; só mude se subir a stack com outras portas):

| Variável          | Padrão                         | Uso                                       |
| :---------------- | :----------------------------- | :---------------------------------------- |
| `E2E_ALUNO_URL`   | `http://aluno.localhost:8088`  | área do aluno                             |
| `E2E_ADM_URL`     | `http://adm.localhost:8088`    | ADM                                       |
| `E2E_SITE_URL`    | `http://cursos.localhost:8088` | site público                              |
| `E2E_MAILPIT_URL` | `http://localhost:8026`        | e-mails (links de confirmação e de senha) |
| `E2E_SENHA_SEED`  | `123456A#b123`                 | senha das contas do seed                  |
| `E2E_SLOWMO`      | `0`                            | câmera lenta, em ms entre ações           |

## Contas e sessão

- **Seed da stack** (criado pela API no perfil `e2e`): `admin@<dominio>` e
  `aluno@<dominio>`, senha `123456A#b123`. São **só leitura**: nenhum teste troca a senha,
  sai ou mexe no perfil delas.
- **Sessão nova por teste**: as fixtures `comoAdmin` e `comoAluno` (`support/sessoes.ts`) entram pela API
  no contexto do navegador; ao abrir a página, o app renova a sessão pelo cookie `HttpOnly`. Não há
  sessão gravada: o refresh token é rotativo e um token reutilizado derruba a sessão inteira.
- **Conta própria**: teste que troca senha, sai, compra ou mexe no perfil cria um aluno novo
  (`support/contas.ts`), confirmado pelo link que chega no Mailpit.
- **Termos**: se outra jornada publicou termos, `aceitarTermosSePedir` (`support/telas.ts`) aceita antes
  de seguir.

## Dado de teste e limpeza

- Cursos, contas e ofertas levam um carimbo único (`pw<tempo><sequência>`, e-mails
  `pw...@teste.<dominio>`): execuções seguidas não colidem, mesmo sem limpar a stack.
- Os testes **não apagam** o que criam; o lixo some com o `down -v` da stack. Para começar do zero:
  `down -v` e `up` de novo.
- O pagamento não sai para a internet: a ida ao `pay.hotmart.com` é interceptada (`page.route`) e a
  notificação da Hotmart é enviada pelo próprio teste (`support/pagamentos.ts`, a partir das fixtures do
  backend).

## Os specs

**ADM** (`e2e/adm/`)

- **`acesso.spec.ts`** — entrada do admin, volta para onde ia, senha errada; aluno barrado na tela e na
  API do ADM.
- **`conteudo.spec.ts`** — cursos (validação, slug repetido, capa em formato errado, exclusão com
  turmas, filtro), conteúdo da turma (reordenar, inativar, exclusão com aulas) e aula (vídeo recusado na
  tela e pela API, edição, arquivo de apoio recusado).
- **`curso-completo.spec.ts`** — cria um curso completo e envia um vídeo curto até ficar "pronto"
  (o worker converte de verdade).
- **`turma.spec.ts`** — matrículas (matricular, período, cancelar, e-mail sem conta, turma encerrada),
  moderação do fórum e configurações da turma (certificado, exclusão).
- **`termos.spec.ts`** — texto vazio barrado; publicação de uma versão nova.
- **`pagamentos.spec.ts`** — cadastra a Hotmart (validação, credencial errada recusada), ativa e vende
  uma turma; evento de oferta não cadastrada é ignorado e reprocessado depois; o pedido aparece no
  painel.
- **`auditoria.spec.ts`** — a trilha mostra quem criou o curso, de onde e os campos gravados.

**Aluno** (`e2e/aluno/`)

- **`entrar.spec.ts`** — login, senha errada, e-mail inexistente com a mesma resposta, volta para a
  página privada, redirecionamento externo ignorado, 404.
- **`cadastro.spec.ts`** — cadastro confirmado pelo link do e-mail; validações; e-mail já cadastrado não
  é revelado; link de confirmação inválido ou sem token.
- **`senha.spec.ts`** — recuperação de senha pelo e-mail e os casos de link inválido.
- **`sessao.spec.ts`** — a sessão volta pelo cookie ao abrir e recarregar; sair encerra; cookie de
  refresh reutilizado derruba a família.
- **`estudo.spec.ts`** — do curso à aula: o vídeo toca até o fim, o progresso sobe, retomada, vista e
  revisar, anterior e próxima, arquivo de apoio, fórum da aula, certificado (bloqueado → emitido →
  validado); sem matrícula, matrícula futura, arquivar.
- **`perfil.spec.ts`** — troca de nome, foto (formato errado recusado) e senha.
- **`compra.spec.ts`** — compra pelo botão do site com a Hotmart interceptada: aprovada (o curso
  aparece), reembolso (o curso some), boleto aguardando, e o "Comprar" sem login que volta para a compra
  depois de entrar.

**Site** (`e2e/site/`)

- **`site.spec.ts`** — home e catálogo com o curso publicado (rascunho não aparece), página do curso com
  meta tags, capa, 404, rotas da API fora das públicas barradas, sitemap e robots, validação de
  certificado e termos publicados. O site é regenerado a cada 3 s na stack de e2e; os testes recarregam
  até a mudança aparecer.

**Apoio** (`e2e/support/`): `config.ts` (endereços e trava), `api.ts` (montar dados pela API),
`sessoes.ts` (fixtures de sessão), `contas.ts` e `mailpit.ts` (contas confirmadas pelo e-mail),
`telas.ts` (login pela tela, termos, escolher opção do `CampoSelect`), `amostras.ts` (arquivos de
amostra), `pagamentos.ts` (Hotmart e notificações).

## Lighthouse do site

Com a stack no ar, mede desempenho, acessibilidade, boas práticas e SEO das páginas do site (desktop) e
falha abaixo de 90, 95, 95 e 90:

```bash
npm run lighthouse
```

Precisa de pelo menos um curso publicado (rode antes o `npm run e2e -- e2e/site/`).

## Manutenção

- Seletores pelo que o usuário vê: campo pelo rótulo (`getByLabel`, `getByRole('textbox', { name })`),
  botão e link pelo nome acessível (`getByRole`), linha da tabela pelo dado que o próprio teste criou.
  **Nunca** por posição nem por classe interna da `@celke/kole-react` (`.lp-*`): ela muda numa
  atualização da biblioteca.
- O `CampoSelect` da biblioteca não dá nome acessível ao combobox: use `escolher(page, rótulo, opção)`.
- Se a tela mudar um rótulo por decisão de UX, **conserte o teste** — não volte o rótulo para acomodar a
  automação.
- Espera sempre por condição (`expect(...).toBeVisible()`, `expect.poll`, `toPass`), nunca por tempo
  fixo.
`````

`web/e2e/adm/auditoria.spec.ts`:

`````ts
import { request as criarRequest } from '@playwright/test';
import { clienteApi, montarCurso } from '../support/api.ts';
import { ADMIN_SEED } from '../support/config.ts';
import { expect, test } from '../support/sessoes.ts';
import { escolher } from '../support/telas.ts';

test('a trilha mostra quem criou o curso, de onde e os campos gravados', async ({
  comoAdmin: page,
}) => {
  const contexto = await criarRequest.newContext();
  const curso = await montarCurso(await clienteApi(contexto));
  await contexto.dispose();

  await page.goto('/auditoria');
  await expect(page.getByRole('heading', { name: 'Auditoria' })).toBeVisible();
  await escolher(page, 'Entidade', 'Curso');
  await page.getByRole('textbox', { name: 'Id' }).fill(String(curso.cursoId));
  const linha = page.getByRole('row').filter({ hasText: `Curso #${curso.cursoId}` });
  await expect(linha).toHaveCount(1);
  await expect(linha.getByText(ADMIN_SEED)).toBeVisible();
  await expect(linha.getByText('POST /api/adm/cursos')).toBeVisible();

  await linha.click();
  const dialogo = page.getByRole('dialog', { name: `Inclusão: Curso #${curso.cursoId}` });
  await expect(dialogo.getByRole('cell', { name: 'titulo', exact: true })).toBeVisible();
  await expect(dialogo.getByRole('cell', { name: curso.titulo })).toBeVisible();
  await expect(dialogo.getByRole('cell', { name: 'RASCUNHO' })).toBeVisible();
});
`````

### Dependências do front

Depois de gravar os arquivos, dentro de `web/`, rode `npm install` para instalar as dependências do `package.json` e gerar o `package-lock.json`.

## Antes de executar

Fase 16 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/auditoria
```

## Verificação

- `./mvnw verify` e as jornadas `-Pe2e`; no `web/`, format, lint, typecheck, test, build e `npm run e2e`.
- Na stack de e2e: alterar um curso pelo ADM e ver o registro em Segurança → Auditoria; `docker compose
  ... exec api ls /var/log/plataforma-cursos` mostra o `api.log`.
- `UPDATE auditoria ...` direto no banco é recusado.
- Com tudo verde, as tasks de `.sdd/tasks/auditoria-v1.0/` passam para `Status: Concluída` e os critérios de aceite
  são marcados (`- [x]`).

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md .sdd/
git commit -m "feat(auditoria): auditar as ações e guardar os logs em arquivo"
git push -u origin feature/auditoria
```

Abra o Pull Request de `feature/auditoria` para `homolog`.
