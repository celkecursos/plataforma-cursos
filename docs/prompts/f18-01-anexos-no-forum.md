# Imagens no fórum de dúvidas

**Fase:** 18
**Branch:** `feature/forum-anexos`

## Prompt

Vamos completar o fórum de dúvidas: o backend recebe imagens nas perguntas e respostas desde a Fase 7,
mas a área do aluno não tinha onde anexá-las e nem o aluno nem a moderação viam as imagens publicadas.
Siga os padrões do `web/` (fases 11 a 14). Não faça commit.

Como a API trata as imagens (não muda nesta fase):

- `POST /api/aluno/forum/anexos/envio` `{ contentType, tamanho }` devolve `{ chave, url, contentType }`:
  o navegador faz o PUT da imagem direto no armazenamento, com aquele `Content-Type`.
- A pergunta (`POST /api/aluno/turmas/{id}/forum`) e a resposta (`POST /api/aluno/forum/topicos/{id}/respostas`)
  levam `anexos` com as chaves. Ao publicar, a API confere que cada chave é do próprio aluno, regrava a
  imagem em JPEG sem metadados (até 1600 × 1600) e **apaga o envio original**, mesmo se a publicação
  falhar depois. JPEG ou PNG, até 5 MB, até 3 por mensagem.
- Tópicos e respostas voltam com `anexos: [{ id, url }]` (URL temporária).
- A resposta do instrutor no ADM não leva imagens.

O que esta etapa entrega:

- **`compartilhado/src/ui/imagens.ts`**: `triarImagens` confere tipo, tamanho e quantidade em todos os
  caminhos (o `accept` do seletor não filtra o que chega por arrastar ou colar) e devolve uma mensagem
  por motivo; `imagensColadas` tira as imagens de um Ctrl+V; `LIMITES_FORUM` com as regras da API.
- **`CampoImagens`**: botão "Anexar imagem", arrastar e soltar, colar um print (na área ou, pela `ref`,
  no texto da mensagem), prévia com remover, progresso de cada envio e o botão desabilitado no limite.
  Cada escolha tem um `id` próprio (duas imagens com o mesmo nome continuam distintas) e as prévias são
  liberadas ao remover e ao desmontar.
- **`GaleriaImagens`**: miniaturas das imagens publicadas; clicar abre o `DialogoArquivo` da
  `@celke/kole-react` para folhear em tela cheia.
- **Envio só ao publicar** (`useImagensMensagem` e `enviarImagensForum`): uma imagem por vez, direto ao
  armazenamento, e então a mensagem com as chaves. Como a API apaga o envio ao processá-lo, enviar na
  hora de publicar garante que uma nova tentativa nunca use uma chave que já sumiu.
- Área do aluno: imagens na nova pergunta e na resposta (colar no texto também anexa); a pergunta e
  as respostas mostram as imagens. ADM: a moderação do fórum mostra as imagens.
- Testes: unitários da triagem, do colar, do campo (escolher, arrastar um PDF, colar até o limite,
  remover) e do envio (ordem, progresso, falha no meio); Playwright com caminho feliz (pergunta com duas
  imagens, resposta com um print colado, imagens carregando e ampliando, moderação vendo as imagens) e
  infeliz (GIF recusado, quarta imagem recusada, remover, imagem falsa recusada pela API mantendo o
  texto e publicando sem ela).

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos alterados (substitua o conteúdo inteiro)

`docs/capitulos.md`:

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
  - Domínios, todos subdomínios de `celke.uno` (o domínio principal pertence a outras aplicações): `cursos.` (site), `aluno.`, `adm.` e `media.`, e os equivalentes `homolog-*`.
  - Fluxo do vídeo: upload direto ao Garage → worker com ffmpeg → HLS → playlist assinada pela API.
  - Modelo de dados enxuto: usuário, papel, curso, turma, módulo, aula, matrícula, vídeo, progresso, fórum, certificado, termo.
- **Entrega:** diagrama da arquitetura e lista de funcionalidades.

### Cap. 2 — Repositório, branches e fluxo de trabalho

**Fase:** 0 · **Prompts:** [f00-01](prompts/f00-01-estrutura-inicial.md), [f00-02](prompts/f00-02-orientacoes-para-ia.md)

- **Objetivo:** deixar o repositório pronto para o fluxo de PRs com testes e deploy.
- **Conteúdo:**
  - Repositório `celkecursos/plataforma-cursos` com README e `.gitignore` (segredos, `target/`, `node_modules/`).
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
  - Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, **sem `Domain`**, para não ser enviado às outras aplicações de `celke.uno`.
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
  - Proteção CSRF no refresh e no logout: `Origin` e `Sec-Fetch-Site` conferidos, porque outras aplicações em `*.celke.uno` são o mesmo site para o navegador e o `SameSite=Strict` não as barra.
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
  - E-mails pela fila, montados com Thymeleaf e enviados pelo worker: Mailpit no desenvolvimento, Iagente nos servidores (remetente de `celke.uno` com SPF e DKIM).
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
- **Entrega:** `homolog-aluno.celke.uno/api` e `aluno.celke.uno/api` respondendo com HTTPS.

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
  - O ADM pede URLs pré-assinadas de upload multipart, e o navegador envia direto para `media.celke.uno` (prefixo `uploads/` do bucket `midia`).
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
- **Segurança:** CSP (`connect-src 'self' https://media.celke.uno`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html`.
- **Testes:** Vitest + Testing Library; Playwright no pipeline (login do aluno e do admin, aluno barrado no ADM, logout).
- **Entrega:** login funcionando em `aluno.celke.uno` e `adm.celke.uno`.

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
  - Pré-renderização estática (cada página vira um HTML) em `cursos.celke.uno`, gerada pelo container `site` a partir da API do próprio ambiente ao subir e a cada 5 minutos.
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
`````

`docs/plano-implementacao.md`:

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
│   ├── site/                    gerador do site público (Node, sem dependências)
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

- [x] Criar repositório `celkecursos/plataforma-cursos` no GitHub
- [x] Primeiro commit na `main` (README, `.gitignore`, `docs/`)
- [x] Criar `homolog` a partir da `main`
- [x] Orientações para agentes de IA em `.claude/`: rules por caminho (segurança, fronts, testes, prompts),
  commits em Conventional Commits sem coautoria de IA (`/gc`, `/pc`), `/nova-branch`, `/abrir-pr`,
  `/revisar-dod` e o pipeline SDD com portões humanos
- [ ] Regras de proteção em `main` e `homolog`: só PR, checks obrigatórios (ativar quando o pipeline existir)
- [ ] Environments `homolog` e `production` com seus secrets
- [ ] Confirmar os subdomínios livres em `celke.uno`
- [ ] Obter host, porta e credenciais SMTP no painel da Iagente; configurar SPF/DKIM

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
- [x] Refresh e logout: verificação de `Origin` e `Sec-Fetch-Site` (CSRF), porque outras aplicações em `*.celke.uno` contam como o mesmo site; origens em `AUTH_ORIGENS`
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
- [x] `infra/scripts/gerar-env-servidor.sh`: `.env` de cada ambiente com segredos aleatórios e diferentes, hosts de `celke.uno`, SMTP para preencher
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

**Pronto quando:** cadastro, login e logout funcionando pela API em `homolog-aluno.celke.uno/api` e
`aluno.celke.uno/api`, e-mail chegando pela Iagente, pipeline completo verde e um rollback testado de propósito.

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
- [x] Pré-renderização estática (uma página HTML por rota) em `cursos.celke.uno`: o container `site` gera ao subir e a cada `SITE_INTERVALO` (padrão 5 min) a partir da API do próprio ambiente, com troca atômica da versão; a mesma imagem serve homologação e produção
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
`````

`web/adm/src/paginas/turma/ForumAba.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CampoTextarea,
  Carregando,
  Dialogo,
  Status,
  Tabela,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { GaleriaImagens } from '@compartilhado/ui/GaleriaImagens.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { Topico, TopicoResumo, Turma } from '../../tipos.ts';

const TAMANHO = 20;

/** Moderação do fórum da turma: o admin vê também o que está oculto. */
export function ForumAba({ turma }: { turma: Turma }) {
  const [pagina, setPagina] = useState(1);
  const [aberto, setAberto] = useState<number | null>(null);
  const topicos = useCarregar(
    () =>
      api.get<Pagina<TopicoResumo>>(
        `/api/adm/turmas/${turma.id}/forum?page=${pagina - 1}&size=${TAMANHO}`,
      ),
    [turma.id, pagina],
  );

  return (
    <div className="pc-pilha">
      <Tabela<TopicoResumo>
        rotulo="Tópicos do fórum"
        colunas={[
          {
            campo: 'titulo',
            rotulo: 'Tópico',
            destaque: true,
            render: (t) => (
              <span className="pc-linha">
                {t.fixado && <Badge cor="pri" icone="pin" rotulo="Fixado" aparencia="suave" />}
                {t.titulo}
              </span>
            ),
          },
          { campo: 'autorNome', rotulo: 'Autor' },
          { campo: 'respostas', rotulo: 'Respostas', alinhamento: 'direita' },
          {
            campo: 'respondidoInstrutor',
            rotulo: 'Situação',
            render: (t) =>
              t.oculto ? (
                <Status rotulo="Oculto" severidade="neutral" />
              ) : t.respondidoInstrutor ? (
                <Status rotulo="Respondido" severidade="ok" />
              ) : (
                <Status rotulo="Aguardando resposta" severidade="warn" />
              ),
          },
          { campo: 'criadoEm', rotulo: 'Criado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={topicos.dados?.itens ?? []}
        carregando={topicos.carregando && !topicos.dados}
        atualizando={topicos.carregando && !!topicos.dados}
        erro={topicos.erro}
        aoRecarregar={topicos.recarregar}
        vazioTitulo="Nenhum tópico"
        vazioDescricao="As dúvidas dos alunos aparecem aqui."
        vazioIcone="message-square"
        aoClicarLinha={(t) => setAberto(t.id)}
        paginacao={{
          total: topicos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto != null && (
        <PainelTopico
          topicoId={aberto}
          aoFechar={() => setAberto(null)}
          aoMudar={topicos.recarregar}
        />
      )}
    </div>
  );
}

function PainelTopico({
  topicoId,
  aoFechar,
  aoMudar,
}: {
  topicoId: number;
  aoFechar: () => void;
  aoMudar: () => void;
}) {
  const toast = useToast();
  const { executar, executando } = useAcao();
  const [resposta, setResposta] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const topico = useCarregar(
    () => api.get<Topico>(`/api/adm/forum/topicos/${topicoId}`),
    [topicoId],
  );

  const alterar = async (acao: () => Promise<Topico>, sucesso: string) => {
    const novo = await executar(acao, sucesso);
    if (novo) {
      topico.definir(novo);
      aoMudar();
    }
  };

  const responder = async () => {
    if (!resposta.trim()) {
      setErro('Escreva a resposta.');
      return;
    }
    setEnviando(true);
    setErro(undefined);
    try {
      topico.definir(
        await api.post<Topico>(`/api/adm/forum/topicos/${topicoId}/respostas`, {
          corpo: resposta.trim(),
        }),
      );
      setResposta('');
      toast.ok('Resposta publicada');
      aoMudar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('corpo')) || mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  const t = topico.dados;
  return (
    <Dialogo
      aberto
      aparencia="lateral"
      tamanho="grande"
      titulo={t?.titulo ?? 'Tópico'}
      subtitulo={t ? `${t.autorNome} · ${dataHora(t.criadoEm)}` : undefined}
      aoFechar={aoFechar}
    >
      {topico.erro && <Alerta severidade="err" mensagem={topico.erro} />}
      {!t && !topico.erro && <Carregando mensagem="Carregando o tópico..." />}
      {t && (
        <div className="pc-pilha">
          <div className="pc-linha">
            {t.oculto && <Status rotulo="Oculto para os alunos" severidade="neutral" />}
            <Botao
              rotulo={t.fixado ? 'Desafixar' : 'Fixar'}
              icone={t.fixado ? 'pin-off' : 'pin'}
              aparencia="contorno"
              tamanho="pequeno"
              disabled={executando}
              onClick={() =>
                void alterar(
                  () => api.patch<Topico>(`/api/adm/forum/topicos/${t.id}`, { fixado: !t.fixado }),
                  t.fixado ? 'Tópico desafixado' : 'Tópico fixado',
                )
              }
            />
            <Botao
              rotulo={t.oculto ? 'Mostrar tópico' : 'Ocultar tópico'}
              icone={t.oculto ? 'eye' : 'eye-off'}
              aparencia="contorno"
              tamanho="pequeno"
              severidade={t.oculto ? 'pri' : 'err'}
              disabled={executando}
              onClick={() =>
                void alterar(
                  () => api.patch<Topico>(`/api/adm/forum/topicos/${t.id}`, { oculto: !t.oculto }),
                  t.oculto ? 'Tópico visível de novo' : 'Tópico ocultado',
                )
              }
            />
          </div>
          {/* HTML já filtrado pela API (Markdown → HTML seguro) */}
          <div className="pc-texto-longo" dangerouslySetInnerHTML={{ __html: t.corpoHtml }} />
          <GaleriaImagens imagens={t.anexos} de={t.autorNome} />

          <h3 className="pc-subtitulo">Respostas ({t.respostas.length})</h3>
          {t.respostas.map((r) => (
            <article
              key={r.id}
              className={`pc-resposta${r.oculto ? ' pc-resposta-oculta' : ''}`}
              aria-label={`Resposta de ${r.autorNome}`}
            >
              <header className="pc-linha">
                <strong>{r.autorNome}</strong>
                {r.instrutor && <Badge cor="pri" rotulo="Instrutor" aparencia="suave" />}
                {r.oculto && <Status rotulo="Oculta" severidade="neutral" />}
                <span className="pc-dica">{dataHora(r.criadoEm)}</span>
                <Botao
                  rotulo={r.oculto ? 'Mostrar' : 'Ocultar'}
                  aparencia="texto"
                  tamanho="pequeno"
                  disabled={executando}
                  onClick={() =>
                    void alterar(
                      () =>
                        api.patch<Topico>(`/api/adm/forum/respostas/${r.id}`, {
                          oculto: !r.oculto,
                        }),
                      r.oculto ? 'Resposta visível de novo' : 'Resposta ocultada',
                    )
                  }
                />
              </header>
              <div dangerouslySetInnerHTML={{ __html: r.corpoHtml }} />
              <GaleriaImagens imagens={r.anexos} de={r.autorNome} />
            </article>
          ))}

          <form
            noValidate
            className="pc-pilha"
            onSubmit={(evento) => {
              evento.preventDefault();
              void responder();
            }}
          >
            <CampoTextarea
              rotulo="Responder como instrutor"
              valor={resposta}
              aoMudar={setResposta}
              erro={erro}
              rows={5}
              maxLength={10_000}
              dica="Aceita Markdown."
            />
            <div>
              <Botao type="submit" rotulo="Publicar resposta" icone="send" carregando={enviando} />
            </div>
          </form>
        </div>
      )}
    </Dialogo>
  );
}
`````

`web/aluno/src/paginas/forum/ForumLista.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CampoTextarea,
  CampoTexto,
  Card,
  Carregando,
  ItemLista,
  Lista,
  Paginacao,
  Status,
  Vazio,
} from '@celke/kole-react';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CampoImagens, type CampoImagensRef } from '@compartilhado/ui/CampoImagens.tsx';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { LIMITES_FORUM } from '@compartilhado/ui/imagens.ts';
import { erroObrigatorio, semErros } from '@compartilhado/ui/validacao.ts';
import { caminhoTopico } from '../../caminhos.ts';
import type { Topico, TopicoResumo } from '../../tipos.ts';
import { useImagensMensagem } from './useImagensMensagem.ts';

const TAMANHO = 10;

interface Props {
  turmaId: number;
  /** Na página da aula: só os tópicos dela, e a pergunta nova fica ligada a ela. */
  aulaId?: number;
}

/** Tópicos do fórum (fixados primeiro) e o formulário para perguntar. */
export function ForumLista({ turmaId, aulaId }: Props) {
  const navigate = useNavigate();
  const [pagina, setPagina] = useState(1);
  const [perguntando, setPerguntando] = useState(false);
  const topicos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (aulaId) filtro.set('aulaId', String(aulaId));
    return api.get<Pagina<TopicoResumo>>(`/api/aluno/turmas/${turmaId}/forum?${filtro}`);
  }, [turmaId, aulaId, pagina]);

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        {!perguntando && (
          <Botao
            rotulo="Fazer uma pergunta"
            icone="message-square"
            onClick={() => setPerguntando(true)}
          />
        )}
      </div>
      {perguntando && (
        <NovaPergunta
          turmaId={turmaId}
          aulaId={aulaId}
          aoCancelar={() => setPerguntando(false)}
          aoPublicar={(topico) => navigate(caminhoTopico(turmaId, topico.id))}
        />
      )}
      {topicos.erro && <Alerta severidade="err" mensagem={topicos.erro} />}
      {!topicos.dados && !topicos.erro && <Carregando mensagem="Carregando o fórum..." />}
      {topicos.dados?.itens.length === 0 && (
        <Vazio
          titulo="Nenhuma pergunta ainda"
          descricao={
            aulaId
              ? 'Tem dúvida sobre esta aula? Pergunte ao instrutor.'
              : 'Tem dúvida? Pergunte ao instrutor.'
          }
          icone="message-square"
        />
      )}
      {!!topicos.dados?.itens.length && (
        <>
          <Lista rotulo="Perguntas do fórum">
            {topicos.dados.itens.map((t) => (
              <ItemLista
                key={t.id}
                titulo={t.titulo}
                icone={t.fixado ? 'pin' : 'message-square'}
                onClick={() => navigate(caminhoTopico(turmaId, t.id))}
                fim={
                  <span className="pc-linha">
                    {t.respondidoInstrutor && <Status rotulo="Respondida" severidade="ok" />}
                    <Badge rotulo={`${t.respostas} resp.`} aparencia="suave" />
                  </span>
                }
              >
                {t.autorNome} · {dataHora(t.criadoEm)}
              </ItemLista>
            ))}
          </Lista>
          {topicos.dados.totalPaginas > 1 && (
            <Paginacao
              total={topicos.dados.total}
              pagina={pagina}
              tamanho={TAMANHO}
              aoMudarPagina={setPagina}
            />
          )}
        </>
      )}
    </div>
  );
}

function NovaPergunta({
  turmaId,
  aulaId,
  aoCancelar,
  aoPublicar,
}: Props & { aoCancelar: () => void; aoPublicar: (t: Topico) => void }) {
  const [titulo, setTitulo] = useState('');
  const [corpo, setCorpo] = useState('');
  const [erros, setErros] = useState<{ titulo?: string; corpo?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const imagens = useImagensMensagem();
  const campoImagens = useRef<CampoImagensRef>(null);

  const publicar = async () => {
    const novos = {
      titulo: erroObrigatorio(titulo, 'Dê um título à pergunta.', 150),
      corpo: erroObrigatorio(corpo, 'Descreva a sua dúvida.', 10_000),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      const anexos = await imagens.enviar();
      aoPublicar(
        await api.post<Topico>(`/api/aluno/turmas/${turmaId}/forum`, {
          titulo: titulo.trim(),
          corpo: corpo.trim(),
          aulaId: aulaId ?? null,
          anexos,
        }),
      );
      imagens.limpar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ titulo: erro.erroDoCampo('titulo'), corpo: erro.erroDoCampo('corpo') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Card titulo={aulaId ? 'Pergunta sobre esta aula' : 'Nova pergunta'}>
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void publicar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoTexto
          rotulo="Título da pergunta"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={150}
        />
        <CampoTextarea
          rotulo="Sua dúvida"
          valor={corpo}
          aoMudar={setCorpo}
          erro={erros.corpo}
          obrigatorio
          rows={6}
          maxLength={10_000}
          dica="Aceita Markdown (**negrito**, listas com -, `código`)."
          onPaste={(evento) => campoImagens.current?.colar(evento)}
        />
        <CampoImagens
          ref={campoImagens}
          rotulo="Imagens da pergunta"
          valor={imagens.imagens}
          aoMudar={imagens.setImagens}
          limites={LIMITES_FORUM}
          progresso={imagens.progresso}
          desabilitado={enviando}
        />
        <div className="pc-linha">
          <Botao type="submit" rotulo="Publicar pergunta" icone="send" carregando={enviando} />
          <Botao rotulo="Cancelar" aparencia="texto" onClick={aoCancelar} />
        </div>
      </form>
    </Card>
  );
}
`````

`web/aluno/src/paginas/forum/TopicoPagina.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CabecalhoPagina,
  Card,
  CampoTextarea,
  Carregando,
} from '@celke/kole-react';
import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CampoImagens, type CampoImagensRef } from '@compartilhado/ui/CampoImagens.tsx';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { GaleriaImagens } from '@compartilhado/ui/GaleriaImagens.tsx';
import { LIMITES_FORUM } from '@compartilhado/ui/imagens.ts';
import { caminhoAula, caminhoTurma } from '../../caminhos.ts';
import type { Topico } from '../../tipos.ts';
import { useImagensMensagem } from './useImagensMensagem.ts';

/** Uma pergunta do fórum com as respostas; o aluno também pode responder. */
export function TopicoPagina() {
  const { turmaId, topicoId } = useParams();
  const navigate = useNavigate();
  const [resposta, setResposta] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const imagens = useImagensMensagem();
  const campoImagens = useRef<CampoImagensRef>(null);
  const topico = useCarregar(
    () => api.get<Topico>(`/api/aluno/forum/topicos/${topicoId}`),
    [topicoId],
  );

  const responder = async () => {
    if (!resposta.trim()) {
      setErro('Escreva a resposta.');
      return;
    }
    setErro(undefined);
    setEnviando(true);
    try {
      const anexos = await imagens.enviar();
      topico.definir(
        await api.post<Topico>(`/api/aluno/forum/topicos/${topicoId}/respostas`, {
          corpo: resposta.trim(),
          anexos,
        }),
      );
      setResposta('');
      imagens.limpar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('corpo')) || mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  if (topico.erro && !topico.dados) {
    return (
      <div className="pc-pagina">
        <Alerta
          severidade="err"
          titulo="Não foi possível abrir a pergunta"
          mensagem={topico.erro}
        />
      </div>
    );
  }
  if (!topico.dados) return <Carregando mensagem="Carregando a pergunta..." />;
  const t = topico.dados;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={t.titulo}
        icone="message-square"
        acoes={
          <>
            {t.aulaId && (
              <Botao
                rotulo="Ir para a aula"
                icone="circle-play"
                aparencia="contorno"
                onClick={() => navigate(caminhoAula(turmaId!, t.aulaId!))}
              />
            )}
            <Botao
              rotulo="Voltar ao fórum"
              icone="chevron-left"
              aparencia="texto"
              onClick={() => navigate(`${caminhoTurma(turmaId!)}?aba=forum`)}
            />
          </>
        }
      >
        {t.autorNome} · {dataHora(t.criadoEm)}
      </CabecalhoPagina>
      <Card>
        {/* HTML já filtrado pela API (Markdown → HTML seguro) */}
        <div className="pc-texto-longo" dangerouslySetInnerHTML={{ __html: t.corpoHtml }} />
        <GaleriaImagens imagens={t.anexos} de={t.autorNome} />
      </Card>

      <h2 className="pc-subtitulo">
        {t.respostas.length === 0 ? 'Nenhuma resposta ainda' : `Respostas (${t.respostas.length})`}
      </h2>
      {t.respostas.map((r) => (
        <article key={r.id} className="pc-resposta" aria-label={`Resposta de ${r.autorNome}`}>
          <header className="pc-linha">
            <strong>{r.autorNome}</strong>
            {r.instrutor && <Badge cor="pri" rotulo="Instrutor" aparencia="suave" />}
            <span className="pc-dica">{dataHora(r.criadoEm)}</span>
          </header>
          <div dangerouslySetInnerHTML={{ __html: r.corpoHtml }} />
          <GaleriaImagens imagens={r.anexos} de={r.autorNome} />
        </article>
      ))}

      <Card titulo="Responder">
        <form
          noValidate
          className="pc-pilha"
          onSubmit={(evento) => {
            evento.preventDefault();
            void responder();
          }}
        >
          <CampoTextarea
            rotulo="Sua resposta"
            valor={resposta}
            aoMudar={setResposta}
            erro={erro}
            rows={4}
            maxLength={10_000}
            onPaste={(evento) => campoImagens.current?.colar(evento)}
          />
          <CampoImagens
            ref={campoImagens}
            rotulo="Imagens da resposta"
            valor={imagens.imagens}
            aoMudar={imagens.setImagens}
            limites={LIMITES_FORUM}
            progresso={imagens.progresso}
            desabilitado={enviando}
          />
          <div>
            <Botao type="submit" rotulo="Publicar resposta" icone="send" carregando={enviando} />
          </div>
        </form>
      </Card>
    </div>
  );
}
`````

`web/compartilhado/src/ui/estilos.css`:

`````css
/* Telas fora da área logada (cadastro, confirmação, recuperação de senha): cartão centralizado
   com os tokens da @celke/kole-react, nos dois temas. */
.pc-conta {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px 16px;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-ui);
}

.pc-conta-cartao {
  width: 100%;
  max-width: 440px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 32px 28px;
  display: grid;
  gap: 18px;
}

.pc-conta-marca {
  font-size: var(--fs-caption);
  color: var(--t2);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0;
}

.pc-conta-titulo {
  font-size: var(--fs-titulo);
  margin: 0;
}

.pc-conta-descricao {
  color: var(--t2);
  margin: 0;
}

.pc-conta-form {
  display: grid;
  gap: 14px;
}

.pc-conta-rodape {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  justify-content: space-between;
  font-size: var(--fs-corpo-2);
}

.pc-conta-rodape a,
.pc-link {
  color: var(--pri);
}

.pc-pagina {
  display: grid;
  gap: 20px;
}

.pc-grade-cards {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

/* Telas de gestão (ADM) */
.pc-pilha {
  display: grid;
  gap: 16px;
}

.pc-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.pc-linha-fim {
  justify-content: flex-end;
}

.pc-filtros {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pc-grade-detalhes {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  align-items: start;
}

.pc-dica {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
  margin: 0;
}

.pc-subtitulo {
  font-size: var(--fs-corpo);
  margin: 8px 0 0;
}

.pc-texto-longo {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
}

.pc-texto-longo :where(p, ul, ol, h1, h2, h3) {
  white-space: normal;
}

.pc-capa {
  max-width: min(360px, 100%);
  border-radius: var(--r-card);
}

.pc-lista-aulas {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 20px;
}

.pc-lista-aulas li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
}

.pc-aula-titulo {
  background: none;
  border: 0;
  padding: 0;
  font: inherit;
  cursor: pointer;
  text-align: left;
}

.pc-arquivos {
  display: grid;
  gap: 10px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.pc-resposta {
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 12px 14px;
  display: grid;
  gap: 8px;
}

.pc-resposta-oculta {
  opacity: 0.6;
}

/* Área do aluno */
.pc-player video {
  width: 100%;
  max-height: 70vh;
  background: #000;
  border-radius: var(--r-card);
}

.pc-progresso-turma {
  display: grid;
  gap: 4px;
  max-width: 520px;
}

.pc-navegacao-aulas {
  justify-content: space-between;
}

.pc-formulario-estreito {
  max-width: 420px;
}

/* Pagamentos (ADM) */
.pc-codigo {
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-corpo-2);
  overflow-wrap: anywhere;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
}

.pc-passos {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 6px;
}

.pc-json {
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-corpo-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 420px;
  overflow: auto;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  margin: 0;
}

.pc-linha-tempo {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

.pc-linha-tempo li {
  display: grid;
  gap: 2px;
  border-left: 3px solid var(--border);
  padding-left: 12px;
}

/* Imagens das mensagens do fórum: escolha (CampoImagens) e exibição (GaleriaImagens) */
.pc-imagens {
  display: grid;
  gap: 8px;
}

.pc-imagens-zona {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 12px;
  border: 1px dashed var(--border-2);
  border-radius: var(--r-card);
}

.pc-imagens-zona-sobre {
  border-color: var(--pri);
  background: var(--pri-bg);
}

.pc-imagens-erros {
  color: var(--err-fg);
  font-size: var(--fs-corpo-2);
}

.pc-imagens-erros p {
  margin: 0;
}

.pc-imagens-lista,
.pc-galeria {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.pc-imagens-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  max-width: 100%;
}

.pc-imagens-item img {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: var(--r-field);
}

.pc-imagens-texto {
  display: grid;
  gap: 2px;
  min-width: 120px;
}

.pc-imagens-nome {
  overflow-wrap: anywhere;
  font-size: var(--fs-corpo-2);
}

.pc-galeria button {
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  background: none;
  cursor: zoom-in;
  overflow: hidden;
}

.pc-galeria img {
  display: block;
  width: 140px;
  height: 100px;
  object-fit: cover;
}
`````

### Arquivos novos

`web/aluno/src/forum.test.ts`:

`````ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ImagemLocal } from '@compartilhado/ui/imagens.ts';
import { enviarImagensForum } from './paginas/forum/useImagensMensagem.ts';

const { post, enviarParaUrl } = vi.hoisted(() => ({ post: vi.fn(), enviarParaUrl: vi.fn() }));
vi.mock('@compartilhado/api/cliente.ts', () => ({ api: { post } }));
vi.mock('@compartilhado/api/envio.ts', () => ({ enviarParaUrl }));

function imagem(id: string, nome: string, tipo: string, tamanho: number): ImagemLocal {
  const arquivo = new File(['x'], nome, { type: tipo });
  Object.defineProperty(arquivo, 'size', { value: tamanho });
  return { id, arquivo, previa: `blob:${nome}` };
}

afterEach(() => vi.clearAllMocks());

describe('envio das imagens do fórum', () => {
  it('pede a URL, envia direto ao armazenamento e devolve as chaves na ordem', async () => {
    post
      .mockResolvedValueOnce({
        chave: 'uploads/forum/7/a',
        url: 'https://midia/a',
        contentType: 'image/png',
      })
      .mockResolvedValueOnce({
        chave: 'uploads/forum/7/b',
        url: 'https://midia/b',
        contentType: 'image/jpeg',
      });
    enviarParaUrl.mockImplementation(
      async (_url, _arquivo, _tipo, aoProgredir: (n: number) => void) => {
        aoProgredir(50);
        return '"etag"';
      },
    );
    const progresso: [string, number][] = [];

    const chaves = await enviarImagensForum(
      [imagem('1', 'a.png', 'image/png', 100), imagem('2', 'b.jpg', 'image/jpeg', 200)],
      (id, fracao) => progresso.push([id, fracao]),
    );

    expect(chaves).toEqual(['uploads/forum/7/a', 'uploads/forum/7/b']);
    expect(post).toHaveBeenNthCalledWith(1, '/api/aluno/forum/anexos/envio', {
      contentType: 'image/png',
      tamanho: 100,
    });
    // O PUT leva o Content-Type que a API assinou
    expect(enviarParaUrl.mock.calls[1]!.slice(0, 3)).toEqual([
      'https://midia/b',
      expect.any(File),
      'image/jpeg',
    ]);
    expect(progresso).toEqual([
      ['1', 0],
      ['1', 0.5],
      ['1', 1],
      ['2', 0],
      ['2', 0.25],
      ['2', 1],
    ]);
  });

  it('se uma imagem falha, o erro sobe e as seguintes não são enviadas', async () => {
    post.mockResolvedValue({ chave: 'k', url: 'https://midia/k', contentType: 'image/png' });
    enviarParaUrl.mockRejectedValueOnce(new Error('armazenamento recusou'));

    await expect(
      enviarImagensForum(
        [imagem('1', 'a.png', 'image/png', 10), imagem('2', 'b.png', 'image/png', 10)],
        () => undefined,
      ),
    ).rejects.toThrow('armazenamento recusou');
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('sem imagens, não chama a API', async () => {
    expect(await enviarImagensForum([], () => undefined)).toEqual([]);
    expect(post).not.toHaveBeenCalled();
  });
});
`````

`web/aluno/src/paginas/forum/useImagensMensagem.ts`:

`````ts
import { useCallback, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import type { ImagemLocal } from '@compartilhado/ui/imagens.ts';
import type { EnvioSimples } from '../../tipos.ts';

/**
 * Envia as imagens de uma mensagem do fórum, uma por vez, direto ao armazenamento (URL pré-assinada
 * pela API), e devolve as chaves para publicar a pergunta ou a resposta.
 */
export async function enviarImagensForum(
  imagens: readonly ImagemLocal[],
  aoProgredir: (id: string, fracao: number) => void,
): Promise<string[]> {
  const chaves: string[] = [];
  for (const imagem of imagens) {
    const { arquivo } = imagem;
    const envio = await api.post<EnvioSimples>('/api/aluno/forum/anexos/envio', {
      contentType: arquivo.type,
      tamanho: arquivo.size,
    });
    aoProgredir(imagem.id, 0);
    await enviarParaUrl(envio.url, arquivo, envio.contentType, (enviados) =>
      aoProgredir(imagem.id, arquivo.size ? enviados / arquivo.size : 1),
    );
    aoProgredir(imagem.id, 1);
    chaves.push(envio.chave);
  }
  return chaves;
}

/**
 * Imagens escolhidas para a mensagem e o progresso do envio. O envio acontece só ao publicar: a API
 * apaga os envios ao processá-los, então uma tentativa nova sempre envia de novo.
 */
export function useImagensMensagem() {
  const [imagens, setImagens] = useState<ImagemLocal[]>([]);
  const [progresso, setProgresso] = useState<Record<string, number>>();

  const enviar = useCallback(async () => {
    if (imagens.length === 0) return [];
    setProgresso({});
    try {
      return await enviarImagensForum(imagens, (id, fracao) =>
        setProgresso((atual) => ({ ...atual, [id]: fracao })),
      );
    } finally {
      setProgresso(undefined);
    }
  }, [imagens]);

  const limpar = useCallback(() => {
    imagens.forEach((imagem) => URL.revokeObjectURL(imagem.previa));
    setImagens([]);
  }, [imagens]);

  return { imagens, setImagens, progresso, enviar, limpar };
}
`````

`web/compartilhado/src/ui/CampoImagens.tsx`:

`````tsx
import { AcaoLinha, Botao, Progresso } from '@celke/kole-react';
import {
  forwardRef,
  useEffect,
  useId,
  useImperativeHandle,
  useRef,
  useState,
  type ClipboardEvent,
  type DragEvent,
} from 'react';
import { bytes } from './formato.ts';
import { imagensColadas, triarImagens, type ImagemLocal, type LimitesImagens } from './imagens.ts';

export interface CampoImagensRef {
  /** Para o formulário repassar o que foi colado em outro campo (ex.: no texto da mensagem). */
  colar: (evento: ClipboardEvent) => void;
}

interface Props {
  rotulo: string;
  valor: ImagemLocal[];
  aoMudar: (imagens: ImagemLocal[]) => void;
  limites: LimitesImagens;
  /** Durante o envio: fração enviada (0 a 1) de cada imagem, pelo `id`. */
  progresso?: Record<string, number>;
  /** Erro vindo de fora (a API recusou a imagem). */
  erro?: string;
  desabilitado?: boolean;
}

let sequencia = 0;
const novoId = () =>
  typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `img-${Date.now()}-${++sequencia}`;

/**
 * Imagens anexadas a uma mensagem: escolher, arrastar ou colar (Ctrl+V de um print). Confere tipo,
 * tamanho e quantidade em todos os caminhos; o envio fica com quem usa (cada API tem o seu).
 */
export const CampoImagens = forwardRef<CampoImagensRef, Props>(function CampoImagens(
  { rotulo, valor, aoMudar, limites, progresso, erro, desabilitado = false },
  ref,
) {
  const entrada = useRef<HTMLInputElement>(null);
  const idEntrada = useId();
  const [recusas, setRecusas] = useState<string[]>([]);
  const [sobre, setSobre] = useState(false);
  // As prévias vivas, para liberar ao desmontar
  const previas = useRef(new Set<string>());
  const cheio = valor.length >= limites.quantidadeMaxima;

  useEffect(() => {
    const vivas = previas.current;
    return () => vivas.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const adicionar = (arquivos: File[]) => {
    if (desabilitado || arquivos.length === 0) return;
    const { aceitas, recusas: motivos } = triarImagens(valor.length, arquivos, limites);
    setRecusas(motivos);
    if (aceitas.length === 0) return;
    const novas = aceitas.map((arquivo) => {
      const previa = URL.createObjectURL(arquivo);
      previas.current.add(previa);
      return { id: novoId(), arquivo, previa };
    });
    aoMudar([...valor, ...novas]);
  };

  const colar = (evento: ClipboardEvent) => {
    const imagens = imagensColadas(evento.clipboardData);
    if (imagens.length === 0) return;
    // Só impede o colar padrão quando há imagem: texto continua indo para o campo de texto
    evento.preventDefault();
    adicionar(imagens);
  };

  useImperativeHandle(ref, () => ({ colar }));

  const remover = (imagem: ImagemLocal) => {
    URL.revokeObjectURL(imagem.previa);
    previas.current.delete(imagem.previa);
    setRecusas([]);
    aoMudar(valor.filter((i) => i.id !== imagem.id));
  };

  const soltar = (evento: DragEvent) => {
    evento.preventDefault();
    setSobre(false);
    adicionar(Array.from(evento.dataTransfer.files));
  };

  const mensagens = [erro, ...recusas].filter(Boolean);
  const mb = Math.round(limites.tamanhoMaximo / (1024 * 1024));

  return (
    <div className="pc-imagens">
      <div
        role="group"
        aria-label={rotulo}
        className={`pc-imagens-zona${sobre ? ' pc-imagens-zona-sobre' : ''}`}
        onDragOver={(evento) => {
          evento.preventDefault();
          if (!desabilitado) setSobre(true);
        }}
        onDragLeave={() => setSobre(false)}
        onDrop={soltar}
        onPaste={colar}
      >
        <input
          ref={entrada}
          id={idEntrada}
          type="file"
          accept={limites.tipos.join(',')}
          multiple
          hidden
          aria-label={`Anexar ${rotulo.toLowerCase()}`}
          disabled={desabilitado || cheio}
          onChange={(evento) => {
            const arquivos = Array.from(evento.target.files ?? []);
            evento.target.value = '';
            adicionar(arquivos);
          }}
        />
        <Botao
          rotulo="Anexar imagem"
          icone="image"
          aparencia="contorno"
          tamanho="pequeno"
          disabled={desabilitado || cheio}
          onClick={() => entrada.current?.click()}
        />
        <span className="pc-dica">
          {cheio
            ? `Limite de ${limites.quantidadeMaxima} imagens atingido.`
            : `Ou arraste e solte, ou cole um print (Ctrl+V). JPG ou PNG, até ${mb} MB, no máximo ${limites.quantidadeMaxima}.`}
        </span>
      </div>

      {mensagens.length > 0 && (
        <div role="alert" className="pc-imagens-erros">
          {mensagens.map((mensagem) => (
            <p key={mensagem}>{mensagem}</p>
          ))}
        </div>
      )}

      {valor.length > 0 && (
        <ul className="pc-imagens-lista" aria-label="Imagens anexadas">
          {valor.map((imagem) => {
            const enviado = progresso?.[imagem.id];
            return (
              <li key={imagem.id} className="pc-imagens-item">
                <img
                  src={imagem.previa}
                  alt={`Prévia de ${imagem.arquivo.name || 'imagem colada'}`}
                />
                <span className="pc-imagens-texto">
                  <span className="pc-imagens-nome">{imagem.arquivo.name || 'imagem colada'}</span>
                  <span className="pc-dica">{bytes(imagem.arquivo.size)}</span>
                  {enviado !== undefined && (
                    <Progresso
                      rotulo={`Envio de ${imagem.arquivo.name || 'imagem colada'}`}
                      valor={Math.round(enviado * 100)}
                      altura="xs"
                    />
                  )}
                </span>
                {!desabilitado && (
                  <AcaoLinha
                    icone="x"
                    severidade="err"
                    rotulo={`Remover ${imagem.arquivo.name || 'imagem colada'}`}
                    onClick={() => remover(imagem)}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
});
`````

`web/compartilhado/src/ui/GaleriaImagens.tsx`:

`````tsx
import { DialogoArquivo, type Anexo } from '@celke/kole-react';
import { useState } from 'react';

interface Props {
  /** Imagens já publicadas (URL temporária devolvida pela API). */
  imagens: readonly { id: number; url: string }[];
  /** Quem enviou, para o texto alternativo (ex.: "Imagem 1 de Ana"). */
  de: string;
}

/** Miniaturas das imagens de uma mensagem; clicar abre o visualizador em tela cheia. */
export function GaleriaImagens({ imagens, de }: Props) {
  const [aberta, setAberta] = useState<number | null>(null);
  if (imagens.length === 0) return null;
  const arquivos: Anexo[] = imagens.map((imagem, i) => ({
    nome: `Imagem ${i + 1} de ${de}`,
    tamanho: 0,
    tipo: 'image/jpeg',
    url: imagem.url,
  }));

  return (
    <>
      <ul className="pc-galeria" aria-label={`Imagens de ${de}`}>
        {arquivos.map((arquivo, i) => (
          <li key={imagens[i]!.id}>
            <button type="button" onClick={() => setAberta(i)} title={`Ampliar ${arquivo.nome}`}>
              <img src={arquivo.url} alt={arquivo.nome} loading="lazy" />
            </button>
          </li>
        ))}
      </ul>
      <DialogoArquivo
        aberto={aberta !== null}
        aoFechar={() => setAberta(null)}
        arquivos={arquivos}
        indice={aberta ?? 0}
        aoMudarIndice={setAberta}
      />
    </>
  );
}
`````

`web/compartilhado/src/ui/imagens.test.tsx`:

`````tsx
import { ProvedorUi } from '@celke/kole-react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { CampoImagens } from './CampoImagens.tsx';
import { imagensColadas, LIMITES_FORUM, triarImagens, type ImagemLocal } from './imagens.ts';

const MB = 1024 * 1024;

function arquivo(nome: string, tipo: string, tamanho = 1000): File {
  const f = new File(['x'], nome, { type: tipo });
  Object.defineProperty(f, 'size', { value: tamanho });
  return f;
}

describe('triagem das imagens', () => {
  it('aceita JPEG e PNG dentro dos limites', () => {
    const { aceitas, recusas } = triarImagens(
      0,
      [arquivo('a.jpg', 'image/jpeg'), arquivo('b.png', 'image/png')],
      LIMITES_FORUM,
    );
    expect(aceitas.map((f) => f.name)).toEqual(['a.jpg', 'b.png']);
    expect(recusas).toEqual([]);
  });

  it('recusa tipo errado, tamanho acima do limite e o que passa da quantidade', () => {
    const { aceitas, recusas } = triarImagens(
      1,
      [
        arquivo('anim.gif', 'image/gif'),
        arquivo('doc.pdf', 'application/pdf'),
        arquivo('grande.png', 'image/png', 6 * MB),
        arquivo('c.png', 'image/png'),
        arquivo('d.png', 'image/png'),
        arquivo('e.png', 'image/png'),
      ],
      LIMITES_FORUM,
    );
    expect(aceitas.map((f) => f.name)).toEqual(['c.png', 'd.png']);
    expect(recusas).toEqual([
      'anim.gif e doc.pdf: envie imagens JPG ou PNG.',
      'grande.png: cada imagem pode ter até 5 MB.',
      'e.png: anexe no máximo 3 imagens por mensagem.',
    ]);
  });

  it('imagem colada sem nome aparece como "imagem colada"', () => {
    const { recusas } = triarImagens(0, [arquivo('', 'image/gif')], LIMITES_FORUM);
    expect(recusas).toEqual(['imagem colada: envie imagens JPG ou PNG.']);
  });

  it('do que foi colado, fica só o que é imagem', () => {
    const png = arquivo('print.png', 'image/png');
    const dados = {
      items: [
        { kind: 'string', type: 'text/plain', getAsFile: () => null },
        { kind: 'file', type: 'image/png', getAsFile: () => png },
        {
          kind: 'file',
          type: 'application/pdf',
          getAsFile: () => arquivo('a.pdf', 'application/pdf'),
        },
      ],
    } as unknown as DataTransfer;
    expect(imagensColadas(dados)).toEqual([png]);
    expect(imagensColadas(null)).toEqual([]);
  });
});

function Campo() {
  const [imagens, setImagens] = useState<ImagemLocal[]>([]);
  return (
    <ProvedorUi>
      <CampoImagens
        rotulo="Imagens da pergunta"
        valor={imagens}
        aoMudar={setImagens}
        limites={LIMITES_FORUM}
      />
    </ProvedorUi>
  );
}

describe('campo de imagens', () => {
  afterEach(cleanup);

  beforeAll(() => {
    URL.createObjectURL = vi.fn((f: Blob) => `blob:${(f as File).name}`);
    URL.revokeObjectURL = vi.fn();
  });

  it('escolhe pelo seletor, mostra a prévia e remove', async () => {
    const usuario = userEvent.setup();
    render(<Campo />);
    await usuario.upload(
      screen.getByLabelText('Anexar imagens da pergunta'),
      arquivo('tela.png', 'image/png'),
    );

    expect(screen.getByRole('img', { name: 'Prévia de tela.png' })).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Remover tela.png' }));
    expect(screen.queryByRole('img', { name: 'Prévia de tela.png' })).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:tela.png');
  });

  it('arrastar um PDF é recusado (o accept do seletor não vale para arrastar)', () => {
    render(<Campo />);
    fireEvent.drop(screen.getByRole('group', { name: 'Imagens da pergunta' }), {
      dataTransfer: { files: [arquivo('doc.pdf', 'application/pdf')] },
    });
    expect(screen.getByRole('alert')).toHaveTextContent('doc.pdf: envie imagens JPG ou PNG.');
    expect(screen.queryByRole('list', { name: 'Imagens anexadas' })).not.toBeInTheDocument();
  });

  it('colar um print adiciona a imagem; no limite, o botão fica desabilitado', () => {
    render(<Campo />);
    const zona = screen.getByRole('group', { name: 'Imagens da pergunta' });
    for (const nome of ['1.png', '2.png', '3.png']) {
      const png = arquivo(nome, 'image/png');
      fireEvent.paste(zona, {
        clipboardData: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => png }] },
      });
    }
    expect(screen.getAllByRole('img')).toHaveLength(3);
    expect(screen.getByRole('button', { name: 'Anexar imagem' })).toBeDisabled();
    expect(screen.getByText('Limite de 3 imagens atingido.')).toBeInTheDocument();
  });
});
`````

`web/compartilhado/src/ui/imagens.ts`:

`````ts
/** Imagem escolhida pelo usuário e ainda não enviada (o arquivo fica só no navegador). */
export interface ImagemLocal {
  /** Identificador desta escolha (duas imagens com o mesmo nome continuam distintas). */
  id: string;
  arquivo: File;
  /** Endereço de pré-visualização (`URL.createObjectURL`); liberado ao remover. */
  previa: string;
}

export interface LimitesImagens {
  /** Tipos aceitos (MIME). */
  tipos: readonly string[];
  /** Tamanho máximo de cada imagem, em bytes. */
  tamanhoMaximo: number;
  /** Quantas imagens cabem na mensagem. */
  quantidadeMaxima: number;
}

/** Regras da API do fórum: JPEG ou PNG, até 5 MB, até 3 por mensagem. */
export const LIMITES_FORUM: LimitesImagens = {
  tipos: ['image/jpeg', 'image/png'],
  tamanhoMaximo: 5 * 1024 * 1024,
  quantidadeMaxima: 3,
};

export interface Triagem {
  aceitas: File[];
  /** Uma mensagem por motivo, pronta para mostrar. */
  recusas: string[];
}

/**
 * Separa o que pode entrar do que não pode (tipo, tamanho, quantidade). Vale para escolha, arrastar
 * e colar: o `accept` do seletor não filtra o que chega por arrastar ou colar.
 */
export function triarImagens(
  jaEscolhidas: number,
  novas: readonly File[],
  limites: LimitesImagens,
): Triagem {
  const aceitas: File[] = [];
  const tipoErrado: string[] = [];
  const grandes: string[] = [];
  const sobrando: string[] = [];
  for (const arquivo of novas) {
    const nome = arquivo.name || 'imagem colada';
    if (!limites.tipos.includes(arquivo.type)) tipoErrado.push(nome);
    else if (arquivo.size > limites.tamanhoMaximo) grandes.push(nome);
    else if (jaEscolhidas + aceitas.length >= limites.quantidadeMaxima) sobrando.push(nome);
    else aceitas.push(arquivo);
  }
  const recusas: string[] = [];
  const mb = Math.round(limites.tamanhoMaximo / (1024 * 1024));
  if (tipoErrado.length) recusas.push(`${lista(tipoErrado)}: envie imagens JPG ou PNG.`);
  if (grandes.length) recusas.push(`${lista(grandes)}: cada imagem pode ter até ${mb} MB.`);
  if (sobrando.length) {
    recusas.push(
      `${lista(sobrando)}: anexe no máximo ${limites.quantidadeMaxima} imagens por mensagem.`,
    );
  }
  return { aceitas, recusas };
}

function lista(nomes: string[]): string {
  if (nomes.length === 1) return nomes[0]!;
  return `${nomes.slice(0, -1).join(', ')} e ${nomes.at(-1)}`;
}

/** Arquivos de imagem de um evento de colar (Ctrl+V de um print). */
export function imagensColadas(dados: DataTransfer | null): File[] {
  if (!dados) return [];
  return Array.from(dados.items)
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((arquivo): arquivo is File => arquivo !== null);
}
`````

`web/e2e/aluno/forum.spec.ts`:

`````ts
import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { PNG } from '../support/amostras.ts';
import { clienteApi, montarCurso, type ClienteApi, type CursoMontado } from '../support/api.ts';
import { ADM_URL, ADMIN_SEED } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Fórum de dúvidas com imagens: o aluno anexa (escolhendo, arrastando ou colando um print), a imagem
 * vai direto ao armazenamento e a API a regrava sem metadados; aluno e moderação veem as imagens.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
});

test.afterAll(async () => {
  await contexto.dispose();
});

const png = (nome: string) => ({ ...PNG, name: nome });

async function alunoNoForum(page: Page, request: APIRequestContext) {
  const email = await alunoConfirmado(request);
  // Começa ontem: o relógio da VM do Docker (Windows) às vezes volta alguns segundos
  await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
    email,
    formaPagamento: 'CORTESIA',
    inicio: new Date(Date.now() - 86_400_000).toISOString(),
  });
  await entrarPelaApi(page, email, SENHA_NOVA);
  await page.goto(`/turmas/${curso.turmaId}?aba=forum`);
  await aceitarTermosSePedir(page, 'turma');
  await page.getByRole('button', { name: 'Fazer uma pergunta' }).click();
}

/** Cola um print (imagem na área de transferência) no elemento. */
async function colarImagem(page: Page, seletor: string, nome: string) {
  await page.locator(seletor).evaluate(
    (alvo, { base64, nome }) => {
      const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
      const dados = new DataTransfer();
      dados.items.add(new File([bytes], nome, { type: 'image/png' }));
      alvo.dispatchEvent(
        new ClipboardEvent('paste', { clipboardData: dados, bubbles: true, cancelable: true }),
      );
    },
    { base64: PNG.buffer.toString('base64'), nome },
  );
}

async function imagensCarregadas(page: Page, lista: string) {
  const imagens = page.getByRole('list', { name: lista }).getByRole('img');
  await expect(imagens.first()).toBeVisible();
  await expect
    .poll(() =>
      imagens.evaluateAll((todas) => todas.every((i) => (i as HTMLImageElement).naturalWidth > 0)),
    )
    .toBe(true);
  return imagens;
}

test('pergunta com imagens, resposta com print colado e a moderação vê as imagens', async ({
  page,
  request,
  browser,
}) => {
  await alunoNoForum(page, request);
  const titulo = `Erro ao compilar ${stamp()}`;
  await page.getByRole('textbox', { name: 'Título da pergunta' }).fill(titulo);
  await page.getByRole('textbox', { name: 'Sua dúvida' }).fill('Segue o **print** do erro.');
  await page
    .getByLabel('Anexar imagens da pergunta')
    .setInputFiles([png('erro-1.png'), png('erro-2.png')]);
  await expect(page.getByRole('img', { name: 'Prévia de erro-1.png' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Prévia de erro-2.png' })).toBeVisible();

  const envios = page.waitForResponse('**/api/aluno/forum/anexos/envio');
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  expect((await envios).status()).toBe(200);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  // As imagens publicadas carregam do armazenamento e abrem ampliadas
  const daPergunta = await imagensCarregadas(page, `Imagens de ${'Aluno do Playwright'}`);
  await expect(daPergunta).toHaveCount(2);
  await daPergunta.first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');

  // Resposta com um print colado direto no texto
  await page.getByRole('textbox', { name: 'Sua resposta' }).fill('Mais um print, agora colado.');
  await colarImagem(page, 'textarea', 'print.png');
  await expect(page.getByRole('img', { name: 'Prévia de print.png' })).toBeVisible();
  await page.getByRole('button', { name: 'Publicar resposta' }).click();
  await expect(page.getByText('Respostas (1)')).toBeVisible();
  const daResposta = page
    .getByRole('article', { name: 'Resposta de Aluno do Playwright' })
    .getByRole('img');
  await expect(daResposta).toHaveCount(1);
  // O formulário volta vazio
  await expect(page.getByRole('list', { name: 'Imagens anexadas' })).toHaveCount(0);

  // A moderação do ADM vê as imagens da pergunta e da resposta
  const contextoAdm = await browser.newContext({ baseURL: ADM_URL });
  const moderacao = await contextoAdm.newPage();
  await entrarPelaApi(moderacao, ADMIN_SEED);
  await moderacao.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=forum`);
  await moderacao.getByRole('cell', { name: titulo }).click();
  const painel = moderacao.getByRole('dialog');
  await expect(
    painel.getByRole('list', { name: 'Imagens de Aluno do Playwright' }).first().getByRole('img'),
  ).toHaveCount(2);
  await expect(
    painel.getByRole('article', { name: 'Resposta de Aluno do Playwright' }).getByRole('img'),
  ).toHaveCount(1);
  await contextoAdm.close();
});

test('recusa GIF e o que passa de 3 imagens, e permite remover', async ({ page, request }) => {
  await alunoNoForum(page, request);
  const campo = page.getByLabel('Anexar imagens da pergunta');

  await campo.setInputFiles({ ...PNG, name: 'animado.gif', mimeType: 'image/gif' });
  await expect(page.getByText('animado.gif: envie imagens JPG ou PNG.')).toBeVisible();

  await campo.setInputFiles([png('1.png'), png('2.png'), png('3.png'), png('4.png')]);
  await expect(page.getByText('4.png: anexe no máximo 3 imagens por mensagem.')).toBeVisible();
  await expect(
    page.getByRole('list', { name: 'Imagens anexadas' }).getByRole('listitem'),
  ).toHaveCount(3);
  await expect(page.getByRole('button', { name: 'Anexar imagem' })).toBeDisabled();

  await page.getByRole('button', { name: 'Remover 2.png' }).click();
  await expect(
    page.getByRole('list', { name: 'Imagens anexadas' }).getByRole('listitem'),
  ).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Anexar imagem' })).toBeEnabled();
});

test('imagem falsa é recusada pela API; o texto fica e dá para publicar sem ela', async ({
  page,
  request,
}) => {
  await alunoNoForum(page, request);
  const titulo = `Pergunta com imagem falsa ${stamp()}`;
  await page.getByRole('textbox', { name: 'Título da pergunta' }).fill(titulo);
  await page.getByRole('textbox', { name: 'Sua dúvida' }).fill('O arquivo diz ser PNG, mas não é.');
  await page.getByLabel('Anexar imagens da pergunta').setInputFiles({
    name: 'falso.png',
    mimeType: 'image/png',
    buffer: Buffer.from('não sou uma imagem'),
  });

  const publicacao = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/api/aluno/turmas/${curso.turmaId}/forum`) &&
      r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  expect((await publicacao).status()).toBe(400);
  await expect(page.getByRole('alert').first()).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Título da pergunta' })).toHaveValue(titulo);

  await page.getByRole('button', { name: 'Remover falso.png' }).click();
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();
});
`````

## Antes de executar

Fase 17 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/forum-anexos
```

## Verificação

- No `web/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build` e `npm run e2e` (com a stack
  de e2e de pé).
- Na stack de e2e, como aluno matriculado: fazer uma pergunta com duas imagens e responder colando um
  print; as imagens aparecem na pergunta e na resposta e abrem ampliadas; um GIF e a quarta imagem são
  recusados na tela.
- No ADM, Turma → Fórum: o tópico mostra as imagens da pergunta e da resposta.

## Depois de executar

```bash
git add web/ docs/
git commit -m "feat(forum): imagens nas perguntas e respostas do fórum"
git push -u origin feature/forum-anexos
```

Abra o Pull Request de `feature/forum-anexos` para `homolog`.
