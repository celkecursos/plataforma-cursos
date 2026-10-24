# Estrutura inicial do repositório

**Fase:** 0 — Preparação
**Branch:** `main`

## Prompt

Estamos começando o projeto **Plataforma de Cursos**: uma plataforma de cursos online com área do
aluno, painel administrativo e site. O backend será em Java 25 com Spring Boot 4.1 e PostgreSQL 18,
os vídeos ficarão num armazenamento S3 (Garage) convertidos para HLS com ffmpeg, o frontend será em
React usando a biblioteca `@celke/kole-react`, e a infraestrutura usará Docker, Caddy, GitHub Actions e deploy
numa VPS.

Nesta etapa, crie apenas os arquivos iniciais: o `README.md`, o `.gitignore`, a licença, o plano
de implementação e o roteiro do tutorial. Não crie código, pastas de backend/frontend nem faça commit.

### 0. Configuração do projeto

Antes de criar qualquer arquivo, **pergunte ao usuário** e espere as respostas. Domínio, subdomínios e
repositório são de quem cria o projeto: não use valores de exemplo.

1. "Qual é o seu domínio? (ex.: meudominio.com.br) Os endereços do projeto serão subdomínios dele."
2. "Quais subdomínios usar? Enter aceita o padrão: site `cursos`, área do aluno `aluno`, ADM `adm` e
   mídia `media`. A homologação usa os mesmos com o prefixo `homolog-`."
3. "Qual usuário ou organização do GitHub é dono do repositório?" (sugira o dono do `origin`, se
   houver)

Confira com o usuário que nenhum desses subdomínios já está em uso. Crie `docs/configuracao.md` com
as respostas (troque os valores da segunda coluna):

`````markdown
# Configuração do projeto

Valores deste projeto. Os prompts usam os marcadores da primeira coluna no lugar deles.

| Marcador | Valor | Uso |
|---|---|---|
| `<dominio>` | meudominio.com.br | domínio dos endereços e dos e-mails |
| `<sub-site>` | cursos | site público |
| `<sub-aluno>` | aluno | área do aluno e API |
| `<sub-adm>` | adm | painel administrativo e API (restrito por IP) |
| `<sub-media>` | media | vídeos e arquivos (Garage) |
| `<dono-github>` | meu-usuario | dono do repositório e das imagens no GHCR |

Na homologação, os subdomínios ganham o prefixo `homolog-` (ex.: `homolog-aluno.meudominio.com.br`).
`````

A partir daqui, troque esses marcadores pelos valores informados em todo o conteúdo deste prompt.

### 1. `README.md`

Crie com exatamente este conteúdo:

````markdown
# Plataforma de Cursos

Plataforma de cursos online com área do aluno, painel administrativo e site, desenvolvida passo a passo
como tutorial.

- **Backend:** Java 25, Spring Boot 4.1, PostgreSQL 18
- **Vídeos:** armazenamento S3 (Garage) com conversão para HLS via ffmpeg
- **Frontend:** React com [`@celke/kole-react`](https://github.com/celkecursos/kole-react)
- **Infraestrutura:** Docker, Caddy, GitHub Actions, deploy em VPS

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
- [Roteiro do tutorial (capítulos)](docs/capitulos.md)
- [Prompts para recriar o projeto do zero](docs/prompts-iniciais/README.md)
````

### 2. `.gitignore`

Crie com exatamente este conteúdo:

```gitignore
# Segredos
.env
.env.*
!.env.example

# Java / Maven
target/
*.class
*.log

# Node
node_modules/
dist/
.react-router/
coverage/
playwright-report/
test-results/

# IDE / sistema
.idea/
.vscode/
*.iml
.DS_Store
```

### 3. `LICENSE.txt` (licença MIT)

Antes de criar este arquivo, **pergunte ao usuário** e espere a resposta:

1. "Qual nome (pessoa ou empresa) aparece como titular dos direitos na licença?"
2. "Qual ano entra na licença? (Enter para usar o ano atual)"

Crie `LICENSE.txt` na raiz com o texto abaixo, trocando `<ano>` e `<titular>` pelas respostas (sem os
sinais `<` e `>`):

```text
Copyright (c) <ano> <titular>

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### 4. `docs/plano-implementacao.md`

Crie com exatamente este conteúdo:

`````markdown
# Plano de implementação — Plataforma de Cursos

Sistema do aluno (cursos, aulas em vídeo, fórum, certificado) com backend Java, frontend React
usando `@celke/kole-react`, tudo em Docker numa VPS da Hostinger. Este plano também é o roteiro do tutorial:
cada fase vira uma branch e um ou mais capítulos.

**Prompts:** cada etapa implementada tem um prompt em [`docs/prompts/`](prompts/README.md). Executados
na ordem do índice, eles recriam o projeto do zero.

**Ordem de trabalho:** primeiro todo o backend (com infraestrutura, CI e deploy), depois o frontend,
com a `@celke/kole-react` instalada do GitHub (commit fixo no `package.json`).

---

## 1. Decisões

| Tema | Decisão |
|---|---|
| Java | **Java 25 LTS** |
| Backend | **Spring Boot 4.1.x** (última correção disponível ao criar o projeto); organizado por funcionalidade; profiles `api` e `worker` no mesmo código. Atualizar para 4.2 numa branch `fix/` quando sair |
| Banco | **PostgreSQL 18** + Flyway |
| Fila | Tabela no PostgreSQL com `SELECT ... FOR UPDATE SKIP LOCKED` (sem Redis/RabbitMQ) |
| S3 na VPS | **Garage** (`dxflrs/garage:v2.4.1`), modo `--single-node`. Escolhido por ser leve, ter configuração curta e suportar tudo que o projeto usa (veja abaixo). O MinIO comunitário parou de publicar imagens Docker e entrou em modo de manutenção |
| Acesso ao S3 | Somente AWS SDK v2 com path-style; trocar de servidor = trocar endpoint no `.env` |
| Vídeo | Upload direto por URL pré-assinada → ffmpeg → HLS 720p + 1080p → playlist reescrita pela API com segmentos assinados |
| E-mail | **SMTP da Iagente** |
| Frontend | Pacote único em `web/` com `aluno`, `adm` e o gerador do `site`; `@celke/kole-react` instalada **do GitHub** com commit fixo |
| Hospedagem | Tudo em Docker na VPS (2 vCPU, 8 GB, 100 GB NVMe); Caddy com HTTPS automático serve os fronts e faz proxy de `/api` |
| Ambientes | Produção + homologação leve na mesma VPS |
| Branches | `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main` |
| CI | GitHub Actions: testes + e2e em todo PR; build e e2e refeitos na `main` |
| Deploy | SSH com chave presa a `deploy.sh`, imagem com tag = SHA, health check e rollback |

### Garage: o que o projeto usa e o que ele não tem

- ✅ URLs pré-assinadas, upload multipart, CORS por bucket, lifecycle com `Expiration` e `AbortIncompleteMultipartUpload` (limpa uploads abandonados)
- ❌ Versionamento, ACLs e bucket policies. Não fazem falta: o bucket é privado e o acesso é por chave com permissão por bucket.
- Um bucket privado `midia`, com os prefixos `uploads/` (originais enviados) e `videos/` (HLS e thumbnails). O próprio Garage cria o bucket e a chave da aplicação na primeira subida (`--default-bucket`); a expiração de uploads abandonados é feita por prefixo.

### Domínios (somente subdomínios de `<dominio>`)

O domínio principal `<dominio>` pode ter outras aplicações e não é tocado: o projeto usa só os subdomínios abaixo.

| Produção | Homologação | Serve |
|---|---|---|
| `<sub-site>.<dominio>` | `homolog-<sub-site>.<dominio>` | site |
| `<sub-aluno>.<dominio>` | `homolog-<sub-aluno>.<dominio>` | SPA do aluno + `/api` |
| `<sub-adm>.<dominio>` | `homolog-<sub-adm>.<dominio>` | SPA do ADM + `/api` (restrito por IP) |
| `<sub-media>.<dominio>` | `homolog-<sub-media>.<dominio>` | Garage (subdomínio próprio: a assinatura depende do host) |

> Nomes propostos — confirmar que nenhum desses subdomínios já está em uso antes da etapa 1.8 (VPS).

Cuidados por dividir o domínio com outras aplicações:

- Cookies **sem** atributo `Domain` (cookie só do host). Com `Domain=<dominio>`, todas as outras aplicações dos subdomínios receberiam o refresh token.
- A CSP e o CORS do Garage listam os subdomínios exatos, nunca `*.<dominio>`.
- HSTS sem `includeSubDomains`, a não ser que todas as aplicações de `<dominio>` já usem HTTPS.

### SMTP Iagente

- Conta: cadastro pelo link https://celke.com.br/page/iagente, que dá alguns e-mails gratuitos para enviar.
- Host, porta e credenciais vêm do painel da Iagente (porta 587 com STARTTLS é o padrão esperado; confirmar no painel).
- Remetente: endereço de `<dominio>` com SPF e DKIM configurados na Iagente.
- Credenciais só no `.env` de cada ambiente. Homologação com remetente identificado ("[HOMOLOG]") ou envio restrito a e-mails de teste.
- No dev e no e2e: Mailpit no compose (captura os e-mails sem enviar).

---

## 2. Fluxo de branches e pipeline

```
feature/x ─┐
fix/y ─────┼─PR─▶ homolog ──PR──▶ main
           │      (deploy          (build refeito,
           │       homologação)     deploy produção)
hotfix/z ──────────────────────PR─▶ main ──merge──▶ homolog
```

| Evento | Testes | e2e | Deploy |
|---|---|---|---|
| PR → `homolog` ou → `main` | ✅ | ✅ | — |
| Push em `homolog` | ✅ | ✅ | homologação |
| Push em `main` | ✅ | ✅ | produção (aprovação opcional no environment `production`) |

Regras:

- `main` recebe código **somente** da `homolog` (ou de `hotfix/*`). Alteração reprovada na homologação é desfeita com `git revert` na `homolog` antes de promover.
- Depois de um hotfix, merge da `main` de volta na `homolog`.
- Branch de trabalho apagada automaticamente após o merge (Settings → *Automatically delete head branches*).
- Branches curtas: merge em poucos dias. A `feature/base-projeto` é a única exceção.

**e2e por etapa:**

- **Fases de backend:** e2e de API — o compose completo sobe no Actions e os testes chamam a API por HTTP, passando pelo Caddy (REST Assured num módulo separado, ou Playwright em modo API).
- **Fases de frontend:** acrescenta Playwright no navegador.

---

## 3. Estrutura do repositório

```
plataforma-cursos/
├── CLAUDE.md                    regras gerais do repositório
├── .claude/                     orientações para agentes de IA: rules, skills (commits, especificação),
│                                agentes e comandos do pipeline SDD (/sdd → /gerar-tasks → /implementar-tasks)
├── .sdd/                        specs e tasks geradas pelo pipeline SDD (quando usado)
├── backend/
│   ├── CLAUDE.md                regras do projeto Java (código e testes)
│   ├── .claude/skills/          criar-teste-unitario, criar-teste-integracao, criar-teste-e2e
│   │                            (cada uma com SKILL.md = processo e padrao.md = padrão)
│   ├── Dockerfile               targets: api | worker (com ffmpeg)
│   ├── pom.xml                  Surefire (*Test), Failsafe (*IT), JaCoCo, Spotless, perfil e2e
│   └── src/
│       ├── main/java/br/com/celke/cursos/
│       │   ├── shared/          config, security, erros, storage S3, fila, e-mail
│       │   ├── auth/
│       │   ├── usuario/
│       │   ├── curso/           curso, turma, módulo, aula
│       │   ├── matricula/       matrícula + AcessoAulaPolicy
│       │   ├── video/           upload, transcodificação, playlist
│       │   ├── progresso/
│       │   ├── arquivo/
│       │   ├── forum/
│       │   ├── certificado/
│       │   └── perfil/
│       │   (cada módulo: api/ · application/ · domain/ · infra/)
│       ├── test/java/           unitários (*Test) e integração (*IT) + support/
│       └── e2e/java/            e2e caixa-preta (*E2E: REST Assured + Awaitility)
├── web/                         (a partir da Fase 11) um pacote só, um build por app
│   ├── package.json
│   ├── compartilhado/           cliente HTTP, sessão, rotas protegidas, validação
│   ├── aluno/
│   ├── adm/
│   ├── site/                    site público (React Router com renderização no servidor)
│   ├── e2e/                     Playwright contra a stack de e2e
│   ├── Dockerfile               build dos fronts + Caddy interno (aluno :8081, ADM :8082)
│   └── Caddyfile
├── infra/
│   ├── compose.yml              ambiente local (inclui Mailpit)
│   ├── compose.prod.yml         um ambiente dos servidores: API, worker, web, PostgreSQL, Garage
│   ├── compose.homolog.yml      sobreposição: limites menores e Swagger
│   ├── compose.e2e.yml          sobreposição: Caddy com *.localhost, Mailpit, seed e2e
│   ├── e2e.env                  variáveis da stack de e2e (só valores de teste)
│   ├── garage/garage.toml
│   ├── caddy/
│   │   ├── compose.yml          Caddy único da VPS, ligado às redes dos dois ambientes
│   │   ├── Caddyfile            trechos comuns (headers, api, mídia, restrição por IP)
│   │   ├── sites/               producao.caddy, homolog.caddy
│   │   ├── e2e.caddy            sites da stack de e2e
│   │   └── .env.example
│   └── scripts/                 gerar-env.sh, gerar-env-servidor.sh, preparar-vps.sh, deploy.sh, backup...
├── docs/
│   ├── plano-implementacao.md
│   ├── prompts/                 um prompt por fase (especificação e verificação)
│   └── prompts-iniciais/        sequência completa para criar o projeto do zero
└── .github/workflows/
    └── pipeline.yml
```

---

## 4. Fases

Cada fase = uma branch = um PR para `homolog`. As regras gerais ficam no `CLAUDE.md` da raiz; as do
backend, no `backend/CLAUDE.md`, com o padrão de cada nível de teste nas skills
`/criar-teste-unitario`, `/criar-teste-integracao` e `/criar-teste-e2e` (`backend/.claude/skills/`).
Definição de pronto de **todo** PR:

- [ ] `./mvnw verify` do backend passando (unitários + integração + cobertura + Spotless); `npm test` a partir da Fase 11
- [ ] e2e passando no Actions
- [ ] Rotas novas com matriz de papéis (anônimo / ALUNO / ADMIN) e, se usam a `AcessoAulaPolicy`, matriz de acesso ao conteúdo
- [ ] Endpoints novos documentados no OpenAPI
- [ ] Migrations compatíveis com a versão anterior (expandir → depois contrair)
- [ ] Nenhum segredo no código ou no compose
- [ ] Funcionando na homologação

---

### Fase 0 — Preparação (sem branch)

- [x] Criar repositório `<dono-github>/plataforma-cursos` no GitHub
- [x] Primeiro commit na `main` (README, `.gitignore`, `docs/`)
- [x] Criar `homolog` a partir da `main`
- [x] Orientações para agentes de IA em `.claude/`: rules por caminho (segurança, fronts, testes, prompts),
  commits em Conventional Commits sem coautoria de IA (`/gc`, `/pc`), `/nova-branch`, `/abrir-pr`,
  `/revisar-dod` e o pipeline SDD com portões humanos
- [ ] Regras de proteção em `main` e `homolog`: só PR, checks obrigatórios (ativar quando o pipeline existir)
- [ ] Environments `homolog` e `production` com seus secrets
- [ ] Confirmar os subdomínios livres em `<dominio>`
- [ ] Criar a conta na Iagente (https://celke.com.br/page/iagente, com alguns e-mails gratuitos), obter host, porta e credenciais SMTP no painel e configurar SPF/DKIM

---

## Bloco A — Backend

### Fase 1 — `feature/base-projeto`

Objetivo: API rodando local e na VPS (homologação e produção), com auth completa, CI, e2e de API e
deploy automático. Depois desta fase, toda funcionalidade nova segue o fluxo normal.

**1.1 Esqueleto do backend e padrão de testes** — capítulo 3

- [x] `CLAUDE.md` da raiz (regras gerais) e `backend/CLAUDE.md` (código e testes do projeto Java)
- [x] Skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e` em `backend/.claude/skills/`, cada uma com o padrão do nível (`padrao.md`)
- [x] Projeto gerado no Spring Initializr: Spring Boot 4.1.1, Java 25, `br.com.celke.cursos`; dependências: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc, testcontainers. As demais (AWS SDK, mail, Thymeleaf) entram na etapa que as usa
- [x] `application.yaml` com credenciais do banco sem valor padrão, `open-in-view` desligado, `ddl-auto: validate`, threads virtuais, shutdown gracioso, cabeçalhos do proxy, sem stack trace nas respostas; `application-worker.yaml` sem servidor HTTP, sem Flyway (as migrations são da API) e com `spring.main.keep-alive`
- [x] Sem usuário embutido do Spring Security (`UserDetailsServiceAutoConfiguration` excluída)
- [x] `SecurityConfig` (só na aplicação web, com `@ConditionalOnWebApplication`): stateless, sem CSRF/sessão/formulário/HTTP Basic, 401 para não autenticado; públicos só `GET` de health/probes e da documentação
- [x] Actuator: só `health`, com probes de liveness/readiness e sem detalhes
- [x] Swagger ligado por `DOCS_ENABLED=true` (dev e homologação), desligado por padrão
- [x] Surefire para `*Test` (com `-Dskip.unit=true` para pular), Failsafe para `*IT`, JaCoCo somando os dois com mínimo de 80% de linhas, Spotless (palantir-java-format) verificado no `verify`
- [x] `support/Containers` (PostgreSQL 18 como bean com `@ServiceConnection`) e `support/IntegrationTest` (`MockMvcTester`, `stamp()`)
- [x] `SegurancaPadraoIT`: health e probes públicos com `UP` e sem detalhes; demais rotas e métodos exigem autenticação; documentação desligada
- [x] Dockerfile multi-stage: build com JDK 25, jar em camadas, execução com JRE 25, `curl` para healthcheck e usuário sem root; targets `api` e `worker` (com ffmpeg)

**1.2 Ambiente local** — capítulo 4

- [x] `infra/compose.yml` (projeto `plataforma-cursos`): PostgreSQL 18.6, Garage v2.4.1, Mailpit v1.31.6, API e worker, com versões fixas
- [x] Variáveis obrigatórias com `${VARIAVEL:?mensagem}`; conexão do banco num bloco compartilhado (`x-banco`) pela API e pelo worker
- [x] `infra/.env.example` e `infra/scripts/gerar-env.sh`, que cria o `infra/.env` com segredos aleatórios (permissão 600) e não sobrescreve um existente
- [x] Garage em `--single-node --default-bucket`: `garage.toml` sem segredos (vêm de `GARAGE_RPC_SECRET` e `GARAGE_ADMIN_TOKEN`), volume de metadados e dados; bucket `midia` e chave da aplicação criados na primeira subida
- [x] PostgreSQL com o volume em `/var/lib/postgresql` (layout da versão 18)
- [x] Healthchecks (`pg_isready`, `garage status`, `mailpit readyz`, readiness da API com `curl`) e `depends_on: condition: service_healthy`
- [x] Worker sobe depois da API, que aplica as migrations
- [x] Portas ligadas apenas em `127.0.0.1` e configuráveis no `.env` (`API_PORTA`, `POSTGRES_PORTA`, `S3_PORTA`, `MAILPIT_PORTA`)
- [x] Swagger ligado no dev (`DOCS_ENABLED=true`)

**1.3 Padrões compartilhados** — capítulo 5

- [x] `ProblemDetail` (RFC 9457) com handler global: validação com a lista `erros` (`campo`, `mensagem`) em português, regra de negócio (`RegraNegocioException`, `NaoEncontradoException`, `ConflitoException`), erros do Spring MVC traduzidos em `messages.properties`, erro inesperado só com o id da requisição; `instance` e `requestId` em todo erro
- [x] 401/403 da segurança também em `ProblemDetail` (`RespostasSeguranca`)
- [x] Id de correlação `X-Request-Id` (aceito só se seguro, senão gerado) no MDC e na resposta; logs em JSON (ECS) no compose
- [x] `Pagina<T>` e paginação padrão (20, máximo 100); `Clock` único em UTC
- [x] `StorageService` (AWS SDK v2, path-style): presign GET/PUT, multipart, consulta, exclusão; presigner com o endereço público
- [x] Configuração do bucket aplicada pela API ao subir, de forma idempotente: CORS para os hosts do front (expondo `ETag`) e lifecycle que expira `uploads/` e uploads em partes incompletos
- [x] Fila: tabela `jobs` + `FilaService` (enfileirar, reservar com `SKIP LOCKED`, lease, concluir, falhar com backoff, falha definitiva), `ProcessadorFila` por tipo e consumo agendado só no worker
- [x] `EmailService` (valida destinatário e template) e envio no worker com Thymeleaf e SMTP; health check do e-mail desligado
- [x] `@ConfigurationProperties` validadas (`app.armazenamento`, `app.email`, `app.fila`)
- [x] `support/Containers` com Garage e Mailpit; `support/Mailpit`; controller de erros só de teste
- [x] Testes: handler de erros, id de correlação, `StorageService` contra o Garage real (inclusive preflight do CORS), fila (backoff, tentativas esgotadas, lease vencido, concorrência sem duplicidade), e-mail entregue no Mailpit com HTML escapado

**1.4 Usuários e autenticação** — capítulos 6 e 7

- [x] Migrations `usuarios` (e-mail único e em minúsculas), `usuario_papeis` e `refresh_tokens`
- [x] Hash de senha Argon2; BCrypt `$2y$` do Laravel aceito e regravado em Argon2 no login; política de senha por tamanho (admin: 12 a 128)
- [x] Comando `docker compose run --rm api criar-admin <email> "<nome>"`: sem servidor HTTP, senha digitada duas vezes no terminal
- [x] Access token JWT (Nimbus, HS256, 10 min, `sub` e `papeis`) com o Resource Server do Spring Security: só HS256, emissor e validade conferidos, papéis em `ROLE_*`; `JWT_SEGREDO` validado ao subir
- [x] Refresh opaco com hash SHA-256, rotação, família revogada na reutilização (gravada mesmo com a sessão recusada) e `SELECT ... FOR UPDATE`
- [x] Cookie `refresh_token`: `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 14 dias, **sem `Domain`**
- [x] Rotas `login`, `refresh`, `logout` (públicas), `sair-de-todos` e `me` (privadas); mesma resposta e mesmo tempo para e-mail inexistente e senha errada
- [x] Seed do profile `test` (admin, aluno); `support/UsuariosSeed`; `login()`, `bearer()` e `criarUsuario()` no `IntegrationTest`
- [x] Testes: token adulterado, expirado, de outra chave, com outro algoritmo, `alg: none` e de outro emissor recusados; rotação, reutilização, logout e sair de todos; matriz de papéis das rotas de autenticação

**1.5 Autorização e proteções** — capítulos 8 e 9

- [x] `SecurityFilterChain` nega tudo por padrão; rotas públicas declaradas com método HTTP; papéis do token + `@PreAuthorize` (matriz de papéis testada)
- [x] Limite por IP com Bucket4j nas rotas de autenticação (`LimitePorIpFilter`): conta toda requisição, roda antes da segurança, 429 em ProblemDetail com `Retry-After`
- [x] Bloqueio temporário por falhas seguidas: 5 por e-mail + IP e 20 por e-mail (qualquer IP), 15 min, contadores zerados por login correto ou após 1 h sem falhas; verificado antes de calcular o hash
- [x] Refresh e logout: verificação de `Origin` e `Sec-Fetch-Site` (CSRF), porque outras aplicações em `*.<dominio>` contam como o mesmo site; origens em `AUTH_ORIGENS`
- [x] Auditoria de login (`auditoria_login`): resultado, IP e user-agent de toda tentativa, em transação própria
- [x] `server.forward-headers-strategy: native`: `X-Forwarded-For` aceito só de proxy interno e lido da direita para a esquerda
- [x] Erros que nascem em filtros escritos por `ProblemDetailJson` (mesmo formato do handler global)

**1.6 Cadastro e recuperação de senha** — capítulo 10

- [x] Migration: `usuarios.email_confirmado_em` (contas existentes confirmadas), `tokens_uso_unico` e novo resultado `EMAIL_NAO_CONFIRMADO` na auditoria
- [x] Cadastro de aluno (`POST /api/auth/cadastro`) com conta pendente e link de confirmação (48 h); resposta igual para e-mail novo e já cadastrado — conta ativa recebe um aviso (no máximo um por hora), cadastro pendente recebe o link de novo
- [x] Confirmação (`confirmar-email`) e reenvio (`reenviar-confirmacao`); login com e-mail pendente recebe 403 próprio só quando a senha está certa
- [x] Recuperação (`recuperar-senha`, link de 1 h, resposta igual exista ou não a conta) e redefinição (`redefinir-senha`): troca a senha, confirma o e-mail e encerra todas as sessões
- [x] Tokens de uso único com hash SHA-256 (`TokenSeguro`, compartilhado com o refresh): um novo invalida os anteriores do mesmo tipo; no máximo 3 e-mails por hora por conta e tipo
- [x] Senha de aluno com 10 a 128 caracteres; contas do comando e dos seeds nascem confirmadas
- [x] Templates de e-mail (`confirmacao-email`, `recuperacao-senha`, `cadastro-existente`) com links para `FRONT_ALUNO_URL`; envio pelo worker (Mailpit no ambiente local)
- [x] Rotas da conta públicas (só POST) e no `LimitePorIpFilter`
- [x] Healthcheck do worker por arquivo de batimento (`BatimentoWorker`), separado do consumo da fila: o `up --wait` e o deploy passam a esperar o worker
- [x] Testes: jornadas de cadastro e recuperação com os e-mails lidos no Mailpit, link de uso único, limite de e-mails, mesma resposta para e-mail inexistente

**1.7 e2e de API e pipeline** — capítulo 11

- [x] `infra/compose.prod.yml`: stack dos servidores com Caddy, imagens do GHCR pela `TAG`, `restart: unless-stopped`, healthchecks e nenhuma porta publicada além das do Caddy
- [x] `infra/caddy/Caddyfile`: hosts pelo ambiente (`HOST_ALUNO`, `HOST_ADM`, `HOST_MEDIA`), só `/api/*` e `/actuator/health` chegam à API, 404 para o resto, headers de segurança, `-Server`, `lb_try_duration`, `X-Forwarded-For` substituído
- [x] `infra/compose.e2e.yml` + `infra/e2e.env` (valores só de teste, no git): HTTP em `*.localhost` na porta 8088, Mailpit, imagens locais, profile `e2e`, projeto Compose separado
- [x] Seed do profile `e2e` (`SeedE2e`: admin e aluno, senha de `SEED_E2E_SENHA`)
- [x] Perfil Maven `e2e` (`src/e2e/java`, REST Assured, Awaitility) com `support/` conforme o padrão da skill `criar-teste-e2e`; Spotless também nas fontes e2e
- [x] Jornadas `CadastroE2E` (cadastro → e-mail entregue pelo worker → confirmação → login), `SessaoE2E` (login → refresh → logout pelo Caddy, cookie sem `Domain`, origem recusada) e `ProxyE2E` (headers, rotas fora da API bloqueadas, `X-Forwarded-For` forjado sem efeito)
- [x] `.github/workflows/pipeline.yml`: testes → imagens (cache `type=gha`) → e2e → relatórios e `docker compose logs` em caso de falha; roda em PR e push para `homolog` e `main`
- [ ] Regras de proteção de `homolog` e `main` com os checks `Testes (unitários e integração)` e `e2e (stack completa)` obrigatórios (configuração no GitHub)

**1.8 VPS** — capítulos 12 e 13 · roteiro em [vps.md](vps.md)

- [x] `infra/scripts/preparar-vps.sh` (Ubuntu 24.04): Docker oficial com rotação de logs, administrador com sudo, `deploy` sem sudo no grupo docker, SSH só por chave e sem root (`01-plataforma.conf`, antes do `50-cloud-init.conf`), `ufw` (22/80/443), fail2ban, atualizações automáticas, swap de 2 GB, `/opt/plataforma-cursos/{caddy,producao,homolog}`
- [x] `compose.prod.yml` por ambiente (`AMBIENTE`): rede `plataforma-cursos-<ambiente>`, apelidos `<ambiente>-api` e `<ambiente>-garage`, nenhuma porta publicada, limites de recursos e `-Xmx` ajustado
- [x] `compose.homolog.yml`: limites menores, worker ligado com meia CPU (envia os e-mails da homologação), Swagger ligado
- [x] Caddy único (`infra/caddy/compose.yml`) nas portas 80/443, ligado às duas redes; `Caddyfile` com trechos comuns e `sites/producao.caddy` e `sites/homolog.caddy`
  - homologação restrita por IP (`HOMOLOG_IPS`) e com `X-Robots-Tag: noindex` — senha do Caddy não serve, porque ocuparia o cabeçalho `Authorization` do JWT
  - `adm.` de produção restrito por IP (`ADM_IPS`)
  - HSTS sem `includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, sem `Server`, `lb_try_duration`; `X-Forwarded-For` substituído pelo IP real
- [x] e2e usando o mesmo `Caddyfile` com `caddy/e2e.caddy`
- [x] `infra/scripts/gerar-env-servidor.sh`: `.env` de cada ambiente com segredos aleatórios e diferentes, hosts de `<dominio>`, SMTP para preencher
- [x] Roteiro `docs/vps.md`: painel, DNS, chaves, preparação, cópia dos arquivos, `.env`, GHCR, primeira subida, verificação
- [ ] Executar o roteiro na VPS (painel, DNS dos seis subdomínios, preparação, `.env`, login no GHCR)
- [ ] Primeira subida dos dois ambientes e do Caddy, com as imagens publicadas pelo deploy automático (1.9)

Limites de recursos:

| Serviço | Produção | Homologação |
|---|---|---|
| API | 1 GB, `-Xmx512m` | 768 MB, `-Xmx384m`, `cpus: 0.5` |
| Worker + ffmpeg | 2 GB, `-Xmx768m`, `cpus: 1.5` | 768 MB, `-Xmx256m`, `cpus: 0.5` (sempre ligado: envia os e-mails) |
| PostgreSQL | 1 GB | 512 MB |
| Garage | 768 MB | 512 MB |
| Caddy (compartilhado) | 128 MB | — |

**1.9 Deploy automático** — capítulo 14

- [x] Publicação no GHCR pelo job de e2e, depois das jornadas e só em push: as imagens testadas recebem a tag do SHA (sem rebuild); label `org.opencontainers.image.source` liga o pacote ao repositório
- [x] `infra/scripts/deploy.sh` (na VPS em `/usr/local/bin/plataforma-cursos-deploy`, dono root): valida `<producao|homolog> <sha>` (também de `SSH_ORIGINAL_COMMAND`), extrai só os arquivos de compose esperados, backup do banco, troca a tag, `up --pull missing --wait`, rollback automático para a versão anterior (arquivos e tag) se algum container não ficar saudável; `flock`, `trap '' HUP`, guarda 5 versões e 10 backups, `deploy.log`
- [x] Chave do deploy presa ao script no `authorized_keys` (`command=...,no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty`), instalada pelo `preparar-vps.sh`
- [x] Workflow reutilizável `deploy.yml` (environment `homolog` ou `production`, um deploy por ambiente, nunca cancelado); no pipeline: push em `homolog` → homologação; push em `main` → produção, só com a variável `DEPLOY_ATIVO=true` (sem ela, os deploys são pulados); só PRs cancelam execuções antigas
- [x] Testado localmente: deploy de uma versão boa, deploy de uma versão quebrada com rollback automático, backup restaurável, validação de argumentos (inclusive injeção pelo SSH) e deploy simultâneo barrado; chave presa ao script testada com `sshd` real (sem shell, terminal ou túnel)
- [x] Roteiro `docs/vps.md`: secrets do repositório, primeira subida pelo pipeline, Caddy e administradores, operação (histórico, voltar versão, atualizar Caddy e script)
- [ ] Secrets `VPS_HOST`, `VPS_SSH_KEY` e `VPS_KNOWN_HOSTS` e variável `DEPLOY_ATIVO=true` no repositório (conta dona) e, se o plano permitir, aprovação obrigatória no environment `production`
- [ ] Primeiro deploy nos dois ambientes pelo pipeline; Caddy no ar; administrador criado em cada um

**Pronto quando:** cadastro, login e logout funcionando pela API em `homolog-<sub-aluno>.<dominio>/api` e
`<sub-aluno>.<dominio>/api`, e-mail chegando pela Iagente, pipeline completo verde e um rollback testado de propósito.

---

### Fase 2 — `feature/cursos`

Objetivo: API de gestão de cursos, turmas, módulos e aulas. Capítulos 15 e 16.

- [x] Migration `V6`: `cursos` (slug único com `CHECK` do formato), `turmas`, `modulos` e `aulas`; status como texto com `CHECK` (curso `RASCUNHO`/`PUBLICADO`/`ARQUIVADO`, turma `PREPARACAO`/`ATIVA`/`ENCERRADA`, módulo e aula `ATIVO`/`INATIVO`); aula gratuita; `ON DELETE RESTRICT` em toda a hierarquia
- [x] Ordem de módulos e aulas única por pai com restrição `DEFERRABLE INITIALLY DEFERRED` (a reordenação troca todas as posições numa transação); item novo entra no fim; exclusão refaz a sequência; o pai fica travado (`PESSIMISTIC_WRITE`) enquanto a ordem muda
- [x] CRUD em `/api/adm/**` com Bean Validation: cursos (lista paginada com filtro por status, slug gerado do título quando não informado, 409 para slug repetido), turmas, módulos e aulas, e `PATCH .../status`
- [x] Exclusão só de item vazio (curso sem turmas, turma sem módulos, módulo sem aulas); o resto é arquivado, encerrado ou inativado pelo status, para não perder matrículas e histórico
- [x] Reordenação `PUT /api/adm/turmas/{id}/modulos/ordem` e `PUT /api/adm/modulos/{id}/aulas/ordem` com a lista completa de ids (400 se faltar, sobrar ou repetir)
- [x] `/api/adm/**` exige ADMIN no `SecurityConfig` (antes de ler o corpo) e de novo com `@PreAuthorize` nos controllers
- [x] Capa do curso: URL pré-assinada de PUT em `uploads/capas/<curso>/<uuid>` (JPEG ou PNG, até 5 MB); a confirmação aceita só a chave gerada para o mesmo curso, confere o tamanho real, recusa lado acima de 8000 px antes de decodificar, reduz para até 1280 × 720 e regrava em JPEG sem metadados em `capas/<curso>/<uuid>.jpg`; o original e a capa anterior são apagados. `ProcessadorImagem` em `shared/imagem` (reutilizado pela foto de perfil)
- [x] Catálogo público (`GET`, declarado no `SecurityConfig`): `/api/cursos` (publicados, paginado, ordem alfabética), `/api/cursos/{slug}` (módulos e aulas ativos da turma ativa mais recente, sem dados internos) e `/api/cursos/{slug}/capa` (endereço estável que redireciona para uma URL assinada nova, com cache de 5 minutos); rascunho e arquivado respondem 404
- [x] Testes: unitários (slug, ordenação, DTOs, serviços, capa, processamento de imagem com EXIF e transparência), integração (matriz de papéis de todas as rotas, CRUD, ordem com restrição adiável e criação simultânea, catálogo, capa com o Garage real)
- [x] e2e `CatalogoE2E`: o ADM cria curso → turma → módulo → aulas, envia a capa pelo Caddy direto ao Garage e publica; o visitante sem login vê o curso, o conteúdo e a capa; o aluno recebe 403 na gestão

**Pronto quando:** um curso completo, com capa, é cadastrado pela API do ADM e aparece no catálogo
público com o conteúdo da turma ativa.

---

### Fase 3 — `feature/matricula-acesso`

Objetivo: matrícula e uma política única de acesso. Capítulos 17 e 18.

- [x] Migration `V7` `matriculas`: usuário e turma (FK `RESTRICT`), início, fim opcional (sem prazo), status `ATIVA`/`CANCELADA`, forma de pagamento (`MANUAL`, `CORTESIA`, `PIX`, `CARTAO`, `BOLETO`), transação, arquivamento; índice único parcial de uma matrícula ativa por aluno e turma e de transação por forma de pagamento (pronto para o webhook)
- [x] "Vencida" e "aguardando início" não são status gravados: `Matricula.situacao(agora)` calcula pelo `Clock` (fim inclusivo), sem job para marcar matrículas
- [x] Fronteira entre módulos: a matrícula guarda só os ids; o conteúdo vem do `ConteudoTurmaService` (exportado pelo módulo `curso`, também usado pelo catálogo) e os usuários do `UsuarioService`
- [x] Endpoints do ADM: listar as matrículas da turma, matricular por e-mail (início padrão agora; turma encerrada, usuário inexistente, matrícula ativa repetida e transação repetida recusados), alterar período, cancelar; turma com matrículas não pode ser excluída (409 pela FK)
- [x] `AcessoAulaPolicy` (classe pura com `Clock`): aula liberada com matrícula vigente na turma ou aula aberta (gratuita, turma ativa, curso publicado); turma inteira só com matrícula vigente. `AcessoAulaService.exigirAcesso` é a porta de entrada para vídeo, arquivo, fórum, progresso e certificado
- [x] Endpoints do aluno (`/api/aluno`, escopo do token): meus cursos por aba (`ATIVOS` com as futuras, `ARQUIVADOS`, `EXPIRADOS`; cancelada em nenhuma), turma com módulos e aulas ativos, aula, arquivar/desarquivar (matrícula de outro aluno responde 404)
- [x] Testes: situação e política em tabela (instante antes, exato e depois de cada limite), matriz de papéis, matriz de acesso ao conteúdo (vigente, sem, vencida, cancelada, futura, gratuita, curso em rascunho, conteúdo inativo, outra turma), IDOR
- [x] e2e `MatriculaE2E`: conta nova vê só a aula gratuita; matriculada vê a turma e a aula paga; com o período vencido, 403 e o curso nos expirados

**Pronto quando:** o ADM matricula um aluno e o acesso dele às aulas segue o período da matrícula,
decidido num ponto só.

---

### Fase 4 — `feature/upload-video`

Objetivo: o vídeo enviado vira HLS comprimido. Capítulos 19 a 21.

- [x] Migration `V8` `videos`: um vídeo por aula (FK `RESTRICT`: aula com vídeo responde 409 na exclusão), status `AGUARDANDO_ENVIO`/`PENDENTE`/`PROCESSANDO`/`PRONTO`/`ERRO`, chaves do original e do HLS, tamanho original e final, duração, resolução, variantes, tempo de processamento e erro. O HLS publicado só muda quando a conversão do envio novo termina
- [x] Envio em partes (`POST /api/adm/aulas/{id}/video/envio`): MP4, MOV, MKV ou WebM até 5 GB, validados antes de assinar; partes de 16 MiB (maiores acima de 10.000 partes), uma URL pré-assinada por parte em `uploads/videos/<aula>/<uuid>`; envio anterior ainda aberto é abortado
- [x] Conclusão (`.../envio/conclusao` com os ETags): junta as partes, confere o tamanho real igual ao informado e a assinatura do início do arquivo (`ftyp` ou EBML); recusado é apagado e o vídeo fica com erro; aceito vai para a fila na mesma transação
- [x] Worker (`TranscodificacaoManipulador`): `ffprobe` → ffmpeg via `ProcessBuilder` (lista de argumentos, `nice -n 10`), numa passada só
  - H.264 CRF 24 (`veryfast`), `maxrate` 2800k/5000k, AAC 128k, segmentos de 6 s, 720p + 1080p sem aumentar a resolução de origem (abaixo de 720p, uma variante na altura original)
  - thumbnail; envio para `videos/<aula>/<job>/`; apaga o original e o HLS anterior só depois de publicar
- [x] Lease renovado a cada minuto durante a conversão; worker que cai deixa o lease vencer e o job volta para a fila (refaz o prefixo do job do zero); `stop_grace_period: 30s`. Arquivo inválido é erro permanente; desistência da fila (`ManipuladorJob.desistir`) deixa o vídeo com erro
- [x] Endpoints do ADM: status com tamanho antes/depois, redução percentual, variantes, segundos de processamento por minuto de vídeo e thumbnail; reprocessar (só com erro e com o original ainda no bucket); remover o vídeo e os arquivos (fora do processamento)
- [ ] Medir tempo de processamento por minuto de vídeo na VPS e registrar no tutorial (o ADM já mostra a medida: `segundosProcessamentoPorMinuto`)
- [x] Testes: unitários (formatos e assinaturas, divisão em partes, variantes, argumentos do ffmpeg, saída do ffprobe, entidade, serviços, fila), integração com o Garage e o ffmpeg reais (transcodificação do vídeo de 3 s, troca, extensão falsa, vídeo corrompido e reprocessamento, matriz de papéis); o pipeline instala o ffmpeg
- [x] e2e `VideoE2E`: upload em partes pelo Caddy direto ao Garage → worker → status "pronto" com a thumbnail no host de mídia

**Pronto quando:** o ADM envia o vídeo de uma aula direto ao armazenamento e, sem passar o arquivo pela
API, ele vira HLS em 720p/1080p com o tamanho antes e depois registrado.

---

### Fase 5 — `feature/entrega-video`

Objetivo: vídeo protegido e progresso salvo. Capítulos 22 a 24.

- [x] `GET /api/aulas/{id}/video.m3u8` (master) e `GET /api/aulas/{id}/video/{variante}.m3u8`: passam pela `AcessoAulaService` (matrícula vigente ou aula gratuita); a master aponta para as variantes servidas pela API e cada variante traz os segmentos por URLs pré-assinadas válidas pela duração do vídeo + `app.video.margem-playlist` (2 h). Só nomes no formato gerado pelo worker são aceitos na reescrita; resposta com `Cache-Control: no-store`
- [ ] Opcional (cap. 23): HLS com AES-128 e chave por endpoint autenticado — não feito nesta fase
- [x] Progresso (migration `V9` `progresso_aulas`, um registro por aluno e aula): `GET` e `PATCH /api/aluno/aulas/{id}/progresso` (posição, vista, revisar; só os campos enviados mudam) e `GET /api/aluno/turmas/{id}/progresso` (percentual das aulas ativas vistas, arredondado para baixo; turma sem aulas = 0%). Sempre no escopo do token; a turma exige matrícula vigente (`AcessoAulaService.exigirAcessoTurma`)
- [x] Log de acesso (migration `V10` `acessos_video`): cada abertura da master registra usuário, aula, IP e navegador; o worker apaga todo dia o que passa de `app.video.retencao-log-acesso` (180 dias, o prazo do Marco Civil para registros de acesso a aplicações)
- [x] Testes: unitários (reescrita das playlists e playlists adulteradas, validade, percentual com as bordas, entidade e serviço do progresso, retenção do log), integração (matriz de acesso ao conteúdo na playlist e no progresso, segmento assinado baixando do Garage com `X-Amz-Expires` = duração + margem, isolamento entre alunos, retenção do log)
- [x] e2e `EntregaVideoE2E` (margem de 5 s na stack de e2e): playlist assinada → segmento baixa com 200 → após expirar, recusado (o Garage responde 400 "Date is too old"; a AWS, 403); progresso refletido na turma

**Pronto quando:** o aluno matriculado assiste ao vídeo por uma playlist assinada, sem acesso direto ao
bucket, e retoma de onde parou com o progresso refletido na turma.

---

### Fase 6 — `feature/arquivos-apoio`

Capítulo 25.

- [x] Migration `V11` `arquivos_apoio`: de uma aula **ou** de um módulo (`CHECK` com `num_nonnulls`), título, nome de download, chave, tipo e tamanho; aula ou módulo com arquivos responde 409 na exclusão
- [x] Envio pelo ADM em duas etapas, como a capa: `POST /api/adm/{aulas|modulos}/{id}/arquivos/envio` (ZIP, PDF ou TXT até 200 MB) → PUT direto no Garage → `POST /api/adm/{aulas|modulos}/{id}/arquivos` confere a chave gerada para o mesmo dono, o tamanho real e o conteúdo (assinatura do ZIP e do PDF; texto sem byte nulo) e move de `uploads/` (que expira) para `arquivos/`; recusado é apagado. Nome de download limpo (sem caminho, acento ou aspas) e com a extensão do formato real. `DELETE /api/adm/arquivos/{id}` remove do bucket
- [x] Aluno (`/api/aluno`): lista da aula (mesmo acesso da aula, gratuita inclusive) e do módulo (matrícula vigente na turma; módulo inativo responde 404) e `GET /arquivos/{id}/download` com URL pré-assinada de 5 minutos que baixa como anexo (`Content-Disposition` com o nome do arquivo); a chave do bucket nunca sai na resposta
- [x] Testes: unitários (formatos, nomes, serviços), integração (matriz de papéis do ADM, matriz de acesso ao conteúdo na aula e no módulo, conteúdo falso, chave de outra aula, exclusões bloqueadas) e e2e `ArquivoE2E` (envio pelo Caddy direto ao Garage; download bloqueado sem matrícula e liberado com matrícula)

**Pronto quando:** o aluno matriculado baixa o arquivo da aula ou do módulo por uma URL curta, e sem
matrícula recebe 403.

---

### Fase 7 — `feature/forum`

Capítulo 26.

- [x] Migration `V12`: `forum_topicos` (da turma ou de uma aula da turma; aula excluída vira tópico da turma), `forum_respostas` (marca de instrutor) e `forum_anexos` (de um tópico **ou** de uma resposta); Markdown guardado junto com o HTML já filtrado
- [x] Aluno (`/api/aluno`, só com matrícula vigente na turma): listar tópicos visíveis (fixados primeiro, filtro por aula, contagem de respostas), perguntar, ver o tópico com as respostas e responder; tópico ou resposta oculta não aparece (404)
- [x] Markdown → HTML em duas camadas: commonmark com `escapeHtml` e `sanitizeUrls` e sanitizador OWASP com lista fechada de elementos (sem eventos, estilo, imagens ou iframes); links com `rel="nofollow noopener noreferrer"`. Só o HTML filtrado sai na resposta
- [x] Anexos de imagem: URL pré-assinada em `uploads/forum/<usuario>/<uuid>`; ao publicar, a API confere que a chave é do próprio usuário (contra IDOR), regrava em JPEG sem metadados (até 1600 × 1600) em `forum/anexos/` e devolve URLs temporárias; até 3 por mensagem
- [x] Moderação no ADM (`/api/adm`): todos os tópicos e respostas (ocultos inclusive), ocultar/mostrar e fixar tópico, ocultar resposta, responder como instrutor (o tópico fica marcado como respondido)
- [x] Limite de mensagens por aluno contado no banco (`app.forum`: 20 perguntas e respostas por hora): 429 com `Retry-After` até a mais antiga sair da janela
- [x] Testes: unitários (XSS em tabela, entidade, limite, anexos e IDOR, serviços, DTOs), integração (pergunta e resposta do instrutor, XSS guardado, anexo e IDOR, sem matrícula, moderação, 429, matriz de papéis da moderação)
- [x] e2e `ForumE2E`: aluno pergunta (com HTML malicioso filtrado), admin responde como instrutor, aluno vê a resposta

**Pronto quando:** o aluno matriculado pergunta no fórum da aula, o instrutor responde pela moderação e
o aluno vê a resposta, sem nenhum HTML perigoso chegando ao navegador.

---

### Fase 8 — `feature/certificado`

Capítulo 27.

- [x] Regra de conclusão por turma (migration `V13`: `percentual_conclusao` de 1 a 100, padrão 100, e `carga_horaria_horas` opcional), definida em `PUT /api/adm/turmas/{id}/certificado`; turma sem aulas nunca é concluída
- [x] Situação (`GET /api/aluno/turmas/{id}/certificado`: percentual visto, exigido, concluiu, certificado se já emitido) e emissão (`POST`, com o nome completo confirmado pelo aluno), ambas com matrícula vigente; emitir de novo devolve o mesmo certificado (dados impressos congelados na emissão: nome, curso, turma, carga horária)
- [x] PDF com openhtmltopdf a partir de um template Thymeleaf, gerado uma vez e guardado em `certificados/<turma>/<codigo>.pdf`; download do próprio aluno por URL de 5 minutos (de outro aluno, 404); lista em `GET /api/aluno/certificados`
- [x] Código de validação de 12 caracteres sem letras ambíguas (60 bits aleatórios) e `GET /api/certificados/{codigo}` público, sem e-mail nem ids internos
- [x] Testes: unitários (regra com as bordas, código, nome, serviço, turma), integração (ciclo completo com PDF no Garage, regra do ADM, sem matrícula, download de outro aluno, validações) e e2e `CertificadoE2E` (aluno conclui → gera certificado → baixa o PDF → código valida sem login)

**Pronto quando:** o aluno que concluiu a turma baixa o certificado em PDF e qualquer pessoa confere o
código impresso nele.

---

### Fase 9 — `feature/perfil-termos`

Capítulo 28.

- [x] Perfil (`/api/perfil`, para aluno e admin, sempre pelo token): dados sem senha nem hash, troca de nome, troca de senha (exige a atual, política do papel — aluno 10, admin 12 —, diferente da atual; encerra todas as sessões e limpa o cookie de refresh) e foto (migration `V14`: `foto_chave`; envio direto ao bucket, chave do próprio usuário contra IDOR, regravada em JPEG sem metadados até 400 × 400, anterior apagada)
- [x] Termos versionados (migration `V14`: `termos` com tipo `TERMOS_USO`/`POLITICA_PRIVACIDADE` e versão crescente; `aceites_termos` com IP e navegador): a vigente de cada tipo é a maior versão; `GET /api/termos/vigentes` público, `GET /api/termos/pendentes` e `POST /api/termos/{id}/aceite` (só a vigente; antiga 409)
- [x] Bloqueio: `TermoPendenteInterceptor` em `/api/aluno/**` e `/api/aulas/**` responde 403 com `termosPendentes` enquanto houver vigente sem aceite (primeiro acesso e cada versão nova); perfil, sessão e termos continuam liberados. `RegraNegocioException.propriedades()` leva campos extras ao ProblemDetail
- [x] ADM: `POST /api/adm/termos` publica a próxima versão (Markdown → HTML filtrado pelo `MarkdownSeguro`, agora em `shared/texto`) e `GET /api/adm/termos?tipo=` lista as versões
- [x] Testes: unitários (termos, interceptor, perfil), integração (perfil com usuário próprio, troca de senha encerrando a sessão, foto e IDOR, bloqueio e aceite — apagando os termos ao fim, porque o banco é compartilhado entre as classes) e e2e `PerfilTermosE2E`. No e2e, `Api.login` aceita os termos pendentes como o front fará no primeiro acesso; `Api.loginSemAceite` serve à jornada do bloqueio
- [x] e2e de API: novo termo publicado → rotas do aluno bloqueadas até o aceite; perfil atualizado e troca de senha invalidando o refresh antigo

**Pronto quando:** o aluno cuida do próprio perfil e só usa a área do aluno depois de aceitar os termos
vigentes, a cada versão nova.

---

### Fase 10 — `feature/operacao`

Capítulos 29 a 31. Fica antes do front porque precisa estar pronta antes de entrarem dados reais em produção.

- [x] Backup com restic para fora da VPS (`infra/scripts/backup.sh`): dump do PostgreSQL por stdin e arquivos do Garage com o snapshot consistente dos metadados; retenção 7/4/6. Restauração (`restaurar.sh`) testada na stack de e2e — banco e os 11 objetos do bucket de volta — e documentada em [operação](operacao.md)
- [ ] Agendar o backup no cron da VPS e repetir o teste de restauração na homologação (depende da VPS no ar)
- [x] Alertas do worker por e-mail (`ALERTA_EMAIL`): jobs que foram para ERRO e disco abaixo de 15% livre, a cada 15 minutos
- [ ] Monitor de uptime externo (UptimeRobot ou Better Stack) no health check e no site (depende da VPS no ar)
- [x] Rotação de logs do Docker (`json-file`, 5 × 10 MB) em todos os serviços; uploads órfãos já expiram pelo bucket (2 dias); roteiro para limpar a homologação
- [x] Conta das horas de vídeo que cabem no disco e plano de saída para o R2/B2 (troca de endpoint + `rclone sync`) em [operação](operacao.md)
- [x] Revisão OWASP ASVS nível 1 documentada; Trivy no pipeline (críticos com correção quebram o build — já corrigiu Tomcat e Jackson); OWASP ZAP baseline na stack local (0 falhas; `Cross-Origin-Resource-Policy` acrescentado ao Caddy)
- [ ] OWASP ZAP baseline na homologação a cada versão (depende da VPS no ar)

**Pronto quando:** existe backup diário fora da VPS com a restauração testada, os alertas chegam por
e-mail e o pipeline barra imagens com vulnerabilidade crítica conhecida.

---

## Bloco B — Frontend

**Biblioteca de layout:** `@celke/kole-react`, instalada do GitHub (`github:celkecursos/kole-react`)
com o commit fixo no `package.json`. Ela traz a moldura (`Estrutura`, `TelaLogin`), formulários,
tabelas e feedback. Os componentes próprios da área do aluno (`PlayerVideo` com hls.js, `CardCurso`,
`ListaAulas`, fórum) ficam no app enquanto não existirem na biblioteca.

### Fase 11 — `feature/web-base`

Capítulo 33.

- [x] `web/` com `aluno` e `adm` (Vite + React + TypeScript, Node 24); um pacote só, sem workspaces; `site` entra na Fase 14
- [x] `@celke/kole-react` do GitHub com commit fixo
- [x] Cliente HTTP: token em memória, refresh silencioso, fila de requisições durante o refresh
- [x] Rotas protegidas por papel; tratamento de termo pendente
- [x] Aluno: login, cadastro, confirmação de e-mail, recuperar senha
- [x] ADM: login, layout `Estrutura`
- [x] Proxy do Vite `/api → localhost:8080` (mesma origem também no dev)
- [x] Imagem `web`: build dos fronts + Caddy servindo `/srv/aluno` (:8081) e `/srv/adm` (:8082); o Caddy da VPS manda `/api` para a API e o resto para ela
- [x] Caddy: CSP (mídia em `MIDIA_ORIGEM`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html` (o Swagger da homologação continua: a API ainda é testada por ele)
- [x] Vitest + Testing Library; Playwright no navegador entra no `pipeline.yml`
- [x] e2e (Playwright, caminho feliz e infeliz): login aluno e admin, senha errada, cadastro com validação e confirmação pelo Mailpit, recuperação de senha, links inválidos, aluno barrado no ADM, sessão por refresh, refresh reutilizado, logout

### Fase 12 — `feature/web-adm`

Capítulo 34.

- [x] Cursos, turmas, módulos e aulas (`Tabela`, `Form`, `Paginacao`, `Abas`); reordenar com os botões subir/descer (acessível por teclado, uma troca por clique)
- [x] Upload de vídeo multipart direto ao Garage com progresso e cancelamento, status do processamento (consulta a cada 3 s), reprocessar, remover
- [x] Capa do curso, arquivos de apoio (aula e módulo), matrículas (período, cancelamento), moderação do fórum (responder, fixar, ocultar), certificado da turma, termos
- [x] e2e (Playwright, caminho feliz e infeliz): admin cria curso completo e envia vídeo curto até "pronto"; validações na tela, conflitos da API (slug repetido, exclusão com filhos), formatos recusados, matrícula sem conta e em turma encerrada, moderação do fórum

### Fase 13 — `feature/web-aluno`

Capítulo 35.

- [x] Meus cursos em abas (ativos, arquivados, expirados), arquivar/desarquivar
- [x] Turma com módulos, aulas, arquivos do módulo e progresso (vistas e revisar)
- [x] Aula: player HLS (hls.js; token só nas playlists da API) com retomada e envio da posição, anterior/próxima, vista/revisar, arquivos, fórum da aula (pergunta e resposta; as imagens nas mensagens entram na Fase 18)
- [x] Certificado (situação, emissão com o nome completo, PDF), lista de certificados e perfil (nome, foto, senha)
- [x] e2e (Playwright, caminho feliz e infeliz): login → turma → aula → vídeo toca até o fim → vista → progresso da turma; retomada, revisar, navegação, download do arquivo, fórum, certificado (bloqueado → emitido → validado), perfil; sem matrícula, matrícula futura, arquivar

### Fase 14 — `feature/site`

Capítulo 36.

- [x] Layout novo com os tokens da `@celke/kole-react` (HTML estático, sem JavaScript de layout)
- [x] Pré-renderização estática (uma página HTML por rota) em `<sub-site>.<dominio>`: o container `site` gera ao subir e a cada `SITE_INTERVALO` (padrão 5 min) a partir da API do próprio ambiente, com troca atômica da versão; a mesma imagem serve homologação e produção
- [x] Home, catálogo, página do curso, validar certificado, termos, privacidade, 404
- [x] SEO: meta tags, canônico, Open Graph, dados estruturados (Course), sitemap, `robots.txt` (homologação fora dos buscadores)
- [x] Meta de Lighthouse no CI (desktop): desempenho ≥ 90, acessibilidade ≥ 95, boas práticas ≥ 95, SEO ≥ 90 em todas as páginas

---

### Fase 15 (opcional) — `feature/migracao-legado`

Capítulo 37.

- [ ] ETL do MySQL do CLMS: usuários, cursos, turmas, módulos, aulas, matrículas, progresso
- [ ] Mapear status numéricos do legado (`3, 4, 13, 14, 15`...) para os enums novos
- [ ] Senhas `$2y$` do Laravel aceitas no primeiro login e re-hash
- [ ] Reprocessar vídeos existentes no S3 para HLS


### Fase 16 — `feature/pagamentos`

Prompt: [`docs/prompts/f16-01-pagamentos-hotmart.md`](prompts/f16-01-pagamentos-hotmart.md). Operação e
configuração da Hotmart: [`docs/pagamentos.md`](pagamentos.md).

- [x] Módulo `pagamento` com a porta `GatewayPagamento` (um bean por meio de pagamento) e o status de pedido único para todos
- [x] Meio de pagamento configurado pelo painel (formulário a partir dos campos do gateway, segredos com AES-GCM, um ativo por vez)
- [x] Hotmart com o **webhook 2.0.0** (`X-HOTMART-HOTTOK`, JSON com `id` por evento); a versão 1.0.0 não é mais recomendada
- [x] Checkout por redirecionamento com o código do pedido no `xcod`; página de retorno que acompanha o status
- [x] Notificações idempotentes, processadas pela fila, tolerantes a fora de ordem; matrícula criada, mantida ou cancelada conforme o status
- [x] Conta do comprador sem senha, com link de criar senha (nunca senha por e-mail)
- [x] Reconciliação pela API de vendas (preparada, ligada por configuração)
- [x] Painel: meios de pagamento, ofertas por turma, pedidos e eventos recebidos (reprocessar)
- [x] Testes unitários, de integração, e2e e Playwright com caminho feliz e infeliz
- [x] `PAGAMENTOS_CHAVE` nos `.env` dos ambientes; homologação (restrita por IP) aceita só o caminho do webhook de fora
- [ ] Na homologação: "Enviar teste" da Hotmart de cada evento, fixtures trocadas pelos corpos reais


### Fase 17 — `feature/auditoria`

Prompt: [`docs/prompts/f17-01-auditoria-e-logs.md`](prompts/f17-01-auditoria-e-logs.md). Operação:
[`docs/operacao.md`](operacao.md), seções 4 e 4.1.

- [x] Trilha de auditoria (`auditoria`) gravada por um ouvinte dos eventos do Hibernate, na transação da mudança: usuário, operação, entidade e id, antes e depois (só o que mudou), origem (rota ou job), IP, navegador, id da requisição e data
- [x] Dados sensíveis (`@DadoSensivel`) como `***`; entidades de log e de sessão fora da trilha (`@SemAuditoria`)
- [x] Só inclusão garantida pelo banco (gatilho); retenção de 5 anos pelo worker
- [x] Tela de consulta no ADM (Segurança → Auditoria) com filtros e o antes/depois lado a lado
- [x] Logs da API e do worker também em arquivo, no volume `logs`: um por dia, 180 dias ou 2 GB por serviço
- [x] Testes unitários, de integração e Playwright

### Fase 18 — `feature/forum-anexos`

Prompt: [`docs/prompts/f18-01-anexos-no-forum.md`](prompts/f18-01-anexos-no-forum.md).

- [x] Imagens na pergunta e na resposta do aluno: escolher, arrastar ou colar um print (no texto também), com prévia, remover e progresso do envio
- [x] Tipo, tamanho e quantidade conferidos em todos os caminhos (o `accept` do seletor não filtra arrastar e colar): JPEG ou PNG, até 5 MB, até 3 por mensagem
- [x] Envio direto ao armazenamento só ao publicar (a API apaga o envio ao processá-lo; uma nova tentativa envia de novo)
- [x] Imagens publicadas na pergunta e nas respostas, para o aluno e na moderação do ADM, com visualizador em tela cheia
- [x] Testes unitários e Playwright (caminho feliz e infeliz)

### Fase 19 — `feature/seguranca`

Prompt: [`docs/prompts/f19-01-seguranca.md`](prompts/f19-01-seguranca.md). Operação:
[`docs/operacao.md`](operacao.md), seção 7.

- [x] Segundo fator (TOTP, RFC 6238) obrigatório para administradores: desafio depois da senha, cadastro por QR code, códigos de recuperação de uso único, segredo cifrado (`TOTP_CHAVE`), código sem reuso, comando `redefinir-2fa`
- [x] Usuário do banco só de dados para a API e o worker; migrations com o dono do schema no serviço `migracao` (comando `migrar`), que cria e libera o usuário da aplicação
- [x] Política de senha: lista de senhas comuns e vazadas, nome e e-mail, sequências e repetições; medidor de força nas telas
- [x] Limite por usuário nas gravações e no checkout
- [x] Dependabot (Maven, npm, Actions, Docker) e `npm audit` no pipeline
- [x] ADM → Segurança → Usuários: busca, bloquear e desbloquear, encerrar as sessões (o access token já emitido deixa de valer na hora), tudo na auditoria
- [x] Checkout só em HTTPS nos domínios da Hotmart; a compra só abre o pedido com a confirmação do aluno
- [x] Token dos links de e-mail sai da barra de endereço depois de lido
- [x] Teste que varre todas as rotas da API: só as públicas declaradas respondem sem login
- [x] Testes unitários, de integração e Playwright

### Fase 20 — `feature/lgpd`

Prompt: [`docs/prompts/f20-01-lgpd.md`](prompts/f20-01-lgpd.md). Operação:
[`docs/operacao.md`](operacao.md), seção 4.2. Modelo da política:
[`docs/politica-privacidade-modelo.md`](politica-privacidade-modelo.md).

- [x] Baixar meus dados: JSON com a conta e uma seção por módulo (`DadosPessoais` em cada módulo)
- [x] Excluir minha conta: senha e confirmação, aviso por e-mail, conta anonimizada, progresso, certificados, foto, sessões e segundo fator apagados, pedidos sem e-mail e nome; o que tem prazo legal fica
- [x] Retenção da auditoria de login (180 dias)
- [x] Modelo da política de privacidade com dados, finalidades, bases legais e os prazos aplicados pelo sistema
- [x] Perfil do aluno → Privacidade; testes de integração e Playwright

### Fase 21 — `feature/site-layout`

Prompt: [`docs/prompts/f21-01-site-react-ssr.md`](prompts/f21-01-site-react-ssr.md).

- [x] Site em React Router (modo framework) com renderização no servidor a cada requisição: HTML completo para os buscadores, hidratado no navegador; o container `site` vira um servidor Node na porta 3000 (o `web` deixa de servir a 8083)
- [x] Leituras da API com cache curto em memória (`SITE_CACHE_SEGUNDOS`, 30 s), 404 de verdade para curso inexistente, CSP com nonce por requisição
- [x] Layout moderno: topo fixo, abertura com ilustração, vantagens, cursos em destaque, como funciona, página do curso com coluna de compra fixa e conteúdo por módulo, rodapé, tema escuro
- [x] Busca no catálogo direto no navegador (sem acento e sem diferenciar maiúsculas)
- [x] Favicon da `@celke/kole-react` nos três fronts
- [x] Script `npm run cursos-exemplo` que cria cursos publicados de exemplo pela API do ADM
- [x] Testes unitários do SEO, Playwright do site e Lighthouse (metas da Fase 14)

---

## 5. Mapa fase → capítulos do tutorial

O conteúdo de cada capítulo está em [capitulos.md](capitulos.md).

| Fase | Branch | Capítulos |
|---|---|---|
| 0 | — | 1, 2 |
| 1 | `feature/base-projeto` | 3–14 |
| 2 | `feature/cursos` | 15, 16 |
| 3 | `feature/matricula-acesso` | 17, 18 |
| 4 | `feature/upload-video` | 19–21 |
| 5 | `feature/entrega-video` | 22–24 |
| 6 | `feature/arquivos-apoio` | 25 |
| 7 | `feature/forum` | 26 |
| 8 | `feature/certificado` | 27 |
| 9 | `feature/perfil-termos` | 28 |
| 10 | `feature/operacao` | 29–31 |
| — | repositório da `@celke/kole-react` | 32 |
| 11 | `feature/web-base` | 33 |
| 12 | `feature/web-adm` | 34 |
| 13 | `feature/web-aluno` | 35 |
| 14 | `feature/site` | 36 |
| 15 | `feature/migracao-legado` | 37 |
| 16 | `feature/pagamentos` | 38–40 |
| 17 | `feature/auditoria` | 41 |
| 18 | `feature/forum-anexos` | 26 |
| 19 | `feature/seguranca` | 42, 43 |
| 20 | `feature/lgpd` | 44 |
| 21 | `feature/site-layout` | 45 |
`````

### 5. `docs/capitulos.md`

Crie com exatamente este conteúdo:

`````markdown
# Roteiro do tutorial — capítulos

Roteiro de gravação da Plataforma de Cursos. Os capítulos seguem a ordem das fases do
[plano de implementação](plano-implementacao.md): primeiro todo o backend, com infraestrutura,
testes, pipeline e deploy; depois o frontend. Cada capítulo é gravado executando os
[prompts](prompts/README.md) da etapa correspondente.

## Formato de cada capítulo

Cada capítulo traz: **Objetivo**, **Conteúdo**, **Segurança** (quando houver), **Testes** e
**Entrega**. A entrega é o que quem acompanha o tutorial precisa ter funcionando no fim do
capítulo.

| Parte | Capítulos | Fases do plano |
|---|---|---|
| 1. Fundação | 1–5 | 0, 1.1–1.3 |
| 2. Autenticação e segurança | 6–10 | 1.4–1.6 |
| 3. Qualidade e entrega contínua | 11–14 | 1.7–1.9 |
| 4. Domínio de cursos | 15–18 | 2, 3 |
| 5. Vídeo | 19–24 | 4, 5 |
| 6. Restante do domínio | 25–28 | 6–9 |
| 7. Operação | 29–31 | 10 |
| 8. Frontend com `@celke/kole-react` | 32–36 | 11–14 |
| 9. Migração do legado (opcional) | 37 | 15 |
| 10. Vendas e pagamentos | 38–40 | 16 |
| 11. Auditoria e logs | 41 | 17 |

---

## Parte 1 — Fundação

### Cap. 1 — Visão geral e arquitetura

**Fase:** 0

- **Objetivo:** entender o que será construído, o que fica de fora e como as peças se ligam.
- **Conteúdo:**
  - Funcionalidades que vêm do CLMS legado: cursos, turmas, módulos, aulas em vídeo, matrícula, progresso, arquivos de apoio, fórum, certificado, perfil e termos.
  - O que fica de fora: LeadLovers, automação de e-mail, clube de desafios. A venda pela Hotmart entra no fim, na Parte 10, numa estrutura que aceita outros meios de pagamento.
  - Arquitetura: uma VPS da Hostinger (2 vCPU, 8 GB, 100 GB NVMe) com tudo em Docker — Caddy (HTTPS e roteamento), API e worker Spring Boot, PostgreSQL 18 e Garage (S3) — servindo produção e uma homologação leve.
  - Domínios, todos subdomínios de `<dominio>` (o domínio principal pertence a outras aplicações): `cursos.` (site), `aluno.`, `adm.` e `media.`, e os equivalentes `homolog-*`.
  - Fluxo do vídeo: upload direto ao Garage → worker com ffmpeg → HLS → playlist assinada pela API.
  - Modelo de dados enxuto: usuário, papel, curso, turma, módulo, aula, matrícula, vídeo, progresso, fórum, certificado, termo.
- **Entrega:** diagrama da arquitetura e lista de funcionalidades.

### Cap. 2 — Repositório, branches e fluxo de trabalho

**Fase:** 0 · **Prompts:** [f00-01](prompts/f00-01-estrutura-inicial.md), [f00-02](prompts/f00-02-orientacoes-para-ia.md)

- **Objetivo:** deixar o repositório pronto para o fluxo de PRs com testes e deploy.
- **Conteúdo:**
  - Repositório `<dono-github>/plataforma-cursos` com README e `.gitignore` (segredos, `target/`, `node_modules/`).
  - Branches: `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main`.
  - A `main` recebe código só da `homolog`; alteração reprovada na homologação é desfeita com `git revert`; depois de um hotfix, merge da `main` de volta na `homolog`.
  - Exclusão automática da branch após o merge; environments `homolog` e `production` no GitHub.
  - Como os prompts recriam o projeto passo a passo.
  - Orientações para agentes de IA versionadas em `.claude/`: rules que carregam pelo caminho tocado,
    mensagens de commit em Conventional Commits sem coautoria de IA (`/gc` e `/pc`), `/nova-branch`,
    `/abrir-pr`, `/revisar-dod` e o pipeline SDD (spec → tasks → código) com três portões humanos.
- **Entrega:** `main` e `homolog` no GitHub e a branch `feature/base-projeto` criada.

### Cap. 3 — Esqueleto do backend e padrão de testes

**Fase:** 1.1 · **Prompt:** [f01-01](prompts/f01-01-esqueleto-backend-e-testes.md)

- **Objetivo:** criar o projeto Java e as regras que todas as próximas etapas seguem.
- **Conteúdo:**
  - Spring Boot 4.1 com Java 25, gerado no Spring Initializr, organizado por funcionalidade (`shared/`, `auth/`, `curso/`...; cada módulo com `api/`, `application/`, `domain/`, `infra/`).
  - Dependências mínimas: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc. As demais entram na etapa que as usa.
  - `application.yaml` sem valores padrão para segredos; perfil `worker` sem servidor HTTP.
  - `CLAUDE.md` da raiz e do backend; skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e`, cada uma com o padrão do seu nível.
  - Build: Surefire (`*Test`), Failsafe (`*IT`), JaCoCo com 80% de linhas somando os dois, Spotless com palantir-java-format.
  - Dockerfile multi-stage com targets `api` e `worker` (com ffmpeg), jar em camadas.
- **Segurança:**
  - Tudo exige autenticação; públicos só `GET` de health/probes e da documentação.
  - Sem sessão, CSRF, formulário, HTTP Basic nem usuário embutido do Spring Security.
  - Actuator expõe só `health`, sem detalhes; Swagger só com `DOCS_ENABLED=true`.
  - Imagens rodam com usuário sem root.
- **Testes:** base `support/Containers` (PostgreSQL 18 no Testcontainers) e `support/IntegrationTest` (`MockMvcTester`); `SegurancaPadraoIT` como referência.
- **Entrega:** `./mvnw verify` verde e as imagens `api` e `worker` construídas.

### Cap. 4 — Ambiente local com Docker

**Fase:** 1.2 · **Prompt:** [f01-02](prompts/f01-02-ambiente-local.md)

- **Objetivo:** subir toda a stack local com um comando.
- **Conteúdo:**
  - `infra/compose.yml` com PostgreSQL 18, Garage, API, worker e Mailpit, todos com versão fixa.
  - Garage em modo `--single-node --default-bucket`: na primeira subida ele cria o bucket privado `midia` (prefixos `uploads/` e `videos/`) e a chave da aplicação; `garage.toml` com `replication_factor = 1` e `db_engine = "sqlite"`, sem segredos.
  - `.env` + `.env.example`; `gerar-env.sh` cria o `.env` com segredos aleatórios; variáveis obrigatórias com `${VARIAVEL:?mensagem}`.
  - Healthchecks e `depends_on: condition: service_healthy`; o worker sobe depois da API, que aplica as migrations.
  - Portas do host configuráveis no `.env`, para quando a porta padrão já estiver em uso.
- **Segurança:** portas publicadas só em `127.0.0.1`; nenhum segredo no yaml nem no `garage.toml`; `.env` com permissão 600.
- **Entrega:** `docker compose up --wait` sobe tudo saudável, a API responde em `/actuator/health`, o Swagger abre e o painel do Mailpit fica acessível.

### Cap. 5 — Padrões compartilhados

**Fase:** 1.3 · **Prompt:** [f01-03](prompts/f01-03-padroes-compartilhados.md)

- **Objetivo:** criar a base que todos os módulos usam.
- **Conteúdo:**
  - Erros no formato `ProblemDetail` (RFC 9457) com handler global: validação com a lista `erros` (`campo`, `mensagem`) em português, exceções de negócio, erros do Spring MVC traduzidos, erro inesperado só com o id da requisição; 401/403 da segurança no mesmo formato.
  - Id de correlação (`X-Request-Id`) no log e na resposta; logs em JSON (ECS).
  - DTOs como `record`, paginação padrão (`Pagina<T>`), `Clock` único.
  - `StorageService` com AWS SDK v2 (path-style): URLs pré-assinadas de GET/PUT com o endereço público, upload em partes, consulta, exclusão.
  - Configuração do bucket aplicada pela API ao subir: CORS para os hosts do front (expondo o `ETag`) e expiração de `uploads/` abandonados.
  - Fila no PostgreSQL: tabela `jobs`, `SELECT ... FOR UPDATE SKIP LOCKED`, lease, backoff exponencial, erro permanente, processador por tipo e consumo só no worker.
  - `EmailService` enfileira; o worker monta o HTML com Thymeleaf e envia pelo SMTP (Mailpit no desenvolvimento, Iagente nos servidores).
  - Configuração obrigatória em `@ConfigurationProperties` validadas.
- **Segurança:** nenhuma resposta com stack trace ou mensagem interna; id de correlação recebido só é aceito se for seguro para o log; nome de template restrito a `[a-z0-9-]`; variáveis escapadas no HTML do e-mail; preflight do CORS recusado para origens fora da lista.
- **Testes:** handler de erros e 401/403; `StorageService` contra o Garage real; dois consumidores da fila nunca pegam o mesmo job; backoff e lease vencido; e-mail conferido no Mailpit.
- **Entrega:** `./mvnw verify` verde com Garage e Mailpit no Testcontainers, e a stack local aplicando o CORS no bucket.

---

## Parte 2 — Autenticação e segurança

### Cap. 6 — Usuários e senhas

**Fase:** 1.4 · **Prompt:** [f01-04](prompts/f01-04-usuarios-e-login.md)

- **Objetivo:** guardar usuários e senhas com segurança.
- **Conteúdo:**
  - Migrations de `usuarios` e `papeis` (ALUNO, ADMIN).
  - Senhas com Argon2; hash `$2y$` do Laravel aceito e regravado em Argon2 no login.
  - Política de senha por tamanho (mínimo 12 para admin, máximo 128).
  - O primeiro admin é criado por comando (`docker compose run --rm api criar-admin <email>`), com a senha digitada na hora — nunca por migration.
  - Seed do profile `test` e `support/UsuariosSeed`.
- **Testes:** hash da senha e criação do admin.
- **Entrega:** admin criado pelo comando e gravado com a senha em hash.

### Cap. 7 — Login com JWT e refresh em cookie

**Fase:** 1.4 · **Prompt:** [f01-04](prompts/f01-04-usuarios-e-login.md)

- **Objetivo:** sessão segura sem guardar token em `localStorage`.
- **Conteúdo:**
  - Access token de 10 minutos no corpo da resposta, guardado pelo frontend só em memória.
  - Refresh token opaco, com o hash salvo no banco e rotação a cada uso. Se um refresh já usado reaparecer, toda a família de tokens é revogada.
  - Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, **sem `Domain`**, para não ser enviado às outras aplicações de `<dominio>`.
  - JWT assinado e validado pelo Resource Server do Spring Security: só HS256, emissor e validade conferidos.
  - Login com a mesma resposta e o mesmo tempo para e-mail inexistente e senha errada.
  - Logout e "sair de todos os dispositivos"; `login()`, `bearer()` e `criarUsuario()` no `IntegrationTest`.
- **Testes:** token adulterado, expirado, de outra chave, com outro algoritmo, `alg: none` ou de outro emissor é recusado; refresh reutilizado revoga a família; atributos do cookie conferidos.
- **Entrega:** login, refresh e logout funcionando pelo Swagger.

### Cap. 8 — Autorização

**Fase:** 1.5 · **Prompt:** [f01-05](prompts/f01-05-protecoes.md)

- **Objetivo:** cada rota acessível só por quem deve.
- **Conteúdo:**
  - Papéis vindos do token, com `@PreAuthorize`.
  - `SecurityFilterChain` em modo "nega tudo por padrão", com cada rota pública declarada junto do método HTTP — nenhuma rota fica liberada para qualquer método por engano (como um `/api/usuarios` aberto).
- **Testes:** matriz parametrizada rota × papel (anônimo / ALUNO / ADMIN) com o status esperado; método não declarado numa rota pública exige token.
- **Entrega:** aluno recebe 403 em rota de admin.

### Cap. 9 — Proteções extras

**Fase:** 1.5 · **Prompt:** [f01-05](prompts/f01-05-protecoes.md)

- **Objetivo:** dificultar força bruta, abuso e CSRF.
- **Conteúdo:**
  - Limite por IP com Bucket4j em login e refresh, num filtro que roda antes da segurança e conta até as requisições inválidas; 429 com `Retry-After`.
  - Bloqueio temporário após falhas seguidas: 5 por e-mail + IP (o dono ainda entra de outro lugar) e 20 por e-mail vindas de qualquer IP; verificado antes de calcular o hash.
  - Proteção CSRF no refresh e no logout: `Origin` e `Sec-Fetch-Site` conferidos, porque outras aplicações em `*.<dominio>` são o mesmo site para o navegador e o `SameSite=Strict` não as barra.
  - Auditoria de login: resultado, IP e user-agent de toda tentativa, gravada mesmo quando o login falha.
  - IP real do cliente atrás do proxy: `X-Forwarded-For` aceito só de proxy interno (`forward-headers-strategy: native`).
  - Sem CORS na API: o Caddy serve front e API na mesma origem.
- **Testes:** bloqueio com relógio ajustável (bordas de 15 min e da janela de 1 h); filtro de limite por IP direto, com limite pequeno; 429 e auditoria pela API; refresh e logout de outra origem recusados sem tocar na sessão.
- **Entrega:** tentativas seguidas de login bloqueadas e registradas na auditoria.

### Cap. 10 — Cadastro, confirmação de e-mail e recuperação de senha

**Fase:** 1.6 · **Prompt:** [f01-06](prompts/f01-06-cadastro-e-recuperacao.md)

- **Objetivo:** o aluno cria a conta e recupera a senha sozinho.
- **Conteúdo:**
  - Cadastro com conta pendente e link de confirmação válido por 48 horas; login só depois de confirmar.
  - Recuperação de senha com link de 1 hora; a nova senha encerra todas as sessões e também confirma o e-mail.
  - Tokens de uso único com só o hash no banco; um link novo invalida o anterior.
  - E-mails pela fila, montados com Thymeleaf e enviados pelo worker: Mailpit no desenvolvimento, Iagente nos servidores (remetente de `<dominio>` com SPF e DKIM).
  - Healthcheck do worker por arquivo de batimento, para o compose e o deploy saberem que ele subiu.
- **Segurança:**
  - Cadastro, reenvio e recuperação respondem sempre o mesmo, exista ou não a conta; quem descobre o que aconteceu é o dono da caixa, pelo e-mail que recebe.
  - No máximo 3 e-mails por hora por conta e tipo, contra uso da plataforma para lotar a caixa de alguém.
  - Senha de aluno de 10 a 128 caracteres; limite por IP também nas rotas da conta.
- **Testes:** jornadas completas lendo o link no Mailpit; link de uso único; link antigo invalidado pelo reenvio; e-mail inexistente sem envio; limite de e-mails por hora.
- **Entrega:** cadastro → e-mail → confirmação → login, e recuperação de senha completa, com os e-mails enviados pelo worker.

---

## Parte 3 — Qualidade e entrega contínua

### Cap. 11 — Testes e2e e pipeline no GitHub Actions

**Fase:** 1.7 · **Prompt:** [f01-07](prompts/f01-07-e2e-e-pipeline.md)

- **Objetivo:** cada PR testado com a stack inteira, usando as imagens que vão para produção.
- **Conteúdo:**
  - `infra/compose.prod.yml`: a stack dos servidores, com Caddy na frente e imagens do GHCR pela tag do commit.
  - `infra/caddy/Caddyfile`: hosts pelo ambiente, só `/api` e o health check chegam à API, headers de segurança.
  - `infra/compose.e2e.yml` + `infra/e2e.env`: a mesma stack com HTTP em `*.localhost`, Mailpit e o seed do profile `e2e`, num projeto Compose separado do ambiente local.
  - Perfil Maven `e2e` no backend (`src/e2e/java`, REST Assured, Awaitility); os sites são escolhidos pelo cabeçalho `Host`, sem depender da resolução de `*.localhost`.
  - Jornadas `CadastroE2E` (e-mail entregue pelo worker de verdade), `SessaoE2E` (login → refresh → logout pelo Caddy) e `ProxyE2E` (headers, rotas bloqueadas, `X-Forwarded-For` forjado sem efeito).
  - `pipeline.yml`: testes → build das imagens com cache → e2e → relatórios e `docker compose logs` anexados quando falha; PR e push para `homolog` e `main`.
  - Regras de proteção das branches com os checks obrigatórios.
- **Segurança:** o e2e prova pelo próprio limite por IP que o cliente não consegue forjar o IP atrás do Caddy; Swagger e endpoints internos do Actuator não saem do proxy.
- **Testes:** as próprias jornadas e2e.
- **Entrega:** PR com o pipeline verde e as regras de proteção ativas.

### Cap. 12 — Preparar a VPS

**Fase:** 1.8 · **Prompt:** [f01-08](prompts/f01-08-vps.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** servidor seguro antes de receber a aplicação.
- **Conteúdo:**
  - `preparar-vps.sh` numa VPS Ubuntu 24.04 nova: Docker oficial com rotação de logs, administrador com sudo e usuário `deploy` sem sudo, SSH só por chave e sem root, fail2ban, atualizações automáticas, swap de 2 GB.
  - Firewall do painel da Hostinger + `ufw`: só 22, 80 e 443.
  - Cuidado: o Docker contorna o `ufw` nas portas que publica. Por isso só o Caddy publica portas; PostgreSQL, Garage e API ficam só na rede interna.
  - A configuração do SSH vale antes da que vem na imagem (`50-cloud-init.conf`), e o script só termina depois de você confirmar o login do administrador num segundo terminal.
  - DNS dos seis subdomínios (produção e homologação) apontando para a VPS.
- **Entrega:** acesso SSH só por chave e portas fechadas conferidas de fora.

### Cap. 13 — Produção e homologação com Docker na VPS

**Fase:** 1.8 · **Prompt:** [f01-08](prompts/f01-08-vps.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** os dois ambientes rodando na mesma VPS sem um derrubar o outro.
- **Conteúdo:**
  - `compose.prod.yml` por ambiente (`AMBIENTE`), com rede e apelidos próprios (`producao-api`, `homolog-api`); `compose.homolog.yml` por cima, com limites menores e o Swagger.
  - `/opt/plataforma-cursos/{caddy,producao,homolog}`, cada ambiente com `.env` próprio (permissão 600) gerado com segredos aleatórios e diferentes.
  - Um Caddy só nas portas 80/443, ligado às redes dos dois ambientes; `Caddyfile` com trechos comuns e um arquivo de sites por ambiente — o e2e usa o mesmo `Caddyfile`.
  - Homologação restrita por IP e com `noindex` (senha do Caddy não serve: ocuparia o cabeçalho `Authorization` do JWT); `adm.` de produção restrito por IP.
  - Headers no Caddy: HSTS sem `includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, sem `Server`; `lb_try_duration` para a API reiniciar sem erro 502.
  - Limites de memória e CPU por container (produção: API 1 GB, worker 2 GB e 1,5 CPU, PostgreSQL 1 GB, Garage 768 MB; homologação menor, com o worker em meia CPU) e `-Xmx` de acordo.
- **Entrega:** `homolog-<sub-aluno>.<dominio>/api` e `<sub-aluno>.<dominio>/api` respondendo com HTTPS.

### Cap. 14 — Deploy automático com rollback

**Fase:** 1.9 · **Prompt:** [f01-09](prompts/f01-09-deploy-automatico.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** merge na branch = versão no ar, com volta automática se falhar.
- **Conteúdo:**
  - Imagens com tag = SHA do commit, nunca `latest`, publicadas no GHCR pelo próprio job de e2e: vai para o servidor exatamente a imagem que passou nas jornadas.
  - `deploy.sh` na VPS: valida os argumentos, recebe só os arquivos de compose esperados, faz backup do banco, troca a tag, espera todos os containers ficarem saudáveis e, se algum não ficar, volta para a versão anterior (arquivos e tag).
  - Um deploy por vez (`flock`), deploy que continua mesmo se a conexão SSH cair, últimas versões e backups guardados.
  - Chave SSH presa ao script no `authorized_keys` (`command=...,no-pty,no-port-forwarding`) e impressão digital da VPS fixada no Actions.
  - Push na `homolog` → homologação; push na `main` → produção, com aprovação opcional; só PRs cancelam execuções antigas do pipeline.
  - Migrations compatíveis com a versão anterior, porque o rollback não desfaz migration.
- **Segurança:** a chave do deploy não abre shell, terminal nem túnel; o script recusa ambiente ou SHA fora do formato (inclusive tentativa de injetar comandos pelo SSH) e só extrai os arquivos esperados do pacote.
- **Testes:** simulação local de uma VPS: versão boa no ar, versão quebrada com rollback automático, backup restaurável, argumentos inválidos e deploy simultâneo recusados; chave presa ao script conferida com um `sshd` real.
- **Entrega:** deploy nos dois ambientes pelo pipeline e um rollback testado de propósito.

---

## Parte 4 — Domínio de cursos

### Cap. 15 — Curso, turma, módulo e aula

**Fase:** 2 · **Prompt:** [f02-01](prompts/f02-01-cursos-e-catalogo.md)

- **Objetivo:** modelar o conteúdo dos cursos.
- **Conteúdo:**
  - Curso → turmas → módulos → aulas. A turma é a oferta do curso: tem os próprios módulos e aulas e, no próximo capítulo, as matrículas.
  - Status como enum e `CHECK` no banco (nada de `[3,4,13,14]`): curso em rascunho, publicado ou arquivado; turma em preparação, ativa ou encerrada; módulo e aula ativos ou inativos.
  - Slug para o site, gerado do título quando não informado (sem acento, minúsculas e hífens) e único no banco.
  - Ordem de módulos e aulas sempre 1, 2, 3...: item novo entra no fim, a exclusão refaz a sequência e a reordenação recebe a lista completa.
  - Restrição única adiável (`DEFERRABLE INITIALLY DEFERRED`): o PostgreSQL só confere a ordem no commit, e a troca de posições acontece numa transação.
  - Trava do pai (`SELECT ... FOR UPDATE`) para duas criações simultâneas não pegarem a mesma posição.
  - Exclusão só de item vazio (`ON DELETE RESTRICT`); o que tem conteúdo é arquivado pelo status.
- **Testes:** slug e ordenação em tabela (lista incompleta, repetida, de outro pai, vazia); reordenação real no PostgreSQL; oito módulos criados ao mesmo tempo recebem 1 a 8.
- **Entrega:** tabelas criadas pelo Flyway e regras de ordem garantidas pelo banco.

### Cap. 16 — CRUD administrativo e catálogo público

**Fase:** 2 · **Prompt:** [f02-01](prompts/f02-01-cursos-e-catalogo.md)

- **Objetivo:** o admin gerencia os cursos e o site lista os publicados.
- **Conteúdo:**
  - Endpoints `/api/adm/**` com validação, `Location` na criação e `PATCH .../status`; reordenação de módulos e aulas.
  - Capa do curso em duas etapas: o navegador envia a imagem direto ao Garage por URL pré-assinada e a API confirma, reduz para até 1280 × 720 e regrava em JPEG.
  - Catálogo público: cursos publicados e curso por slug, com o conteúdo ativo da turma ativa mais recente.
  - Capa no catálogo por um endereço estável da API, que redireciona para uma URL assinada nova: a URL assinada vence em uma hora e quebraria o HTML pré-renderizado do site.
- **Segurança:**
  - `/api/adm/**` exige ADMIN no `SecurityConfig` e de novo com `@PreAuthorize`: o aluno recebe 403 antes de o corpo ser lido, e um controller novo sem a anotação continua protegido.
  - A imagem é conferida pelo conteúdo, não pela extensão nem pelo `Content-Type`: HTML enviado como `image/png` é recusado.
  - A re-codificação descarta o EXIF (GPS e dados da câmera) e qualquer conteúdo escondido no arquivo.
  - Dimensões lidas do cabeçalho antes de decodificar (proteção contra "bomba de descompressão"); 5 MB conferidos de novo no arquivo enviado, porque a URL pré-assinada não limita o tamanho.
  - A confirmação só aceita a chave gerada para o mesmo curso; o arquivo enviado é apagado em qualquer resultado.
  - Catálogo sem dados internos (ids de curso, status, chaves do bucket); rascunho e arquivado respondem 404.
- **Testes:** matriz de papéis de todas as rotas com ids reais; CRUD com `ProblemDetail`; capa com o Garage real (redução, EXIF removido, troca, remoção, envios recusados); catálogo com turmas, módulos e aulas inativos; e2e do admin criando o curso com capa e o visitante vendo no catálogo.
- **Entrega:** curso completo cadastrado pelo Swagger, com capa, visível no catálogo.

### Cap. 17 — Matrícula

**Fase:** 3 · **Prompt:** [f03-01](prompts/f03-01-matricula-e-acesso.md)

- **Objetivo:** ligar alunos às turmas.
- **Conteúdo:**
  - `matriculas` com início, fim opcional (acesso sem prazo), status (enum), forma de pagamento e transação.
  - Status gravado só `ATIVA` ou `CANCELADA`; vigente, vencida ou aguardando início são calculados com o `Clock`, sem job que marque matrículas.
  - Índices únicos parciais: uma matrícula ativa por aluno e turma, e cada transação gera uma matrícula só (base para o webhook de pagamento).
  - Matrícula manual pelo admin: matricular por e-mail, alterar período, cancelar (o histórico fica).
  - Endpoints do aluno: meus cursos (ativos, arquivados, expirados), arquivar e desarquivar.
  - Fronteira entre módulos: a matrícula guarda ids e consulta o conteúdo por um serviço exportado pelo módulo de cursos.
- **Segurança:** tudo da área do aluno usa o id do token, nunca um id enviado pelo cliente; matrícula de outro aluno responde 404.
- **Testes:** situação da matrícula em tabela; matrícula repetida, turma encerrada, transação repetida; abas de meus cursos; turma com matrículas não pode ser excluída.
- **Entrega:** aluno matriculado vê seus cursos.

### Cap. 18 — Política única de acesso

**Fase:** 3 · **Prompt:** [f03-01](prompts/f03-01-matricula-e-acesso.md)

- **Objetivo:** um ponto só decide o que o aluno pode ver.
- **Conteúdo:**
  - `AcessoAulaPolicy`: decide o acesso a aula, vídeo, arquivo, fórum, progresso e certificado; `AcessoAulaService.exigirAcesso` é a porta de entrada dos outros módulos.
  - Regras: matrícula ativa e dentro do período (fim inclusivo), ou aula gratuita de turma ativa e curso publicado.
  - A turma inteira (lista de módulos e aulas) só com matrícula vigente: a aula gratuita não abre a turma.
  - Data e hora por `Clock` injetado.
- **Segurança:**
  - Recurso de outro aluno responde 404, para não revelar que existe.
  - Aula gratuita de curso que voltou para rascunho não vaza; aula ou módulo inativo responde 404.
- **Testes:** unitários com todas as combinações de status × datas (instante antes, exato e depois de cada limite); matriz de acesso ao conteúdo; IDOR (aluno A tentando ver dados do aluno B); e2e com a matrícula vencendo.
- **Entrega:** aluno sem matrícula ou com matrícula vencida recebe 403.

---

## Parte 5 — Vídeo

### Cap. 19 — Upload direto para o Garage

**Fase:** 4

- **Objetivo:** enviar vídeos grandes sem passar pela API.
- **Conteúdo:**
  - O ADM pede URLs pré-assinadas de upload multipart, e o navegador envia direto para `<sub-media>.<dominio>` (prefixo `uploads/` do bucket `midia`).
  - A API valida tipo e tamanho antes de assinar.
- **Segurança:** depois do upload, a API confere o tamanho e o tipo real do arquivo antes de enfileirar.
- **Testes:** upload multipart contra o Garage do Testcontainers; arquivo com extensão falsa rejeitado.
- **Entrega:** vídeo em `uploads/` e job criado na fila.

### Cap. 20 — Transcodificação com ffmpeg

**Fase:** 4

- **Objetivo:** reduzir o tamanho do vídeo e prepará-lo para streaming.
- **Conteúdo:**
  - Worker consome a fila: `ffprobe` para analisar, ffmpeg para gerar HLS em 720p + 1080p (sem aumentar a resolução de origem), H.264 CRF 23–26, `maxrate`, AAC 128k, segmentos de 6 s.
  - Thumbnail e duração; resultado em `videos/`; o original é apagado depois que a transcodificação dá certo.
  - Status `pendente → processando → pronto/erro`, reprocessar pelo ADM.
  - Job volta para a fila se o worker parar no meio (`stop_grace_period` + lease com expiração).
  - Comparativo de tamanho antes e depois.
- **Segurança:** ffmpeg chamado via `ProcessBuilder` com lista de argumentos, nunca concatenando uma string de shell.
- **Testes:** transcodificar um vídeo de 3 s; e2e de upload até o status "pronto".
- **Entrega:** vídeo convertido em HLS com o tamanho reduzido registrado.

### Cap. 21 — Limites da VPS

**Fase:** 4

- **Objetivo:** a transcodificação não pode derrubar a API.
- **Conteúdo:**
  - `cpus` e limite de memória no container do worker; um job por vez; prioridade baixa no processo.
  - Medição do tempo de processamento por minuto de vídeo na VPS.
  - Conta de quantas horas de vídeo cabem no disco.
- **Entrega:** tabela com o tempo medido e a API respondendo normalmente durante a transcodificação.

### Cap. 22 — Playlist assinada pela API

**Fase:** 5

- **Objetivo:** só quem tem acesso assiste, e o bucket continua privado.
- **Conteúdo:**
  - `GET /api/aulas/{id}/video.m3u8` verifica a `AcessoAulaPolicy` e reescreve a playlist com URLs pré-assinadas de cada segmento, válidas pela duração do vídeo + margem.
  - Substitui os cookies assinados de CDN, que não existem nesta hospedagem.
- **Testes:** sem matrícula, 403; com matrícula, segmentos assinados que baixam e depois expiram.
- **Entrega:** vídeo tocando por uma playlist assinada.

### Cap. 23 — Criptografia AES-128 do HLS (nível extra)

**Fase:** 5 (opcional)

- **Objetivo:** um segmento copiado não serve para nada sem a chave.
- **Conteúdo:** segmentos cifrados; a chave só é entregue por um endpoint autenticado que passa pela `AcessoAulaPolicy`.
- **Testes:** a chave sem token ou sem matrícula é recusada.
- **Entrega:** segmento baixado direto do Garage não toca sem a chave.

### Cap. 24 — Progresso e log de acesso

**Fase:** 5

- **Objetivo:** o aluno retoma de onde parou e acompanha o avanço.
- **Conteúdo:**
  - Posição do vídeo, aula vista / revisar, percentual da turma.
  - Log de acesso com retenção definida (LGPD).
- **Testes:** progresso de outro aluno responde 404; percentual calculado com as bordas (nenhuma aula, todas as aulas).
- **Entrega:** progresso salvo e refletido na turma.

---

## Parte 6 — Restante do domínio

### Cap. 25 — Arquivos de apoio

**Fase:** 6

- **Objetivo:** entregar código-fonte e materiais com o mesmo controle do vídeo.
- **Conteúdo:** arquivos por aula e por módulo, enviados pelo ADM e baixados por URL pré-assinada curta, sempre pela `AcessoAulaPolicy`.
- **Testes:** download permitido e bloqueado.
- **Entrega:** aluno matriculado baixa o arquivo; sem matrícula, 403.

### Cap. 26 — Fórum por aula e por turma

**Fase:** 7

- **Objetivo:** dúvidas respondidas dentro da plataforma.
- **Conteúdo:** perguntas e respostas com anexo validado; moderação pelo admin (ocultar, fixar, responder como instrutor); rate limit na criação.
- **Segurança:** conteúdo em Markdown convertido para HTML filtrado, contra XSS.
- **Testes:** XSS, IDOR em anexo, aluno sem matrícula; e2e de pergunta e resposta.
- **Entrega:** aluno pergunta, admin responde, aluno vê a resposta.

### Cap. 27 — Certificado em PDF

**Fase:** 8

- **Objetivo:** certificado gerado e verificável.
- **Conteúdo:**
  - Regra de conclusão (percentual de aulas vistas) configurável por turma; nome confirmado pelo aluno.
  - PDF com openhtmltopdf e código de validação público, usado na página "validar certificado" do site.
- **Testes:** regra de conclusão com as bordas; e2e de conclusão → certificado → validação.
- **Entrega:** certificado baixado e validado pelo código.

### Cap. 28 — Perfil e termos

**Fase:** 9

- **Objetivo:** o aluno cuida dos próprios dados e aceita os termos vigentes.
- **Conteúdo:**
  - Dados, troca de senha (exige a senha atual) e foto de perfil redimensionada, sem metadados EXIF.
  - Termos versionados: aceite obrigatório no primeiro acesso e a cada nova versão.
- **Testes:** troca de senha com usuário próprio do teste; e2e de novo termo bloqueando as rotas até o aceite.
- **Entrega:** perfil editável e aceite de termos funcionando.

---

## Parte 7 — Operação

Fica antes do frontend porque precisa estar pronta antes de entrarem dados reais em produção.

### Cap. 29 — Backup e restauração

**Fase:** 10

- **Objetivo:** recuperar tudo se a VPS for perdida.
- **Conteúdo:**
  - `pg_dump` diário.
  - Backup do Garage com restic **para fora da VPS**: o snapshot da Hostinger sozinho não basta, porque o vídeo e o banco estão no mesmo disco.
  - Teste de restauração documentado.
- **Entrega:** restauração completa executada a partir do backup.

### Cap. 30 — Monitoramento e disco

**Fase:** 10

- **Objetivo:** saber do problema antes do aluno.
- **Conteúdo:**
  - Uptime externo e alertas; alerta de disco e de falhas no worker.
  - Rotação de logs do Docker; limpeza de uploads órfãos e dos dados da homologação.
  - Plano de saída dos vídeos para um S3 externo (R2/B2) quando o disco apertar: só muda o endpoint no `.env`.
- **Entrega:** alertas chegando num teste provocado.

### Cap. 31 — Checklist final de segurança

**Fase:** 10

- **Objetivo:** revisar a API inteira antes de abrir para alunos.
- **Conteúdo:**
  - Revisão das categorias do OWASP ASVS nível 1.
  - Varredura com OWASP ZAP contra a homologação.
  - Trivy no pipeline e atualização de dependências.
- **Entrega:** relatório sem achados de severidade alta.

---

## Parte 8 — Frontend com `@celke/kole-react`

### Cap. 32 — A biblioteca `@celke/kole-react`

**Pré-requisito do frontend** (repositório da biblioteca)

- **Objetivo:** conhecer a biblioteca de layout usada pelos fronts e como instalá-la.
- **Conteúdo:** moldura (`Estrutura`, `TelaLogin`), formulários, tabelas, diálogos, avisos e o visualizador de arquivos; instalação do GitHub (`github:celkecursos/kole-react#<commit>`). Os componentes próprios da área do aluno (player de vídeo, lista de aulas, fórum, campo de imagens) ficam no app.
- **Testes:** componentes testados com Vitest no repositório da biblioteca.
- **Entrega:** commit da biblioteca fixado no `web/package.json`.

### Cap. 33 — Workspace web e a biblioteca

**Fase:** 11

- **Objetivo:** base dos três frontends servida pelo Caddy.
- **Conteúdo:**
  - Pacote único em `web/` com `aluno` e `adm` (Vite + React + TypeScript); o `site` entra na Fase 14.
  - `@celke/kole-react` instalada do GitHub com commit fixo.
  - Client HTTP com token em memória, refresh silencioso e fila de requisições durante o refresh; proteção de rotas por papel; tratamento de termo pendente.
  - Telas de login, cadastro, confirmação de e-mail e recuperação de senha.
  - Imagem `web`: build dos fronts + Caddy servindo `/srv/site`, `/srv/aluno` e `/srv/adm`; proxy do Vite `/api` no desenvolvimento.
- **Segurança:** CSP (`connect-src 'self' https://<sub-media>.<dominio>`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html`.
- **Testes:** Vitest + Testing Library; Playwright no pipeline (login do aluno e do admin, aluno barrado no ADM, logout).
- **Entrega:** login funcionando em `<sub-aluno>.<dominio>` e `<sub-adm>.<dominio>`.

### Cap. 34 — ADM

**Fase:** 12

- **Objetivo:** o admin opera a plataforma pelo navegador.
- **Conteúdo:** cursos, turmas, módulos e aulas (reordenação pelos botões subir e descer); upload de vídeo multipart com progresso e status de processamento; arquivos de apoio, matrículas, moderação do fórum e termos.
- **Testes:** Playwright do admin criando um curso completo e enviando um vídeo curto até "pronto".
- **Entrega:** curso publicado inteiramente pelo ADM.

### Cap. 35 — Área do aluno

**Fase:** 13

- **Objetivo:** a experiência completa do aluno.
- **Conteúdo:** dashboard de cursos; página da turma com progresso; aula com player, lista lateral, anterior/próxima, vista/revisar, arquivos e fórum (com imagens nas perguntas e respostas, Fase 18); certificados e perfil.
- **Testes:** Testing Library e Playwright (login → turma → aula → vídeo toca → marca como vista → progresso atualizado).
- **Entrega:** aluno assiste a uma aula do início ao fim.

### Cap. 36 — Site moderno

**Fase:** 14

- **Objetivo:** vitrine pública rápida e bem indexada.
- **Conteúdo:**
  - Pré-renderização estática (cada página vira um HTML) em `<sub-site>.<dominio>`, gerada pelo container `site` a partir da API do próprio ambiente ao subir e a cada 5 minutos.
  - Home, catálogo, página do curso, validar certificado e páginas legais.
  - Layout novo com os mesmos tokens visuais da `@celke/kole-react`.
  - SEO: meta tags, Open Graph, sitemap, `robots.txt`.
- **Testes:** meta de Lighthouse verificada no pipeline.
- **Entrega:** site publicado com a nota de Lighthouse atingida.

---

## Parte 9 — Migração do legado (opcional)

### Cap. 37 — Importar dados do MySQL do CLMS

**Fase:** 15

- **Objetivo:** trazer os alunos e cursos atuais para a nova plataforma.
- **Conteúdo:**
  - ETL de usuários, cursos, turmas, módulos, aulas, matrículas e progresso.
  - Status numéricos do legado (`3, 4, 13, 14, 15`...) mapeados para os enums novos.
  - Senhas bcrypt do Laravel aproveitadas: o hash `$2y$` é aceito no primeiro login e regravado em Argon2.
  - Reprocessamento dos vídeos já existentes no S3 para HLS.
- **Entrega:** alunos do legado entram com a senha atual e encontram seus cursos.

---

## Parte 10 — Vendas e pagamentos

### Cap. 38 — Meios de pagamento e o webhook da Hotmart

**Fase:** 16

- **Objetivo:** receber as notificações da Hotmart com segurança, numa estrutura que aceita outros meios de pagamento sem mexer no núcleo.
- **Conteúdo:**
  - Webhook 1.0.0 (formato antigo, não recomendado) × 2.0.0: corpo JSON com `id` por evento, `X-HOTMART-HOTTOK` no cabeçalho, `xcod` em `data.purchase.origin`.
  - A porta `GatewayPagamento` (um bean por meio): campos de configuração, capacidades, teste das credenciais, checkout, autenticação, interpretação e consulta; `GatewaysRegistrados` por tipo.
  - Meio de pagamento configurado pelo painel: campos comuns em JSON, segredos cifrados com AES-256-GCM (`PAGAMENTOS_CHAVE`, IV por gravação, webhook token como dado associado), um ativo por vez com índice único parcial.
  - Migration `V15`: meios de pagamento, ofertas, pedidos, histórico e eventos recebidos; usuário sem senha; matrícula ligada ao pedido.
  - `POST /api/pagamentos/webhooks/{token}`: limite de tamanho e por IP, hottok comparado em tempo constante, `INSERT ... ON CONFLICT DO NOTHING` como idempotência, corpo mascarado (documento, telefone, IP), job na fila e 200 na hora.
  - Por que só responder erro quando nada foi gravado: a Hotmart reenvia e desativa a URL que continua falhando.
  - Homologação restrita por IP com exceção só para o caminho do webhook.
- **Segurança:** segredos nunca voltam na API nem ficam em texto puro no banco; 401 sem gravar nada; 413 para corpo grande.
- **Testes:** interpretação de cada fixture (campos opcionais do afiliado, status desconhecido, data em número e em texto), hottok, cifra (ida e volta, IV diferente, chave errada), integração do webhook (duplicado, 401, 404, corpo inválido, 413) e matriz de papéis.
- **Entrega:** "Enviar teste" da Hotmart aparece em "Eventos recebidos" na homologação.

### Cap. 39 — Pedido, status e matrícula

**Fase:** 16

- **Objetivo:** transformar cada notificação na matrícula certa, mesmo com eventos repetidos e fora de ordem.
- **Conteúdo:**
  - Status interno do pedido, igual para todos os meios, e a tabela de transições num lugar só (`TransicoesPedido`): reembolso e chargeback sempre valem; aprovação depois do reembolso não reabre.
  - Worker com uma transação por evento e trava no pedido (`SELECT ... FOR UPDATE`); pedido achado pelo `xcod`, pela transação ou criado como `EXTERNO` (afiliado, link direto).
  - Matrícula pelo pagamento: criar, estender quem já está matriculado, reativar depois de disputa, cancelar no reembolso.
  - Conta do comprador sem senha e o link de criar senha no e-mail da matrícula (nunca senha por e-mail).
  - Checkout por redirecionamento com o código do pedido no `xcod`; página de retorno que consulta o pedido.
  - Avisos ao admin (chargeback, disputa, evento com erro), reprocessamento pelo painel, retenção do corpo dos eventos e a reconciliação pela API de vendas (ligada por configuração).
- **Testes:** todas as transições; integração do fluxo completo (aprovado → acesso e e-mail → reembolso tira o acesso), venda sem `xcod` criando a conta, boleto aguardando, evento antigo ignorado, dois eventos do mesmo pedido ao mesmo tempo; jornada e2e pelo Caddy até o e-mail no Mailpit.
- **Entrega:** compra aprovada libera o curso e o reembolso tira, sem matrícula nem e-mail duplicado.

### Cap. 40 — Comprar pelo site, painel de vendas e testes de ponta a ponta

**Fase:** 16

- **Objetivo:** o aluno compra pelo site e o admin acompanha as vendas pelo ADM.
- **Conteúdo:**
  - ADM: meios de pagamento com o formulário montado a partir dos campos do gateway, "Testar credenciais", ativação e a URL do webhook pronta para copiar; aba "Venda" da turma com as ofertas; pedidos com a linha do tempo; eventos recebidos com o corpo e "Reprocessar".
  - Aluno: `/comprar/:oferta` (volta para a compra depois de entrar ou criar a conta) e `/pagamento/retorno`, que acompanha o pedido a cada 3 s; "Crie sua senha" para quem veio da compra.
  - Site: preço e botão "Comprar" de cada oferta na página do curso, com as ofertas nos dados estruturados.
  - Como adicionar outro meio de pagamento (Mercado Pago, Stripe...) só com um valor no enum e um bean novo.
- **Testes:** Vitest da página de retorno e do formulário; Playwright do admin cadastrando a Hotmart (validação e credencial errada), da compra interceptando o `pay.hotmart.com`, do boleto, do reembolso e do evento ignorado reprocessado.
- **Entrega:** compra de ponta a ponta na homologação com a Hotmart de teste.

---

## Parte 11 — Auditoria e logs

### Cap. 41 — Trilha de auditoria e logs persistentes

**Fase:** 17

- **Objetivo:** saber quem fez o quê nos dados, de onde e quando, e guardar o log técnico por meses.
- **Conteúdo:**
  - Por que não Hibernate Envers, JaVers ou Spring Data Auditing: uma tabela por entidade, outra camada de modelo ou só "quem alterou por último"; aqui, uma tabela única com ação, origem e IP.
  - Ouvinte dos eventos do Hibernate (`PostInsert`, `PostUpdate`, `PostDelete`) que grava na mesma transação, com só os campos que mudaram na alteração.
  - Contexto da ação: usuário do token, rota com as variáveis, IP, navegador, id da requisição; job da fila pelo MDC; "sistema" nos agendamentos.
  - `@DadoSensivel` (hash de senha, segredos, tokens como `***`) e `@SemAuditoria` (progresso, logs, tokens de sessão).
  - Só inclusão garantida pelo banco (gatilho que recusa `UPDATE` e `DELETE`) e a retenção como única exceção.
  - Tela de consulta no ADM com filtros e o antes/depois lado a lado.
  - Logs da API e do worker em arquivo num volume, com rotação diária no horário de Brasília, compressão e limite por idade e por tamanho; como ler na VPS.
- **Segurança:** a trilha não guarda segredos; o ADM só lê; nem a própria aplicação consegue alterar ou apagar fora da retenção.
- **Testes:** unitários da conversão de valores e do contexto; integração com ADM, aluno, worker, segredo oculto, operação recusada sem rastro, gatilho e retenção; Playwright da tela.
- **Entrega:** a alteração de um curso aparece na auditoria com o antes e o depois; os logs ficam no volume depois de um deploy.

---

## Parte 12 — Segurança reforçada

### Cap. 42 — Segundo fator dos administradores e senhas fortes

**Fase:** 19 · **Prompt:** [f19-01](prompts/f19-01-seguranca.md)

- **Objetivo:** uma senha vazada não basta para entrar no ADM, e as senhas fáceis de adivinhar ficam de fora.
- **Conteúdo:**
  - TOTP (RFC 6238) sem biblioteca: HMAC-SHA1, passos de 30 s, 6 dígitos, tolerância de um passo, Base32 e o endereço `otpauth://` do QR code; testes com os vetores da RFC.
  - Login em duas etapas: a senha certa devolve um desafio de 5 minutos (sem sessão); o código abre a sessão; o código errado conta no bloqueio; o mesmo código não vale duas vezes.
  - Primeiro acesso com o QR code e os 10 códigos de recuperação de uso único (só o hash no banco); segredo cifrado com AES-256-GCM e dado associado (`TOTP_CHAVE`); comando `redefinir-2fa`.
  - Política de senha: a lista de senhas comuns e vazadas (gerada por comando a partir de uma lista pública), nome e e-mail dentro da senha, sequências de teclado e de alfabeto, repetições; a mesma regra no cadastro, na redefinição, no perfil e no `criar-admin`; o medidor de força da biblioteca nas telas.
  - Front: a tela da segunda etapa compartilhada entre o ADM e a área do aluno, com o QR code gerado no navegador.
- **Segurança:** a sessão do administrador nunca abre só com a senha; o segredo do aplicativo não sai do banco em texto.
- **Testes:** unitários do TOTP (RFC), da política e do login em duas etapas; integração do cadastro, do código errado, do código de recuperação de uso único e do segredo cifrado; Playwright do admin entrando com o código.
- **Entrega:** o admin cadastra o aplicativo no primeiro acesso e passa a entrar com o código.

### Cap. 43 — Menos privilégio: banco, limites, contas e dependências

**Fase:** 19 · **Prompt:** [f19-01](prompts/f19-01-seguranca.md)

- **Objetivo:** uma falha na aplicação causa o menor estrago possível.
- **Conteúdo:**
  - Usuário do banco só de dados para a API e o worker; o serviço `migracao` com o dono do schema, o `afterMigrate.sql` que cria e libera o usuário da aplicação, e o deploy que completa o `.env` dos servidores.
  - Limite por usuário nas gravações e no checkout (Bucket4j, depois da autenticação).
  - ADM → Usuários: bloquear, desbloquear e encerrar as sessões, com o access token já emitido recusado na hora (validador do JWT com a situação da conta em memória por 30 s).
  - Compra com confirmação (nenhum pedido ao abrir a página) e checkout só em HTTPS nos domínios do meio de pagamento.
  - Token dos links de e-mail fora da barra de endereço.
  - Dependabot e `npm audit` no pipeline.
  - O teste que varre todas as rotas da API e falha se uma rota nova nascer aberta.
- **Testes:** integração do usuário do banco (dados sim, estrutura não), da tela de usuários e da varredura das rotas; unitário do limite por usuário; Playwright do bloqueio e da compra confirmada.
- **Entrega:** a aplicação não consegue apagar tabelas; um aluno bloqueado sai na hora.

---

## Parte 13 — Privacidade

### Cap. 44 — LGPD: cópia dos dados, exclusão da conta e política de privacidade

**Fase:** 20 · **Prompt:** [f20-01](prompts/f20-01-lgpd.md)

- **Objetivo:** o aluno exerce os direitos do titular sozinho, pelo perfil, e a política de privacidade diz exatamente o que o sistema faz.
- **Conteúdo:**
  - O que é dado pessoal na plataforma, por módulo, e por que cada módulo responde pelas próprias tabelas (`DadosPessoais`).
  - Baixar meus dados: um JSON com a conta e uma seção por módulo, datas em ISO-8601.
  - Excluir a conta: o que é apagado (progresso, certificados, foto, sessões, segundo fator), o que é anonimizado (conta, pedidos) e o que fica pelo prazo legal (matrículas e pedidos, aceites, registros de acesso, auditoria, backups); a anonimização em vez de apagar a linha do usuário.
  - Retenção: a auditoria de login passa a ser limpa depois de 180 dias, como o log de acesso aos vídeos.
  - O modelo da política de privacidade: controlador e encarregado, dados e finalidades, bases legais, compartilhamento, prazos, direitos, segurança, cookies.
- **Segurança:** a exclusão exige a senha e a palavra de confirmação; o access token cai na hora; administrador não se exclui por aqui.
- **Testes:** integração da exportação e da exclusão (dados anonimizados, sessões encerradas, auditoria mantida, e-mail enviado) e da retenção; Playwright de baixar os dados e excluir a conta.
- **Entrega:** um aluno baixa os dados e exclui a conta pelo perfil; a política publicada bate com o sistema.

---

## Parte 14 — Site dinâmico

### Cap. 45 — Site em React com renderização no servidor

**Fase:** 21 · **Prompt:** [f21-01](prompts/f21-01-site-react-ssr.md)

- **Objetivo:** o site público mostra sempre o que está na API, com o mesmo HTML completo para os buscadores e uma interface moderna.
- **Conteúdo:**
  - Estático gerado de tempos em tempos × renderização no servidor a cada requisição: o que muda para o SEO (nada, o HTML chega pronto) e para o conteúdo (na hora, sem esperar a próxima geração).
  - React Router em modo framework: `loader` no servidor, `meta` com canônico, Open Graph e `noindex` na homologação, `headers` de cache, `ErrorBoundary` com 404 de verdade, rotas de recurso para `sitemap.xml` e `robots.txt`.
  - Streaming para o navegador e a página inteira de uma vez para os robôs (`isbot`); CSP com nonce por requisição.
  - Cache curto das leituras da API no servidor e o que muda no Caddy (o `site` atende na porta 3000).
  - Layout moderno com os tokens da `@celke/kole-react`, tema escuro e movimento reduzido; busca do catálogo no navegador depois da hidratação.
  - Cursos de exemplo pela API do ADM, com capa gerada no próprio script.
- **Testes:** funções de SEO (unidade), Playwright do site (meta tags, 404, busca, certificado) e Lighthouse no pipeline.
- **Entrega:** o site publica um curso assim que ele é publicado no ADM, com Lighthouse 100 nas quatro categorias.
`````

### 6. Os próprios prompts

Copie esta pasta de prompts, como está, para `docs/prompts-iniciais/` no repositório.

## Antes de executar

O repositório já existe no GitHub e está clonado (ou iniciado com `git init -b main` e o `origin`
configurado), vazio.

## Verificação

- `docs/configuracao.md` existe com o domínio, os subdomínios e o dono do repositório informados.
- `README.md`, `.gitignore`, `LICENSE.txt` (com o titular e o ano informados), `docs/plano-implementacao.md`, `docs/capitulos.md` e `docs/prompts-iniciais/` existem.
- `git status` mostra os arquivos como não rastreados (nenhum commit foi feito).

## Depois de executar

```bash
git add README.md .gitignore LICENSE.txt docs/
git commit -m "docs: criar estrutura inicial e plano de implementação"
git push -u origin main

git switch -c homolog
git push -u origin homolog

git switch -c feature/base-projeto
```
