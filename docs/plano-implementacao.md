# Plano de implementação — Plataforma de Cursos

Sistema do aluno (cursos, aulas em vídeo, fórum, certificado) com backend Java, frontend React
usando `@celke/ui`, tudo em Docker numa VPS da Hostinger. Este plano também é o roteiro do tutorial:
cada fase vira uma branch e um ou mais capítulos.

**Prompts:** cada etapa implementada tem um prompt em [`docs/prompts/`](prompts/README.md). Executados
na ordem do índice, eles recriam o projeto do zero.

**Ordem de trabalho:** primeiro todo o backend (com infraestrutura, CI e deploy), depois o frontend,
quando o `@celke/ui` já estiver publicado no npm.

Referências analisadas:

- `petardo-com/cesar-nicolau-szpak-055290` — base de Docker/Spring (com problemas de segurança a corrigir)
- `petardo-com/layout-proprio-biblioteca` — `@celke/ui`
- `petardo-clms/celke-administrativo-v8-clms` — regras da área do aluno
- `petardo-clms/celke-administrativo-v8-adm` — modelo de dados e gestão
- `petardo-clms/celke-administrativo-v8-import-video-vimeo` — parâmetros de compressão ffmpeg
- `petardo-clms/celke-administrativo-v8-site` — conteúdo do site (layout será refeito)

---

## 1. Decisões

| Tema | Decisão |
|---|---|
| Java | **Java 25 LTS** |
| Backend | **Spring Boot 4.1.x** (última correção disponível ao criar o projeto); organizado por funcionalidade; profiles `api` e `worker` no mesmo código. Atualizar para 4.2 numa branch `fix/` quando sair (previsto para novembro de 2026). O 3.5 da referência perdeu o suporte gratuito em 30/06/2026 |
| Banco | **PostgreSQL 18** + Flyway |
| Fila | Tabela no PostgreSQL com `SELECT ... FOR UPDATE SKIP LOCKED` (sem Redis/RabbitMQ) |
| S3 na VPS | **Garage** (`dxflrs/garage:v2.4.1`), modo `--single-node`. Escolhido por ser leve, ter configuração curta e suportar tudo que o projeto usa (veja abaixo). O MinIO comunitário parou de publicar imagens Docker e entrou em modo de manutenção |
| Acesso ao S3 | Somente AWS SDK v2 com path-style; trocar de servidor = trocar endpoint no `.env` |
| Vídeo | Upload direto por URL pré-assinada → ffmpeg → HLS 720p + 1080p → playlist reescrita pela API com segmentos assinados |
| E-mail | **SMTP da Iagente** |
| Frontend | Workspace npm com `site`, `aluno`, `adm`; `@celke/ui` instalado **do npm** (publicado antes da fase de front) |
| Hospedagem | Tudo em Docker na VPS (2 vCPU, 8 GB, 100 GB NVMe); Caddy com HTTPS automático serve os fronts e faz proxy de `/api` |
| Ambientes | Produção + homologação leve na mesma VPS |
| Branches | `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main` |
| CI | GitHub Actions: testes + e2e em todo PR; build e e2e refeitos na `main` |
| Deploy | SSH com chave presa a `deploy.sh`, imagem com tag = SHA, health check e rollback |

### Garage: o que o projeto usa e o que ele não tem

- ✅ URLs pré-assinadas, upload multipart, CORS por bucket, lifecycle com `Expiration` e `AbortIncompleteMultipartUpload` (limpa uploads abandonados)
- ❌ Versionamento, ACLs e bucket policies. Não fazem falta: os buckets são privados e o acesso é por chave com permissão por bucket.

### Domínios (somente subdomínios de `celke.uno`)

O domínio principal `celke.uno` já é usado por outras aplicações e não será tocado.

| Produção | Homologação | Serve |
|---|---|---|
| `cursos.celke.uno` | `homolog-cursos.celke.uno` | site |
| `aluno.celke.uno` | `homolog-aluno.celke.uno` | SPA do aluno + `/api` |
| `adm.celke.uno` | `homolog-adm.celke.uno` | SPA do ADM + `/api` (restrito por IP) |
| `media.celke.uno` | `homolog-media.celke.uno` | Garage (subdomínio próprio: a assinatura depende do host) |

> Nomes propostos — confirmar que nenhum desses subdomínios já está em uso antes da etapa 1.8 (VPS).

Cuidados por dividir o domínio com outras aplicações:

- Cookies **sem** atributo `Domain` (cookie só do host). Com `Domain=celke.uno`, todas as outras aplicações dos subdomínios receberiam o refresh token.
- A CSP e o CORS do Garage listam os subdomínios exatos, nunca `*.celke.uno`.
- HSTS sem `includeSubDomains`, a não ser que todas as aplicações de `celke.uno` já usem HTTPS.

### SMTP Iagente

- Host, porta e credenciais vêm do painel da Iagente (porta 587 com STARTTLS é o padrão esperado; confirmar no painel).
- Remetente: endereço de `celke.uno` com SPF e DKIM configurados na Iagente.
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
├── web/                         (a partir da Fase 11)
│   ├── package.json             workspaces
│   ├── site/
│   ├── aluno/
│   └── adm/
├── infra/
│   ├── compose.yml              dev (inclui Mailpit)
│   ├── compose.prod.yml         VPS (base)
│   ├── compose.homolog.yml      sobreposição: limites menores, worker sob demanda
│   ├── compose.e2e.yml          sobreposição: HTTP, *.localhost, seed e2e, Mailpit
│   ├── garage/garage.toml
│   ├── caddy/
│   │   ├── Dockerfile           Caddy (+ build dos fronts a partir da Fase 11)
│   │   └── Caddyfile
│   └── scripts/                 deploy.sh, backup-db.sh, backup-s3.sh, restore.sh, garage-init.sh
├── docs/
│   ├── plano-implementacao.md
│   └── prompts/                 prompts para recriar o projeto em sequência
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

- [x] Criar repositório `celkecursos/plataforma-cursos` no GitHub
- [ ] Primeiro commit na `main` (README, `.gitignore`, `docs/`)
- [ ] Criar `homolog` a partir da `main`
- [ ] Regras de proteção em `main` e `homolog`: só PR, checks obrigatórios (ativar quando o pipeline existir)
- [ ] Environments `homolog` e `production` com seus secrets
- [ ] Confirmar os subdomínios livres em `celke.uno`
- [ ] Obter host, porta e credenciais SMTP no painel da Iagente; configurar SPF/DKIM

---

## Bloco A — Backend

### Fase 1 — `feature/base-projeto`

Objetivo: API rodando local e na VPS (homologação e produção), com auth completa, CI, e2e de API e
deploy automático. Depois desta fase, toda funcionalidade nova segue o fluxo normal.

**1.1 Esqueleto do backend e padrão de testes** — capítulos 2 e 5

- [x] `CLAUDE.md` da raiz (regras gerais) e `backend/CLAUDE.md` (código e testes do projeto Java)
- [x] Skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e` em `backend/.claude/skills/`, cada uma com o padrão do nível (`padrao.md`)
- [x] Projeto gerado no Spring Initializr: Spring Boot 4.1.1, Java 25, `br.com.celke.cursos`; dependências: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc, testcontainers. As demais (AWS SDK, mail, Thymeleaf) entram na etapa que as usa
- [x] `application.yaml` com credenciais do banco sem valor padrão, `open-in-view` desligado, `ddl-auto: validate`, threads virtuais, shutdown gracioso, cabeçalhos do proxy, sem stack trace nas respostas; `application-worker.yaml` sem servidor HTTP
- [x] Sem usuário embutido do Spring Security (`UserDetailsServiceAutoConfiguration` excluída)
- [x] `SecurityConfig`: stateless, sem CSRF/sessão/formulário/HTTP Basic, 401 para não autenticado; públicos só `GET` de health/probes e da documentação
- [x] Actuator: só `health`, com probes de liveness/readiness e sem detalhes
- [x] Swagger ligado por `DOCS_ENABLED=true` (dev e homologação), desligado por padrão
- [x] Surefire para `*Test` (com `-Dskip.unit=true` para pular), Failsafe para `*IT`, JaCoCo somando os dois com mínimo de 80% de linhas, Spotless (palantir-java-format) verificado no `verify`
- [x] `support/Containers` (PostgreSQL 18 como bean com `@ServiceConnection`) e `support/IntegrationTest` (`MockMvcTester`, `stamp()`)
- [x] `SegurancaPadraoIT`: health e probes públicos com `UP` e sem detalhes; demais rotas e métodos exigem autenticação; documentação desligada
- [x] Dockerfile multi-stage: build com JDK 25, jar em camadas, execução com JRE 25 e usuário sem root; targets `api` e `worker` (com ffmpeg)

**1.2 Ambiente local** — capítulo 2

- [ ] `infra/compose.yml`: PostgreSQL 18, Garage, API, worker, Mailpit; versões fixas
- [ ] Variáveis obrigatórias com `${VARIAVEL:?mensagem}` no compose (mensagem clara quando faltar)
- [ ] `garage.toml` com `replication_factor = 1`, `db_engine = "sqlite"`, volumes para `metadata_dir` e `data_dir`, segredos (`rpc_secret`, `admin_token`) vindos do `.env`
- [ ] `garage-init.sh`: buckets `uploads` e `videos`, chave da aplicação com leitura/escrita só nesses buckets, CORS e lifecycle (expira `uploads/` abandonados)
- [ ] Healthchecks + `depends_on: condition: service_healthy`
- [ ] `.env.example` completo; `.env` no `.gitignore`
- [ ] Portas ligadas apenas em `127.0.0.1`

**1.3 Padrões compartilhados** — capítulo 4

- [ ] `ProblemDetail` (RFC 9457) com handler global, sem stack trace na resposta; 401/403 da segurança também em `ProblemDetail`
- [ ] DTOs como `record`, paginação padrão
- [ ] Logs JSON com id de correlação
- [ ] `StorageService` (AWS SDK v2, path-style): presign GET/PUT, multipart, delete
- [ ] Fila: tabela `jobs` + `FilaService` (enfileirar, reservar com `SKIP LOCKED`, concluir, falhar com backoff)
- [ ] `EmailService` com templates (Thymeleaf), envio assíncrono pela fila, SMTP da Iagente
- [ ] `support/Containers` com Garage e Mailpit; `support/Mailpit`
- [ ] Testes de `StorageService`, `FilaService` (dois consumidores nunca pegam o mesmo job) e `EmailService`

**1.4 Usuários e autenticação** — capítulos 6 e 7

- [ ] Migrations `usuarios`, `papeis`, `refresh_tokens`, `tokens_uso_unico`
- [ ] Hash de senha Argon2 (aceitar também `$2y$` do Laravel, para a migração do legado)
- [ ] Comando para criar admin: `docker compose run --rm api criar-admin <email>` com senha digitada na hora
- [ ] Login: access token de 10 min no corpo; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, **sem `Domain`**
- [ ] Refresh com rotação; reuso de token revoga a família
- [ ] Logout e "sair de todos os dispositivos"
- [ ] Seed do profile `test` (admin, aluno); `support/UsuariosSeed`; `login()` e `bearer()` no `IntegrationTest`

**1.5 Autorização e proteções** — capítulos 8 e 10

- [ ] `SecurityFilterChain` nega tudo por padrão; rotas públicas declaradas com método HTTP
- [ ] Papéis do token + `@PreAuthorize`
- [ ] Rate limit (Bucket4j) por IP e por e-mail em login, cadastro e recuperação; bloqueio temporário após falhas seguidas
- [ ] Endpoint de refresh: verificação de `Origin` (CSRF)
- [ ] Auditoria de login (sucesso/falha, IP, user-agent)

**1.6 Cadastro e recuperação de senha** — capítulo 9

- [ ] Cadastro com confirmação de e-mail
- [ ] Recuperação com token de uso único e expiração; resposta igual para e-mail existente ou não
- [ ] E-mails pela Iagente; verificados no Mailpit nos testes

**1.7 Testes de autorização e e2e de API** — capítulo novo antes do 31

- [ ] Matriz parametrizada rota × papel → status esperado
- [ ] Testes de token adulterado, expirado, `alg` trocado, refresh reutilizado, rate limit 429
- [ ] `compose.e2e.yml`: HTTP, `*.localhost`, seed do profile `e2e` (admin, aluno matriculado, aluno sem matrícula)
- [ ] Perfil Maven `e2e` no backend (`src/e2e/java`, REST Assured, Awaitility) com `support/` conforme o padrão da skill `criar-teste-e2e`
- [ ] Jornadas `CadastroE2E` (cadastro → e-mail no Mailpit → confirmação → login) e `SessaoE2E` (login → refresh → logout pelo Caddy, cookie sem `Domain`)
- [ ] `pipeline.yml`: testes → build das imagens (cache `type=gha`) → e2e → artefatos em caso de falha (relatórios + `docker compose logs`)

**1.8 VPS** — capítulos 31 e 32

- [ ] Usuário `deploy` sem sudo, SSH só por chave, root desativado, fail2ban, `ufw` (22/80/443), swap de 2 GB
- [ ] DNS dos subdomínios de produção e homologação apontando para a VPS
- [ ] `/opt/plataforma-cursos/{producao,homolog,caddy}` com `.env` próprio (permissão 600) e segredos diferentes
- [ ] Caddy único nas portas 80/443, ligado às redes dos dois ambientes
  - homologação com senha ou IP e `X-Robots-Tag: noindex`; `adm.` restrito por IP
  - por enquanto `aluno.` e `adm.` só fazem proxy de `/api` (os fronts entram na Fase 11)
  - headers: HSTS (sem `includeSubDomains`), `X-Content-Type-Options`, `Referrer-Policy`; `lb_try_duration` no proxy da API
- [ ] Limites de recursos:

| Serviço | Produção | Homologação |
|---|---|---|
| API | 1 GB, `-Xmx512m` | 768 MB, `-Xmx384m`, `cpus: 0.5` |
| Worker + ffmpeg | 2 GB, `cpus: 1.5` | desligado; `--profile worker` sob demanda |
| PostgreSQL | 1 GB | 512 MB |
| Garage | 768 MB | 512 MB |
| Caddy (compartilhado) | 128 MB | — |

**1.9 Deploy automático** — capítulos 32.1 e 34

- [ ] `docker login ghcr.io` na VPS com token só de leitura
- [ ] `deploy.sh <ambiente> <sha>`: valida argumentos, backup do banco, `pull`, `up -d --wait`, grava a tag atual, rollback se falhar
- [ ] `authorized_keys` com `command="/opt/plataforma-cursos/deploy.sh",no-pty,no-port-forwarding`
- [ ] Jobs de deploy no `pipeline.yml`: push em `homolog` → homologação; push em `main` → produção
- [ ] Primeiro deploy nos dois ambientes; criar o admin em cada um

**Pronto quando:** cadastro, login e logout funcionando pela API em `homolog-aluno.celke.uno/api` e
`aluno.celke.uno/api`, e-mail chegando pela Iagente, pipeline completo verde e um rollback testado de propósito.

---

### Fase 2 — `feature/cursos`

Objetivo: API de gestão de cursos, turmas, módulos e aulas. Capítulos 11 e 12.

- [ ] Migrations: `cursos`, `turmas`, `modulos`, `aulas` com status como enum, ordem, slug, aula gratuita
- [ ] CRUD em `/api/adm/**` com validação
- [ ] Reordenação de módulos e aulas
- [ ] Capa do curso: upload pré-assinado de imagem, redimensionamento, sem EXIF
- [ ] Endpoints públicos de catálogo (para o site): cursos publicados, por slug
- [ ] Testes: validações, ordenação, autorização
- [ ] e2e de API: admin cria curso → turma → módulo → aula; catálogo público lista o curso

---

### Fase 3 — `feature/matricula-acesso`

Objetivo: matrícula e uma política única de acesso. Capítulos 13 e 14.

- [ ] Migration `matriculas` (início, fim, status enum, forma de pagamento, transação)
- [ ] Endpoints do ADM: matricular, alterar período, cancelar
- [ ] `AcessoAulaPolicy`: status ativo + dentro do período, ou aula gratuita; usada por aula, vídeo, arquivo, fórum, progresso e certificado
- [ ] Endpoints do aluno: meus cursos (ativos, arquivados, expirados), turma com módulos e aulas, arquivar/desarquivar
- [ ] Testes: todas as combinações status × datas; IDOR (aluno A em dados do aluno B)
- [ ] e2e de API: aluno matriculado vê a turma; sem matrícula ou com matrícula vencida recebe 403

---

### Fase 4 — `feature/upload-video`

Objetivo: o vídeo enviado vira HLS comprimido. Capítulos 15 a 18.

- [ ] Migration `videos` (status, duração, tamanho original e final, chaves S3, erro)
- [ ] Presign de upload multipart para `uploads/`; validação de tipo e tamanho antes de assinar
- [ ] Confirmação do upload: API confere tamanho e assinatura real do arquivo; enfileira job
- [ ] Worker: `ffprobe` → ffmpeg via `ProcessBuilder` (lista de argumentos)
  - H.264 CRF 23–26, `maxrate`, AAC 128k, segmentos de 6 s, 720p + 1080p (sem aumentar a resolução de origem)
  - thumbnail; envio para `videos/`; apaga o original após sucesso
- [ ] Job volta para a fila se o worker parar no meio (`stop_grace_period` + lease com expiração)
- [ ] Endpoints do ADM: status de processamento, reprocessar, tamanho antes/depois
- [ ] Medir tempo de processamento por minuto de vídeo na VPS e registrar no tutorial
- [ ] Testes: transcodificação de vídeo de 3 s; arquivo com extensão falsa rejeitado
- [ ] e2e de API: upload multipart de vídeo curto → status "pronto"

---

### Fase 5 — `feature/entrega-video`

Objetivo: vídeo protegido e progresso salvo. Capítulos 19 a 21.

- [ ] `GET /api/aulas/{id}/video.m3u8` e playlists das variantes: verifica a policy e reescreve com segmentos pré-assinados (validade = duração + margem)
- [ ] Opcional (cap. 20): HLS com AES-128 e chave por endpoint autenticado
- [ ] Progresso: posição atual, vista/revisar, percentual da turma
- [ ] Log de acesso com retenção definida (LGPD)
- [ ] Testes: 403 sem matrícula no `.m3u8` e na chave; URL de segmento expira
- [ ] e2e de API: playlist assinada → segmento baixa com 200 → após expirar, 403; progresso refletido na turma

---

### Fase 6 — `feature/arquivos-apoio`

Capítulo 22.

- [ ] Arquivos por aula (código-fonte) e por módulo; upload pelo ADM
- [ ] Download por URL pré-assinada curta, sempre pela policy
- [ ] Testes e e2e de download permitido e bloqueado

---

### Fase 7 — `feature/forum`

Capítulo 23.

- [ ] Tópicos por aula e por turma; perguntas, respostas e anexos validados
- [ ] Markdown convertido para HTML filtrado (contra XSS)
- [ ] Moderação no ADM: ocultar, fixar, responder como instrutor
- [ ] Rate limit em criação de pergunta e resposta
- [ ] Testes: XSS, IDOR em anexo, acesso de aluno sem matrícula
- [ ] e2e de API: aluno pergunta, admin responde, aluno vê a resposta

---

### Fase 8 — `feature/certificado`

Capítulo 24.

- [ ] Regra de conclusão (percentual de aulas vistas) configurável por turma
- [ ] Dados do certificado (nome completo) confirmados pelo aluno
- [ ] PDF com openhtmltopdf; código de validação público
- [ ] Endpoint público de validação (usado pelo site)
- [ ] e2e de API: aluno conclui → gera certificado → código valida

---

### Fase 9 — `feature/perfil-termos`

Capítulo 25.

- [ ] Perfil: dados, troca de senha (exige senha atual), foto redimensionada sem EXIF
- [ ] Termos versionados; aceite obrigatório no primeiro acesso e a cada nova versão (API responde com indicação de termo pendente)
- [ ] Endpoints do ADM: nova versão de termo e de política de privacidade
- [ ] e2e de API: novo termo publicado → rotas do aluno bloqueadas até o aceite

---

### Fase 10 — `feature/operacao`

Capítulos 35 a 37. Fica antes do front porque precisa estar pronta antes de entrarem dados reais em produção.

- [ ] Backup diário do PostgreSQL e do Garage para fora da VPS (restic); restauração testada e documentada
- [ ] Monitor de uptime externo; alerta de disco e de falhas no worker
- [ ] Rotação de logs do Docker; limpeza de uploads órfãos e dos dados da homologação
- [ ] Conta "horas de vídeo que cabem no disco" e plano de saída para S3 externo (R2/B2)
- [ ] Revisão OWASP ASVS nível 1 da API; varredura com OWASP ZAP na homologação; Trivy no pipeline

---

## Bloco B — Frontend

**Pré-requisito:** `@celke/ui` publicado no npm, já com os componentes da área do aluno:
`PlayerVideo` (hls.js, `xhrSetup` com token, retomar posição, enviar progresso), `CardCurso`,
`ListaAulas` e componentes de fórum. Esses componentes são feitos no repositório da biblioteca.

### Fase 11 — `feature/web-base`

Capítulo 26.

- [ ] Workspace `web/` com `site`, `aluno`, `adm` (Vite + React + TypeScript, Node LTS)
- [ ] `@celke/ui` do npm com versão fixa
- [ ] Cliente HTTP: token em memória, refresh silencioso, fila de requisições durante o refresh
- [ ] Rotas protegidas por papel; tratamento de termo pendente
- [ ] Aluno: login, cadastro, confirmação de e-mail, recuperar senha
- [ ] ADM: login, layout `Estrutura`
- [ ] Proxy do Vite `/api → localhost:8080` (mesma origem também no dev)
- [ ] Imagem `web`: build dos fronts + Caddy servindo `/srv/site`, `/srv/aluno`, `/srv/adm`
- [ ] Caddy: CSP (`connect-src 'self' https://media.celke.uno`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html`; desligar o Swagger da homologação se não for mais necessário
- [ ] Vitest + Testing Library; Playwright no navegador entra no `pipeline.yml`
- [ ] e2e: login aluno, login admin, aluno barrado no ADM, logout

### Fase 12 — `feature/web-adm`

Capítulo 29.

- [ ] Cursos, turmas, módulos e aulas (`Tabela`, `Form`, `Filtros`, `Paginacao`), arrastar para reordenar
- [ ] Upload de vídeo multipart com progresso, status de processamento, reprocessar
- [ ] Arquivos de apoio, matrículas, moderação do fórum, termos
- [ ] e2e: admin cria curso completo e envia vídeo curto até "pronto"

### Fase 13 — `feature/web-aluno`

Capítulos 27 e 28.

- [ ] Dashboard (`CardCurso`), cursos arquivados e expirados
- [ ] Turma com módulos, aulas e progresso
- [ ] Aula: `PlayerVideo`, `ListaAulas`, anterior/próxima, vista/revisar, arquivos, fórum
- [ ] Certificados e perfil (dados, senha, foto)
- [ ] e2e: login → turma → aula → vídeo toca → marca como vista → progresso atualizado

### Fase 14 — `feature/site`

Capítulo 30.

- [ ] Layout novo com os tokens do `@celke/ui`
- [ ] Pré-renderização estática (uma página HTML por rota) em `cursos.celke.uno`
- [ ] Home, catálogo, página do curso (dados da API no build), validar certificado, termos, privacidade
- [ ] SEO: meta tags, Open Graph, sitemap, `robots.txt`
- [ ] Meta de Lighthouse definida e verificada no CI

---

### Fase 15 (opcional) — `feature/migracao-legado`

Capítulo 38.

- [ ] ETL do MySQL do CLMS: usuários, cursos, turmas, módulos, aulas, matrículas, progresso
- [ ] Mapear status numéricos do legado (`3, 4, 13, 14, 15`...) para os enums novos
- [ ] Senhas `$2y$` do Laravel aceitas no primeiro login e re-hash
- [ ] Reprocessar vídeos existentes no S3 para HLS

---

## 5. Mapa fase → capítulos do tutorial

| Fase | Branch | Capítulos |
|---|---|---|
| 0 | — | 1 |
| 1 | `feature/base-projeto` | 2–10, e2e/pipeline, 31, 32, 32.1, 34 |
| 2 | `feature/cursos` | 11, 12 |
| 3 | `feature/matricula-acesso` | 13, 14 |
| 4 | `feature/upload-video` | 15–18 |
| 5 | `feature/entrega-video` | 19–21 |
| 6 | `feature/arquivos-apoio` | 22 |
| 7 | `feature/forum` | 23 |
| 8 | `feature/certificado` | 24 |
| 9 | `feature/perfil-termos` | 25 |
| 10 | `feature/operacao` | 35–37 |
| 11 | `feature/web-base` | 26 |
| 12 | `feature/web-adm` | 29 |
| 13 | `feature/web-aluno` | 27, 28 |
| 14 | `feature/site` | 30 |
| 15 | `feature/migracao-legado` | 38 |
