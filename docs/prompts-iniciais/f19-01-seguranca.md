# Segurança: segundo fator, menos privilégio e senhas fortes

**Fase:** 19
**Branch:** `feature/seguranca`

## Prompt

Vamos reforçar a segurança da plataforma para que uma senha vazada, uma conta roubada ou uma falha na
aplicação causem o menor estrago possível. Siga o `backend/CLAUDE.md`, as rules de `.claude/rules/` e os
padrões do `web/`. Não faça commit.

Entregas:

1. Segundo fator (TOTP) obrigatório para os administradores.
2. Usuário do banco só de dados para a API e o worker; migrations com o dono do schema.
3. Política de senha que recusa senhas comuns, vazadas e previsíveis, com medidor de força nas telas.
4. Limite por usuário nas gravações e no checkout.
5. Dependabot e `npm audit` no pipeline.
6. ADM → Segurança → Usuários: bloquear, desbloquear e encerrar as sessões.
7. Checkout só em HTTPS nos domínios do meio de pagamento.
8. Token dos links de e-mail fora da barra de endereço.
9. Compra só com a confirmação do aluno.
10. Um teste que varre todas as rotas e falha se alguma nascer aberta.

### 1. Segundo fator dos administradores (módulo `auth`)

**TOTP sem biblioteca** — `auth/domain/Totp`: RFC 6238 com HMAC-SHA1, passos de 30 s e 6 dígitos;
segredo de 20 bytes em Base32 (`gerarSegredo`, `base32`, `decodificarBase32`); `uri(emissor, conta,
segredo)` no formato `otpauth://totp/<emissor>:<conta>?secret=...&issuer=...&algorithm=SHA1&digits=6&period=30`
(espaço como `%20`); `verificar(segredo, codigo, agora, ultimoPassoUsado)` aceita um passo antes e um
depois do atual, compara em tempo constante, ignora espaços e só aceita passo **maior** que o último
usado (o mesmo código não vale duas vezes). Testes com os vetores SHA-1 da RFC (59 s → `287082`,
1111111109 → `081804`, 1111111111 → `050471`, 1234567890 → `005924`, 2000000000 → `279037`).

**Banco** (`V17__seguranca.sql`):

- `segundo_fator` (`usuario_id` PK e FK com `ON DELETE CASCADE`, `segredo BYTEA`, `ativado_em`,
  `ultimo_passo`, `criado_em`): `ativado_em` nulo = cadastro iniciado e não confirmado.
- `codigos_recuperacao` (`id`, `usuario_id`, `hash VARCHAR(64)`, `usado_em`, `criado_em`).
- `ck_tokens_uso_unico_tipo` passa a aceitar `DESAFIO_SEGUNDO_FATOR`; `ck_auditoria_login_resultado`
  passa a aceitar `SEGUNDO_FATOR_PENDENTE`, `SEGUNDO_FATOR_INVALIDO`, `SEGUNDO_FATOR_CADASTRADO` e
  `CODIGO_RECUPERACAO`.
- `usuarios.sessoes_encerradas_em TIMESTAMPTZ` (entrega 6).

As entidades `SegundoFator` e `CodigoRecuperacao` são `@SemAuditoria` (o último passo muda a cada login;
os eventos ficam na auditoria de login).

**Configuração** — `SegundoFatorProperties` em `app.auth.segundo-fator`: `chave` (`${TOTP_CHAVE:}`,
`@NotBlank`, 32 bytes em Base64 conferidos por `@AssertTrue`), `emissor` (`Plataforma de Cursos`) e
`rejeitar-reuso` (`true`; `false` só nos testes, que entram várias vezes no mesmo intervalo). Mova a
`CifraSegredos` dos pagamentos para `shared/cripto` com o nome da variável no construtor
(`new CifraSegredos(chave, "TOTP_CHAVE")`), usada nas mensagens de erro.

**`SegundoFatorService`**: `exigido(usuario)` (papel `ADMIN`), `ativo(id)`, `iniciarCadastro(usuario)`
(gera ou troca o segredo pendente, cifrado com o dado associado `segundo-fator:<id>`; 409 se já ativo),
`verificar(usuario, codigo, codigoRecuperacao)` (trava a linha; o primeiro código certo ativa e gera 10
códigos de recuperação `xxxxx-xxxxx` de um alfabeto sem `0/o/1/l/i`, guardados só como SHA-256; código
de recuperação vale uma vez, com ou sem hífen e em qualquer caixa), `redefinir(id)` e
`ativarComSegredo(id, segredo)` (só para o seed do e2e).

**Login em duas etapas** — `AuthService.entrar` devolve `sealed interface Entrada permits Sessao,
Desafio`. Para o administrador com a senha certa: audita `SEGUNDO_FATOR_PENDENTE`, **não** zera o
bloqueio, emite um token de uso único `DESAFIO_SEGUNDO_FATOR` (5 minutos; fora do limite de e-mails por
hora: `TipoToken.porEmail()`) e devolve `Desafio(token, cadastro, expiraEm)`, sem sessão nem cookie.
`cadastrarSegundoFator(desafio)` devolve segredo e URI. `concluirSegundoFator(desafio, codigo,
codigoRecuperacao, origem)` confere o desafio (`DesafioInvalidoException`, 401 "O tempo para informar o
código acabou. Entre de novo."), o bloqueio, o código; código errado audita `SEGUNDO_FATOR_INVALIDO`,
conta falha no bloqueio e lança `CodigoInvalidoException` (400, campo `codigo`, sem desfazer a transação);
certo consome o desafio, audita `SUCESSO`, `SEGUNDO_FATOR_CADASTRADO` ou `CODIGO_RECUPERACAO`, zera o
bloqueio e abre a sessão.

**Rotas** (públicas, no `SecurityConfig` com comentário, no `LimitePorIpFilter` e na lista do teste de
rotas):

- `POST /api/auth/login` → `SessaoResponse` ou `DesafioResponse { segundoFator: CODIGO | CADASTRO,
  desafio, expiraEm }` (OpenAPI com `oneOf`).
- `POST /api/auth/segundo-fator/cadastro { desafio }` → `{ segredo, uri }`.
- `POST /api/auth/segundo-fator { desafio, codigo | codigoRecuperacao }` → `SessaoResponse` com o cookie
  de refresh e, só na ativação, `codigosRecuperacao` (`@JsonInclude(NON_EMPTY)`).

**Comando** `redefinir-2fa <email>` (`ComandoRedefinirSegundoFator`, perfil `comando`): apaga o segundo
fator, revoga as sessões e avisa no terminal. O `ComandoCriarAdmin.foiPedido` reconhece os dois
comandos e cada um ignora os argumentos do outro.

**Seed do e2e** — `SeedSegundoFatorE2e` (perfil `e2e`, `@Order(2)`, depois do `SeedE2e`, `@Order(1)`):
ativa o segundo fator do admin do seed com o segredo Base32 de `SEED_E2E_TOTP`.

**Front** (`compartilhado/src/sessao`):

- `ProvedorSessao.entrar` devolve `{ tipo: 'sessao', usuario } | { tipo: 'desafio', desafio }`.
- `SegundoFator.tsx`: no cadastro, chama a rota de cadastro e desenha o QR code com o pacote `qrcode`
  (`toDataURL`; `npm install qrcode` e `npm install -D @types/qrcode`), mostra a chave em grupos de 4;
  campo "Código do aplicativo" (`autoComplete="one-time-code"`), alternativa "Sem o celular? Use um
  código de recuperação", "Voltar"; na ativação, a tela "Guarde os códigos de recuperação" e só no
  "Guardei os códigos, continuar" a sessão é entregue ao cliente da API.
- `segundoFator.ts` (funções puras com teste): `limparCodigo`, `erroCodigo`, `erroCodigoRecuperacao`,
  `segredoEmGrupos`.
- As páginas de entrada do ADM e do aluno mostram o `SegundoFator` quando o login devolve o desafio.

### 2. Usuário do banco só de dados

- `PlataformaCursosApplication`: com o argumento `migrar`, sobe sem servidor HTTP com o perfil `migrar`
  (`application-migrar.yaml`: Flyway ligado, fila desligada), aplica as migrations e termina.
- `db/migration/afterMigrate.sql` (callback do Flyway) com os placeholders `usuario_app` e `senha_app`
  (`spring.flyway.placeholders` de `DB_APP_USERNAME`/`DB_APP_PASSWORD`; em branco não faz nada): cria ou
  atualiza o papel com `LOGIN PASSWORD`, `GRANT CONNECT`, `USAGE` no schema, `REVOKE CREATE`, `SELECT,
  INSERT, UPDATE, DELETE` em todas as tabelas, `USAGE, SELECT` nas sequências e `REVOKE ALL` no
  `flyway_schema_history`.
- `compose.prod.yml`: serviço `migracao` (imagem da API, `command: ["migrar"]`, `restart: "no"`, dono do
  schema e `DB_APP_*`), a API com `depends_on: migracao: service_completed_successfully`; o `x-app` passa a
  usar `POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD` com `SPRING_FLYWAY_ENABLED: "false"`. No
  `compose.e2e.yml`, o `migracao` usa a imagem `plataforma-cursos-api:e2e`.
- `gerar-env-servidor.sh` gera `POSTGRES_APP_USER=plataforma_cursos_app` e a senha; o `deploy.sh`
  ganha `completar_env`, que acrescenta ao `.env` `POSTGRES_APP_USER`, `POSTGRES_APP_PASSWORD` e
  `TOTP_CHAVE` que ainda não existirem. O ambiente local (`compose.yml`) segue com um usuário só.
- Teste (`UsuarioAplicacaoBancoIT`, placeholder `app_teste` no perfil de teste): o usuário da aplicação
  faz `SELECT`/`INSERT`/`UPDATE`/`DELETE` e recebe `42501` em `CREATE TABLE`, `DROP`, `ALTER`,
  `TRUNCATE` e na leitura do histórico do Flyway.

### 3. Política de senha (`usuario/domain/PoliticaSenha`)

- `validar(senha, minimo, String... pessoais)` devolve a primeira mensagem ou `null`: obrigatória;
  `MINIMO_ALUNO = 10`, `MINIMO_ADMIN = 12`, `MAXIMO = 128`; **comum** ("Esta senha é muito comum ou já
  apareceu em vazamentos. Escolha outra.") pela lista em `src/main/resources/seguranca/senhas-comuns.txt`,
  carregada uma vez (holder preguiçoso) e comparada em minúsculas; **pessoal** ("A senha não pode conter
  o seu nome ou o seu e-mail.") com trechos de 4 ou mais caracteres do valor inteiro, da parte local do
  e-mail e das palavras; **previsível** ("A senha é fácil de adivinhar (sequência ou repetição). Escolha
  outra.") com no máximo 2 caracteres distintos, bloco repetido (`(.{1,4})\1+`) ou trecho de uma
  sequência (dígitos, alfabeto, linhas e diagonais do teclado, nos dois sentidos).
- Gere a lista (senhas de 10 a 128 caracteres de uma lista pública de 1 milhão de senhas vazadas, em
  minúsculas, sem repetição; o `grep -v` final tira 5 entradas que não fazem sentido no projeto):

  ```bash
  mkdir -p backend/src/main/resources/seguranca
  curl -fsSL https://raw.githubusercontent.com/danielmiessler/SecLists/4c0fd59432ab8508edc23b3c880127edb12e34d5/Passwords/Common-Credentials/xato-net-10-million-passwords-1000000.txt \
    | awk 'length($0)>=10 && length($0)<=128' | tr 'A-Z' 'a-z' | LC_ALL=C sort -u | grep -viE 'p[j]c' \
    > backend/src/main/resources/seguranca/senhas-comuns.txt
  wc -l backend/src/main/resources/seguranca/senhas-comuns.txt   # 117567
  ```

- Aplique no cadastro (antes de qualquer outra regra, com nome e e-mail), na redefinição (confere o
  dono do link sem consumi-lo: `TokenUsoUnicoService.dono`; mínimo pelo papel), no perfil (campo
  `novaSenha`), no `criar-admin` e no seed. Recusa no formato da validação:
  `shared/erro/CampoInvalidoException(campo, mensagem)` (400 com `erros[]`).
- Front: `CampoSenha` com `forca` e `politica={{ minimo: SENHA_MINIMA_ALUNO }}` no cadastro, na
  redefinição e no perfil; dica "Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas
  mais usadas."; o erro do campo `senha` vindo da API aparece no campo.

### 4. Limite por usuário

`shared/security/LimitePorUsuarioFilter` (Bucket4j + Caffeine, relógio da aplicação), depois da
segurança: só `POST/PUT/PATCH/DELETE` em `/api/` com `JwtAuthenticationToken`; balde por usuário de
`app.limites-usuario.escritas-por-minuto` (120) e, em `POST /api/pagamentos/checkout`, outro de
`checkouts-por-hora` (10); estourou: 429 em ProblemDetail com `Retry-After`. Nos testes de integração os
limites ficam altos (o admin do seed grava a suíte toda) e o filtro tem teste unitário próprio; na stack
de e2e, `APP_LIMITES_USUARIO_ESCRITAS_POR_MINUTO=3000` e `..._CHECKOUTS_POR_HORA=300`.

### 5. Dependências

- `.github/dependabot.yml`: Maven (`/backend`), npm (`/web`), GitHub Actions, Docker (`/backend`,
  `/web`) e docker-compose (`/infra`, `/infra/caddy`), semanal (segunda, 6h de Brasília), PRs para a
  `homolog`, patch e minor agrupados, mensagens `chore(deps)`/`ci(deps)`.
- Pipeline, job do front: `npm audit --omit=dev --audit-level=high` depois do `npm ci`.

### 6. Usuários no ADM

- `Usuario`: `sessoesEncerradasEm`, `bloquear(agora)` (inativa e encerra as sessões),
  `desbloquear(agora)`, `encerrarSessoes(agora)`; `UsuarioRepository.buscar(busca, papel, ativo,
  pageable)` (nome ou e-mail, sem diferenciar maiúsculas); `UsuarioService.listar/bloquear/desbloquear/
  encerrarSessoes`.
- `auth/application/UsuariosAdmService` (o admin não bloqueia a si mesmo: 409) revoga os refresh tokens
  e esquece a conta no `SessaoValidaValidator`: um `OAuth2TokenValidator<Jwt>` no `JwtDecoder` que recusa
  conta inativa, inexistente ou token com `iat` anterior ao encerramento (truncado em segundos), com a
  situação da conta em Caffeine por 30 s.
- `UsuariosAdmController` em `/api/adm/usuarios` (`@PreAuthorize`): `GET` com `busca`, `papel`, `ativo`
  e paginação; `PATCH /{id}/situacao { ativo }`; `POST /{id}/encerrar-sessoes` (204). Resposta
  `UsuarioAdm` com papéis, situação, e-mail confirmado, segundo fator ativo, encerramento e criação.
- Tela `adm/src/paginas/usuarios/UsuariosPagina.tsx` no menu Segurança (antes da Auditoria): filtros,
  tabela, ações "Encerrar as sessões de ..." e "Bloquear/Desbloquear ..." com confirmação, sem ações na
  linha do próprio admin.

### 7. Checkout seguro

- `CampoConfiguracao` ganha `hostsPermitidos`, as fábricas `selecao(...)` e `url(...)` e
  `erroUrl(valor, hosts)`: só `https`, com host e sem credencial na URL; com hosts, só eles. A Hotmart
  declara `HOSTS_CHECKOUT = pay.hotmart.com, sandbox.hotmart.com` e recusa no checkout (409) um endereço
  gravado fora da regra.
- Front: `checkoutSeguro(url)` (só `https:`) antes de sair da plataforma.

### 8. Token fora da barra de endereço

`compartilhado/src/ui/useTokenDaUrl.ts`: lê o `token` uma vez e troca a URL (`replace`) pelos outros
parâmetros (`semToken`, com teste). Usado em `ConfirmarEmailPagina` e `RedefinirSenhaPagina`.

### 9. Compra com confirmação

- `GET /api/pagamentos/ofertas/{id}` (logado): `CheckoutService.resumo` com as mesmas regras do checkout
  (oferta ativa, meio ativo, curso publicado e turma ativa) → `ResumoOferta { id, titulo,
  precoCentavos, moeda, curso, turma, meioPagamento }`.
- `ComprarPagina`: mostra curso, turma, oferta e valor ("Confirmar a compra") e só chama o checkout no
  botão "Ir para o pagamento".

### 10. Varredura das rotas

`RotasProtegidasIT` lista todas as rotas `/api/**` do `RequestMappingHandlerMapping`: sem login, toda
rota fora da lista de públicas responde 401 "Autenticação necessária."; o aluno recebe 403 em
`/api/adm/**`; do Actuator só o health responde; a documentação fica fora.

### 11. Testes de apoio

- `IntegrationTest.entrar`: se o login devolver o desafio, cadastra o aplicativo (guardando o segredo por
  e-mail) e conclui com o código calculado; `codigoTotp(email)` e `concluirSegundoFator(...)`.
- e2e Java: `support/Totp.java` e `E2eConfig.TOTP_SEED`; `Api.loginSemAceite` conclui a segunda etapa.
- Playwright: `support/totp.ts` (`node:crypto`), `TOTP_SEED` no `config.ts`, segunda etapa em
  `entrarPelaApi`, `clienteApi` e `confirmarSegundoFator` (`telas.ts`); `acesso.spec.ts` com o código
  certo e o errado; `usuarios.spec.ts`; `compra.spec.ts` com o "Ir para o pagamento".
- Variáveis: `TOTP_CHAVE` em `application-test.yaml`, `infra/.env.example`, `gerar-env.sh`,
  `gerar-env-servidor.sh`, compose e `infra/e2e.env` (com `POSTGRES_APP_*` e `SEED_E2E_TOTP`).

### 12. Documentação

`docs/operacao.md` (ASVS, segundo fator, `redefinir-2fa`, usuário do banco, restauração), `docs/vps.md`
(variáveis novas e o primeiro login do admin), `README.md` (segredo do admin de exemplo no aplicativo,
`redefinir-2fa`), `web/e2e/README.md`, plano (Fase 19) e capítulos (Parte 12).

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos removidos (ou movidos para outro caminho)

- `backend/src/main/java/br/com/celke/cursos/pagamento/application/CifraSegredos.java`
- `backend/src/test/java/br/com/celke/cursos/pagamento/application/CifraSegredosTest.java`

### Arquivos alterados (substitua o conteúdo inteiro)

`.github/workflows/pipeline.yml`:

`````yaml
# Testes, build das imagens e e2e em todo PR para homolog e main. Depois do merge (push), refaz tudo
# com o código exato do commit, publica no GHCR as imagens que passaram no e2e (tag = SHA) e implanta:
# push na homolog → homologação; push na main → produção. Os deploys só rodam com a variável do
# repositório DEPLOY_ATIVO=true (criada quando a VPS estiver pronta); sem ela, são pulados.
name: Pipeline

on:
  pull_request:
    branches: [homolog, main]
  push:
    branches: [homolog, main]
  workflow_dispatch:

concurrency:
  group: pipeline-${{ github.ref }}
  # Só PRs cancelam execuções antigas: um deploy nunca é interrompido no meio
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

permissions:
  contents: read

env:
  COMPOSE_E2E: docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env

jobs:
  testes:
    name: Testes (unitários e integração)
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      # A transcodificação de vídeo é testada com o ffmpeg de verdade; o apt só roda quando a imagem do
      # runner não traz o ffmpeg
      - name: Instalar ffmpeg
        timeout-minutes: 10
        run: |
          if command -v ffmpeg > /dev/null && command -v ffprobe > /dev/null; then
            ffmpeg -version | head -1
          else
            sudo apt-get update -q && sudo apt-get install -y -q --no-install-recommends ffmpeg
          fi

      # Unitários, integração (Testcontainers), cobertura mínima e formatação
      - name: Verificar
        run: ./mvnw -B verify

      - name: Relatórios dos testes
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: relatorios-testes
          path: |
            backend/target/surefire-reports
            backend/target/failsafe-reports

      - name: Relatório de cobertura
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: cobertura
          path: backend/target/site/jacoco
          if-no-files-found: ignore

  frontend:
    name: Frontend (lint, tipos, unitários e build)
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: web
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-node@v6
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: web/package-lock.json

      # O lock guarda o kole-react como git+ssh; o repositório é público e vem por HTTPS
      - name: Dependências
        run: |
          git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
          npm ci --no-audit --no-fund

      # Falha com vulnerabilidade alta ou crítica nas dependências que vão para o navegador
      - name: Auditoria das dependências
        run: npm audit --omit=dev --audit-level=high
      - run: npm run format:check
      - run: npm run lint
      - run: npm run typecheck
      - run: npm test
      - run: npm run build

  e2e:
    name: e2e (stack completa)
    needs: [testes, frontend]
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - uses: docker/setup-buildx-action@v4

      # As mesmas imagens que vão para os servidores, com cache de camadas entre execuções
      - name: Imagem da API
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: api
          tags: plataforma-cursos-api:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=api
          cache-to: type=gha,mode=max,scope=api

      - name: Imagem do worker
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: worker
          tags: plataforma-cursos-worker:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=worker
          cache-to: type=gha,mode=max,scope=worker

      - name: Imagem dos fronts
        uses: docker/build-push-action@v7
        with:
          context: web
          target: web
          tags: plataforma-cursos-web:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web
          cache-to: type=gha,mode=max,scope=web

      - name: Imagem do gerador do site
        uses: docker/build-push-action@v7
        with:
          context: web
          target: site
          tags: plataforma-cursos-site:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web

      # Vulnerabilidades críticas com correção disponível nas imagens (sistema e dependências Java)
      - name: Varredura da imagem da API (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-api:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do worker (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-worker:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem dos fronts (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-web:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do site (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-site:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

      - uses: actions/setup-node@v6
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: web/package-lock.json

      # Navegador real contra os fronts servidos pelo container web, atrás do Caddy
      - name: Jornadas e2e dos fronts (Playwright)
        working-directory: web
        run: |
          git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
          npm ci --no-audit --no-fund
          npx playwright install --with-deps chromium
          npm run e2e

      # Meta de Lighthouse do site público (desempenho, acessibilidade, boas práticas e SEO)
      - name: Lighthouse do site
        working-directory: web
        run: npm run lighthouse

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker web site; do
            docker tag "plataforma-cursos-$imagem:e2e" "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
            docker push "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
          done

      - name: Logs da stack
        if: failure()
        run: $COMPOSE_E2E logs --no-color > stack-e2e.log

      - name: Relatórios do e2e
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: e2e-falha
          path: |
            stack-e2e.log
            backend/target/failsafe-reports
            web/playwright-report
            web/test-results

      - name: Derrubar a stack de e2e
        if: always()
        run: $COMPOSE_E2E down -v

  deploy-homolog:
    name: Homologação
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/homolog' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: homolog
    secrets: inherit

  deploy-producao:
    name: Produção
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: producao
    secrets: inherit
`````

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
  ofertas, pedidos e notificações recebidas; usuários (bloquear e encerrar sessões) e trilha de
  auditoria.
- **Site público:** home, catálogo, página do curso com preço e botão de compra, validação de
  certificado, termos e privacidade, com SEO (Open Graph, dados estruturados, sitemap).
- **Conta e segurança:** cadastro com confirmação de e-mail, login com JWT e refresh em cookie
  `HttpOnly`, verificação em duas etapas (aplicativo autenticador) obrigatória para administradores,
  senhas comuns e vazadas recusadas, recuperação de senha, bloqueio por tentativas, limites por IP e por
  usuário, termos versionados com aceite, usuário do banco da aplicação sem permissão de mudar tabelas.
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
git clone https://github.com/celkecursos/plataforma-cursos.git
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
| Administrador | `admin@celke.com.br` | `123456A#b123` |
| Aluno | `aluno@celke.com.br` | `123456A#b123` |

O administrador entra em duas etapas: depois da senha, informe o código de 6 dígitos de um aplicativo
autenticador (Google Authenticator, Microsoft Authenticator, 1Password...). No aplicativo, adicione uma
conta pela chave `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP` (tipo "baseado em tempo"): é o segredo do admin de
exemplo, definido em `SEED_E2E_TOTP` no `infra/e2e.env`.

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

**3. Criar o administrador** (pede a senha no terminal; mínimo de 12 caracteres, sem o nome ou o
e-mail e fora das senhas mais usadas)

```bash
docker compose -f infra/compose.yml run --rm api criar-admin admin@exemplo.com "Administrador"
```

No primeiro login do ADM, leia o QR code com um aplicativo autenticador e guarde os códigos de
recuperação que aparecem em seguida.

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

# Administrador perdeu o celular e os códigos de recuperação: o próximo login pede o QR code de novo
docker compose -f infra/compose.yml run --rm api redefinir-2fa admin@exemplo.com
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

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Api.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Requisições à plataforma pelo Caddy, como um navegador faria. Todos os sites respondem na mesma
 * porta; o cabeçalho Host escolhe qual (sem depender da resolução de {@code *.localhost}).
 */
public final class Api {

    private Api() {}

    public static RequestSpecification aluno() {
        return site(E2eConfig.HOST_ALUNO);
    }

    public static RequestSpecification adm() {
        return site(E2eConfig.HOST_ADM);
    }

    public static RequestSpecification site(String host) {
        return given().baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", host + ":" + E2eConfig.PORTA)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    /**
     * Login pela área do aluno; confere o 200, aceita os termos vigentes ainda pendentes (como o front faz
     * no primeiro acesso) e devolve a resposta do login (access token e cookie).
     */
    public static Response login(String email, String senha) {
        Response resposta = loginSemAceite(email, senha);
        aceitarTermos(resposta.jsonPath().getString("accessToken"));
        return resposta;
    }

    /**
     * Login sem tocar nos termos (jornada que confere o bloqueio por termo pendente). O admin do seed
     * conclui a segunda etapa com o código do aplicativo autenticador, calculado pelo segredo do seed.
     */
    public static Response loginSemAceite(String email, String senha) {
        Response resposta = aluno().body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String desafio = resposta.jsonPath().getString("desafio");
        if (desafio == null) {
            return resposta;
        }
        return aluno().body("{\"desafio\":\"%s\",\"codigo\":\"%s\"}"
                        .formatted(desafio, Totp.codigoAtual(E2eConfig.TOTP_SEED)))
                .post("/api/auth/segundo-fator")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    /** Aceita todos os termos vigentes que o usuário do token ainda não aceitou. */
    public static void aceitarTermos(String accessToken) {
        var pendentes = aluno().header("Authorization", bearer(accessToken))
                .get("/api/termos/pendentes")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id", Long.class);
        for (Long id : pendentes) {
            aluno().header("Authorization", bearer(accessToken))
                    .post("/api/termos/" + id + "/aceite")
                    .then()
                    .statusCode(204);
        }
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Contas.java`:

`````java
package br.com.celke.cursos.e2e.support;

/**
 * Conta própria de uma jornada, criada pelo caminho real (cadastro → e-mail → confirmação). Os
 * usuários do seed são só leitura: logout e sessões usam uma conta assim.
 */
public final class Contas {

    public static final String SENHA = "girassol-azul-da-jornada";

    private Contas() {}

    public static String criarConfirmada() {
        String email = Dados.email();
        Api.aluno()
                .body("{\"nome\":\"Conta e2e\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/cadastro")
                .then()
                .statusCode(202);
        String token = Mailpit.token(Mailpit.aguardar(email, "Confirme o seu e-mail"));
        Api.aluno()
                .body("{\"token\":\"%s\"}".formatted(token))
                .post("/api/auth/confirmar-email")
                .then()
                .statusCode(200);
        return email;
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/E2eConfig.java`:

`````java
package br.com.celke.cursos.e2e.support;

/**
 * Endereços da stack de e2e. Padrões iguais aos de {@code infra/e2e.env}; podem ser trocados por
 * propriedades do sistema ({@code -De2e.porta=...}).
 */
public final class E2eConfig {

    /** Porta do Caddy no host. Os sites são escolhidos pelo cabeçalho Host. */
    public static final int PORTA = Integer.getInteger("e2e.porta", 8088);

    public static final String MAILPIT = System.getProperty("e2e.mailpit", "http://localhost:8026");

    public static final String SENHA_SEED = System.getProperty("e2e.senha-seed", "123456A#b123");

    /** Segredo (Base32) do aplicativo autenticador do admin do seed ({@code SEED_E2E_TOTP}). */
    public static final String TOTP_SEED = System.getProperty("e2e.totp-seed", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");

    public static final String HOST_ALUNO = "aluno.localhost";
    public static final String HOST_ADM = "adm.localhost";

    public static final String ADMIN_SEED = "admin@celke.com.br";
    public static final String ALUNO_SEED = "aluno@celke.com.br";

    private E2eConfig() {}

    /** Origem que o navegador envia para um host da plataforma ({@code http://aluno.localhost:8088}). */
    public static String origem(String host) {
        return "http://" + host + ":" + PORTA;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/PlataformaCursosApplication.java`:

`````java
package br.com.celke.cursos;

import br.com.celke.cursos.usuario.api.ComandoCriarAdmin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlataformaCursosApplication {

    /** Comando (e perfil) que só aplica as migrations. */
    static final String MIGRAR = "migrar";

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlataformaCursosApplication.class);
        if (args.length > 0 && MIGRAR.equals(args[0])) {
            // Só as migrations, com o dono do schema (serviço migracao do compose): sobe, migra e termina
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles(MIGRAR);
            System.exit(SpringApplication.exit(app.run(args)));
        }
        if (ComandoCriarAdmin.foiPedido(args)) {
            // Comando de linha: sem servidor HTTP; termina ao fim do comando
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles(ComandoCriarAdmin.PERFIL);
            System.exit(SpringApplication.exit(app.run(args)));
        }
        app.run(args);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/AuthController.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.api.SegundoFatorDtos.CadastroRequest;
import br.com.celke.cursos.auth.api.SegundoFatorDtos.CadastroResponse;
import br.com.celke.cursos.auth.api.SegundoFatorDtos.ConfirmacaoRequest;
import br.com.celke.cursos.auth.api.SegundoFatorDtos.DesafioResponse;
import br.com.celke.cursos.auth.application.AuthService;
import br.com.celke.cursos.auth.application.AuthService.Desafio;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.SessaoInvalidaException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final CookieRefresh cookieRefresh;
    private final VerificadorOrigem verificadorOrigem;

    public AuthController(
            AuthService authService,
            UsuarioService usuarioService,
            CookieRefresh cookieRefresh,
            VerificadorOrigem verificadorOrigem) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.cookieRefresh = cookieRefresh;
        this.verificadorOrigem = verificadorOrigem;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Entrar com e-mail e senha",
            description = "Devolve o access token e grava o refresh token em cookie. Para administradores, "
                    + "devolve o desafio do segundo fator (sem sessão): a entrada termina em "
                    + "POST /api/auth/segundo-fator.")
    @ApiResponse(
            responseCode = "200",
            description = "Sessão aberta ou desafio do segundo fator",
            content = @Content(schema = @Schema(oneOf = {SessaoResponse.class, DesafioResponse.class})))
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return switch (authService.entrar(request.email(), request.senha(), OrigemRequisicao.de(http))) {
            case Sessao sessao -> comCookie(sessao);
            case Desafio desafio -> ResponseEntity.ok(DesafioResponse.de(desafio));
        };
    }

    @PostMapping("/segundo-fator/cadastro")
    @Operation(
            summary = "Cadastrar o aplicativo autenticador",
            description = "Primeiro acesso do administrador: devolve o segredo e o endereço do QR code. "
                    + "O cadastro só vale depois do primeiro código certo.")
    public CadastroResponse cadastrarSegundoFator(@Valid @RequestBody CadastroRequest request) {
        return CadastroResponse.de(authService.cadastrarSegundoFator(request.desafio()));
    }

    @PostMapping("/segundo-fator")
    @Operation(
            summary = "Concluir a entrada com o segundo fator",
            description = "Confere o código do aplicativo (ou um código de recuperação), devolve o access "
                    + "token e grava o refresh token em cookie. Na ativação, devolve os códigos de recuperação.")
    public ResponseEntity<SessaoResponse> concluirSegundoFator(
            @Valid @RequestBody ConfirmacaoRequest request, HttpServletRequest http) {
        var sessao = authService.concluirSegundoFator(
                request.desafio(), request.codigo(), request.codigoRecuperacao(), OrigemRequisicao.de(http));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieRefresh.criar(sessao.sessao().refresh().token()).toString())
                .body(SessaoResponse.de(sessao));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar a sessão",
            description = "Troca o refresh token do cookie por um novo e emite outro access token.")
    public ResponseEntity<SessaoResponse> refresh(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken, HttpServletRequest http) {
        verificadorOrigem.verificar(http);
        return comCookie(authService.renovar(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sair", description = "Revoga a sessão do cookie e apaga o cookie.")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken, HttpServletRequest http) {
        verificadorOrigem.verificar(http);
        authService.sair(refreshToken);
        return semCookie();
    }

    @PostMapping("/sair-de-todos")
    @Operation(summary = "Sair de todos os dispositivos", description = "Revoga todas as sessões do usuário.")
    public ResponseEntity<Void> sairDeTodos(@AuthenticationPrincipal Jwt jwt) {
        authService.sairDeTodos(Long.valueOf(jwt.getSubject()));
        return semCookie();
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário da sessão")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService
                .buscarPorId(Long.valueOf(jwt.getSubject()))
                .map(UsuarioResponse::de)
                .orElseThrow(SessaoInvalidaException::new);
    }

    private ResponseEntity<SessaoResponse> comCookie(Sessao sessao) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieRefresh.criar(sessao.refresh().token()).toString())
                .body(SessaoResponse.de(sessao));
    }

    private ResponseEntity<Void> semCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/LimitePorIpFilter.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.LimitesProperties;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de requisições por IP nas rotas de autenticação (token bucket do Bucket4j). Conta toda
 * requisição, até as inválidas, e roda antes da segurança e do Spring MVC: o excesso é barrado
 * sem calcular hash nem consultar o banco.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LimitePorIpFilter extends OncePerRequestFilter {

    /** Rotas limitadas (sempre POST). */
    static final Set<String> ROTAS = Set.of(
            "/api/auth/login",
            "/api/auth/segundo-fator",
            "/api/auth/segundo-fator/cadastro",
            "/api/auth/refresh",
            "/api/auth/cadastro",
            "/api/auth/confirmar-email",
            "/api/auth/reenviar-confirmacao",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha");

    static final String CABECALHO_RESTANTE = "X-RateLimit-Remaining";

    private static final Logger LOG = LoggerFactory.getLogger(LimitePorIpFilter.class);

    private final int limitePorMinuto;
    private final JsonMapper jsonMapper;
    private final TimeMeter relogio;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public LimitePorIpFilter(LimitesProperties props, JsonMapper jsonMapper, Clock clock) {
        this.limitePorMinuto = props.requisicoesPorIpPorMinuto();
        this.jsonMapper = jsonMapper;
        this.relogio = new RelogioBucket(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !ROTAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        ConsumptionProbe consumo = baldes.get(ip, chave -> novoBalde()).tryConsumeAndReturnRemaining(1);
        response.setHeader(CABECALHO_RESTANTE, String.valueOf(consumo.getRemainingTokens()));
        if (consumo.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        MuitasTentativasException limite =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn("Limite de requisições por IP atingido em {}", request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limite.getMessage());
    }

    private Bucket novoBalde() {
        return Bucket.builder()
                .addLimit(
                        limite -> limite.capacity(limitePorMinuto).refillGreedy(limitePorMinuto, Duration.ofMinutes(1)))
                .withCustomTimePrecision(relogio)
                .build();
    }

    /** Relógio do Bucket4j a partir do {@link Clock} da aplicação (testável). */
    record RelogioBucket(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(clock.millis()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/SessaoResponse.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.AuthService.SessaoSegundoFator;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Resposta do login e do refresh. O access token vai no corpo e o front o guarda só em memória;
 * o refresh token vai apenas no cookie {@code HttpOnly}. Os códigos de recuperação do segundo fator
 * só aparecem uma vez, quando ele acaba de ser ativado.
 */
public record SessaoResponse(
        String accessToken,
        String tipo,
        Instant expiraEm,
        UsuarioResponse usuario,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> codigosRecuperacao) {

    public static SessaoResponse de(Sessao sessao) {
        return new SessaoResponse(
                sessao.acesso().valor(),
                "Bearer",
                sessao.acesso().expiraEm(),
                UsuarioResponse.de(sessao.usuario()),
                List.of());
    }

    public static SessaoResponse de(SessaoSegundoFator sessao) {
        SessaoResponse base = de(sessao.sessao());
        return new SessaoResponse(
                base.accessToken(), base.tipo(), base.expiraEm(), base.usuario(), sessao.codigosRecuperacao());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.SegundoFatorService.Cadastro;
import br.com.celke.cursos.auth.application.SegundoFatorService.Verificacao;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (com bloqueio por falhas seguidas e auditoria), segundo fator dos administradores, renovação e
 * saída.
 */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioLoginService bloqueioLoginService;
    private final AuditoriaLoginService auditoriaLoginService;
    private final SegundoFatorService segundoFatorService;
    private final TokenUsoUnicoService tokens;
    private final Clock clock;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService,
            BloqueioLoginService bloqueioLoginService,
            AuditoriaLoginService auditoriaLoginService,
            SegundoFatorService segundoFatorService,
            TokenUsoUnicoService tokens,
            Clock clock) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueioLoginService = bloqueioLoginService;
        this.auditoriaLoginService = auditoriaLoginService;
        this.segundoFatorService = segundoFatorService;
        this.tokens = tokens;
        this.clock = clock;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Confere o bloqueio antes de tudo (sem calcular hash), depois a senha. Falha e sucesso
     * atualizam os contadores de bloqueio e ficam na auditoria. Toda falha recebe a mesma mensagem;
     * só quem acertou a senha fica sabendo que falta confirmar o e-mail. O administrador que acerta a
     * senha recebe um desafio (sem sessão) para a segunda etapa.
     */
    @Transactional
    public Entrada entrar(String email, String senha, OrigemRequisicao origem) {
        try {
            bloqueioLoginService.verificar(email, origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(email, null, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        // Conta sem senha (criada por uma compra) confere o hash fictício e falha como senha errada
        boolean possuiSenha = encontrado.filter(Usuario::possuiSenha).isPresent();
        String hash = possuiSenha ? encontrado.get().getSenhaHash() : hashFicticio;
        boolean senhaConfere = passwordEncoder.matches(senha, hash) && possuiSenha;
        ResultadoLogin resultado = resultado(encontrado, senhaConfere);
        if (resultado == ResultadoLogin.SUCESSO && SegundoFatorService.exigido(encontrado.get())) {
            resultado = ResultadoLogin.SEGUNDO_FATOR_PENDENTE;
        }
        auditoriaLoginService.registrar(email, encontrado.orElse(null), resultado, origem);
        if (resultado == ResultadoLogin.EMAIL_NAO_CONFIRMADO) {
            // Quem chegou aqui sabe a senha: pode saber que falta confirmar o e-mail
            bloqueioLoginService.registrarSucesso(email, origem.ip());
            throw new EmailNaoConfirmadoException();
        }
        if (resultado != ResultadoLogin.SUCESSO && resultado != ResultadoLogin.SEGUNDO_FATOR_PENDENTE) {
            bloqueioLoginService.registrarFalha(email, origem.ip());
            throw new CredenciaisInvalidasException();
        }
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        if (resultado == ResultadoLogin.SEGUNDO_FATOR_PENDENTE) {
            // Os contadores de falha só zeram quando a segunda etapa passar
            String desafio = tokens.emitir(usuario.getId(), TipoToken.DESAFIO_SEGUNDO_FATOR)
                    .orElseThrow();
            Instant expiraEm = clock.instant().plus(tokens.validade(TipoToken.DESAFIO_SEGUNDO_FATOR));
            return new Desafio(desafio, !segundoFatorService.ativo(usuario.getId()), expiraEm);
        }
        bloqueioLoginService.registrarSucesso(email, origem.ip());
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    /** Primeiro acesso do administrador: gera o segredo do aplicativo autenticador. */
    @Transactional
    public Cadastro cadastrarSegundoFator(String desafio) {
        return segundoFatorService.iniciarCadastro(donoDoDesafio(desafio));
    }

    /**
     * Segunda etapa: confere o código do aplicativo (ou de recuperação) e abre a sessão. Código errado
     * conta no bloqueio como uma senha errada; o desafio vale até vencer ou ser usado.
     */
    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public SessaoSegundoFator concluirSegundoFator(
            String desafio, String codigo, String codigoRecuperacao, OrigemRequisicao origem) {
        Usuario usuario = donoDoDesafio(desafio);
        try {
            bloqueioLoginService.verificar(usuario.getEmail(), origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(usuario.getEmail(), usuario, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Verificacao verificacao = segundoFatorService.verificar(usuario, codigo, codigoRecuperacao);
        if (!verificacao.valida()) {
            auditoriaLoginService.registrar(usuario.getEmail(), usuario, ResultadoLogin.SEGUNDO_FATOR_INVALIDO, origem);
            bloqueioLoginService.registrarFalha(usuario.getEmail(), origem.ip());
            throw new CodigoInvalidoException();
        }
        tokens.consumir(desafio, TipoToken.DESAFIO_SEGUNDO_FATOR).orElseThrow(DesafioInvalidoException::new);
        auditoriaLoginService.registrar(usuario.getEmail(), usuario, verificacao.resultado(), origem);
        bloqueioLoginService.registrarSucesso(usuario.getEmail(), origem.ip());
        Sessao sessao = abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
        return new SessaoSegundoFator(sessao, verificacao.codigosRecuperacao());
    }

    private Usuario donoDoDesafio(String desafio) {
        return tokens.dono(desafio, TipoToken.DESAFIO_SEGUNDO_FATOR)
                .flatMap(usuarioService::buscarPorId)
                .filter(Usuario::isAtivo)
                .orElseThrow(DesafioInvalidoException::new);
    }

    private static ResultadoLogin resultado(Optional<Usuario> encontrado, boolean senhaConfere) {
        if (encontrado.isEmpty()) {
            return ResultadoLogin.EMAIL_DESCONHECIDO;
        }
        if (!senhaConfere) {
            return ResultadoLogin.SENHA_INVALIDA;
        }
        if (!encontrado.get().isAtivo()) {
            return ResultadoLogin.USUARIO_INATIVO;
        }
        return encontrado.get().isEmailConfirmado() ? ResultadoLogin.SUCESSO : ResultadoLogin.EMAIL_NAO_CONFIRMADO;
    }

    /**
     * Troca o refresh token por um novo e emite outro access token. Sem transação própria: a
     * revogação de uma família por reutilização precisa ficar gravada mesmo com a sessão recusada.
     */
    public Sessao renovar(String refreshToken) {
        RefreshEmitido novo = refreshTokenService.rotacionar(refreshToken).orElseThrow(SessaoInvalidaException::new);
        Usuario usuario = usuarioService
                .buscarPorId(novo.usuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(SessaoInvalidaException::new);
        return abrirSessao(usuario, novo);
    }

    public void sair(String refreshToken) {
        refreshTokenService.revogarFamiliaDoToken(refreshToken);
    }

    public void sairDeTodos(Long usuarioId) {
        refreshTokenService.revogarTodas(usuarioId);
    }

    private Sessao abrirSessao(Usuario usuario, RefreshEmitido refresh) {
        return new Sessao(usuario, tokenAcessoService.emitir(usuario), refresh);
    }

    /** Resultado da primeira etapa do login: a sessão ou o desafio do segundo fator. */
    public sealed interface Entrada permits Sessao, Desafio {}

    public record Sessao(Usuario usuario, TokenAcesso acesso, RefreshEmitido refresh) implements Entrada {}

    /** {@code cadastro}: o administrador ainda não cadastrou o aplicativo autenticador. */
    public record Desafio(String token, boolean cadastro, Instant expiraEm) implements Entrada {}

    /** Os códigos de recuperação só vêm quando o segundo fator acabou de ser ativado. */
    public record SessaoSegundoFator(Sessao sessao, List<String> codigosRecuperacao) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/CadastroService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.CampoInvalidoException;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro do aluno e confirmação do e-mail. A resposta ao cliente é a mesma para e-mail novo e já
 * cadastrado; quem descobre o que aconteceu é o dono da caixa, pelo e-mail que recebe.
 */
@Service
public class CadastroService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final ContaProperties props;

    /** Contas que já receberam o aviso de "cadastro com e-mail existente" na última hora. */
    private final Cache<Long, Boolean> avisosEnviados = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public CadastroService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.props = props;
    }

    /**
     * E-mail novo: cria a conta pendente e envia o link de confirmação. E-mail com cadastro pendente:
     * reenvia o link (a senha informada é ignorada). E-mail de conta ativa: avisa o dono.
     */
    @Transactional
    public void cadastrar(String nome, String email, String senha) {
        // A política vale antes de tudo: a recusa não depende de a conta existir (não revela quem tem cadastro)
        String problema = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO, nome, email);
        if (problema != null) {
            throw new CampoInvalidoException("senha", problema);
        }
        Optional<Usuario> existente = usuarioService.buscarPorEmail(email);
        if (existente.isPresent()) {
            tratarEmailExistente(existente.get());
            return;
        }
        try {
            enviarConfirmacao(usuarioService.cadastrarAluno(nome, email, senha));
        } catch (ConflitoException e) {
            // Outro cadastro com o mesmo e-mail chegou junto: a resposta continua a mesma
        }
    }

    @Transactional
    public void reenviarConfirmacao(String email) {
        usuarioService
                .buscarPorEmail(email)
                .filter(u -> u.isAtivo() && !u.isEmailConfirmado())
                .ifPresent(this::enviarConfirmacao);
    }

    @Transactional
    public void confirmarEmail(String token) {
        Long usuarioId = tokens.consumir(token, TipoToken.CONFIRMACAO_EMAIL).orElseThrow(LinkInvalidoException::new);
        Usuario usuario = usuarioService.buscarPorId(usuarioId).orElseThrow(LinkInvalidoException::new);
        usuarioService.confirmarEmail(usuario);
    }

    private void tratarEmailExistente(Usuario usuario) {
        if (!usuario.isAtivo()) {
            return;
        }
        if (!usuario.isEmailConfirmado()) {
            enviarConfirmacao(usuario);
            return;
        }
        if (avisosEnviados.asMap().putIfAbsent(usuario.getId(), Boolean.TRUE) == null) {
            emailService.enviar(
                    usuario.getEmail(),
                    "Tentativa de cadastro com o seu e-mail",
                    "cadastro-existente",
                    Map.of(
                            "nome", usuario.getNome(),
                            "linkEntrar", props.urlAluno() + "/entrar",
                            "linkRecuperar", props.urlAluno() + "/recuperar-senha"));
        }
    }

    private void enviarConfirmacao(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.CONFIRMACAO_EMAIL)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Confirme o seu e-mail",
                        "confirmacao-email",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/confirmar-email?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.CONFIRMACAO_EMAIL)))));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/JwtConfig.java`:

`````java
package br.com.celke.cursos.auth.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Assinatura e validação do access token: HMAC-SHA256 com a chave de {@code app.auth.jwt-segredo}.
 * O decoder aceita só HS256 (token com {@code alg} trocado ou {@code none} é recusado), confere o
 * emissor e a validade sem tolerância.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(AuthProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties props, Clock clock, SessaoValidaValidator sessaoValida) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator validade = new JwtTimestampValidator(Duration.ZERO);
        validade.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(validade, new JwtIssuerValidator(props.emissor()), sessaoValida)));
        return decoder;
    }

    private static SecretKey chave(AuthProperties props) {
        return new SecretKeySpec(props.chaveJwt(), "HmacSHA256");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/RecuperacaoSenhaService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.CampoInvalidoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de senha por link enviado ao e-mail. A resposta ao cliente é a mesma exista ou não a
 * conta. Ao redefinir, todas as sessões abertas são encerradas.
 */
@Service
public class RecuperacaoSenhaService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final ContaProperties props;

    public RecuperacaoSenhaService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.props = props;
    }

    @Transactional
    public void solicitar(String email) {
        usuarioService.buscarPorEmail(email).filter(Usuario::isAtivo).ifPresent(this::enviarLink);
    }

    /**
     * Troca a senha pelo link. A política (com o mínimo do papel: admin exige mais) é conferida antes de
     * usar o link, para a pessoa poder tentar outra senha. Também confirma o e-mail (o link chegou à
     * caixa do dono) e encerra todas as sessões, inclusive a de quem tiver descoberto a senha antiga.
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Usuario usuario = tokens.dono(token, TipoToken.RECUPERACAO_SENHA)
                .flatMap(usuarioService::buscarPorId)
                .filter(Usuario::isAtivo)
                .orElseThrow(LinkInvalidoException::new);
        int minimo =
                usuario.getPapeis().contains(Papel.ADMIN) ? PoliticaSenha.MINIMO_ADMIN : PoliticaSenha.MINIMO_ALUNO;
        String problema = PoliticaSenha.validar(novaSenha, minimo, usuario.getNome(), usuario.getEmail());
        if (problema != null) {
            throw new CampoInvalidoException("senha", problema);
        }
        tokens.consumir(token, TipoToken.RECUPERACAO_SENHA).orElseThrow(LinkInvalidoException::new);
        usuarioService.trocarSenha(usuario, novaSenha);
        usuarioService.confirmarEmail(usuario);
        refreshTokenService.revogarTodas(usuario.getId());
    }

    /**
     * Link de "criar senha" para a conta criada por uma compra (o mesmo token da recuperação, com a
     * mesma validade). Vazio se a conta já recebeu o máximo de links na última hora.
     */
    @Transactional
    public Optional<LinkSenha> linkCriarSenha(Long usuarioId) {
        return tokens.emitir(usuarioId, TipoToken.RECUPERACAO_SENHA)
                .map(token -> new LinkSenha(
                        props.urlAluno() + "/redefinir-senha?token=" + token + "&origem=pagamento",
                        Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA))));
    }

    /** Link de criar senha e a validade escrita para o e-mail ("1 hora"). */
    public record LinkSenha(String url, String validade) {}

    private void enviarLink(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.RECUPERACAO_SENHA)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/redefinir-senha?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA)))));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/TokenUsoUnicoService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import br.com.celke.cursos.auth.infra.TokenUsoUnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens dos links enviados por e-mail. Emitir um novo invalida os anteriores do mesmo tipo; cada
 * conta recebe no máximo {@code emailsPorHora} tokens de um tipo por hora.
 */
@Service
@EnableConfigurationProperties(ContaProperties.class)
public class TokenUsoUnicoService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenUsoUnicoService.class);

    /** Tempo para digitar o código do aplicativo depois de acertar a senha. */
    static final Duration VALIDADE_DESAFIO = Duration.ofMinutes(5);

    private final TokenUsoUnicoRepository repository;
    private final ContaProperties props;
    private final Clock clock;

    public TokenUsoUnicoService(TokenUsoUnicoRepository repository, ContaProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Devolve o token em texto, ou vazio se a conta já recebeu o máximo de e-mails na última hora. */
    @Transactional
    public Optional<String> emitir(Long usuarioId, TipoToken tipo) {
        Instant agora = clock.instant();
        long naUltimaHora =
                repository.countByUsuarioIdAndTipoAndCriadoEmAfter(usuarioId, tipo, agora.minus(Duration.ofHours(1)));
        if (tipo.porEmail() && naUltimaHora >= props.emailsPorHora()) {
            LOG.warn("Limite de e-mails de {} atingido para o usuário {}", tipo, usuarioId);
            return Optional.empty();
        }
        repository.invalidarPendentes(usuarioId, tipo, agora);
        String token = TokenSeguro.gerar();
        repository.save(new TokenUsoUnico(usuarioId, tipo, TokenSeguro.hash(token), agora.plus(validade(tipo)), agora));
        return Optional.of(token);
    }

    /** Dono de um token ainda válido, sem usá-lo (para conferir regras antes de consumir); trava o token. */
    @Transactional
    public Optional<Long> dono(String token, TipoToken tipo) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        return repository
                .findByHash(TokenSeguro.hash(token))
                .filter(t -> t.podeSerUsado(tipo, agora))
                .map(TokenUsoUnico::getUsuarioId);
    }

    /** Usa o token e devolve o id do dono; vazio se o token não existe, já foi usado, expirou ou é de outro tipo. */
    @Transactional
    public Optional<Long> consumir(String token, TipoToken tipo) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        return repository
                .findByHash(TokenSeguro.hash(token))
                .filter(t -> t.podeSerUsado(tipo, agora))
                .map(t -> {
                    t.marcarUsado(agora);
                    return t.getUsuarioId();
                });
    }

    public Duration validade(TipoToken tipo) {
        return switch (tipo) {
            case CONFIRMACAO_EMAIL -> props.validadeConfirmacao();
            case RECUPERACAO_SENHA -> props.validadeRecuperacao();
            case DESAFIO_SEGUNDO_FATOR -> VALIDADE_DESAFIO;
        };
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/ResultadoLogin.java`:

`````java
package br.com.celke.cursos.auth.domain;

/**
 * Resultado registrado na auditoria. Nas falhas, o cliente só recebe "e-mail ou senha inválidos";
 * {@code EMAIL_NAO_CONFIRMADO} só acontece com a senha certa. Os administradores passam por
 * {@code SEGUNDO_FATOR_PENDENTE} (senha certa) antes do {@code SUCESSO}.
 */
public enum ResultadoLogin {
    SUCESSO,
    EMAIL_DESCONHECIDO,
    SENHA_INVALIDA,
    USUARIO_INATIVO,
    EMAIL_NAO_CONFIRMADO,
    BLOQUEADO,
    SEGUNDO_FATOR_PENDENTE,
    SEGUNDO_FATOR_INVALIDO,
    SEGUNDO_FATOR_CADASTRADO,
    CODIGO_RECUPERACAO
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/TipoToken.java`:

`````java
package br.com.celke.cursos.auth.domain;

public enum TipoToken {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA,
    /** Segunda etapa do login do administrador: não vai por e-mail. */
    DESAFIO_SEGUNDO_FATOR;

    /** Tokens enviados por e-mail contam no limite de e-mails por hora. */
    public boolean porEmail() {
        return this != DESAFIO_SEGUNDO_FATOR;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/CheckoutController.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.CheckoutResponse;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoPublico;
import br.com.celke.cursos.pagamento.application.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compra: o checkout (aluno logado) e a situação do pedido para a página de retorno (pública). */
@RestController
@RequestMapping("/api/pagamentos")
@Tag(name = "Pagamentos — compra")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    @Operation(
            summary = "Comprar uma oferta",
            description = "Abre o pedido com o e-mail da conta e devolve para onde levar o aluno.")
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PagamentoRequests.Checkout request) {
        return ResponseEntity.status(201)
                .body(CheckoutResponse.de(checkoutService.iniciar(Long.valueOf(jwt.getSubject()), request.ofertaId())));
    }

    @GetMapping("/ofertas/{id}")
    @Operation(
            summary = "Oferta a confirmar",
            description = "Curso, turma, título e preço de uma oferta à venda, mostrados antes de abrir o pedido.")
    public CheckoutService.ResumoOferta oferta(@PathVariable Long id) {
        return checkoutService.resumo(id);
    }

    @GetMapping("/pedidos/{codigo}")
    @Operation(
            summary = "Situação do pedido",
            description = "Curso e turma só para o dono logado; para os outros, só o status.")
    public PedidoPublico pedido(@AuthenticationPrincipal Jwt jwt, @PathVariable String codigo) {
        Long usuarioId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        return PedidoPublico.de(checkoutService.situacao(codigo, usuarioId));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/CheckoutService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Botão "Comprar" (abre o pedido e monta o checkout) e a página de retorno (situação do pedido). */
@Service
public class CheckoutService {

    private final OfertaRepository ofertas;
    private final GatewayRepository gateways;
    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final ConteudoTurmaService conteudoTurmaService;
    private final UsuarioService usuarioService;
    private final Clock clock;

    public CheckoutService(
            OfertaRepository ofertas,
            GatewayRepository gateways,
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            ConteudoTurmaService conteudoTurmaService,
            UsuarioService usuarioService,
            Clock clock) {
        this.ofertas = ofertas;
        this.gateways = gateways;
        this.pedidos = pedidos;
        this.historico = historico;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.conteudoTurmaService = conteudoTurmaService;
        this.usuarioService = usuarioService;
        this.clock = clock;
    }

    /** O que o aluno vai comprar, para confirmar antes de abrir o pedido (mesmas regras do checkout). */
    @Transactional(readOnly = true)
    public ResumoOferta resumo(Long ofertaId) {
        Oferta oferta = ofertaAVenda(ofertaId);
        Gateway gateway = gatewayAtivo(oferta);
        TurmaResumo turma = turmaAberta(oferta);
        return new ResumoOferta(
                oferta.getId(),
                oferta.getTitulo(),
                oferta.getPrecoCentavos(),
                oferta.getMoeda(),
                turma.cursoTitulo(),
                turma.turmaNome(),
                gateway.getTipo().name());
    }

    /** Abre o pedido com o e-mail da conta e devolve o checkout do meio de pagamento da oferta. */
    @Transactional
    public CheckoutIniciado iniciar(Long usuarioId, Long ofertaId) {
        Oferta oferta = ofertaAVenda(ofertaId);
        Gateway gateway = gatewayAtivo(oferta);
        TurmaResumo turma = turmaAberta(oferta);
        Usuario usuario = usuarioService.buscarPorId(usuarioId).orElseThrow();
        Instant agora = clock.instant();
        Pedido pedido =
                pedidos.save(Pedido.doCheckout(oferta, usuario.getId(), usuario.getEmail(), usuario.getNome(), agora));
        historico.save(
                new PedidoHistorico(pedido.getId(), null, StatusPedido.CRIADO, null, "Checkout iniciado", agora));
        Checkout checkout = registrados
                .de(gateway.getTipo())
                .iniciarCheckout(
                        pedido,
                        oferta,
                        new Comprador(usuario.getEmail(), usuario.getNome()),
                        configuracoes.ler(gateway));
        return new CheckoutIniciado(pedido.getCodigo(), turma.turmaId(), checkout);
    }

    /**
     * Situação do pedido para a página de retorno. Curso e turma só para o dono logado; para os outros,
     * só o status (o código sozinho não revela nada além disso).
     */
    @Transactional(readOnly = true)
    public SituacaoPedido situacao(String codigo, Long usuarioId) {
        Pedido pedido =
                pedidos.findByCodigo(codigo).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
        if (usuarioId == null || !pedido.pertenceA(usuarioId)) {
            return new SituacaoPedido(pedido.getCodigo(), pedido.getStatus(), null);
        }
        TurmaResumo turma = ofertas.findById(pedido.getOfertaId())
                .flatMap(o -> conteudoTurmaService.turma(o.getTurmaId()))
                .orElse(null);
        return new SituacaoPedido(pedido.getCodigo(), pedido.getStatus(), turma);
    }

    private Oferta ofertaAVenda(Long ofertaId) {
        return ofertas.findById(ofertaId)
                .filter(Oferta::isAtiva)
                .orElseThrow(() -> new NaoEncontradoException("Oferta não encontrada."));
    }

    private Gateway gatewayAtivo(Oferta oferta) {
        return gateways.findById(oferta.getGatewayId())
                .filter(Gateway::isAtivo)
                .orElseThrow(() -> new ConflitoException("Esta oferta não está à venda no momento."));
    }

    private TurmaResumo turmaAberta(Oferta oferta) {
        return conteudoTurmaService
                .turma(oferta.getTurmaId())
                .filter(t -> t.cursoStatus() == StatusCurso.PUBLICADO && t.turmaStatus() == StatusTurma.ATIVA)
                .orElseThrow(() -> new ConflitoException("Este curso não está recebendo matrículas no momento."));
    }

    public record CheckoutIniciado(String pedido, Long turmaId, Checkout checkout) {}

    /** Oferta à venda: título, preço, curso, turma e o meio de pagamento (para onde o aluno vai). */
    public record ResumoOferta(
            Long id,
            String titulo,
            long precoCentavos,
            String moeda,
            String curso,
            String turma,
            String meioPagamento) {}

    /** @param turma nula quando quem consulta não é o dono do pedido */
    public record SituacaoPedido(String codigo, StatusPedido status, TurmaResumo turma) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/ConfiguracaoGatewayService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.TipoCampo;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.ErroCampo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuração de um gateway: valida os campos que o meio de pagamento declara, separa os secretos
 * (cifrados) dos comuns e monta a {@link ConfiguracaoGateway} decifrada para a implementação.
 */
@Service
public class ConfiguracaoGatewayService {

    static final int TAMANHO_MAXIMO_VALOR = 500;
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private final GatewaysRegistrados registrados;
    private final CifraSegredos cifra;
    private final JsonMapper jsonMapper;

    public ConfiguracaoGatewayService(GatewaysRegistrados registrados, CifraSegredos cifra, JsonMapper jsonMapper) {
        this.registrados = registrados;
        this.cifra = cifra;
        this.jsonMapper = jsonMapper;
    }

    /** Campos comuns e secretos decifrados, juntos. */
    public ConfiguracaoGateway ler(Gateway gateway) {
        Map<String, String> valores = new HashMap<>(comuns(gateway));
        valores.putAll(segredos(gateway));
        return new ConfiguracaoGateway(gateway.getId(), gateway.getTipo(), valores);
    }

    public Map<String, String> comuns(Gateway gateway) {
        return jsonMapper.readValue(gateway.getConfiguracao(), MAPA);
    }

    /** @throws IllegalStateException se a chave não está definida ou não decifra (chave trocada) */
    public Map<String, String> segredos(Gateway gateway) {
        byte[] blob = gateway.getSegredos();
        if (blob == null) {
            return Map.of();
        }
        return jsonMapper.readValue(cifra.decifrar(blob, gateway.getWebhookToken()), MAPA);
    }

    /**
     * Valida e grava a configuração. Campo secreto em branco mantém o valor atual (o painel nunca o
     * recebe de volta); campo comum em branco fica com o padrão do campo, se houver.
     */
    public void configurar(Gateway gateway, String nome, Map<String, String> recebidos, Instant agora) {
        List<CampoConfiguracao> campos = registrados.de(gateway.getTipo()).camposConfiguracao();
        Map<String, String> valores = recebidos == null ? Map.of() : recebidos;
        Map<String, String> segredosAtuais = gateway.getSegredos() == null ? Map.of() : segredos(gateway);
        List<ErroCampo> erros = new ArrayList<>();
        Set<String> chaves = campos.stream().map(CampoConfiguracao::chave).collect(Collectors.toSet());
        valores.keySet().stream()
                .filter(chave -> !chaves.contains(chave))
                .sorted()
                .forEach(chave -> erros.add(new ErroCampo("valores." + chave, "Campo desconhecido.")));

        Map<String, String> comuns = new TreeMap<>();
        Map<String, String> secretos = new TreeMap<>();
        for (CampoConfiguracao campo : campos) {
            String valor = limpo(valores.get(campo.chave()));
            if (campo.secreto() && valor == null) {
                valor = segredosAtuais.get(campo.chave());
            }
            if (valor == null) {
                valor = campo.padrao();
            }
            String erro = erro(campo, valor);
            if (erro != null) {
                erros.add(new ErroCampo("valores." + campo.chave(), erro));
            } else if (valor != null) {
                (campo.secreto() ? secretos : comuns).put(campo.chave(), valor);
            }
        }
        if (!erros.isEmpty()) {
            erros.sort(Comparator.comparing(ErroCampo::campo));
            throw new ConfiguracaoInvalidaException(erros);
        }
        byte[] blob = null;
        if (!secretos.isEmpty()) {
            if (!cifra.disponivel()) {
                throw new ConflitoException(
                        "Defina a PAGAMENTOS_CHAVE no .env do ambiente para gravar as credenciais do meio de pagamento.");
            }
            blob = cifra.cifrar(jsonMapper.writeValueAsString(secretos), gateway.getWebhookToken());
        }
        gateway.configurar(nome, jsonMapper.writeValueAsString(comuns), blob, agora);
    }

    /** Situação de cada campo secreto, sem o valor: configurado e os 4 últimos caracteres. */
    public Map<String, SegredoResumo> resumoSegredos(Gateway gateway) {
        Map<String, String> atuais = gateway.getSegredos() == null ? Map.of() : segredos(gateway);
        Map<String, SegredoResumo> resumo = new LinkedHashMap<>();
        for (CampoConfiguracao campo : registrados.de(gateway.getTipo()).camposConfiguracao()) {
            if (campo.secreto()) {
                String valor = atuais.get(campo.chave());
                resumo.put(
                        campo.chave(),
                        valor == null
                                ? new SegredoResumo(false, null)
                                : new SegredoResumo(
                                        true, valor.length() <= 4 ? null : valor.substring(valor.length() - 4)));
            }
        }
        return resumo;
    }

    /** @param final4 os 4 últimos caracteres (nulo se o segredo é curto demais para mostrar algo) */
    public record SegredoResumo(boolean configurado, String final4) {}

    private static String erro(CampoConfiguracao campo, String valor) {
        if (valor == null) {
            return campo.obrigatorio() ? "Informe " + campo.rotulo() + "." : null;
        }
        if (valor.length() > TAMANHO_MAXIMO_VALOR) {
            return "Use no máximo " + TAMANHO_MAXIMO_VALOR + " caracteres.";
        }
        if (campo.tipo() == TipoCampo.SELECAO && !campo.opcoes().contains(valor)) {
            return "Escolha uma das opções: " + String.join(", ", campo.opcoes()) + ".";
        }
        if (campo.tipo() == TipoCampo.URL) {
            return CampoConfiguracao.erroUrl(valor, campo.hostsPermitidos());
        }
        return null;
    }

    private static String limpo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/GatewayAdmService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.TokenSeguro;
import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Meios de pagamento pelo painel: tipos disponíveis, configuração, teste das credenciais e ativação. */
@Service
public class GatewayAdmService {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayAdmService.class);

    private final GatewayRepository gateways;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final CifraSegredos cifra;
    private final ContaProperties conta;
    private final Clock clock;

    public GatewayAdmService(
            GatewayRepository gateways,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            CifraSegredos cifra,
            ContaProperties conta,
            Clock clock) {
        this.gateways = gateways;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.cifra = cifra;
        this.conta = conta;
        this.clock = clock;
    }

    public TiposDisponiveis tipos() {
        return new TiposDisponiveis(
                cifra.disponivel(),
                registrados.todos().stream()
                        .map(g -> new TipoDisponivel(g.tipo(), g.nome(), g.camposConfiguracao(), g.capacidades()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<GatewayDetalhe> listar() {
        return gateways.findAllByOrderById().stream().map(this::detalhe).toList();
    }

    @Transactional(readOnly = true)
    public GatewayDetalhe buscar(Long id) {
        return detalhe(gateway(id));
    }

    @Transactional
    public GatewayDetalhe criar(TipoGateway tipo, String nome, Map<String, String> valores) {
        registrados.de(tipo);
        Instant agora = clock.instant();
        Gateway gateway = new Gateway(tipo, nome, TokenSeguro.gerar(), agora);
        configuracoes.configurar(gateway, nome, valores, agora);
        return detalhe(gateways.save(gateway));
    }

    @Transactional
    public GatewayDetalhe atualizar(Long id, String nome, Map<String, String> valores) {
        Gateway gateway = gateway(id);
        configuracoes.configurar(gateway, nome, valores, clock.instant());
        return detalhe(gateway);
    }

    /** Confere as credenciais gravadas com o meio de pagamento. */
    @Transactional(readOnly = true)
    public ResultadoTeste testar(Long id) {
        Gateway gateway = gateway(id);
        try {
            return registrados.de(gateway.getTipo()).testar(configuracoes.ler(gateway));
        } catch (IllegalStateException e) {
            LOG.warn("Teste do gateway {}: {}", id, e.getMessage());
            return ResultadoTeste.falha(e.getMessage());
        }
    }

    /** Passa a ser o meio de pagamento dos novos checkouts; o anterior continua recebendo notificações. */
    @Transactional
    public GatewayDetalhe ativar(Long id) {
        Gateway gateway = gateway(id);
        gateways.desativarOutros(id);
        gateway = gateway(id);
        gateway.ativar(clock.instant());
        gateways.flush();
        return detalhe(gateway);
    }

    @Transactional
    public GatewayDetalhe desativar(Long id) {
        Gateway gateway = gateway(id);
        gateway.desativar(clock.instant());
        return detalhe(gateway);
    }

    private Gateway gateway(Long id) {
        return gateways.findById(id).orElseThrow(() -> new NaoEncontradoException("Meio de pagamento não encontrado."));
    }

    private GatewayDetalhe detalhe(Gateway gateway) {
        GatewayPagamento implementacao = registrados.de(gateway.getTipo());
        Map<String, SegredoResumo> segredos;
        boolean legiveis = true;
        try {
            segredos = configuracoes.resumoSegredos(gateway);
        } catch (IllegalStateException e) {
            // Chave ausente ou trocada: o painel avisa e pede para informar os segredos de novo
            segredos = Map.of();
            legiveis = false;
        }
        return new GatewayDetalhe(
                gateway,
                implementacao.nome(),
                configuracoes.comuns(gateway),
                segredos,
                legiveis,
                conta.urlAluno() + "/api/pagamentos/webhooks/" + gateway.getWebhookToken());
    }

    public record TiposDisponiveis(boolean cifraDisponivel, List<TipoDisponivel> tipos) {}

    public record TipoDisponivel(
            TipoGateway tipo, String nome, List<CampoConfiguracao> campos, Set<Capacidade> capacidades) {}

    /**
     * @param valores campos comuns (os secretos nunca saem; ver {@code segredos})
     * @param segredosLegiveis falso se a chave mudou ou falta: os segredos precisam ser informados de novo
     * @param webhookUrl endereço para cadastrar no meio de pagamento
     */
    public record GatewayDetalhe(
            Gateway gateway,
            String nomeTipo,
            Map<String, String> valores,
            Map<String, SegredoResumo> segredos,
            boolean segredosLegiveis,
            String webhookUrl) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/PagamentosConfig.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PagamentosProperties.class)
public class PagamentosConfig {

    @Bean
    CifraSegredos cifraSegredos(PagamentosProperties props) {
        return new CifraSegredos(props.chave(), "PAGAMENTOS_CHAVE");
    }

    @Bean
    TransicoesPedido transicoesPedido(PagamentosProperties props) {
        return new TransicoesPedido(props.disputaSuspende());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/CampoConfiguracao.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Campo da configuração de um meio de pagamento. O painel monta o formulário a partir da lista que o
 * gateway declara; o campo secreto é gravado cifrado e nunca volta nas respostas.
 *
 * @param padrao valor usado quando o campo vem em branco (nulo: sem padrão)
 * @param opcoes valores aceitos num campo {@link TipoCampo#SELECAO}; vazia nos outros
 */
public record CampoConfiguracao(
        String chave,
        String rotulo,
        TipoCampo tipo,
        boolean secreto,
        boolean obrigatorio,
        String dica,
        String padrao,
        List<String> opcoes,
        List<String> hostsPermitidos) {

    public CampoConfiguracao {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
        hostsPermitidos = hostsPermitidos == null ? List.of() : List.copyOf(hostsPermitidos);
    }

    /** Seleção entre as opções, com um valor padrão. */
    public static CampoConfiguracao selecao(
            String chave, String rotulo, String dica, String padrao, List<String> opcoes) {
        return new CampoConfiguracao(chave, rotulo, TipoCampo.SELECAO, false, true, dica, padrao, opcoes, List.of());
    }

    /**
     * Endereço HTTPS. Com {@code hostsPermitidos}, só esses domínios são aceitos: um endereço trocado no
     * painel não manda o aluno (com o e-mail dele na URL) para um site de terceiros.
     */
    public static CampoConfiguracao url(
            String chave, String rotulo, String dica, String padrao, List<String> hostsPermitidos) {
        return new CampoConfiguracao(
                chave, rotulo, TipoCampo.URL, false, true, dica, padrao, List.of(), hostsPermitidos);
    }

    public static CampoConfiguracao texto(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(
                chave, rotulo, TipoCampo.TEXTO, false, obrigatorio, dica, null, List.of(), List.of());
    }

    public static CampoConfiguracao segredo(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(
                chave, rotulo, TipoCampo.TEXTO, true, obrigatorio, dica, null, List.of(), List.of());
    }

    /** Só HTTPS; com hosts permitidos, só esses domínios. */
    public static String erroUrl(String valor, List<String> hostsPermitidos) {
        URI uri;
        try {
            uri = URI.create(valor);
        } catch (IllegalArgumentException e) {
            return "Informe um endereço começando com https://.";
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return "Informe um endereço começando com https://.";
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!hostsPermitidos.isEmpty() && !hostsPermitidos.contains(host)) {
            return "O endereço precisa ser de " + String.join(" ou ", hostsPermitidos) + ".";
        }
        return null;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/GatewayHotmart.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import br.com.celke.cursos.shared.erro.ConflitoException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Hotmart com o webhook 2.0.0: notificação autenticada pelo hottok no cabeçalho
 * {@code X-HOTMART-HOTTOK} (a Hotmart não assina o corpo), checkout por redirecionamento para o
 * {@code pay.hotmart.com} com o código do pedido no {@code xcod}, e consulta das vendas pela API.
 */
public class GatewayHotmart implements GatewayPagamento {

    public static final String CABECALHO_HOTTOK = "X-HOTMART-HOTTOK";
    static final String CHECKOUT_PADRAO = "https://pay.hotmart.com";

    /** Status consultados um a um quando a venda não está entre as aprovadas/completas. */
    static final List<String> STATUS_CONSULTADOS = List.of(
            "REFUNDED",
            "CHARGEBACK",
            "CANCELLED",
            "EXPIRED",
            "DISPUTE",
            "OVERDUE",
            "WAITING_PAYMENT",
            "PRINTED_BILLET");

    private static final Logger LOG = LoggerFactory.getLogger(GatewayHotmart.class);

    /** Domínios de checkout da Hotmart (produção e sandbox). */
    static final List<String> HOSTS_CHECKOUT = List.of("pay.hotmart.com", "sandbox.hotmart.com");

    private static final List<CampoConfiguracao> CAMPOS = List.of(
            CampoConfiguracao.segredo(
                    "hottok",
                    "Hottok",
                    true,
                    "Ferramentas → Webhook na Hotmart: o token que vai no cabeçalho de cada notificação."),
            CampoConfiguracao.selecao(
                    "ambiente",
                    "Ambiente",
                    "Sandbox para testar com a conta de testes da Hotmart.",
                    "PRODUCAO",
                    List.of("PRODUCAO", "SANDBOX")),
            CampoConfiguracao.url(
                    "urlCheckoutBase",
                    "Endereço do checkout",
                    "Início do link de pagamento das ofertas (domínio da Hotmart).",
                    CHECKOUT_PADRAO,
                    HOSTS_CHECKOUT),
            CampoConfiguracao.texto(
                    "clientId", "Client ID", false, "Ferramentas → Credenciais (API). Só para a reconciliação."),
            CampoConfiguracao.segredo("clientSecret", "Client Secret", false, "Ferramentas → Credenciais (API)."),
            CampoConfiguracao.segredo("basic", "Basic", false, "Ferramentas → Credenciais (API): o token Basic."));

    private final InterpretadorHotmart interpretador;
    private final ClienteApiHotmart api;
    private final Clock clock;

    public GatewayHotmart(InterpretadorHotmart interpretador, ClienteApiHotmart api, Clock clock) {
        this.interpretador = interpretador;
        this.api = api;
        this.clock = clock;
    }

    @Override
    public TipoGateway tipo() {
        return TipoGateway.HOTMART;
    }

    @Override
    public String nome() {
        return "Hotmart";
    }

    @Override
    public List<CampoConfiguracao> camposConfiguracao() {
        return CAMPOS;
    }

    @Override
    public Set<Capacidade> capacidades() {
        return EnumSet.of(Capacidade.CHECKOUT_REDIRECIONAMENTO, Capacidade.CONSULTA_API, Capacidade.RECORRENCIA);
    }

    @Override
    public ResultadoTeste testar(ConfiguracaoGateway configuracao) {
        if (configuracao.valor("hottok") == null) {
            return ResultadoTeste.falha("Informe o hottok.");
        }
        Optional<Credenciais> credenciais;
        try {
            credenciais = credenciais(configuracao);
        } catch (IllegalArgumentException e) {
            return ResultadoTeste.falha(e.getMessage());
        }
        if (credenciais.isEmpty()) {
            return ResultadoTeste.ok("Hottok gravado. Sem as credenciais da API, a reconciliação fica desligada"
                    + " para este meio de pagamento.");
        }
        try {
            api.token(credenciais.get());
            return ResultadoTeste.ok("Credenciais da API conferidas com a Hotmart.");
        } catch (FalhaApiHotmart e) {
            return ResultadoTeste.falha(e.getMessage() + " Confira o Client ID, o Client Secret e o Basic.");
        }
    }

    /**
     * {@code <checkout>/<produto>?off=<oferta>&email=&name=&xcod=<pedido>&sck=plataforma}: o e-mail e o
     * nome chegam preenchidos e o {@code xcod} volta na notificação.
     */
    @Override
    public Checkout iniciarCheckout(
            Pedido pedido, Oferta oferta, Comprador comprador, ConfiguracaoGateway configuracao) {
        String base = configuracao.valor("urlCheckoutBase", CHECKOUT_PADRAO).replaceAll("/+$", "");
        if (CampoConfiguracao.erroUrl(base, HOSTS_CHECKOUT) != null) {
            // Gravado antes da regra de domínios: não manda o aluno para fora da Hotmart
            throw new ConflitoException("O endereço do checkout da Hotmart está inválido. Confira no painel.");
        }
        StringBuilder url = new StringBuilder(base)
                .append('/')
                .append(UriUtils.encodePathSegment(oferta.codigoParaCheckout(), StandardCharsets.UTF_8))
                .append("?off=")
                .append(codificar(oferta.getOfertaExterna()))
                .append("&email=")
                .append(codificar(comprador.email()));
        if (comprador.nome() != null && !comprador.nome().isBlank()) {
            url.append("&name=").append(codificar(comprador.nome()));
        }
        url.append("&xcod=").append(codificar(pedido.getCodigo())).append("&sck=plataforma");
        return new Checkout.Redirecionamento(url.toString());
    }

    /** Hottok do cabeçalho comparado em tempo constante com o configurado. */
    @Override
    public boolean autenticar(NotificacaoRecebida notificacao, ConfiguracaoGateway configuracao) {
        String esperado = configuracao.valor("hottok");
        String recebido = notificacao.cabecalho(CABECALHO_HOTTOK);
        if (esperado == null || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recebido.strip().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<EventoPagamento> interpretar(NotificacaoRecebida notificacao) {
        return List.of(interpretador.interpretar(notificacao.corpoTexto()));
    }

    /** A venda no histórico de vendas, como evento sintético ({@code reconciliacao:<transação>:<status>}). */
    @Override
    public Optional<EventoPagamento> consultar(String transacao, ConfiguracaoGateway configuracao) {
        Optional<Credenciais> credenciais = credenciais(configuracao);
        if (credenciais.isEmpty()) {
            LOG.debug("Hotmart sem credenciais da API: transação {} não consultada", transacao);
            return Optional.empty();
        }
        Optional<JsonNode> venda = api.venda(credenciais.get(), transacao, null);
        for (int i = 0; venda.isEmpty() && i < STATUS_CONSULTADOS.size(); i++) {
            venda = api.venda(credenciais.get(), transacao, STATUS_CONSULTADOS.get(i));
        }
        return venda.map(item -> evento(transacao, item));
    }

    private EventoPagamento evento(String transacao, JsonNode item) {
        JsonNode compra = item.path("purchase");
        String status = Optional.ofNullable(InterpretadorHotmart.texto(compra.path("status")))
                .map(s -> s.toUpperCase(Locale.ROOT))
                .orElse("DESCONHECIDO");
        StatusPedido statusPedido = InterpretadorHotmart.POR_STATUS.get(status);
        JsonNode comprador = item.path("buyer");
        String email = InterpretadorHotmart.texto(comprador.path("email"));
        return new EventoPagamento(
                "reconciliacao:%s:%s".formatted(transacao, status),
                "RECONCILIACAO_" + status,
                clock.instant(),
                transacao,
                statusPedido,
                InterpretadorHotmart.texto(compra.path("tracking").path("external_code")),
                InterpretadorHotmart.texto(item.path("product").path("id")),
                InterpretadorHotmart.texto(compra.path("offer").path("code")),
                email == null
                        ? null
                        : new CompradorEvento(
                                email.toLowerCase(Locale.ROOT),
                                InterpretadorHotmart.texto(comprador.path("name")),
                                null),
                InterpretadorHotmart.centavos(compra.path("price").path("value")),
                InterpretadorHotmart.texto(compra.path("price").path("currency_code")),
                InterpretadorHotmart.forma(
                        InterpretadorHotmart.texto(compra.path("payment").path("type"))),
                null);
    }

    /** Credenciais da API, se as três foram informadas. */
    private static Optional<Credenciais> credenciais(ConfiguracaoGateway configuracao) {
        String clientId = configuracao.valor("clientId");
        String clientSecret = configuracao.valor("clientSecret");
        String basic = configuracao.valor("basic");
        if (clientId == null && clientSecret == null && basic == null) {
            return Optional.empty();
        }
        if (clientId == null || clientSecret == null || basic == null) {
            throw new IllegalArgumentException("Informe o Client ID, o Client Secret e o Basic juntos.");
        }
        return Optional.of(
                new Credenciais(clientId, clientSecret, basic, "SANDBOX".equals(configuracao.valor("ambiente"))));
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/perfil/application/PerfilService.java`:

`````java
package br.com.celke.cursos.perfil.application;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.CampoInvalidoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil do próprio usuário: nome, senha (exige a atual e encerra todas as sessões) e foto, enviada
 * direto ao bucket e regravada em JPEG sem metadados (até 400 × 400).
 */
@Service
public class PerfilService {

    public static final long TAMANHO_MAXIMO_FOTO = 5L * 1024 * 1024;
    static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");
    static final int LADO_FOTO = 400;
    static final Duration VALIDADE_UPLOAD = Duration.ofMinutes(10);
    static final Duration VALIDADE_FOTO = Duration.ofHours(1);

    private static final Pattern UUID_FORMATO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final StorageService storage;
    private final ProcessadorImagem processador;
    private final Clock clock;

    public PerfilService(
            UsuarioService usuarioService,
            RefreshTokenService refreshTokenService,
            StorageService storage,
            ProcessadorImagem processador,
            Clock clock) {
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.storage = storage;
        this.processador = processador;
        this.clock = clock;
    }

    public Usuario buscar(Long usuarioId) {
        return usuarioService.buscarPorId(usuarioId).orElseThrow(PerfilService::semUsuario);
    }

    public Usuario renomear(Long usuarioId, String nome) {
        buscar(usuarioId);
        return usuarioService.renomear(usuarioId, nome);
    }

    /**
     * Troca a senha conferindo a atual e a política do papel (admin exige mais caracteres). Todas as
     * sessões são encerradas: o usuário entra de novo com a senha nova.
     */
    @Transactional
    public void trocarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = buscar(usuarioId);
        if (!usuarioService.senhaConfere(usuario, senhaAtual)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "A senha atual não confere.");
        }
        int minimo =
                usuario.getPapeis().contains(Papel.ADMIN) ? PoliticaSenha.MINIMO_ADMIN : PoliticaSenha.MINIMO_ALUNO;
        String problema = PoliticaSenha.validar(novaSenha, minimo, usuario.getNome(), usuario.getEmail());
        if (problema != null) {
            throw new CampoInvalidoException("novaSenha", problema);
        }
        if (usuarioService.senhaConfere(usuario, novaSenha)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "A nova senha precisa ser diferente da atual.");
        }
        usuarioService.trocarSenha(usuario, novaSenha);
        refreshTokenService.revogarTodas(usuarioId);
    }

    /** URL para o navegador enviar a foto, com o mesmo Content-Type informado aqui. */
    public EnvioFoto iniciarEnvioFoto(Long usuarioId, String contentType, long tamanho) {
        if (!TIPOS.contains(contentType)) {
            throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
        }
        if (tamanho > TAMANHO_MAXIMO_FOTO) {
            throw tamanhoExcedido();
        }
        String chave = prefixoUpload(usuarioId) + UUID.randomUUID();
        URL url = storage.urlUpload(chave, contentType, VALIDADE_UPLOAD);
        return new EnvioFoto(chave, url, contentType, clock.instant().plus(VALIDADE_UPLOAD));
    }

    /** Confere a imagem enviada pelo próprio usuário, grava a versão processada e apaga a anterior. */
    public Usuario confirmarFoto(Long usuarioId, String chave) {
        buscar(usuarioId);
        String prefixo = prefixoUpload(usuarioId);
        if (chave == null
                || !chave.startsWith(prefixo)
                || !UUID_FORMATO.matcher(chave.substring(prefixo.length())).matches()) {
            throw new ImagemInvalidaException("Envio de foto inválido. Comece um novo envio.");
        }
        var info = storage.info(chave)
                .orElseThrow(() -> new ImagemInvalidaException("A foto não foi enviada. Comece um novo envio."));
        byte[] processada;
        try {
            if (info.tamanho() > TAMANHO_MAXIMO_FOTO) {
                throw tamanhoExcedido();
            }
            processada = processador.normalizar(storage.baixar(chave), LADO_FOTO, LADO_FOTO);
        } finally {
            storage.excluir(chave);
        }
        String foto = "fotos/%d/%s.jpg".formatted(usuarioId, UUID.randomUUID());
        storage.gravar(foto, processada, "image/jpeg");
        try {
            usuarioService.trocarFoto(usuarioId, foto).ifPresent(storage::excluir);
        } catch (RuntimeException e) {
            storage.excluir(foto);
            throw e;
        }
        return buscar(usuarioId);
    }

    public void removerFoto(Long usuarioId) {
        buscar(usuarioId);
        usuarioService.trocarFoto(usuarioId, null).ifPresent(storage::excluir);
    }

    /** URL temporária da foto, ou vazio sem foto. */
    public Optional<URL> urlFoto(Usuario usuario) {
        return Optional.ofNullable(usuario.getFotoChave()).map(chave -> storage.urlDownload(chave, VALIDADE_FOTO));
    }

    private static String prefixoUpload(Long usuarioId) {
        return "uploads/fotos/%d/".formatted(usuarioId);
    }

    private static ImagemInvalidaException tamanhoExcedido() {
        return new ImagemInvalidaException("A foto pode ter no máximo 5 MB.");
    }

    private static RegraNegocioException semUsuario() {
        return new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente.");
    }

    public record EnvioFoto(String chave, URL url, String contentType, Instant expiraEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`:

`````java
package br.com.celke.cursos.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: tudo exige autenticação, exceto as rotas públicas declaradas aqui com o
 * método HTTP. A API é stateless: o access token (JWT) vem no cabeçalho {@code Authorization}, sem
 * sessão, formulário ou HTTP Basic. Só existe na aplicação web: o worker não atende HTTP.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    /** Claim do JWT com os papéis do usuário (ALUNO, ADMIN), convertidos em ROLE_ALUNO, ROLE_ADMIN. */
    public static final String CLAIM_PAPEIS = "papeis";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        RespostasSeguranca respostas = new RespostasSeguranca(jsonMapper);
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401 e 403 no formato ProblemDetail
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorPapeis()))
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas))
                .authorizeHttpRequests(rotas -> rotas
                        // Health check e probes (Docker e Caddy)
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Documentação: só responde quando DOCS_ENABLED=true
                        .requestMatchers(
                                HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Autenticação: login e renovação/saída pelo cookie de refresh
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        // Segunda etapa do login dos administradores: autenticada pelo desafio da primeira
                        .requestMatchers(HttpMethod.POST, "/api/auth/segundo-fator", "/api/auth/segundo-fator/cadastro")
                        .permitAll()
                        // Conta: cadastro, confirmação de e-mail e recuperação de senha
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/cadastro",
                                "/api/auth/confirmar-email",
                                "/api/auth/reenviar-confirmacao",
                                "/api/auth/recuperar-senha",
                                "/api/auth/redefinir-senha")
                        .permitAll()
                        // Catálogo: cursos publicados, detalhe e capa
                        .requestMatchers(HttpMethod.GET, "/api/cursos", "/api/cursos/*", "/api/cursos/*/capa")
                        .permitAll()
                        // Termos de uso e política de privacidade vigentes (páginas do site)
                        .requestMatchers(HttpMethod.GET, "/api/termos/vigentes")
                        .permitAll()
                        // Validação pública do certificado pelo código (página "validar certificado" do site)
                        .requestMatchers(HttpMethod.GET, "/api/certificados/*")
                        .permitAll()
                        // Notificações dos meios de pagamento: autenticadas pelo próprio meio (hottok...)
                        .requestMatchers(HttpMethod.POST, "/api/pagamentos/webhooks/*")
                        .permitAll()
                        // Situação do pedido na página de retorno (sem login, só o status)
                        .requestMatchers(HttpMethod.GET, "/api/pagamentos/pedidos/*")
                        .permitAll()
                        // Área do ADM: o papel é exigido aqui e repetido com @PreAuthorize nos controllers
                        .requestMatchers("/api/adm/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated());
        return http.build();
    }

    private static JwtAuthenticationConverter conversorPapeis() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(CLAIM_PAPEIS);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/api/ComandoCriarAdmin.java`:

`````java
package br.com.celke.cursos.usuario.api;

import br.com.celke.cursos.usuario.application.CriarAdminService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code criar-admin <email> <nome>}: pede a senha duas vezes no terminal, sem eco.
 *
 * <pre>docker compose run --rm api criar-admin admin@celke.uno "Nome do Admin"</pre>
 */
@Component
@Profile(ComandoCriarAdmin.PERFIL)
public class ComandoCriarAdmin implements ApplicationRunner {

    public static final String NOME = "criar-admin";
    public static final String PERFIL = "comando";

    private final CriarAdminService criarAdminService;

    public ComandoCriarAdmin(CriarAdminService criarAdminService) {
        this.criarAdminService = criarAdminService;
    }

    /** Comandos de linha: o nome é o primeiro argumento. */
    private static final java.util.Set<String> COMANDOS = java.util.Set.of(NOME, "redefinir-2fa");

    /** Algum comando de linha (criar-admin, redefinir-2fa) foi pedido no primeiro argumento. */
    public static boolean foiPedido(String[] args) {
        return args.length > 0 && COMANDOS.contains(args[0]);
    }

    @Override
    public void run(ApplicationArguments args) {
        var argumentos = args.getNonOptionArgs();
        if (argumentos.isEmpty() || !NOME.equals(argumentos.get(0))) {
            return; // outro comando
        }
        if (argumentos.size() != 3) {
            throw new IllegalArgumentException("Uso: criar-admin <email> \"<nome>\"");
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("O comando precisa de um terminal (docker compose run com TTY).");
        }
        char[] senha = console.readPassword("Senha do administrador: ");
        char[] confirmacao = console.readPassword("Repita a senha: ");
        try {
            if (!Arrays.equals(senha, confirmacao)) {
                throw new IllegalArgumentException("As senhas não conferem.");
            }
            Usuario admin = criarAdminService.criar(argumentos.get(2), argumentos.get(1), new String(senha));
            console.printf("Administrador %s criado (id %d).%n", admin.getEmail(), admin.getId());
        } finally {
            Arrays.fill(senha, '\0');
            Arrays.fill(confirmacao, '\0');
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/CriarAdminService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Cria um administrador. Usado pelo comando {@code criar-admin}, nunca por migration. */
@Service
public class CriarAdminService {

    private final UsuarioService usuarioService;

    public CriarAdminService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public Usuario criar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        String problemaSenha = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN, nome, email);
        if (problemaSenha != null) {
            throw new IllegalArgumentException(problemaSenha);
        }
        return usuarioService.criarConfirmado(nome, email, senha, Set.of(Papel.ADMIN));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/SeedE2e.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seed da stack de e2e (profile {@code e2e}): um admin e um aluno confirmados, com a senha de
 * {@code SEED_E2E_SENHA}. Nunca ligado nos servidores.
 */
@Component
@Profile("e2e")
@Order(1)
public class SeedE2e implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin@celke.com.br";
    public static final String ALUNO_EMAIL = "aluno@celke.com.br";

    private final UsuarioService usuarioService;
    private final String senha;

    public SeedE2e(UsuarioService usuarioService, @Value("${SEED_E2E_SENHA}") String senha) {
        String problema = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN, ADMIN_EMAIL, ALUNO_EMAIL);
        if (problema != null) {
            throw new IllegalArgumentException("SEED_E2E_SENHA inválida: " + problema);
        }
        this.usuarioService = usuarioService;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Admin do e2e", ADMIN_EMAIL, Papel.ADMIN);
        criarSeNaoExiste("Aluno do e2e", ALUNO_EMAIL, Papel.ALUNO);
    }

    private void criarSeNaoExiste(String nome, String email, Papel papel) {
        if (usuarioService.buscarPorEmail(email).isEmpty()) {
            usuarioService.criarConfirmado(nome, email, senha, Set.of(papel));
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/UsuarioService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações de usuário usadas pelos outros módulos (eles nunca acessam o repositório direto). */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Cria um usuário com o e-mail já confirmado: contas criadas por quem administra a plataforma
     * (comando {@code criar-admin}, seeds). A senha já deve ter passado pela {@code PoliticaSenha}.
     */
    @Transactional
    public Usuario criarConfirmado(String nome, String email, String senha, Set<Papel> papeis) {
        return criar(nome, email, senha, papeis, true);
    }

    /** Cadastro feito pelo próprio aluno: o e-mail fica pendente até ele confirmar. */
    @Transactional
    public Usuario cadastrarAluno(String nome, String email, String senha) {
        return criar(nome, email, senha, Set.of(Papel.ALUNO), false);
    }

    /**
     * Conta de aluno criada por uma compra: sem senha e com o e-mail confirmado (o pagamento chegou a
     * ele). O dono cria a senha pelo link enviado no e-mail da matrícula.
     */
    @Transactional
    public Usuario criarAlunoSemSenha(String nome, String email) {
        return criar(nome, email, null, Set.of(Papel.ALUNO), true);
    }

    private Usuario criar(String nome, String email, String senha, Set<Papel> papeis, boolean confirmado) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        String hash = senha == null ? null : passwordEncoder.encode(senha);
        return repository.save(new Usuario(nome, emailNormalizado, hash, papeis, confirmado, clock.instant()));
    }

    @Transactional
    public void confirmarEmail(Usuario usuario) {
        usuario.confirmarEmail(clock.instant());
        repository.save(usuario);
    }

    /** Troca a senha. A nova senha já deve ter passado pela {@code PoliticaSenha}. */
    @Transactional
    public void trocarSenha(Usuario usuario, String novaSenha) {
        usuario.trocarHashSenha(passwordEncoder.encode(novaSenha), clock.instant());
        repository.save(usuario);
    }

    /** A senha informada é a atual do usuário (troca de senha no perfil). */
    public boolean senhaConfere(Usuario usuario, String senha) {
        return senha != null && usuario.possuiSenha() && passwordEncoder.matches(senha, usuario.getSenhaHash());
    }

    @Transactional
    public Usuario renomear(Long usuarioId, String nome) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        usuario.renomear(nome, clock.instant());
        return repository.save(usuario);
    }

    /** Troca a foto de perfil (nula remove); devolve a chave da anterior. */
    @Transactional
    public Optional<String> trocarFoto(Long usuarioId, String chave) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        String anterior = usuario.trocarFoto(chave, clock.instant());
        repository.save(usuario);
        return Optional.ofNullable(anterior);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(Usuario.normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id);
    }

    /** Usuários indexados pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Usuario> buscarPorIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    /** Lista do ADM, ordenada por nome. */
    @Transactional(readOnly = true)
    public Page<Usuario> listar(String busca, Papel papel, Boolean ativo, Pageable pagina) {
        String termo = busca == null || busca.isBlank()
                ? null
                : "%" + busca.strip().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "") + "%";
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("nome", "id"));
        return repository.buscar(termo, papel, ativo, ordenada);
    }

    @Transactional
    public Usuario bloquear(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        usuario.bloquear(clock.instant());
        return usuario;
    }

    @Transactional
    public Usuario desbloquear(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        usuario.desbloquear(clock.instant());
        return usuario;
    }

    @Transactional
    public Usuario encerrarSessoes(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        usuario.encerrarSessoes(clock.instant());
        return usuario;
    }

    /** Regrava o hash quando ele usa um algoritmo antigo (ex.: BCrypt vindo do sistema legado). */
    @Transactional
    public void atualizarHashSeNecessario(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.trocarHashSenha(passwordEncoder.encode(senha), clock.instant());
            repository.save(usuario);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/domain/PoliticaSenha.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de senha: tamanho mínimo e máximo (o máximo evita hash de textos enormes), fora da lista de
 * senhas comuns e vazadas, sem o nome ou o e-mail de quem a usa e sem sequência ou repetição óbvia.
 * Não exige mistura de caracteres: comprimento e imprevisibilidade protegem mais e incomodam menos.
 */
public final class PoliticaSenha {

    public static final int MINIMO_ALUNO = 10;
    public static final int MINIMO_ADMIN = 12;
    public static final int MAXIMO = 128;

    public static final String COMUM = "Esta senha é muito comum ou já apareceu em vazamentos. Escolha outra.";
    public static final String PESSOAL = "A senha não pode conter o seu nome ou o seu e-mail.";
    public static final String PREVISIVEL = "A senha é fácil de adivinhar (sequência ou repetição). Escolha outra.";

    /** Lista de senhas comuns e vazadas, com 10 a 128 caracteres, em minúsculas (SecLists). */
    static final String LISTA = "/seguranca/senhas-comuns.txt";

    /** Trechos de nome e e-mail menores que isso não contam (ex.: "ana" em "banana"). */
    static final int TAMANHO_MINIMO_TRECHO = 4;

    /** Sequências de teclado e de alfabeto; senha inteira dentro de uma delas (ou do reverso) é previsível. */
    private static final List<String> SEQUENCIAS = List.of(
            "0123456789".repeat(4),
            "abcdefghijklmnopqrstuvwxyz".repeat(2),
            "qwertyuiopasdfghjklzxcvbnm".repeat(2),
            "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik9ol0p",
            "qazwsxedcrfvtgbyhnujmikolp");

    /** Um bloco de até 4 caracteres repetido do começo ao fim ("abcabcabcabc"). */
    private static final Pattern BLOCO_REPETIDO = Pattern.compile("(.{1,4})\\1+");

    private PoliticaSenha() {}

    /**
     * Devolve a mensagem do problema, ou {@code null} se a senha atende à política.
     *
     * @param pessoais nome, e-mail e outros dados da pessoa que a senha não pode conter
     */
    public static String validar(String senha, int minimo, String... pessoais) {
        if (senha == null || senha.isBlank()) {
            return "A senha é obrigatória.";
        }
        if (senha.length() < minimo) {
            return "A senha deve ter pelo menos " + minimo + " caracteres.";
        }
        if (senha.length() > MAXIMO) {
            return "A senha deve ter no máximo " + MAXIMO + " caracteres.";
        }
        String normalizada = senha.toLowerCase(Locale.ROOT);
        if (Comuns.LISTA.contains(normalizada)) {
            return COMUM;
        }
        if (contemPessoal(normalizada, pessoais)) {
            return PESSOAL;
        }
        if (previsivel(normalizada)) {
            return PREVISIVEL;
        }
        return null;
    }

    static boolean contemPessoal(String senha, String... pessoais) {
        return Arrays.stream(pessoais)
                .filter(valor -> valor != null && !valor.isBlank())
                .flatMap(valor -> trechos(valor.toLowerCase(Locale.ROOT)).stream())
                .anyMatch(senha::contains);
    }

    /** Partes do dado que contam: o e-mail inteiro, o usuário antes do @ e cada palavra do nome. */
    private static Set<String> trechos(String valor) {
        Set<String> trechos = new HashSet<>();
        String local = valor.contains("@") ? valor.substring(0, valor.indexOf('@')) : valor;
        trechos.add(valor.strip());
        trechos.add(local.strip());
        trechos.addAll(Arrays.asList(local.split("[^\\p{L}\\p{N}]+")));
        trechos.removeIf(t -> t.length() < TAMANHO_MINIMO_TRECHO);
        return trechos;
    }

    static boolean previsivel(String senha) {
        if (senha.chars().distinct().count() <= 2
                || BLOCO_REPETIDO.matcher(senha).matches()) {
            return true;
        }
        String reversa = new StringBuilder(senha).reverse().toString();
        return SEQUENCIAS.stream().anyMatch(s -> s.contains(senha) || s.contains(reversa));
    }

    /** Lista carregada uma vez, no primeiro uso. */
    private static final class Comuns {

        static final Set<String> LISTA = carregar();

        private static Set<String> carregar() {
            try (InputStream entrada = PoliticaSenha.class.getResourceAsStream(PoliticaSenha.LISTA)) {
                if (entrada == null) {
                    throw new IllegalStateException("Lista de senhas comuns não encontrada: " + PoliticaSenha.LISTA);
                }
                Set<String> senhas = new HashSet<>(160_000);
                try (BufferedReader leitor =
                        new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
                    leitor.lines().map(String::strip).filter(l -> !l.isEmpty()).forEach(senhas::add);
                }
                return Set.copyOf(senhas);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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

    /** Encerramento das sessões pelo ADM: access tokens emitidos antes disso deixam de valer. */
    @Column(name = "sessoes_encerradas_em")
    private Instant sessoesEncerradasEm;

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

    /** Bloqueia a conta (não entra mais) e encerra as sessões abertas. */
    public void bloquear(Instant agora) {
        this.ativo = false;
        encerrarSessoes(agora);
    }

    public void desbloquear(Instant agora) {
        this.ativo = true;
        this.atualizadoEm = agora;
    }

    public void encerrarSessoes(Instant agora) {
        this.sessoesEncerradasEm = agora;
        this.atualizadoEm = agora;
    }

    public Instant getSessoesEncerradasEm() {
        return sessoesEncerradasEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getEmailConfirmadoEm() {
        return emailConfirmadoEm;
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

`backend/src/main/java/br/com/celke/cursos/usuario/infra/UsuarioRepository.java`:

`````java
package br.com.celke.cursos.usuario.infra;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Lista do ADM: busca por nome ou e-mail (sem diferenciar maiúsculas), papel e situação. */
    @Query("""
            SELECT DISTINCT u FROM Usuario u LEFT JOIN u.papeis p
             WHERE (:busca IS NULL OR LOWER(u.nome) LIKE :busca OR u.email LIKE :busca)
               AND (:papel IS NULL OR p = :papel)
               AND (:ativo IS NULL OR u.ativo = :ativo)
            """)
    Page<Usuario> buscar(String busca, Papel papel, Boolean ativo, Pageable pagina);
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
    # Usuário da API e do worker (só dados): criado e liberado pelo afterMigrate.sql quando informado.
    # Em branco (desenvolvimento), a aplicação usa o próprio dono do schema
    placeholders:
      usuario_app: ${DB_APP_USERNAME:}
      senha_app: ${DB_APP_PASSWORD:}
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
    segundo-fator:
      # Chave AES-256 dos segredos do aplicativo autenticador dos administradores: 32 bytes em Base64
      # (openssl rand -base64 32). Trocar a chave exige cadastrar o aplicativo de novo (redefinir-2fa)
      chave: ${TOTP_CHAVE:}
      emissor: Plataforma de Cursos
      rejeitar-reuso: true
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
  limites-usuario:
    # Gravações (POST, PUT, PATCH, DELETE) de um usuário logado a cada minuto, em todas as rotas
    escritas-por-minuto: 120
    # Pedidos abertos no meio de pagamento por um usuário a cada hora
    checkouts-por-hora: 10
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

`backend/src/test/java/br/com/celke/cursos/auth/AuthLoginIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.application.CriarAdminService;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: login (corpo, cookie de refresh, mensagens iguais para e-mail inexistente e senha errada),
 * validação, /api/auth/me, admin criado pelo serviço do comando e hash legado regravado em Argon2.
 * Não cobre: validade e assinatura do JWT (AuthTokenIT) e renovação (AuthRefreshIT).
 * Dado criado: usuários it-<stamp>@teste.celke.uno.
 */
class AuthLoginIT extends IntegrationTest {

    @Autowired
    private CriarAdminService criarAdminService;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("login devolve access token, dados públicos do usuário e o cookie de refresh")
    void loginComSucesso() throws Exception {
        MvcTestResult resposta = entrar(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);

        assertThat(resposta).bodyJson().isLenientlyEqualTo("""
                        {
                          "tipo": "Bearer",
                          "usuario": {"email": "aluno.seed@teste.celke.uno", "nome": "Aluno do Seed", "papeis": ["ALUNO"]}
                        }
                        """);
        assertThat(resposta)
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isNotBlank();
        assertThat(resposta).bodyJson().extractingPath("$.expiraEm").asString().isNotBlank();
        String corpo = resposta.getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("senha", "hash", "refresh");

        String cookie = resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=1209600")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("e-mail é aceito com letras maiúsculas")
    void emailComMaiusculas() {
        entrar("Aluno.Seed@Teste.Celke.UNO", UsuariosSeed.SENHA);
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente recebem exatamente a mesma resposta")
    void credenciaisInvalidas() throws Exception {
        MvcTestResult senhaErrada = tentarLogin(UsuariosSeed.ALUNO_EMAIL, "senha-errada");
        MvcTestResult emailInexistente = tentarLogin(stamp() + "@teste.celke.uno", "senha-errada");

        for (MvcTestResult resposta : new MvcTestResult[] {senhaErrada, emailInexistente}) {
            assertThat(resposta)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("E-mail ou senha inválidos.");
            assertThat(resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    @DisplayName("payload inválido devolve 400 com os campos")
    void validacao() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"senha\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("email", "senha");
    }

    @Test
    @DisplayName("/api/auth/me devolve o usuário do token, sem dados sensíveis")
    void me() throws Exception {
        String token = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);

        MvcTestResult resposta = mvc.get()
                .uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        assertThat(resposta).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"email": "admin.seed@teste.celke.uno", "papeis": ["ADMIN"]}
                        """);
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("senha", "hash");
    }

    @Test
    @DisplayName("admin criado pelo serviço do comando criar-admin entra com o papel ADMIN")
    void adminCriadoPeloComando() {
        String email = stamp() + "@teste.celke.uno";
        criarAdminService.criar("Admin do Comando", email, "girassol-azul-do-lago");

        assertThat(entrar(email, "girassol-azul-do-lago"))
                .bodyJson()
                .extractingPath("$.usuario.papeis")
                .asArray()
                .containsExactly("ADMIN");
    }

    @Test
    @DisplayName("hash BCrypt do sistema legado ($2y$) entra e é regravado em Argon2")
    void hashLegado() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String hashLaravel =
                new BCryptPasswordEncoder().encode("senha-do-legado").replaceFirst("^\\$2a\\$", "\\$2y\\$");
        jdbc.sql("UPDATE usuarios SET senha_hash = :hash WHERE id = :id")
                .param("hash", hashLaravel)
                .param("id", usuario.id())
                .update();

        entrar(usuario.email(), "senha-do-legado");

        assertThat(senhaHash(usuario.id())).startsWith("{argon2}");
        entrar(usuario.email(), "senha-do-legado");
    }

    @Test
    @DisplayName("senha nova é gravada em Argon2, nunca em texto")
    void senhaEmArgon2() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);

        assertThat(senhaHash(usuario.id())).startsWith("{argon2}$argon2id$").doesNotContain(usuario.senha());
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
    }

    private String senhaHash(Long id) {
        return jdbc.sql("SELECT senha_hash FROM usuarios WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/CadastroIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: cadastro → e-mail de confirmação → confirmação → login; mesma resposta para e-mail novo e já
 * cadastrado; reenvio do link; link de uso único; limite de e-mails por hora.
 * Não cobre: regras caso a caso (CadastroServiceTest, TokenUsoUnicoServiceTest).
 * Dado criado: contas it-<stamp>@teste.celke.uno e os e-mails delas no Mailpit.
 */
class CadastroIT extends IntegrationTest {

    private static final String SENHA = "senha-do-cadastro";
    private static final String MENSAGEM = "Cadastro recebido. Confira o seu e-mail para continuar.";

    @Autowired
    private Mailpit mailpit;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("cadastro, confirmação pelo link do e-mail e login")
    void jornadaCompleta() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(cadastrar("Ana Souza", email, SENHA))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(email);
        assertThat(recebido.Subject()).isEqualTo("Confirme o seu e-mail");
        assertThat(recebido.HTML())
                .contains("Olá, <span>Ana Souza</span>!")
                .contains("http://localhost:5173/confirmar-email?token=")
                .contains("48 horas");

        // Antes de confirmar, a senha certa recebe a orientação de confirmar o e-mail
        assertThat(tentarLogin(email, SENHA))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");

        assertThat(confirmar(Mailpit.tokenDoLink(recebido)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("E-mail confirmado. Você já pode entrar.");

        assertThat(entrar(email, SENHA))
                .bodyJson()
                .extractingPath("$.usuario.papeis")
                .asArray()
                .containsExactly("ALUNO");
    }

    @Test
    @DisplayName("o link de confirmação vale uma vez só")
    void linkDeUsoUnico() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        processarFila();
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(confirmar(token)).hasStatusOk();
        assertLinkInvalido(confirmar(token));
    }

    @Test
    @DisplayName("e-mail já cadastrado recebe a mesma resposta, a conta não muda e o dono é avisado")
    void emailJaCadastrado() {
        UsuarioTeste existente = criarUsuario(Papel.ALUNO);

        assertThat(cadastrar("Outra Pessoa", existente.email(), "girassol-azul-77"))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        assertThat(mailpit.aguardarEmailPara(existente.email()).Subject())
                .isEqualTo("Tentativa de cadastro com o seu e-mail");
        assertThat(contasCom(existente.email())).isEqualTo(1);
        entrar(existente.email(), existente.senha());
        assertThat(tentarLogin(existente.email(), "girassol-azul-77")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("reenviar confirmação manda um link novo e invalida o anterior")
    void reenviarInvalidaAnterior() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        processarFila();
        String primeiro = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(reenviar(email)).hasStatus(HttpStatus.ACCEPTED);
        processarFila();
        String segundo = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(segundo).isNotEqualTo(primeiro);
        assertLinkInvalido(confirmar(primeiro));
        assertThat(confirmar(segundo)).hasStatusOk();
    }

    @Test
    @DisplayName("reenviar para e-mail sem cadastro responde igual e não envia nada")
    void reenviarSemCadastro() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(reenviar(email))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("Se houver um cadastro pendente para este e-mail, enviaremos um novo link.");
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isZero();
    }

    @Test
    @DisplayName("no máximo 3 e-mails de confirmação por hora para a mesma conta")
    void limiteDeEmailsPorHora() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        for (int i = 0; i < 3; i++) {
            assertThat(reenviar(email)).hasStatus(HttpStatus.ACCEPTED);
        }
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isEqualTo(3);
    }

    @Test
    @DisplayName("senha curta é recusada com o campo indicado")
    void senhaCurta() {
        assertThat(cadastrar("Ana", stamp() + "@teste.celke.uno", "curta"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactly("senha");
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        assertLinkInvalido(confirmar("token-que-nunca-existiu"));
    }

    private MvcTestResult cadastrar(String nome, String email, String senha) {
        return post(
                "/api/auth/cadastro",
                "{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(nome, email, senha));
    }

    private MvcTestResult confirmar(String token) {
        return post("/api/auth/confirmar-email", "{\"token\":\"%s\"}".formatted(token));
    }

    private MvcTestResult reenviar(String email) {
        return post("/api/auth/reenviar-confirmacao", "{\"email\":\"%s\"}".formatted(email));
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return post("/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha));
    }

    private MvcTestResult post(String rota, String json) {
        return mvc.post()
                .uri(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private int contasCom(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();
    }

    private static void assertLinkInvalido(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Link inválido ou expirado. Peça um novo.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/AuthServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.AuthService.Desafio;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.SegundoFatorService.Verificacao;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant EXPIRA = Instant.parse("2026-01-10T12:10:00Z");
    private static final String HASH_FICTICIO = "{argon2}ficticio";
    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.7", "Navegador/1.0");

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BloqueioLoginService bloqueioLoginService;

    @Mock
    private AuditoriaLoginService auditoriaLoginService;

    @Mock
    private SegundoFatorService segundoFatorService;

    @Mock
    private TokenUsoUnicoService tokens;

    private AuthService service;

    @BeforeEach
    void criar() {
        // O construtor calcula o hash fictício usado quando o e-mail não existe
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AuthService(
                usuarioService,
                passwordEncoder,
                tokenAcessoService,
                refreshTokenService,
                bloqueioLoginService,
                auditoriaLoginService,
                segundoFatorService,
                tokens,
                Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "{argon2}hash-da-ana", Papel.ALUNO);
    }

    @Test
    @DisplayName("login correto emite os tokens, zera o bloqueio, audita SUCESSO e regrava hash antigo se preciso")
    void loginCorreto() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);
        when(refreshTokenService.emitir(7L)).thenReturn(new RefreshEmitido("refresh", 7L, EXPIRA));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = (Sessao) service.entrar("ana@celke.uno", "senha-certa", ORIGEM);

        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.refresh().token()).isEqualTo("refresh");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SUCESSO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(usuarioService).atualizarHashSeNecessario(ana, "senha-certa");
    }

    @Test
    @DisplayName("senha errada conta falha, audita SENHA_INVALIDA e não emite tokens")
    void senhaErrada() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SENHA_INVALIDA, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(usuarioService, never()).atualizarHashSeNecessario(any(), any());
    }

    @Test
    @DisplayName("e-mail inexistente confere a senha contra o hash fictício, conta falha e audita EMAIL_DESCONHECIDO")
    void emailInexistente() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ninguem@celke.uno", "qualquer", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(passwordEncoder).matches("qualquer", HASH_FICTICIO);
        verify(auditoriaLoginService).registrar("ninguem@celke.uno", null, ResultadoLogin.EMAIL_DESCONHECIDO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ninguem@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("usuário inativo com a senha certa é recusado e auditado como USUARIO_INATIVO")
    void usuarioInativo() {
        Usuario inativa = UsuarioFixture.inativo(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(inativa));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(auditoriaLoginService).registrar("ana@celke.uno", inativa, ResultadoLogin.USUARIO_INATIVO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("senha certa com e-mail não confirmado recebe 403 próprio, sem tokens, e zera o bloqueio")
    void emailNaoConfirmado() {
        Usuario pendente = UsuarioFixture.naoConfirmado(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(pendente));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(EmailNaoConfirmadoException.class)
                .hasMessage("Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", pendente, ResultadoLogin.EMAIL_NAO_CONFIRMADO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("e-mail não confirmado com a senha errada recebe a mensagem genérica")
    void emailNaoConfirmadoSenhaErrada() {
        when(usuarioService.buscarPorEmail("ana@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("login bloqueado é recusado antes de buscar o usuário ou calcular hash, e auditado como BLOQUEADO")
    void bloqueado() {
        doThrow(new MuitasTentativasException(Duration.ofMinutes(15)))
                .when(bloqueioLoginService)
                .verificar("ana@celke.uno", "203.0.113.7");

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 15 minutos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", null, ResultadoLogin.BLOQUEADO, ORIGEM);
        verify(usuarioService, never()).buscarPorEmail(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
    }

    @Test
    @DisplayName("renovar emite novo access token para o dono do refresh")
    void renovar() {
        Usuario ana = ana();
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.renovar("refresh");

        assertThat(sessao.refresh().token()).isEqualTo("novo");
        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("refresh recusado vira sessão inválida")
    void refreshRecusado() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("refresh"))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessage("Sessão expirada. Entre novamente.");
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("refresh de usuário desativado vira sessão inválida")
    void renovarUsuarioInativo() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.renovar("refresh")).isInstanceOf(SessaoInvalidaException.class);
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("sair e sair de todos delegam a revogação")
    void sair() {
        service.sair("refresh");
        service.sairDeTodos(7L);

        verify(refreshTokenService).revogarFamiliaDoToken("refresh");
        verify(refreshTokenService).revogarTodas(7L);
    }

    private static Usuario admin() {
        return UsuarioFixture.usuario(9, "admin@celke.uno", "{argon2}hash-do-admin", Papel.ADMIN);
    }

    @Test
    @DisplayName("admin com a senha certa recebe o desafio, sem sessão, e o bloqueio não zera")
    void adminRecebeDesafio() {
        Usuario admin = admin();
        when(usuarioService.buscarPorEmail("admin@celke.uno")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-do-admin")).thenReturn(true);
        when(tokens.emitir(9L, TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of("desafio"));
        when(tokens.validade(TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Duration.ofMinutes(5));
        when(segundoFatorService.ativo(9L)).thenReturn(false);

        var entrada = service.entrar("admin@celke.uno", "senha-certa", ORIGEM);

        assertThat(entrada).isEqualTo(new Desafio("desafio", true, Instant.parse("2026-01-10T12:05:00Z")));
        verify(auditoriaLoginService)
                .registrar("admin@celke.uno", admin, ResultadoLogin.SEGUNDO_FATOR_PENDENTE, ORIGEM);
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(bloqueioLoginService, never()).registrarSucesso(anyString(), anyString());
    }

    @Test
    @DisplayName("código errado conta falha, audita e não gasta o desafio")
    void codigoErrado() {
        Usuario admin = admin();
        when(tokens.dono("desafio", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of(9L));
        when(usuarioService.buscarPorId(9L)).thenReturn(Optional.of(admin));
        when(segundoFatorService.verificar(admin, "000000", null)).thenReturn(new Verificacao(null, List.of()));

        assertThatThrownBy(() -> service.concluirSegundoFator("desafio", "000000", null, ORIGEM))
                .isInstanceOf(CodigoInvalidoException.class);
        verify(auditoriaLoginService)
                .registrar("admin@celke.uno", admin, ResultadoLogin.SEGUNDO_FATOR_INVALIDO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("admin@celke.uno", "203.0.113.7");
        verify(tokens, never()).consumir(anyString(), any());
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("código certo gasta o desafio, zera o bloqueio, audita e abre a sessão")
    void codigoCerto() {
        Usuario admin = admin();
        when(tokens.dono("desafio", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of(9L));
        when(tokens.consumir("desafio", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of(9L));
        when(usuarioService.buscarPorId(9L)).thenReturn(Optional.of(admin));
        when(segundoFatorService.verificar(admin, "123456", null))
                .thenReturn(new Verificacao(ResultadoLogin.SEGUNDO_FATOR_CADASTRADO, List.of("abcde-fghjk")));
        when(refreshTokenService.emitir(9L)).thenReturn(new RefreshEmitido("refresh", 9L, EXPIRA));
        when(tokenAcessoService.emitir(admin)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.concluirSegundoFator("desafio", "123456", null, ORIGEM);

        assertThat(sessao.sessao().acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.codigosRecuperacao()).containsExactly("abcde-fghjk");
        verify(auditoriaLoginService)
                .registrar("admin@celke.uno", admin, ResultadoLogin.SEGUNDO_FATOR_CADASTRADO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("admin@celke.uno", "203.0.113.7");
    }

    @Test
    @DisplayName("desafio vencido, usado ou de conta inativa é recusado")
    void desafioInvalido() {
        when(tokens.dono("velho", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concluirSegundoFator("velho", "123456", null, ORIGEM))
                .isInstanceOf(DesafioInvalidoException.class);
        assertThatThrownBy(() -> service.cadastrarSegundoFator("velho")).isInstanceOf(DesafioInvalidoException.class);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/CadastroServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CadastroServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenUsoUnicoService tokens;

    @Mock
    private EmailService emailService;

    private CadastroService service;

    @BeforeEach
    void criar() {
        service = new CadastroService(
                usuarioService,
                tokens,
                emailService,
                new ContaProperties("http://localhost:5173/", Duration.ofHours(48), Duration.ofHours(1), 3));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    private void esperarToken(String token) {
        when(tokens.emitir(7L, TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.of(token));
        when(tokens.validade(TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Duration.ofHours(48));
    }

    @Test
    @DisplayName("e-mail novo: cria a conta pendente e envia o link de confirmação")
    void emailNovo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno("Ana", "ana@celke.uno", "senha-da-ana"))
                .thenReturn(UsuarioFixture.naoConfirmado(ana()));
        esperarToken("abc");

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService)
                .enviar(
                        "ana@celke.uno",
                        "Confirme o seu e-mail",
                        "confirmacao-email",
                        Map.of(
                                "nome", "Usuário 7",
                                "link", "http://localhost:5173/confirmar-email?token=abc",
                                "validade", "48 horas"));
    }

    @Test
    @DisplayName("e-mail de conta ativa: não cria nada e avisa o dono só uma vez por hora")
    void emailExistente() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));

        service.cadastrar("Outra Pessoa", "ana@celke.uno", "girassol-azul-77");
        service.cadastrar("Outra Pessoa", "ana@celke.uno", "girassol-azul-77");

        verify(usuarioService, never()).cadastrarAluno(any(), any(), any());
        verify(emailService, times(1))
                .enviar(
                        "ana@celke.uno",
                        "Tentativa de cadastro com o seu e-mail",
                        "cadastro-existente",
                        Map.of(
                                "nome", "Usuário 7",
                                "linkEntrar", "http://localhost:5173/entrar",
                                "linkRecuperar", "http://localhost:5173/recuperar-senha"));
    }

    @Test
    @DisplayName("e-mail com cadastro pendente: reenvia o link, sem criar outra conta")
    void emailPendente() {
        when(usuarioService.buscarPorEmail("ana@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        esperarToken("novo");

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(usuarioService, never()).cadastrarAluno(any(), any(), any());
        verify(emailService).enviar(eq("ana@celke.uno"), eq("Confirme o seu e-mail"), eq("confirmacao-email"), any());
    }

    @Test
    @DisplayName("e-mail de conta inativa: nada acontece")
    void contaInativa() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("limite de e-mails atingido: a conta é criada, mas o link não é enviado")
    void limiteDeEmails() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno(any(), any(), any())).thenReturn(UsuarioFixture.naoConfirmado(ana()));
        when(tokens.emitir(7L, TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.empty());

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("cadastro simultâneo com o mesmo e-mail não vira erro para o cliente")
    void cadastroSimultaneo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno(any(), any(), any()))
                .thenThrow(new ConflitoException("Já existe um usuário com este e-mail."));

        assertThatCode(() -> service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana"))
                .doesNotThrowAnyException();
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("reenviar confirmação só envia para conta ativa com e-mail pendente")
    void reenviar() {
        when(usuarioService.buscarPorEmail("pendente@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        when(usuarioService.buscarPorEmail("confirmada@celke.uno")).thenReturn(Optional.of(ana()));
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());
        esperarToken("abc");

        service.reenviarConfirmacao("pendente@celke.uno");
        service.reenviarConfirmacao("confirmada@celke.uno");
        service.reenviarConfirmacao("ninguem@celke.uno");

        verify(emailService, times(1)).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("confirmar com token válido confirma o e-mail do dono")
    void confirmar() {
        Usuario pendente = UsuarioFixture.naoConfirmado(ana());
        when(tokens.consumir("abc", TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(pendente));

        service.confirmarEmail("abc");

        verify(usuarioService).confirmarEmail(pendente);
    }

    @Test
    @DisplayName("confirmar com token inválido é recusado sem confirmar nada")
    void confirmarTokenInvalido() {
        when(tokens.consumir("abc", TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmarEmail("abc"))
                .isInstanceOf(LinkInvalidoException.class)
                .hasMessage("Link inválido ou expirado. Peça um novo.");
        verify(usuarioService, never()).confirmarEmail(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/RecuperacaoSenhaServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.CampoInvalidoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenUsoUnicoService tokens;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private RecuperacaoSenhaService service;

    @BeforeEach
    void criar() {
        service = new RecuperacaoSenhaService(
                usuarioService,
                tokens,
                emailService,
                refreshTokenService,
                new ContaProperties("http://localhost:5173", Duration.ofHours(48), Duration.ofHours(1), 3));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    @Test
    @DisplayName("conta ativa recebe o link de 1 hora para criar nova senha")
    void solicitar() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(tokens.emitir(7L, TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of("abc"));
        when(tokens.validade(TipoToken.RECUPERACAO_SENHA)).thenReturn(Duration.ofHours(1));

        service.solicitar("ana@celke.uno");

        verify(emailService)
                .enviar(
                        "ana@celke.uno",
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", "Usuário 7",
                                "link", "http://localhost:5173/redefinir-senha?token=abc",
                                "validade", "1 hora"));
    }

    @Test
    @DisplayName("e-mail sem conta ou de conta inativa não recebe nada e não gera token")
    void semConta() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        service.solicitar("ninguem@celke.uno");
        service.solicitar("ana@celke.uno");

        verify(tokens, never()).emitir(anyLong(), any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("limite de e-mails atingido: nada é enviado")
    void limiteDeEmails() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(tokens.emitir(7L, TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.empty());

        service.solicitar("ana@celke.uno");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("redefinir troca a senha, confirma o e-mail e encerra todas as sessões")
    void redefinir() {
        Usuario ana = ana();
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(tokens.consumir("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));

        service.redefinir("abc", "nova-senha-da-ana");

        verify(usuarioService).trocarSenha(ana, "nova-senha-da-ana");
        verify(usuarioService).confirmarEmail(ana);
        verify(refreshTokenService).revogarTodas(7L);
    }

    @Test
    @DisplayName("token inválido é recusado sem trocar a senha nem encerrar sessões")
    void tokenInvalido() {
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinir("abc", "nova-senha-da-ana"))
                .isInstanceOf(LinkInvalidoException.class);
        verify(usuarioService, never()).trocarSenha(any(), any());
        verify(refreshTokenService, never()).revogarTodas(anyLong());
    }

    @Test
    @DisplayName("senha fora da política é recusada no campo sem gastar o link")
    void senhaFraca() {
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana()));

        assertThatThrownBy(() -> service.redefinir("abc", "1234567890"))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage(PoliticaSenha.COMUM);
        verify(tokens, never()).consumir(anyString(), any());
        verify(usuarioService, never()).trocarSenha(any(), any());
    }

    @Test
    @DisplayName("conta desativada depois do pedido não troca a senha")
    void contaInativa() {
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.redefinir("abc", "nova-senha-da-ana"))
                .isInstanceOf(LinkInvalidoException.class);
        verify(usuarioService, never()).trocarSenha(any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/TokenAcessoServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Emissão com o encoder real e conferência com o decoder real da {@link JwtConfig}. */
class TokenAcessoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final AuthProperties PROPS = new AuthProperties(
            "dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt",
            "plataforma-cursos",
            Duration.ofMinutes(10),
            Duration.ofDays(14),
            true,
            List.of());

    private final JwtConfig config = new JwtConfig();
    private final TokenAcessoService service =
            new TokenAcessoService(config.jwtEncoder(PROPS), PROPS, Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    @DisplayName("token leva emissor, id do usuário, papéis ordenados e validade de 10 minutos")
    void claims() {
        var usuario = UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ADMIN, Papel.ALUNO);

        var token = service.emitir(usuario);
        Jwt jwt = decoderEm(AGORA.plusSeconds(1)).decode(token.valor());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("plataforma-cursos");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("papeis")).containsExactly("ADMIN", "ALUNO");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
        assertThat(token.expiraEm()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    @DisplayName("token vale até um segundo antes do fim e é recusado no instante em que expira")
    void validade() {
        var token = service.emitir(UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ALUNO));
        Instant expira = AGORA.plus(Duration.ofMinutes(10));

        assertThat(decoderEm(expira.minusSeconds(1)).decode(token.valor())).isNotNull();
        assertThatThrownBy(() -> decoderEm(expira.plusSeconds(1)).decode(token.valor()))
                .isInstanceOf(JwtValidationException.class);
    }

    private org.springframework.security.oauth2.jwt.JwtDecoder decoderEm(Instant instante) {
        // Conta sempre ativa: a validação da situação da conta tem teste próprio
        UsuarioService usuarios = Mockito.mock(UsuarioService.class);
        Mockito.when(usuarios.buscarPorId(Mockito.anyLong()))
                .thenReturn(Optional.of(UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ALUNO)));
        return config.jwtDecoder(PROPS, Clock.fixed(instante, ZoneOffset.UTC), new SessaoValidaValidator(usuarios));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/ConfiguracaoGatewayServiceTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.GatewayHotmart;
import br.com.celke.cursos.pagamento.infra.hotmart.InterpretadorHotmart;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.ErroCampo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConfiguracaoGatewayServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String CHAVE = "dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=";

    private final JsonMapper json = JsonMapper.builder().build();
    private final GatewaysRegistrados registrados = new GatewaysRegistrados(
            List.of(new GatewayHotmart(new InterpretadorHotmart(json), null, Clock.fixed(AGORA, ZoneOffset.UTC))));
    private final ConfiguracaoGatewayService service =
            new ConfiguracaoGatewayService(registrados, new CifraSegredos(CHAVE, "PAGAMENTOS_CHAVE"), json);

    private static Gateway gateway() {
        return new Gateway(TipoGateway.HOTMART, "Hotmart", "token-do-webhook", AGORA);
    }

    private static Map<String, String> valores(String... chavesEValores) {
        Map<String, String> valores = new HashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            valores.put(chavesEValores[i], chavesEValores[i + 1]);
        }
        return valores;
    }

    @Test
    @DisplayName("separa os segredos (cifrados) dos campos comuns e aplica os padrões")
    void grava() {
        Gateway gateway = gateway();

        service.configurar(gateway, " Minha Hotmart ", valores("hottok", " segredo-hottok ", "clientId", "abc"), AGORA);

        assertThat(gateway.getNome()).isEqualTo("Minha Hotmart");
        assertThat(gateway.getConfiguracao())
                .isEqualTo(
                        "{\"ambiente\":\"PRODUCAO\",\"clientId\":\"abc\",\"urlCheckoutBase\":\"https://pay.hotmart.com\"}");
        assertThat(gateway.getSegredos()).isNotNull();
        assertThat(service.segredos(gateway)).isEqualTo(Map.of("hottok", "segredo-hottok"));
        assertThat(service.ler(gateway).valores())
                .containsEntry("hottok", "segredo-hottok")
                .containsEntry("clientId", "abc")
                .containsEntry("ambiente", "PRODUCAO");
    }

    @Test
    @DisplayName("segredo em branco mantém o valor atual; informado, troca")
    void segredoEmBrancoMantem() {
        Gateway gateway = gateway();
        service.configurar(gateway, "Hotmart", valores("hottok", "antigo", "basic", "b-antigo"), AGORA);

        service.configurar(gateway, "Hotmart", valores("hottok", "", "basic", "b-novo"), AGORA);

        assertThat(service.segredos(gateway)).isEqualTo(Map.of("hottok", "antigo", "basic", "b-novo"));
        assertThat(service.resumoSegredos(gateway))
                .containsExactly(
                        Map.entry("hottok", new SegredoResumo(true, "tigo")),
                        Map.entry("clientSecret", new SegredoResumo(false, null)),
                        Map.entry("basic", new SegredoResumo(true, "novo")));
    }

    @Test
    @DisplayName("segredo curto demais não mostra os últimos caracteres")
    void segredoCurto() {
        Gateway gateway = gateway();
        service.configurar(gateway, "Hotmart", valores("hottok", "1234"), AGORA);

        assertThat(service.resumoSegredos(gateway).get("hottok")).isEqualTo(new SegredoResumo(true, null));
    }

    @Test
    @DisplayName("campo obrigatório, opção, endereço, tamanho e campo desconhecido viram erros de campo")
    void validacao() {
        Gateway gateway = gateway();

        assertThatThrownBy(() -> service.configurar(
                        gateway,
                        "Hotmart",
                        valores(
                                "ambiente", "TESTE",
                                "urlCheckoutBase", "ftp://pay",
                                "clientId", "x".repeat(501),
                                "inventado", "1"),
                        AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class)
                .hasMessage("Dados inválidos.")
                .extracting(e -> ((ConfiguracaoInvalidaException) e).getErros())
                .isEqualTo(List.of(
                        new ErroCampo("valores.ambiente", "Escolha uma das opções: PRODUCAO, SANDBOX."),
                        new ErroCampo("valores.clientId", "Use no máximo 500 caracteres."),
                        new ErroCampo("valores.hottok", "Informe Hottok."),
                        new ErroCampo("valores.inventado", "Campo desconhecido."),
                        new ErroCampo("valores.urlCheckoutBase", "Informe um endereço começando com https://.")));
        assertThat(gateway.getSegredos()).isNull();
        assertThat(gateway.getConfiguracao()).isEqualTo("{}");
    }

    @Test
    @DisplayName("endereço sem host ou malformado é recusado; valores nulos contam como vazios")
    void enderecoInvalido() {
        assertThatThrownBy(() -> service.configurar(
                        gateway(), "Hotmart", valores("hottok", "h", "urlCheckoutBase", "https://"), AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
        assertThatThrownBy(() -> service.configurar(
                        gateway(), "Hotmart", valores("hottok", "h", "urlCheckoutBase", "https://a b"), AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
        assertThatThrownBy(() -> service.configurar(gateway(), "Hotmart", null, AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
    }

    @Test
    @DisplayName("sem a PAGAMENTOS_CHAVE não grava segredos")
    void semChave() {
        var semChave = new ConfiguracaoGatewayService(registrados, new CifraSegredos("", "PAGAMENTOS_CHAVE"), json);
        Gateway gateway = gateway();

        assertThatThrownBy(() -> semChave.configurar(gateway, "Hotmart", valores("hottok", "h"), AGORA))
                .isInstanceOf(ConflitoException.class)
                .hasMessage(
                        "Defina a PAGAMENTOS_CHAVE no .env do ambiente para gravar as credenciais do meio de pagamento.");
        assertThat(gateway.getSegredos()).isNull();
    }

    @Test
    @DisplayName("sem segredos gravados, a leitura traz só os comuns")
    void semSegredos() {
        assertThat(service.ler(gateway()).valores()).isEmpty();
        assertThat(service.segredos(gateway())).isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/IntegrationTest.java`:

`````java
package br.com.celke.cursos.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.domain.Totp;
import br.com.celke.cursos.shared.fila.ProcessadorFila;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Base de todos os testes de integração (*IT). Não altere para atender um teste específico:
 * qualquer variação cria outro contexto Spring e deixa a suíte lenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Containers.class)
public abstract class IntegrationTest {

    /** Senha dos usuários criados por {@link #criarUsuario(Papel...)}. */
    protected static final String SENHA_TESTE = "senha-do-usuario-de-teste";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcessadorFila processadorFila;

    /** Identificador único em runtime para os dados criados pelo teste (prefixo {@code it-}). */
    protected static String stamp() {
        return "it-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }

    /** Segredo do aplicativo autenticador de cada administrador cadastrado pelos testes (por e-mail). */
    private static final Map<String, String> SEGREDOS_TOTP = new ConcurrentHashMap<>();

    /**
     * Faz login, confere o 200 e devolve a resposta (corpo com o access token e cookie de refresh). O
     * administrador passa pelo segundo fator: no primeiro login o teste cadastra o aplicativo e guarda o
     * segredo para calcular os próximos códigos.
     */
    protected MvcTestResult entrar(String email, String senha) {
        MvcTestResult resposta = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
        assertThat(resposta).hasStatusOk();
        String etapa = ler(resposta, "$.segundoFator");
        if (etapa == null) {
            return resposta;
        }
        String desafio = ler(resposta, "$.desafio");
        if ("CADASTRO".equals(etapa)) {
            MvcTestResult cadastro = mvc.post()
                    .uri("/api/auth/segundo-fator/cadastro")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"desafio\":\"%s\"}".formatted(desafio))
                    .exchange();
            assertThat(cadastro).hasStatusOk();
            SEGREDOS_TOTP.put(email.toLowerCase(), ler(cadastro, "$.segredo"));
        }
        return concluirSegundoFator(desafio, codigoTotp(email));
    }

    /** Código do aplicativo autenticador de um administrador cadastrado por {@link #entrar}. */
    protected static String codigoTotp(String email) {
        String segredo = SEGREDOS_TOTP.get(email.toLowerCase());
        assertThat(segredo)
                .as("segundo fator de %s cadastrado pelos testes", email)
                .isNotNull();
        return Totp.codigo(segredo, Totp.passo(Instant.now()));
    }

    /** Segunda etapa do login com o código do aplicativo; confere o 200. */
    protected MvcTestResult concluirSegundoFator(String desafio, String codigo) {
        MvcTestResult resposta = mvc.post()
                .uri("/api/auth/segundo-fator")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo))
                .exchange();
        assertThat(resposta).hasStatusOk();
        return resposta;
    }

    private static String ler(MvcTestResult resposta, String caminho) {
        try {
            return JsonPath.<String>read(resposta.getResponse().getContentAsString(), caminho);
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Faz login e devolve o access token. */
    protected String login(String email, String senha) {
        return accessToken(entrar(email, senha));
    }

    protected static String accessToken(MvcTestResult resposta) {
        try {
            return JsonPath.read(resposta.getResponse().getContentAsString(), "$.accessToken");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Processa todos os jobs pendentes (no worker isso é feito pelo consumo agendado). */
    protected void processarFila() {
        while (processadorFila.processarProximo()) {
            // continua enquanto houver job disponível
        }
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Cria um usuário próprio do teste (e-mail com {@code stamp}), para fluxos que alteram a sessão
     * ou os dados do usuário — os usuários do seed são só leitura.
     */
    protected UsuarioTeste criarUsuario(Papel... papeis) {
        String email = stamp() + "@teste.celke.uno";
        var usuario = usuarioService.criarConfirmado("Usuário " + email, email, SENHA_TESTE, Set.of(papeis));
        return new UsuarioTeste(usuario.getId(), email, SENHA_TESTE);
    }

    protected record UsuarioTeste(Long id, String email, String senha) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/application/CriarAdminServiceTest.java`:

`````java
package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CriarAdminServiceTest {

    @Mock
    private UsuarioService usuarioService;

    private CriarAdminService service;

    @BeforeEach
    void criar() {
        service = new CriarAdminService(usuarioService);
    }

    @Test
    @DisplayName("cria o usuário só com o papel ADMIN")
    void criaAdmin() {
        service.criar("Admin", "admin@celke.uno", "girassol-azul-do-lago");

        verify(usuarioService)
                .criarConfirmado("Admin", "admin@celke.uno", "girassol-azul-do-lago", Set.of(Papel.ADMIN));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "nome em branco,      '  ',  admin@celke.uno, senha-longa-do-admin, O nome é obrigatório.",
        "e-mail sem arroba,   Admin, admin.celke.uno, senha-longa-do-admin, E-mail inválido.",
        "senha curta,         Admin, admin@celke.uno, curta-11chr,          A senha deve ter pelo menos 12 caracteres.",
    })
    void rejeitaDadosInvalidos(String caso, String nome, String email, String senha, String mensagem) {
        assertThatThrownBy(() -> service.criar(nome, email, senha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensagem);
        verify(usuarioService, never()).criarConfirmado(any(), any(), any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/domain/PoliticaSenhaTest.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaSenhaTest {

    /** Texto sem sequência, repetição nem palavra comum, do tamanho pedido. */
    private static String imprevisivel(int tamanho) {
        String base = "Vx7#qLm2!Rt9@pZ4$wK8%nB3&";
        return base.repeat(tamanho / base.length() + 1).substring(0, tamanho);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um caractere abaixo do mínimo, 11,  'A senha deve ter pelo menos 12 caracteres.'",
        "exatamente o mínimo,           12,  ",
        "exatamente o máximo,           128, ",
        "um caractere acima do máximo,  129, 'A senha deve ter no máximo 128 caracteres.'",
    })
    void validaTamanho(String caso, int tamanho, String esperado) {
        assertThat(PoliticaSenha.validar(imprevisivel(tamanho), PoliticaSenha.MINIMO_ADMIN))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"             "})
    void senhaObrigatoria(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN)).isEqualTo("A senha é obrigatória.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"123456789012", "1234567890", "qwertyuiop", "QWERTYUIOP", "password123"})
    @DisplayName("senha da lista de comuns e vazadas, sem diferenciar maiúsculas, é recusada")
    void comum(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO)).isEqualTo(PoliticaSenha.COMUM);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "contém o usuário do e-mail, mariana.silva2026,  Mariana Silva,       mariana.silva@celke.com.br",
        "contém uma palavra do nome, xablau-OLIVEIRA-77, João de Oliveira,    joao@celke.com.br",
        "contém o e-mail inteiro,    joao@celke.com.br!, João,                joao@celke.com.br",
    })
    @DisplayName("senha com o nome ou o e-mail da pessoa é recusada")
    void pessoal(String caso, String senha, String nome, String email) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO, nome, email))
                .isEqualTo(PoliticaSenha.PESSOAL);
    }

    @Test
    @DisplayName("trechos curtos do nome não contam e dados vazios são ignorados")
    void trechosCurtos() {
        assertThat(PoliticaSenha.validar("bananada-Vx7#q", PoliticaSenha.MINIMO_ALUNO, "Ana Lu", null, " "))
                .isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "%%%%%%%%%%%%",
                "abababababab",
                "xyzxyzxyzxyz",
                "Vx7#Vx7#Vx7#",
                "lkjhgfdsapoi",
                "zyxwvutsrqpo",
                "6789012345678",
                "7ujm8ik9ol0p"
            })
    @DisplayName("sequência de teclado ou de alfabeto e repetição são recusadas")
    void previsivel(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO)).isEqualTo(PoliticaSenha.PREVISIVEL);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"123456A#b123", "minha-nova-senha", "senha-forte-do-teste-e2e", "cavalo correto bateria"})
    @DisplayName("senhas longas e fora dos padrões passam")
    void aceitas(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN, "Ana Souza", "ana@celke.com.br"))
                .isNull();
    }
}
`````

`backend/src/test/resources/application-test.yaml`:

`````yaml
# Profile dos testes de integração. Endereços dos containers vêm de support/Containers.
spring:
  flyway:
    # Usuário da aplicação criado pelo afterMigrate.sql (o UsuarioAplicacaoBancoIT confere o acesso dele)
    placeholders:
      usuario_app: app_teste
      senha_app: senha-do-app-de-teste
  mail:
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
      mail.smtp.starttls.required: false

app:
  auth:
    # Chave só dos testes (48 bytes em Base64)
    jwt-segredo: dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt
    origens-permitidas: http://localhost:5173
    segundo-fator:
      # Chave só dos testes (32 bytes em Base64); os testes entram várias vezes no mesmo intervalo de 30 s
      chave: dGVzdGUtZGEtY2hhdmUtZG8tc2VndW5kby1mYXRvcjI=
      rejeitar-reuso: false
  conta:
    url-aluno: http://localhost:5173
  limites:
    # Todas as requisições da suíte saem do mesmo IP: o limite por IP fica fora do caminho
    requisicoes-por-ip-por-minuto: 100000
  limites-usuario:
    # O admin do seed grava em quase toda a suíte: o limite fica fora do caminho (teste próprio no filtro)
    escritas-por-minuto: 100000
    checkouts-por-hora: 100000
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
  pagamentos:
    # Chave só dos testes (32 bytes em Base64)
    chave: dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=
    hotmart:
      # Sem saída para a internet: o teste de credenciais falha na conexão
      url-token: http://localhost:1/security/oauth/token
      timeout: PT2S
  forum:
    # Limite baixo para o teste do 429 (cada teste usa um aluno próprio)
    mensagens-por-janela: 5
`````

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

O dump traz as tabelas, não os papéis do PostgreSQL: a subida depois da restauração passa pelo serviço
`migracao`, que recria o usuário da aplicação e as permissões dele.

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
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://aluno.celke.uno/actuator/health` e `https://cursos.celke.uno`, com aviso por e-mail e celular |
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

1. Criar o bucket no R2, a chave da aplicação e o CORS para `aluno.celke.uno` e `adm.celke.uno`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `media.celke.uno` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; recusa as senhas mais comuns e vazadas (lista de ~117 mil), as que contêm o nome ou o e-mail e as sequências e repetições; medidor de força nas telas; **segundo fator (TOTP) obrigatório para administradores**, com códigos de recuperação de uso único; bloqueio após falhas por e-mail e por e-mail + IP (o código errado do segundo fator conta como falha); limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões; o ADM bloqueia a conta ou encerra as sessões e o access token já emitido deixa de valer na hora (conferido a cada requisição, 30 s em memória); token de link de e-mail sai da barra de endereço depois de lido |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método e conferidas por um teste que varre todas as rotas; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR); limite por usuário nas gravações (120 por minuto) e no checkout (10 por hora); a compra só abre o pedido com a confirmação do aluno; checkout só em HTTPS nos domínios da Hotmart |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); `npm audit` das dependências do navegador (alta ou crítica quebra o build); Dependabot semanal para Maven, npm, GitHub Actions e imagens Docker; versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção; a API e o worker entram no banco com um usuário que só lê e grava dados (o dono do schema fica para as migrations) |

### Segundo fator dos administradores

- Todo usuário com o papel `ADMIN` entra em duas etapas: a senha certa devolve um desafio (válido por
  5 minutos, sem sessão nem cookie) e a sessão só abre com o código de 6 dígitos do aplicativo
  autenticador (Google Authenticator, Microsoft Authenticator, 1Password...).
- No primeiro acesso, a tela mostra o QR code; o primeiro código certo ativa o segundo fator e mostra
  **uma vez** os 10 códigos de recuperação (cada um vale uma entrada sem o celular).
- O segredo fica cifrado no banco (AES-256-GCM, chave `TOTP_CHAVE` do `.env`); o mesmo código não vale
  duas vezes; cada etapa fica na auditoria de login (`SEGUNDO_FATOR_PENDENTE`, `SEGUNDO_FATOR_INVALIDO`,
  `SEGUNDO_FATOR_CADASTRADO`, `CODIGO_RECUPERACAO`).
- Perdeu o celular e os códigos? Na VPS, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml run --rm api redefinir-2fa admin@celke.com.br
  ```

  O comando apaga o segundo fator e encerra as sessões; o próximo login pede o cadastro de novo.
- Trocar a `TOTP_CHAVE` invalida todos os segredos: rode o `redefinir-2fa` para cada administrador.

### Usuário do banco da aplicação

- O serviço `migracao` (mesma imagem da API, comando `migrar`) roda a cada deploy com o dono do schema
  (`POSTGRES_USER`), aplica as migrations e, no `afterMigrate.sql`, cria (na primeira vez) e libera o
  usuário da aplicação (`POSTGRES_APP_USER`): `SELECT`, `INSERT`, `UPDATE` e `DELETE` nas tabelas, uso
  das sequências, sem criar, alterar ou apagar tabelas e sem acesso ao histórico do Flyway.
- A API e o worker sobem só depois dele, com `POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD`. Uma injeção
  de SQL ou uma conta roubada da aplicação não derruba tabelas nem apaga a trilha de auditoria (o
  gatilho continua barrando `UPDATE` e `DELETE` nela).
- O `deploy.sh` acrescenta ao `.env` as variáveis novas (`POSTGRES_APP_USER`, `POSTGRES_APP_PASSWORD`,
  `TOTP_CHAVE`) que ainda não existirem, com valores aleatórios.

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-aluno.celke.uno/api/cursos -I
  ```
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
| `aluno.celke.uno` | produção | público |
| `adm.celke.uno` | produção | só `ADM_IPS` |
| `cursos.celke.uno` | produção | público (site gerado a partir da API) |
| `media.celke.uno` | produção | público (URLs assinadas) |
| `homolog-aluno.celke.uno`, `homolog-adm.celke.uno`, `homolog-media.celke.uno` | homologação | só `HOMOLOG_IPS`, `noindex`, com Swagger |

## 1. No painel da Hostinger

1. Anote o IP da VPS.
2. Firewall da VPS: libere só 22, 80 e 443 (TCP) e 443 (UDP). O `ufw` faz o mesmo dentro da VPS; o
   painel é a segunda camada.
3. Ative o backup/snapshot semanal do painel (não substitui o backup fora da VPS da fase de operação).

## 2. DNS de celke.uno

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

Confira antes de seguir: `dig +short aluno.celke.uno` responde o IP da VPS.

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

- `producao/.env` e `homolog/.env`: `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` (painel da Iagente).
  Na homologação, use um remetente identificado ou restrinja os envios a e-mails de teste.
- `caddy/.env`: `ACME_EMAIL`, `ADM_IPS` e `HOMOLOG_IPS` (seus IPs, separados por espaço).

Os segredos de cada `.env` são gerados aleatoriamente e são diferentes entre os ambientes: um token de
homologação nunca vale na produção. Entre eles estão a senha do usuário da aplicação no banco
(`POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD`, só lê e grava dados) e a `TOTP_CHAVE`, que cifra o segundo
fator dos administradores. Num ambiente criado antes dessas variáveis, o próprio deploy as acrescenta
ao `.env`.

Depois da primeira subida, crie o administrador (`criar-admin`) e entre no ADM: o primeiro login pede o
cadastro do aplicativo autenticador (QR code) e mostra os códigos de recuperação, que devem ser
guardados fora da VPS.

## 7. Acesso às imagens (GHCR)

Crie no GitHub um token clássico só com `read:packages` e, na VPS, como `deploy`:

```bash
echo "<token>" | docker login ghcr.io -u <usuario-github> --password-stdin
```

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
2. Depois de validar a homologação, abra o PR de `homolog` para `main` e faça o merge: o pipeline
   implanta a produção.
3. Com os dois ambientes no ar (as redes deles existem), suba o Caddy na VPS, como `deploy`:

   ```bash
   cd /opt/plataforma-cursos
   docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d
   ```

4. Crie o administrador de cada ambiente (a senha é pedida no terminal):

   ```bash
   docker compose -p plataforma-cursos-producao -f producao/compose.prod.yml --env-file producao/.env \
     --project-directory producao run --rm api criar-admin admin@celke.uno "Administrador"
   docker compose -p plataforma-cursos-homolog -f homolog/compose.prod.yml -f homolog/compose.homolog.yml \
     --env-file homolog/.env --project-directory homolog run --rm api criar-admin admin@celke.uno "Administrador"
   ```

## 10. Verificação

Do seu computador:

```bash
curl -s https://aluno.celke.uno/actuator/health            # {"groups":[...],"status":"UP"}
curl -sI https://aluno.celke.uno/actuator/health | grep -i strict-transport
curl -s https://aluno.celke.uno/ | grep -o '<title>.*</title>'   # a área do aluno
curl -s https://aluno.celke.uno/v3/api-docs | head -c 15        # <!doctype html> (o front, não a API)
curl -s -o /dev/null -w "%{http_code}\n" https://adm.celke.uno/actuator/health # 403 fora de ADM_IPS
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

**Atualizar o Caddy** (é compartilhado pelos dois ambientes, então não entra no deploy automático):
copie os arquivos novos de `infra/caddy/` (`Caddyfile` e `sites/`) para `/opt/plataforma-cursos/caddy/`
e, como `deploy`:

```bash
cd /opt/plataforma-cursos
docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d --force-recreate
```

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
teste notificar `https://homolog-aluno.celke.uno/api/pagamentos/webhooks/<token>` (o endereço completo
aparece no painel). Configuração da Hotmart: `docs/pagamentos.md`.

**Logs persistentes e auditoria (Fase 17):** nada a configurar. O deploy cria o volume `logs` de cada
ambiente; a API e o worker gravam nele com retenção (180 dias ou 2 GB por serviço). Como ler:
`docs/operacao.md`, seção 4.

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

`infra/.env.example`:

`````bash
# Copie para infra/.env (ou rode ./infra/scripts/gerar-env.sh, que gera os segredos).
# Nunca faça commit do infra/.env.

# PostgreSQL
POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=                 # openssl rand -hex 24

# Autenticação: chave do JWT em Base64, com pelo menos 32 bytes
JWT_SEGREDO=                       # openssl rand -base64 48
# Segundo fator dos administradores: chave AES-256 dos segredos do aplicativo autenticador.
# Trocar a chave exige cadastrar o aplicativo de novo (comando redefinir-2fa)
TOTP_CHAVE=                        # openssl rand -base64 32
# Origens aceitas no refresh e no logout (padrão: a própria API e http://localhost:5173)
# AUTH_ORIGENS=http://localhost:8080,http://localhost:5173
# Área do aluno, base dos links enviados por e-mail (padrão: http://localhost:5173)
# FRONT_ALUNO_URL=http://localhost:5173

# Pagamentos: chave AES-256 dos segredos dos meios de pagamento (hottok, credenciais da API).
# Sem ela a API sobe, mas o painel não grava as credenciais. Trocar a chave exige informar os segredos de novo
PAGAMENTOS_CHAVE=                  # openssl rand -base64 32

# Garage (S3)
GARAGE_RPC_SECRET=                 # openssl rand -hex 32
GARAGE_ADMIN_TOKEN=                # openssl rand -hex 32
S3_BUCKET=midia
S3_ACCESS_KEY=                     # GK + openssl rand -hex 12
S3_SECRET_KEY=                     # openssl rand -hex 32
# Origens do front que podem enviar e baixar direto do bucket (separadas por vírgula)
S3_CORS_ORIGENS=http://localhost:5173

# E-mail
EMAIL_REMETENTE=nao-responda@celke.uno

# Portas no host (opcional; só mude se a porta padrão já estiver em uso)
# API_PORTA=8080
# POSTGRES_PORTA=5432
# S3_PORTA=3900
# MAILPIT_PORTA=8025
`````

`infra/compose.e2e.yml`:

`````yaml
# e2e: a stack de um ambiente dos servidores (compose.prod.yml, com os fronts) e o Caddy dos
# servidores, HTTP em *.localhost (sites de caddy/e2e.caddy), Mailpit no lugar do SMTP, imagens
# construídas localmente e o seed do profile e2e. Projeto separado do ambiente local.
#
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
name: plataforma-cursos-e2e

services:
  caddy:
    image: caddy:2.11.7-alpine
    ports:
      - "127.0.0.1:${E2E_PORTA:?defina E2E_PORTA}:80"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy/e2e.caddy:/etc/caddy/sites/e2e.caddy:ro
    depends_on:
      api:
        condition: service_healthy
      web:
        condition: service_healthy
      site:
        condition: service_healthy

  web:
    image: plataforma-cursos-web:e2e
    build:
      context: ../web
      target: web

  site:
    image: plataforma-cursos-site:e2e
    build:
      context: ../web
      target: site
    environment:
      SITE_URL: http://cursos.localhost:8088
      # Curso publicado pelo teste aparece em segundos
      SITE_INTERVALO: "3"

  migracao:
    image: plataforma-cursos-api:e2e

  api:
    image: plataforma-cursos-api:e2e
    build:
      context: ../backend
      target: api
    environment:
      SPRING_PROFILES_ACTIVE: api,e2e
      SEED_E2E_SENHA: ${SEED_E2E_SENHA:?defina SEED_E2E_SENHA}
      # Admin do seed com o segundo fator ativo; os testes calculam o código com este segredo
      SEED_E2E_TOTP: ${SEED_E2E_TOTP:?defina SEED_E2E_TOTP}
      # Os testes entram várias vezes no mesmo intervalo de 30 s
      APP_AUTH_SEGUNDO_FATOR_REJEITAR_REUSO: "false"
      # Todas as jornadas saem do mesmo IP
      APP_LIMITES_REQUISICOES_POR_IP_POR_MINUTO: "300"
      # O admin do seed grava em quase todas as jornadas
      APP_LIMITES_USUARIO_ESCRITAS_POR_MINUTO: "3000"
      APP_LIMITES_USUARIO_CHECKOUTS_POR_HORA: "300"
      # URLs dos segmentos vencem em segundos: a jornada de entrega confere a expiração
      APP_VIDEO_MARGEM_PLAYLIST: PT5S
      # Sem chamar a Hotmart de verdade: o "Testar credenciais" recebe uma recusa
      APP_PAGAMENTOS_HOTMART_URL_TOKEN: http://mailpit:8025/hotmart-indisponivel

  worker:
    image: plataforma-cursos-worker:e2e
    build:
      context: ../backend
      target: worker
    depends_on:
      mailpit:
        condition: service_healthy

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${E2E_MAILPIT_PORTA:?defina E2E_MAILPIT_PORTA}:8025"
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20
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
  # API e worker entram com o usuário da aplicação: só lê e grava dados (sem criar, alterar ou apagar
  # tabelas). O dono do schema (POSTGRES_USER) só é usado pelas migrations, no serviço migracao
  DB_USERNAME: ${POSTGRES_APP_USER:?defina POSTGRES_APP_USER}
  DB_PASSWORD: ${POSTGRES_APP_PASSWORD:?defina POSTGRES_APP_PASSWORD}
  SPRING_FLYWAY_ENABLED: "false"
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO}
  # Chave dos segredos do aplicativo autenticador (segundo fator dos administradores)
  TOTP_CHAVE: ${TOTP_CHAVE:?defina TOTP_CHAVE}
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
  # Aplica as migrations com o dono do schema e dá ao usuário da aplicação só o acesso aos dados
  # (criado na primeira vez). Roda a cada deploy e termina; a API só sobe depois dele
  migracao:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    command: ["migrar"]
    restart: "no"
    environment:
      <<: *app
      DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
      DB_APP_USERNAME: ${POSTGRES_APP_USER:?defina POSTGRES_APP_USER}
      DB_APP_PASSWORD: ${POSTGRES_APP_PASSWORD:?defina POSTGRES_APP_PASSWORD}
      SPRING_FLYWAY_ENABLED: "true"
      JAVA_TOOL_OPTIONS: -Xmx256m
    depends_on:
      postgres:
        condition: service_healthy

  api:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
      LOGGING_FILE_NAME: /var/log/plataforma-cursos/api.log
    volumes:
      - logs:/var/log/plataforma-cursos
    depends_on:
      migracao:
        condition: service_completed_successfully
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
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
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
      # O worker sobe depois da API (que sobe depois das migrations)
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 30s
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
    image: ${IMAGEM_WEB:-ghcr.io/celkecursos/plataforma-cursos-web}:${TAG:?defina TAG}
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
    image: ${IMAGEM_SITE:-ghcr.io/celkecursos/plataforma-cursos-site}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      API_URL: http://api:8080
      FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
      # Em branco, deriva do FRONT_ALUNO_URL (aluno.celke.uno → cursos.celke.uno)
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

`infra/compose.yml`:

`````yaml
# Ambiente local. Uso, na raiz do repositório:
#   ./infra/scripts/gerar-env.sh          (uma vez: cria infra/.env com segredos aleatórios)
#   docker compose -f infra/compose.yml up -d --build --wait
name: plataforma-cursos

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO no infra/.env}
  # Chave dos segredos do aplicativo autenticador (segundo fator dos administradores)
  TOTP_CHAVE: ${TOTP_CHAVE:?defina TOTP_CHAVE no infra/.env (openssl rand -base64 32)}
  # Origens aceitas no refresh e no logout: o Swagger da própria API e o front de desenvolvimento
  AUTH_ORIGENS: ${AUTH_ORIGENS:-http://localhost:${API_PORTA:-8080},http://localhost:5173}
  # Área do aluno: base dos links de confirmação de e-mail e de recuperação de senha
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:-http://localhost:5173}
  S3_ENDPOINT: http://garage:3900
  # Endereço que o navegador usa nas URLs pré-assinadas
  S3_ENDPOINT_PUBLICO: http://localhost:${S3_PORTA:-3900}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:-}
  # No desenvolvimento os e-mails ficam no Mailpit (http://localhost:8025)
  SMTP_HOST: mailpit
  SMTP_PORT: "1025"
  SMTP_AUTH: "false"
  SMTP_STARTTLS: "false"
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE no infra/.env}
  # Chave dos segredos dos meios de pagamento; sem ela, o painel não grava credenciais
  PAGAMENTOS_CHAVE: ${PAGAMENTOS_CHAVE:-}
  # Logs em JSON (ECS), com o id de correlação de cada requisição
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs

services:
  postgres:
    image: postgres:18.6-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
    volumes:
      # A partir da versão 18 os dados ficam em /var/lib/postgresql/18/docker
      - postgres:/var/lib/postgresql
    ports:
      - "127.0.0.1:${POSTGRES_PORTA:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20

  garage:
    image: dxflrs/garage:v2.4.1
    # Na primeira subida cria o layout de nó único, o bucket e a chave da aplicação
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET no infra/.env}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN no infra/.env}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    ports:
      - "127.0.0.1:${S3_PORTA:-3900}:3900"
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${MAILPIT_PORTA:-8025}:8025"   # painel web
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20

  api:
    build:
      context: ../backend
      target: api
    environment:
      <<: *app
      DOCS_ENABLED: "true"
    ports:
      - "127.0.0.1:${API_PORTA:-8080}:8080"
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
      mailpit:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20

  worker:
    build:
      context: ../backend
      target: worker
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 30s
      retries: 3

volumes:
  postgres:
  garage:
`````

`infra/e2e.env`:

`````bash
# Variáveis da stack de e2e. Valores só de teste (nenhum é usado em servidor): podem ficar no git.
AMBIENTE=e2e
TAG=e2e
E2E_PORTA=8088
E2E_MAILPIT_PORTA=8026

POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=senha-do-banco-e2e
POSTGRES_APP_USER=plataforma_cursos_app
POSTGRES_APP_PASSWORD=senha-da-aplicacao-e2e

JWT_SEGREDO=l4jEHfbYeLV+W8Nf8kJv9QeHkLLCCPJdCLS7pqIJFk5eTx/DoYnOd476xZTrRgMX
TOTP_CHAVE=ZTJlLWNoYXZlLWRvLXNlZ3VuZG8tZmF0b3ItMzJieXQ=

GARAGE_RPC_SECRET=066fb7d1d0a13a90272747cd99d0474d26e520e2d6b214200236a01299618731
GARAGE_ADMIN_TOKEN=token-do-admin-e2e
S3_BUCKET=midia
S3_ACCESS_KEY=GK0a51c4026047d3d88c8c6435
S3_SECRET_KEY=292c9b355552db653bcf18f785fda32855125263d3d912ea8658305d5c00d206

AUTH_ORIGENS=http://aluno.localhost:8088,http://adm.localhost:8088
FRONT_ALUNO_URL=http://aluno.localhost:8088
S3_ENDPOINT_PUBLICO=http://media.localhost:8088
S3_CORS_ORIGENS=http://aluno.localhost:8088,http://adm.localhost:8088

SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
EMAIL_REMETENTE=nao-responda@teste.celke.uno

SEED_E2E_SENHA='123456A#b123'
# Segredo (Base32) do aplicativo autenticador do admin do seed
SEED_E2E_TOTP=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP

PAGAMENTOS_CHAVE=ZTJlLWNoYXZlLWRlLXBhZ2FtZW50b3MtMzItYnl0ZXM=
`````

`infra/scripts/deploy.sh`:

`````bash
#!/usr/bin/env bash
# Deploy de um ambiente na VPS. O pipeline chama pela chave do usuário deploy, que fica presa a este
# script (command= no authorized_keys), mandando os arquivos de compose pela entrada:
#
#   tar -C infra -cz compose.prod.yml compose.homolog.yml garage/garage.toml \
#     | ssh deploy@<vps> "<producao|homolog> <sha-do-commit>"
#
# Passos: valida os argumentos, recebe só os arquivos esperados, faz o backup do banco, aplica a
# nova tag e espera todos os containers ficarem saudáveis. Se algum não ficar, volta para a versão
# anterior (arquivos e tag). O rollback não desfaz migrations: elas precisam ser compatíveis com a
# versão anterior (expandir → depois contrair).
set -euo pipefail

# A conexão SSH pode cair no meio: o deploy continua até o fim
trap '' HUP

BASE=${DEPLOY_BASE:-/opt/plataforma-cursos}
TEMPO_ESPERA=${DEPLOY_TEMPO_ESPERA:-300}
VERSOES_GUARDADAS=5
BACKUPS_GUARDADOS=10

falhar() {
  echo "ERRO: $*" >&2
  exit 1
}

# Com a chave presa ao script, os argumentos chegam em SSH_ORIGINAL_COMMAND
read -r AMBIENTE TAG EXTRA <<< "${SSH_ORIGINAL_COMMAND:-$*}" || true
[[ -z "${EXTRA:-}" ]] || falhar "argumentos demais; uso: <producao|homolog> <sha>"
case "${AMBIENTE:-}" in
  producao)
    ARQUIVOS=(compose.prod.yml garage/garage.toml)
    COMPOSE=(-f compose.prod.yml)
    ;;
  homolog)
    ARQUIVOS=(compose.prod.yml compose.homolog.yml garage/garage.toml)
    COMPOSE=(-f compose.prod.yml -f compose.homolog.yml)
    ;;
  *) falhar "ambiente inválido: '${AMBIENTE:-}' (use producao ou homolog)" ;;
esac
[[ "${TAG:-}" =~ ^[0-9a-f]{40}$ ]] || falhar "tag inválida: '${TAG:-}' (SHA completo do commit)"

DIR=$BASE/$AMBIENTE
[[ -f "$DIR/.env" ]] || falhar "$DIR/.env não existe"
cd "$DIR"

exec 9> .deploy.lock
flock -n 9 || falhar "já existe um deploy de $AMBIENTE em andamento"

log() {
  # Horário de Brasília, com o deslocamento, como nos logs da API e do worker
  echo "[$(TZ=America/Sao_Paulo date +%Y-%m-%dT%H:%M:%S%:z)] $AMBIENTE ${TAG:0:12}: $*" | tee -a deploy.log
}

compose() {
  docker compose -p "plataforma-cursos-$AMBIENTE" "${COMPOSE[@]}" "$@"
}

tag_atual() {
  sed -n 's/^TAG=//p' .env
}

# Guarda os arquivos de uma versão em versoes/<tag>/
guardar_arquivos() {
  local destino="versoes/$1"
  [[ -d "$destino" ]] && return
  mkdir -p "$destino"
  for arquivo in "${ARQUIVOS[@]}"; do
    [[ -f "$arquivo" ]] && install -D -m 640 "$arquivo" "$destino/$arquivo"
  done
}

# Coloca no lugar os arquivos e a tag de uma versão
aplicar_versao() {
  local tag=$1
  for arquivo in "${ARQUIVOS[@]}"; do
    install -D -m 640 "versoes/$tag/$arquivo" "$arquivo"
  done
  sed -i "s/^TAG=.*/TAG=$tag/" .env
}

subir() {
  compose up -d --pull missing --wait --wait-timeout "$TEMPO_ESPERA" --remove-orphans
}

# Segredos que uma versão nova passou a exigir: gerados uma vez, no primeiro deploy que precisa deles
completar_env() {
  local nome valor
  for nome in POSTGRES_APP_USER POSTGRES_APP_PASSWORD TOTP_CHAVE; do
    grep -q "^$nome=" .env && continue
    case "$nome" in
      POSTGRES_APP_USER) valor=plataforma_cursos_app ;;
      POSTGRES_APP_PASSWORD) valor=$(openssl rand -hex 24) ;;
      TOTP_CHAVE) valor=$(openssl rand -base64 32) ;;
    esac
    echo "$nome=$valor" >> .env
    log "$nome acrescentado ao .env"
  done
}

ANTERIOR=$(tag_atual)
log "início (versão atual: ${ANTERIOR:-nenhuma})"
completar_env

# 1. Arquivos da nova versão: só os nomes esperados são extraídos
NOVA="versoes/$TAG"
rm -rf "$NOVA"
mkdir -p "$NOVA"
tar -xz -C "$NOVA" --no-same-owner -- "${ARQUIVOS[@]}" || falhar "pacote de arquivos inválido ou incompleto"
[[ -n "$ANTERIOR" ]] && guardar_arquivos "$ANTERIOR"

# 2. Backup do banco antes de qualquer migration
mkdir -p backups
if compose ps --status running --services 2>/dev/null | grep -qx postgres; then
  BACKUP="backups/$(date -u +%Y%m%dT%H%M%SZ)-${ANTERIOR:0:12}.sql.gz"
  # As variáveis são do container do PostgreSQL (aspas simples de propósito)
  # shellcheck disable=SC2016
  compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$BACKUP"
  log "backup do banco em $BACKUP"
  # Nomes criados por este script (data e tag): ls é seguro aqui
  # shellcheck disable=SC2012
  ls -1t backups/*.sql.gz | tail -n +$((BACKUPS_GUARDADOS + 1)) | xargs -r rm -f
else
  log "banco ainda não está no ar: sem backup (primeira subida)"
fi

# 3. Nova versão
aplicar_versao "$TAG"
if subir; then
  log "versão no ar"
  docker image prune -f > /dev/null
  # shellcheck disable=SC2012
  ls -1dt versoes/*/ | tail -n +$((VERSOES_GUARDADAS + 1)) | xargs -r rm -rf
  exit 0
fi

# 4. Falhou: volta para a anterior
log "FALHOU: algum container não ficou saudável"
compose ps -a | tee -a deploy.log
if [[ -z "$ANTERIOR" ]]; then
  falhar "não há versão anterior para voltar"
fi
log "voltando para ${ANTERIOR:0:12}"
aplicar_versao "$ANTERIOR"
if subir; then
  log "rollback concluído: versão anterior no ar"
else
  log "ROLLBACK FALHOU: verifique a VPS"
fi
exit 1
`````

`infra/scripts/gerar-env-servidor.sh`:

`````bash
#!/usr/bin/env bash
# Gera o .env de um ambiente da VPS com segredos aleatórios. Hosts seguem os subdomínios de
# celke.uno (homolog-* na homologação). SMTP e remetente ficam para preencher com os dados da Iagente.
# Não sobrescreve um arquivo existente.
#
#   gerar-env-servidor.sh producao /opt/plataforma-cursos/producao/.env
#   gerar-env-servidor.sh homolog  /opt/plataforma-cursos/homolog/.env
set -euo pipefail

AMBIENTE=${1:?uso: gerar-env-servidor.sh <producao|homolog> <arquivo>}
DESTINO=${2:?uso: gerar-env-servidor.sh <producao|homolog> <arquivo>}

case "$AMBIENTE" in
  producao) PREFIXO="" ;;
  homolog) PREFIXO="homolog-" ;;
  *) echo "Ambiente inválido: $AMBIENTE (use producao ou homolog)" >&2; exit 1 ;;
esac

if [[ -e "$DESTINO" ]]; then
  echo "$DESTINO já existe; nada foi alterado." >&2
  exit 1
fi

hex() { openssl rand -hex "$1"; }

ALUNO="https://${PREFIXO}aluno.celke.uno"
ADM="https://${PREFIXO}adm.celke.uno"
MEDIA="https://${PREFIXO}media.celke.uno"

umask 077
cat > "$DESTINO" <<ENV
# Ambiente: $AMBIENTE
AMBIENTE=$AMBIENTE
# Tag (SHA do commit) das imagens; o deploy grava a tag de cada versão
TAG=

POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=$(hex 24)
# Usuário da API e do worker: só lê e grava dados (o dono do schema fica para as migrations)
POSTGRES_APP_USER=plataforma_cursos_app
POSTGRES_APP_PASSWORD=$(hex 24)

JWT_SEGREDO=$(openssl rand -base64 48)
# Segundo fator dos administradores (trocar exige cadastrar o aplicativo de novo: redefinir-2fa)
TOTP_CHAVE=$(openssl rand -base64 32)
AUTH_ORIGENS=$ALUNO,$ADM
FRONT_ALUNO_URL=$ALUNO

# Chave dos segredos dos meios de pagamento (trocar exige informar os segredos de novo no painel)
PAGAMENTOS_CHAVE=$(openssl rand -base64 32)
# Reconciliação diária pela API do meio de pagamento (precisa das credenciais da API no painel)
PAGAMENTOS_RECONCILIACAO=false

GARAGE_RPC_SECRET=$(hex 32)
GARAGE_ADMIN_TOKEN=$(hex 32)
S3_BUCKET=midia
S3_ACCESS_KEY=GK$(hex 12)
S3_SECRET_KEY=$(hex 32)
S3_ENDPOINT_PUBLICO=$MEDIA
S3_CORS_ORIGENS=$ALUNO,$ADM

# SMTP da Iagente (dados do painel)
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=true
SMTP_STARTTLS=true
EMAIL_REMETENTE=nao-responda@celke.uno

# Alertas do worker (jobs com erro, disco quase cheio); em branco, desligados
ALERTA_EMAIL=
ENV

chmod 600 "$DESTINO"
echo "$DESTINO criado. Preencha SMTP_HOST, SMTP_USERNAME e SMTP_PASSWORD."
`````

`infra/scripts/gerar-env.sh`:

`````bash
#!/usr/bin/env bash
# Cria infra/.env a partir do infra/.env.example, preenchendo os segredos com valores aleatórios.
# Não sobrescreve um infra/.env existente.
set -euo pipefail

INFRA="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="$INFRA/.env"

if [[ -e "$DESTINO" ]]; then
  echo "infra/.env já existe; nada foi alterado." >&2
  exit 1
fi

hex() { openssl rand -hex "$1"; }

sed -E \
  -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(hex 24)|" \
  -e "s|^JWT_SEGREDO=.*|JWT_SEGREDO=$(openssl rand -base64 48)|" \
  -e "s|^TOTP_CHAVE=.*|TOTP_CHAVE=$(openssl rand -base64 32)|" \
  -e "s|^PAGAMENTOS_CHAVE=.*|PAGAMENTOS_CHAVE=$(openssl rand -base64 32)|" \
  -e "s|^GARAGE_RPC_SECRET=.*|GARAGE_RPC_SECRET=$(hex 32)|" \
  -e "s|^GARAGE_ADMIN_TOKEN=.*|GARAGE_ADMIN_TOKEN=$(hex 32)|" \
  -e "s|^S3_ACCESS_KEY=.*|S3_ACCESS_KEY=GK$(hex 12)|" \
  -e "s|^S3_SECRET_KEY=.*|S3_SECRET_KEY=$(hex 32)|" \
  "$INFRA/.env.example" > "$DESTINO"

chmod 600 "$DESTINO"
echo "infra/.env criado com segredos aleatórios."
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
  usuarios: '/usuarios',
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
    itens: [
      { rotulo: 'Usuários', icone: 'users', rota: CAMINHOS.usuarios },
      { rotulo: 'Auditoria', icone: 'shield-check', rota: CAMINHOS.auditoria },
    ],
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

`web/adm/src/paginas/EntrarPagina.tsx`:

`````tsx
import { TelaLogin } from '@celke/kole-react';
import { useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { DesafioSegundoFator } from '@compartilhado/api/tipos.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { SegundoFator } from '@compartilhado/sessao/SegundoFator.tsx';
import { MARCA } from '../marca.ts';

/**
 * Login do ADM: e-mail e senha, depois o segundo fator (obrigatório para administradores). Quem entra
 * sem o papel de administrador sai na hora, com o aviso.
 */
export function EntrarPagina() {
  const { estado, entrar, sair } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));
  const [desafio, setDesafio] = useState<DesafioSegundoFator>();

  if (estado.status === 'autenticado' && estado.usuario.papeis.includes('ADMIN')) {
    return <Navigate to={destino} replace />;
  }

  if (desafio) {
    return (
      <SegundoFator
        marca="Administração"
        desafio={desafio}
        aoConcluir={() => navigate(destino, { replace: true })}
        aoVoltar={() => setDesafio(undefined)}
      />
    );
  }

  return (
    <TelaLogin
      marca={MARCA}
      modelo="formulario"
      identificador="email"
      titulo="Administração"
      descricao="Acesso restrito à equipe da plataforma."
      aoEntrar={async ({ usuario, senha }) => {
        try {
          const resultado = await entrar(usuario.trim(), senha);
          if (resultado.tipo === 'desafio') {
            setDesafio(resultado.desafio);
            return;
          }
          if (!resultado.usuario.papeis.includes('ADMIN')) {
            await sair();
            return { erro: 'Acesso restrito a administradores.' };
          }
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}
`````

`web/adm/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { AuditoriaPagina } from './paginas/auditoria/AuditoriaPagina.tsx';
import { UsuariosPagina } from './paginas/usuarios/UsuariosPagina.tsx';
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
      { path: 'usuarios', element: <UsuariosPagina /> },
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

/** Conta na lista do ADM (`UsuarioAdm`). */
export interface UsuarioAdm {
  id: number;
  nome: string;
  email: string;
  papeis: ('ALUNO' | 'ADMIN')[];
  ativo: boolean;
  emailConfirmado: boolean;
  segundoFatorAtivo: boolean;
  sessoesEncerradasEm: string | null;
  criadoEm: string;
}

export const PAPEIS: Record<'ALUNO' | 'ADMIN', string> = {
  ALUNO: 'Aluno',
  ADMIN: 'Administrador',
};
`````

`web/aluno/src/pagamento.test.tsx`:

`````tsx
import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  INTERVALO_MS,
  LIMITE_MS,
  resultado,
  RetornoPagamentoPagina,
} from './paginas/pagamento/RetornoPagamentoPagina.tsx';
import { checkoutSeguro } from './paginas/pagamento/ComprarPagina.tsx';
import type { StatusPedido } from './tipos.ts';

describe('resultado do pedido na página de retorno', () => {
  it.each<[StatusPedido, string]>([
    ['APROVADO', 'confirmado'],
    ['CONCLUIDO', 'confirmado'],
    ['CRIADO', 'aguardando'],
    ['AGUARDANDO_PAGAMENTO', 'aguardando'],
    ['CANCELADO', 'recusado'],
    ['EXPIRADO', 'recusado'],
    ['REEMBOLSADO', 'recusado'],
    ['CHARGEBACK', 'recusado'],
  ])('%s → %s', (status, esperado) => {
    expect(resultado(status)).toBe(esperado);
  });
});

function json(corpo: unknown) {
  return new Response(JSON.stringify(corpo), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function pedido(status: StatusPedido) {
  return {
    codigo: 'abc',
    status,
    turmaId: null,
    turmaNome: null,
    cursoTitulo: null,
    cursoSlug: null,
  };
}

function abrir(caminho: string) {
  const roteador = createMemoryRouter(
    [{ path: '/pagamento/retorno', element: <RetornoPagamentoPagina /> }],
    { initialEntries: [caminho] },
  );
  render(<RouterProvider router={roteador} />);
}

describe('página de retorno', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('consulta a cada 3 s até o pagamento ser confirmado', async () => {
    fetchMock
      .mockResolvedValueOnce(json(pedido('CRIADO')))
      .mockResolvedValueOnce(json(pedido('APROVADO')));

    abrir('/pagamento/retorno?pedido=abc');

    expect(await screen.findByText('Aguardando pagamento')).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_MS));
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
  });

  it('para de consultar depois de 2 minutos', async () => {
    fetchMock.mockImplementation(async () => json(pedido('AGUARDANDO_PAGAMENTO')));

    abrir('/pagamento/retorno?pedido=abc');
    await screen.findByText('Aguardando pagamento');
    await act(() => vi.advanceTimersByTimeAsync(LIMITE_MS + INTERVALO_MS * 2));
    const chamadas = fetchMock.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_MS * 5));

    expect(fetchMock.mock.calls.length).toBe(chamadas);
    expect(chamadas).toBeLessThanOrEqual(LIMITE_MS / INTERVALO_MS + 2);
  });

  it('pagamento recusado e link sem pedido', async () => {
    fetchMock.mockResolvedValueOnce(json(pedido('CANCELADO')));
    abrir('/pagamento/retorno?pedido=abc');
    expect(await screen.findByText('Pagamento não aprovado')).toBeInTheDocument();
  });

  it('link sem o código do pedido', () => {
    abrir('/pagamento/retorno');
    expect(screen.getByText('O link não tem o código do pedido.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('código do pedido na página de retorno', () => {
  const fetchMock = vi.fn<typeof fetch>();

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('aceita o xcod como a Hotmart repassa', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockResolvedValueOnce(json(pedido('APROVADO')));
    abrir('/pagamento/retorno?xcod=abc');
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
  });
});

describe('checkoutSeguro', () => {
  it('só aceita HTTPS', () => {
    expect(checkoutSeguro('https://pay.hotmart.com/X123?off=abc')).toBe(true);
    expect(checkoutSeguro('http://pay.hotmart.com/X123')).toBe(false);
    expect(checkoutSeguro('javascript:alert(1)')).toBe(false);
    expect(checkoutSeguro('nao-e-url')).toBe(false);
    expect(checkoutSeguro(null)).toBe(false);
  });
});
`````

`web/aluno/src/paginas/CadastroPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoEmail, CampoSenha, CampoTexto } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import {
  erroConfirmacao,
  erroEmail,
  erroObrigatorio,
  erroSenhaNova,
  SENHA_MINIMA_ALUNO,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Erros = Partial<Record<'nome' | 'email' | 'senha' | 'confirmacao', string>>;

/** Cadastro do aluno: a conta só entra depois de confirmar o e-mail (link enviado pela API). */
export function CadastroPagina() {
  const [parametros] = useSearchParams();
  const voltar = parametros.get('voltar');
  const entrar = voltar
    ? `${CAMINHOS.entrar}?voltar=${encodeURIComponent(voltar)}`
    : CAMINHOS.entrar;
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [mensagem, setMensagem] = useState<string>();

  const enviar = async () => {
    const novos: Erros = {
      nome: erroObrigatorio(nome, 'Informe o seu nome.', 120),
      email: erroEmail(email),
      senha: erroSenhaNova(senha),
      confirmacao: erroConfirmacao(senha, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      const resposta = await api.post<Mensagem>('/api/auth/cadastro', {
        nome: nome.trim(),
        email: email.trim(),
        senha,
      });
      setMensagem(resposta.mensagem);
    } catch (erro) {
      if (erro instanceof ErroApi && erro.erros.length) {
        setErros({
          nome: erro.erroDoCampo('nome'),
          email: erro.erroDoCampo('email'),
          senha: erro.erroDoCampo('senha'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  if (mensagem) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confira o seu e-mail">
        <Alerta severidade="ok" titulo={mensagem}>
          Enviamos um link para <strong>{email.trim()}</strong>. Abra o e-mail e clique no link para
          ativar a conta.
        </Alerta>
        <Link className="pc-link" to={entrar}>
          Depois de confirmar, entre aqui
        </Link>
      </LayoutConta>
    );
  }

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Criar conta"
      descricao="Cadastre-se para acessar seus cursos."
      aoEnviar={enviar}
      rodape={
        <span>
          Já tem conta? <Link to={entrar}>Entrar</Link>
        </span>
      }
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoTexto
        rotulo="Nome"
        valor={nome}
        aoMudar={setNome}
        erro={erros.nome}
        obrigatorio
        autoComplete="name"
      />
      <CampoEmail
        rotulo="E-mail"
        valor={email}
        aoMudar={setEmail}
        erro={erros.email}
        obrigatorio
        autoComplete="email"
      />
      <CampoSenha
        rotulo="Senha"
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas mais usadas."
        forca
        politica={{ minimo: SENHA_MINIMA_ALUNO }}
        autoComplete="new-password"
      />
      <CampoSenha
        rotulo="Repita a senha"
        valor={confirmacao}
        aoMudar={setConfirmacao}
        erro={erros.confirmacao}
        obrigatorio
        autoComplete="new-password"
      />
      <Botao type="submit" rotulo="Criar conta" icone="user-plus" carregando={enviando} bloco />
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/ConfirmarEmailPagina.tsx`:

`````tsx
import { Alerta, Botao, Carregando, CampoEmail } from '@celke/kole-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { useTokenDaUrl } from '@compartilhado/ui/useTokenDaUrl.ts';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Estado = { tipo: 'confirmando' } | { tipo: 'confirmado' } | { tipo: 'erro'; mensagem: string };

/** Destino do link enviado no cadastro: confirma o e-mail e, se o link venceu, permite pedir outro. */
export function ConfirmarEmailPagina() {
  const token = useTokenDaUrl();
  const [estado, setEstado] = useState<Estado>(
    token
      ? { tipo: 'confirmando' }
      : { tipo: 'erro', mensagem: 'Link inválido ou expirado. Peça um novo.' },
  );
  // O link é de uso único: confirmar só uma vez, mesmo com o StrictMode montando duas vezes
  const enviado = useRef(false);

  useEffect(() => {
    if (!token || enviado.current) return;
    enviado.current = true;
    api
      .post('/api/auth/confirmar-email', { token })
      .then(() => setEstado({ tipo: 'confirmado' }))
      .catch((erro: unknown) => setEstado({ tipo: 'erro', mensagem: mensagemDeErro(erro) }));
  }, [token]);

  if (estado.tipo === 'confirmando') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confirmando o seu e-mail">
        <Carregando mensagem="Confirmando..." />
      </LayoutConta>
    );
  }
  if (estado.tipo === 'confirmado') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="E-mail confirmado">
        <Alerta severidade="ok" mensagem="Sua conta está ativa. Agora é só entrar." />
        <Link className="pc-link" to={CAMINHOS.entrar}>
          Entrar
        </Link>
      </LayoutConta>
    );
  }
  return <ReenviarConfirmacao mensagem={estado.mensagem} />;
}

function ReenviarConfirmacao({ mensagem }: { mensagem: string }) {
  const [email, setEmail] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [resposta, setResposta] = useState<string>();

  const enviar = async () => {
    const problema = erroEmail(email);
    setErro(problema);
    if (problema) return;
    setEnviando(true);
    try {
      setResposta(
        (await api.post<Mensagem>('/api/auth/reenviar-confirmacao', { email: email.trim() }))
          .mensagem,
      );
    } catch (falha) {
      setErro(mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Não foi possível confirmar"
      aoEnviar={resposta ? undefined : enviar}
      rodape={<Link to={CAMINHOS.entrar}>Voltar para a entrada</Link>}
    >
      <Alerta severidade="err" mensagem={mensagem} />
      {resposta ? (
        <Alerta severidade="ok" mensagem={resposta} />
      ) : (
        <>
          <CampoEmail rotulo="E-mail" valor={email} aoMudar={setEmail} erro={erro} obrigatorio />
          <Botao type="submit" rotulo="Enviar novo link" icone="mail" carregando={enviando} bloco />
        </>
      )}
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/EntrarPagina.tsx`:

`````tsx
import { TelaLogin } from '@celke/kole-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { DesafioSegundoFator } from '@compartilhado/api/tipos.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { SegundoFator } from '@compartilhado/sessao/SegundoFator.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));
  // Quem veio de "Comprar" cria a conta e volta para a compra depois de entrar
  const comVoltar = parametros.get('voltar')
    ? `?voltar=${encodeURIComponent(parametros.get('voltar')!)}`
    : '';

  // Administrador que entra pela área do aluno também passa pelo segundo fator
  const [desafio, setDesafio] = useState<DesafioSegundoFator>();

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  if (desafio) {
    return (
      <SegundoFator
        marca="Plataforma de Cursos"
        desafio={desafio}
        aoConcluir={() => navigate(destino, { replace: true })}
        aoVoltar={() => setDesafio(undefined)}
      />
    );
  }

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao={
        parametros.get('senha') === 'alterada'
          ? 'Senha alterada. Entre com a nova senha.'
          : 'Acesse seus cursos, aulas e certificados.'
      }
      apresentacao={{
        chamada: 'Plataforma de Cursos',
        titulo: 'Aprenda no seu ritmo',
        descricao:
          'Aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado ao concluir.',
        destaques: [
          { icone: 'circle-play', texto: 'Aulas em vídeo de qualquer lugar' },
          { icone: 'message-square', texto: 'Dúvidas respondidas no fórum' },
          { icone: 'award', texto: 'Certificado com código de validação' },
        ],
      }}
      linkSenha={<Link to={CAMINHOS.recuperarSenha}>Esqueci minha senha</Link>}
      acoes={
        <p>
          Ainda não tem conta? <Link to={CAMINHOS.cadastro + comVoltar}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          const resultado = await entrar(usuario.trim(), senha);
          if (resultado.tipo === 'desafio') {
            setDesafio(resultado.desafio);
            return;
          }
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}
`````

`web/aluno/src/paginas/RedefinirSenhaPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoSenha } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { useTokenDaUrl } from '@compartilhado/ui/useTokenDaUrl.ts';
import {
  erroConfirmacao,
  erroSenhaNova,
  SENHA_MINIMA_ALUNO,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

/**
 * Destino do link de recuperação: cria a nova senha (e encerra as sessões abertas). Vindo do e-mail da
 * matrícula paga (`origem=pagamento`), é a primeira senha da conta criada pela compra.
 */
export function RedefinirSenhaPagina() {
  const [parametros] = useSearchParams();
  const token = useTokenDaUrl();
  const primeiraSenha = parametros.get('origem') === 'pagamento';
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<{ senha?: string; confirmacao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [pronto, setPronto] = useState(false);

  if (!token) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Link inválido">
        <Alerta severidade="err" mensagem="Link inválido ou expirado. Peça um novo." />
        <Link className="pc-link" to={CAMINHOS.recuperarSenha}>
          Pedir um novo link
        </Link>
      </LayoutConta>
    );
  }

  if (pronto) {
    return (
      <LayoutConta
        marca="Plataforma de Cursos"
        titulo={primeiraSenha ? 'Senha criada' : 'Senha alterada'}
      >
        <Alerta
          severidade="ok"
          mensagem={
            primeiraSenha
              ? 'Sua senha foi criada. Entre para acessar o seu curso.'
              : 'Sua nova senha já vale. Por segurança, você saiu de todos os dispositivos.'
          }
        />
        <Link className="pc-link" to={CAMINHOS.entrar}>
          Entrar
        </Link>
      </LayoutConta>
    );
  }

  const enviar = async () => {
    const novos = { senha: erroSenhaNova(senha), confirmacao: erroConfirmacao(senha, confirmacao) };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      await api.post('/api/auth/redefinir-senha', { token, senha });
      setPronto(true);
    } catch (erro) {
      const doCampo = erro instanceof ErroApi ? erro.erroDoCampo('senha') : undefined;
      if (doCampo) setErros({ senha: doCampo });
      else setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo={primeiraSenha ? 'Crie sua senha' : 'Criar nova senha'}
      descricao={
        primeiraSenha ? 'Sua matrícula está confirmada. Crie a senha para entrar.' : undefined
      }
      aoEnviar={enviar}
      rodape={<Link to={CAMINHOS.recuperarSenha}>Pedir um novo link</Link>}
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoSenha
        rotulo={primeiraSenha ? 'Senha' : 'Nova senha'}
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas mais usadas."
        forca
        politica={{ minimo: SENHA_MINIMA_ALUNO }}
        autoComplete="new-password"
      />
      <CampoSenha
        rotulo={primeiraSenha ? 'Repita a senha' : 'Repita a nova senha'}
        valor={confirmacao}
        aoMudar={setConfirmacao}
        erro={erros.confirmacao}
        obrigatorio
        autoComplete="new-password"
      />
      <Botao
        type="submit"
        rotulo={primeiraSenha ? 'Criar senha' : 'Salvar nova senha'}
        icone="save"
        carregando={enviando}
        bloco
      />
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/pagamento/ComprarPagina.tsx`:

`````tsx
import { Alerta, Botao, Carregando } from '@celke/kole-react';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { moeda } from '@compartilhado/ui/formato.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../../caminhos.ts';
import type { Checkout, ResumoOferta } from '../../tipos.ts';

/** Para onde levar o aluno no checkout (redirecionamento separado para os testes). */
export const navegador = {
  ir: (url: string) => window.location.assign(url),
};

/** Só sai da plataforma para um checkout em HTTPS (o backend já limita os domínios do meio). */
export function checkoutSeguro(url: string | null): url is string {
  if (!url) return false;
  try {
    return new URL(url).protocol === 'https:';
  } catch {
    return false;
  }
}

/**
 * Destino do botão "Comprar" do site (exige login: sem sessão, volta para cá depois de entrar ou
 * criar a conta). Mostra o que vai ser comprado e só abre o pedido quando o aluno confirma: abrir a
 * página (um link enviado por alguém, um pré-carregamento do navegador) não cria pedido.
 */
export function ComprarPagina() {
  const { ofertaId } = useParams();
  const [oferta, setOferta] = useState<ResumoOferta>();
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [embutido, setEmbutido] = useState<Checkout | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .get<ResumoOferta>(`/api/pagamentos/ofertas/${Number(ofertaId)}`)
      .then((resposta) => ativo && setOferta(resposta))
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha)));
    return () => {
      ativo = false;
    };
  }, [ofertaId]);

  const comprar = async () => {
    setEnviando(true);
    setErro(undefined);
    try {
      const checkout = await api.post<Checkout>('/api/pagamentos/checkout', {
        ofertaId: Number(ofertaId),
      });
      if (checkout.tipo === 'REDIRECIONAMENTO') {
        if (!checkoutSeguro(checkout.url)) {
          setErro('O endereço de pagamento recebido não é seguro. Avise o suporte.');
          setEnviando(false);
          return;
        }
        navegador.ir(checkout.url);
        return;
      }
      setEmbutido(checkout);
      setEnviando(false);
    } catch (falha) {
      setErro(mensagemDeErro(falha));
      setEnviando(false);
    }
  };

  if (embutido) {
    // Meio de pagamento com formulário próprio: os dados vão para o componente dele
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento">
        <Alerta
          severidade="info"
          mensagem="Este meio de pagamento usa um formulário próprio, ainda não disponível nesta versão."
        />
      </LayoutConta>
    );
  }
  if (!oferta) {
    return erro ? (
      <LayoutConta marca="Plataforma de Cursos" titulo="Não foi possível iniciar a compra">
        <Alerta severidade="err" mensagem={erro} />
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    ) : (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confirmar a compra">
        <Carregando mensagem="Carregando a oferta..." />
      </LayoutConta>
    );
  }
  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Confirmar a compra"
      aoEnviar={() => void comprar()}
      rodape={<Link to={CAMINHOS.inicio}>Cancelar</Link>}
    >
      {erro && <Alerta severidade="err" mensagem={erro} />}
      <dl className="pc-resumo-compra">
        <dt>Curso</dt>
        <dd>{oferta.curso}</dd>
        <dt>Turma</dt>
        <dd>{oferta.turma}</dd>
        <dt>Oferta</dt>
        <dd>{oferta.titulo}</dd>
        <dt>Valor</dt>
        <dd>{moeda(oferta.precoCentavos, oferta.moeda)}</dd>
      </dl>
      <p className="pc-conta-descricao">
        O pagamento é feito na página do meio de pagamento; a matrícula sai assim que ele for
        confirmado.
      </p>
      <Botao
        type="submit"
        rotulo="Ir para o pagamento"
        icone="credit-card"
        carregando={enviando}
        bloco
      />
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/perfil/PerfilPagina.tsx`:

`````tsx
import {
  Alerta,
  Avatar,
  Botao,
  CabecalhoPagina,
  Card,
  CampoEmail,
  CampoSenha,
  CampoTexto,
  Carregando,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  erroConfirmacao,
  erroObrigatorio,
  erroSenhaNova,
  SENHA_MINIMA_ALUNO,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { EnvioSimples, Perfil } from '../../tipos.ts';

const FOTO_MAXIMA = 5 * 1024 * 1024;

/** Dados do aluno: nome, foto e troca de senha. */
export function PerfilPagina() {
  const perfil = useCarregar(() => api.get<Perfil>('/api/perfil'), []);
  if (perfil.erro && !perfil.dados) return <Alerta severidade="err" mensagem={perfil.erro} />;
  if (!perfil.dados) return <Carregando mensagem="Carregando o perfil..." />;
  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meu perfil" icone="user" />
      <Dados perfil={perfil.dados} aoMudar={perfil.definir} />
      <Foto perfil={perfil.dados} aoMudar={perfil.definir} />
      <TrocarSenha />
    </div>
  );
}

function Dados({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const [nome, setNome] = useState(perfil.nome);
  const [erro, setErro] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const problema = erroObrigatorio(nome, 'Informe o seu nome.', 120);
    setErro(problema);
    if (problema) return;
    setSalvando(true);
    try {
      aoMudar(await api.put<Perfil>('/api/perfil', { nome: nome.trim() }));
      toast.ok('Nome atualizado');
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('nome')) || mensagemDeErro(falha));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Dados">
      <form
        noValidate
        className="pc-grade-detalhes"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        <CampoTexto
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          erro={erro}
          obrigatorio
          maxLength={120}
          autoComplete="name"
        />
        <CampoEmail
          rotulo="E-mail"
          valor={perfil.email}
          disabled
          dica="O e-mail da conta não muda."
        />
        <div>
          <Botao type="submit" rotulo="Salvar nome" icone="save" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}

function Foto({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();

  const enviar = async (arquivo: File) => {
    if (!['image/jpeg', 'image/png'].includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie uma foto JPG ou PNG.');
      return;
    }
    if (arquivo.size > FOTO_MAXIMA) {
      toast.err('Foto grande demais', 'A foto pode ter até 5 MB.');
      return;
    }
    const novo = await executar(async () => {
      const envio = await api.post<EnvioSimples>('/api/perfil/foto/envio', {
        contentType: arquivo.type,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.put<Perfil>('/api/perfil/foto', { chave: envio.chave });
    }, 'Foto atualizada');
    if (novo) aoMudar(novo);
  };

  return (
    <Card titulo="Foto">
      <div className="pc-linha">
        <Avatar nome={perfil.nome} foto={perfil.fotoUrl ?? undefined} tamanho="4rem" />
        <EscolherArquivo
          rotulo={perfil.fotoUrl ? 'Trocar foto' : 'Enviar foto'}
          icone="image"
          aparencia="contorno"
          aceitar="image/jpeg,image/png"
          carregando={executando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
        {perfil.fotoUrl && (
          <Botao
            rotulo="Remover foto"
            aparencia="texto"
            severidade="err"
            onClick={async () => {
              if (!(await confirmar.remover({ mensagem: 'A sua foto será removida.' }))) return;
              const ok = await executar(
                () => api.delete('/api/perfil/foto').then(() => true),
                'Foto removida',
              );
              if (ok) aoMudar({ ...perfil, fotoUrl: null });
            }}
          />
        )}
      </div>
      <p className="pc-dica">JPG ou PNG até 5 MB. A foto é reduzida para 400 × 400.</p>
    </Card>
  );
}

type ErrosSenha = Partial<Record<'atual' | 'nova' | 'confirmacao', string>>;

function TrocarSenha() {
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<ErrosSenha>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: ErrosSenha = {
      atual: atual ? undefined : 'Informe a senha atual.',
      nova: erroSenhaNova(nova),
      confirmacao: erroConfirmacao(nova, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setSalvando(true);
    try {
      await api.put('/api/perfil/senha', { senhaAtual: atual, novaSenha: nova });
      // A API encerrou todas as sessões (o cookie de refresh já foi limpo): recarrega na entrada,
      // o que também descarta o token desta aba
      window.location.replace(`${CAMINHOS.entrar}?senha=alterada`);
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ atual: erro.erroDoCampo('senhaAtual'), nova: erro.erroDoCampo('novaSenha') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Trocar senha">
      <form
        noValidate
        className="pc-pilha pc-formulario-estreito"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoSenha
          rotulo="Senha atual"
          valor={atual}
          aoMudar={setAtual}
          erro={erros.atual}
          obrigatorio
          autoComplete="current-password"
        />
        <CampoSenha
          rotulo="Nova senha"
          valor={nova}
          aoMudar={setNova}
          erro={erros.nova}
          obrigatorio
          dica="Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas mais usadas."
          forca
          politica={{ minimo: SENHA_MINIMA_ALUNO }}
          autoComplete="new-password"
        />
        <CampoSenha
          rotulo="Repita a nova senha"
          valor={confirmacao}
          aoMudar={setConfirmacao}
          erro={erros.confirmacao}
          obrigatorio
          autoComplete="new-password"
        />
        <Alerta
          severidade="info"
          mensagem="Ao trocar a senha, você sai de todos os dispositivos e entra de novo."
        />
        <div>
          <Botao type="submit" rotulo="Trocar senha" icone="key" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}
`````

`web/aluno/src/tipos.ts`:

`````ts
/** Respostas da API da área do aluno (/api/aluno, /api/perfil), espelhando os records do backend. */

export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FiltroMeusCursos = 'ATIVOS' | 'ARQUIVADOS' | 'EXPIRADOS';

export interface MeuCurso {
  matriculaId: number;
  turmaId: number;
  cursoTitulo: string;
  cursoSlug: string;
  turmaNome: string;
  capaUrl: string | null;
  inicio: string;
  fim: string | null;
  situacao: SituacaoMatricula;
  arquivada: boolean;
}

export interface AulaResumo {
  id: number;
  titulo: string;
  gratuita: boolean;
}

export interface ModuloAluno {
  id: number;
  titulo: string;
  aulas: AulaResumo[];
}

export interface TurmaAluno {
  matriculaId: number;
  turmaId: number;
  turmaNome: string;
  cursoTitulo: string;
  cursoSlug: string;
  inicio: string;
  fim: string | null;
  modulos: ModuloAluno[];
}

export interface AulaAluno {
  id: number;
  turmaId: number;
  titulo: string;
  descricao: string | null;
  gratuita: boolean;
}

export interface Progresso {
  aulaId: number;
  posicaoSegundos: number;
  vista: boolean;
  vistaEm: string | null;
  revisar: boolean;
}

export interface ProgressoTurma {
  turmaId: number;
  totalAulas: number;
  aulasVistas: number;
  percentual: number;
  aulas: Progresso[];
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

export interface Download {
  url: string;
  expiraEm: string;
}

export interface Certificado {
  id: number;
  turmaId: number;
  codigo: string;
  nomeCompleto: string;
  cursoTitulo: string;
  turmaNome: string;
  cargaHorariaHoras: number | null;
  emitidoEm: string;
}

export interface SituacaoCertificado {
  percentualVisto: number;
  percentualExigido: number;
  concluiu: boolean;
  certificado: Certificado | null;
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
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Perfil {
  id: number;
  nome: string;
  email: string;
  papeis: string[];
  fotoUrl: string | null;
}

export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
}

/** Aula com o módulo e a posição dela na turma, para anterior/próxima. */
export interface AulaNaTurma {
  aula: AulaResumo;
  modulo: ModuloAluno;
  anterior: AulaResumo | null;
  proxima: AulaResumo | null;
}

export function localizarAula(turma: TurmaAluno, aulaId: number): AulaNaTurma | null {
  const todas = turma.modulos.flatMap((modulo) => modulo.aulas.map((aula) => ({ aula, modulo })));
  const indice = todas.findIndex((item) => item.aula.id === aulaId);
  if (indice < 0) return null;
  const { aula, modulo } = todas[indice]!;
  return {
    aula,
    modulo,
    anterior: todas[indice - 1]?.aula ?? null,
    proxima: todas[indice + 1]?.aula ?? null,
  };
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

/** Resposta do checkout: ir para a página do meio de pagamento ou montar o formulário embutido. */
/** Oferta à venda mostrada antes de abrir o pedido (`ResumoOferta`). */
export interface ResumoOferta {
  id: number;
  titulo: string;
  precoCentavos: number;
  moeda: string;
  curso: string;
  turma: string;
  meioPagamento: string;
}

export interface Checkout {
  pedido: string;
  tipo: 'REDIRECIONAMENTO' | 'EMBUTIDO';
  url: string | null;
  dados: Record<string, unknown> | null;
}

/** Situação do pedido na página de retorno; curso e turma só para o dono logado. */
export interface SituacaoPedido {
  codigo: string;
  status: StatusPedido;
  turmaId: number | null;
  turmaNome: string | null;
  cursoTitulo: string | null;
  cursoSlug: string | null;
}
`````

`web/compartilhado/src/api/tipos.ts`:

`````ts
export type Papel = 'ALUNO' | 'ADMIN';

/** Dados públicos do usuário da sessão (`UsuarioResponse` do backend). */
export interface Usuario {
  id: number;
  nome: string;
  email: string;
  papeis: Papel[];
}

/** Resposta do login e do refresh (`SessaoResponse`). */
export interface Sessao {
  accessToken: string;
  tipo: 'Bearer';
  expiraEm: string;
  usuario: Usuario;
  /** Só na ativação do segundo fator: os códigos de recuperação, mostrados uma única vez. */
  codigosRecuperacao?: string[];
}

/**
 * Resposta do login de um administrador com a senha certa: falta o segundo fator (`CODIGO`) ou o
 * cadastro do aplicativo autenticador (`CADASTRO`). Ainda não há sessão.
 */
export interface DesafioSegundoFator {
  segundoFator: 'CODIGO' | 'CADASTRO';
  desafio: string;
  expiraEm: string;
}

/** Segredo do aplicativo autenticador e o endereço `otpauth://` do QR code. */
export interface CadastroSegundoFator {
  segredo: string;
  uri: string;
}

/** Página de listagem (`Pagina<T>` do backend). */
export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}

/** Corpo das respostas de mensagem simples (`MensagemResponse`). */
export interface Mensagem {
  mensagem: string;
}
`````

`web/compartilhado/src/sessao/ProvedorSessao.tsx`:

`````tsx
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api } from '../api/cliente.ts';
import type { DesafioSegundoFator, Sessao, Usuario } from '../api/tipos.ts';

/** Primeira etapa do login: a sessão aberta ou, para o administrador, o desafio do segundo fator. */
export type ResultadoEntrada =
  { tipo: 'sessao'; usuario: Usuario } | { tipo: 'desafio'; desafio: DesafioSegundoFator };

type Estado =
  { status: 'carregando' } | { status: 'anonimo' } | { status: 'autenticado'; usuario: Usuario };

interface ContextoSessao {
  estado: Estado;
  /**
   * Entra com e-mail e senha; erro da API (credenciais, e-mail não confirmado, limite) é lançado. O
   * administrador recebe o desafio do segundo fator, concluído pelo componente `SegundoFator`.
   */
  entrar: (email: string, senha: string) => Promise<ResultadoEntrada>;
  sair: () => Promise<void>;
}

const Contexto = createContext<ContextoSessao | null>(null);

/**
 * Sessão do app. Ao abrir, tenta retomar a sessão pelo cookie de refresh (sem pedir a senha de novo);
 * depois acompanha o cliente da API: renovação, login e expiração mudam o estado aqui.
 */
export function ProvedorSessao({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>({ status: 'carregando' });

  useEffect(() => {
    const parar = api.aoMudarSessao((sessao) =>
      setEstado(
        sessao ? { status: 'autenticado', usuario: sessao.usuario } : { status: 'anonimo' },
      ),
    );
    void api.renovar();
    return parar;
  }, []);

  const entrar = useCallback(async (email: string, senha: string): Promise<ResultadoEntrada> => {
    const resposta = await api.post<Sessao | DesafioSegundoFator>(
      '/api/auth/login',
      { email, senha },
      { semRenovar: true },
    );
    if ('desafio' in resposta) return { tipo: 'desafio', desafio: resposta };
    api.definirSessao(resposta);
    return { tipo: 'sessao', usuario: resposta.usuario };
  }, []);

  const sair = useCallback(async () => {
    try {
      await api.post('/api/auth/logout', undefined, { semRenovar: true });
    } finally {
      api.definirSessao(null);
    }
  }, []);

  const valor = useMemo(() => ({ estado, entrar, sair }), [estado, entrar, sair]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSessao(): ContextoSessao {
  const contexto = useContext(Contexto);
  if (!contexto) throw new Error('useSessao fora do ProvedorSessao');
  return contexto;
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

button.pc-link {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  cursor: pointer;
  text-decoration: underline;
}

/* Segundo fator: QR code do aplicativo autenticador e códigos de recuperação */
.pc-segundo-fator-qr {
  display: grid;
  justify-items: center;
  gap: 8px;
  text-align: center;
  font-size: var(--fs-corpo-2);
}

.pc-segundo-fator-qr img {
  border-radius: 8px;
  background: #fff;
}

.pc-codigos-recuperacao {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-family: var(--font-mono);
  font-size: var(--fs-corpo);
}

.pc-codigos-recuperacao code {
  display: block;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  text-align: center;
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

/* Resumo da compra antes de ir ao meio de pagamento */
.pc-resumo-compra {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 6px 16px;
  margin: 0;
}

.pc-resumo-compra dt {
  color: var(--t2);
}

.pc-resumo-compra dd {
  margin: 0;
  font-weight: 600;
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

| Variável          | Padrão                             | Uso                                                 |
| :---------------- | :--------------------------------- | :-------------------------------------------------- |
| `E2E_ALUNO_URL`   | `http://aluno.localhost:8088`      | área do aluno                                       |
| `E2E_ADM_URL`     | `http://adm.localhost:8088`        | ADM                                                 |
| `E2E_SITE_URL`    | `http://cursos.localhost:8088`     | site público                                        |
| `E2E_MAILPIT_URL` | `http://localhost:8026`            | e-mails (links de confirmação e de senha)           |
| `E2E_SENHA_SEED`  | `123456A#b123`                     | senha das contas do seed                            |
| `E2E_TOTP_SEED`   | `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP` | segredo do aplicativo autenticador do admin do seed |
| `E2E_SLOWMO`      | `0`                                | câmera lenta, em ms entre ações                     |

## Contas e sessão

- **Seed da stack** (criado pela API no perfil `e2e`): `admin@celke.com.br` e
  `aluno@celke.com.br`, senha `123456A#b123`. São **só leitura**: nenhum teste troca a senha,
  sai ou mexe no perfil delas.
- **Segundo fator do admin**: o admin do seed já tem o aplicativo autenticador cadastrado com o segredo
  de `SEED_E2E_TOTP`; `support/totp.ts` calcula o código como o celular, e as fixtures e o
  `confirmarSegundoFator` (`support/telas.ts`) passam pela segunda etapa. Na stack de e2e o mesmo código
  vale mais de uma vez (os testes entram várias vezes no mesmo intervalo de 30 s).
- **Sessão nova por teste**: as fixtures `comoAdmin` e `comoAluno` (`support/sessoes.ts`) entram pela API
  no contexto do navegador; ao abrir a página, o app renova a sessão pelo cookie `HttpOnly`. Não há
  sessão gravada: o refresh token é rotativo e um token reutilizado derruba a sessão inteira.
- **Conta própria**: teste que troca senha, sai, compra ou mexe no perfil cria um aluno novo
  (`support/contas.ts`), confirmado pelo link que chega no Mailpit.
- **Termos**: se outra jornada publicou termos, `aceitarTermosSePedir` (`support/telas.ts`) aceita antes
  de seguir.

## Dado de teste e limpeza

- Cursos, contas e ofertas levam um carimbo único (`pw<tempo><sequência>`, e-mails
  `pw...@teste.celke.uno`): execuções seguidas não colidem, mesmo sem limpar a stack.
- Os testes **não apagam** o que criam; o lixo some com o `down -v` da stack. Para começar do zero:
  `down -v` e `up` de novo.
- O pagamento não sai para a internet: a ida ao `pay.hotmart.com` é interceptada (`page.route`) e a
  notificação da Hotmart é enviada pelo próprio teste (`support/pagamentos.ts`, a partir das fixtures do
  backend).

## Os specs

**ADM** (`e2e/adm/`)

- **`acesso.spec.ts`** — entrada do admin com o código do aplicativo, volta para onde ia, código errado,
  senha errada; aluno barrado na tela e na API do ADM.
- **`usuarios.spec.ts`** — bloqueio de um aluno (não entra mais) e desbloqueio; o admin sem as ações na
  própria linha.
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
- **`compra.spec.ts`** — compra pelo botão do site com a Hotmart interceptada: a página mostra curso e
  valor e só abre o pedido no "Ir para o pagamento"; aprovada (o curso aparece), reembolso (o curso
  some), boleto aguardando, e o "Comprar" sem login que volta para a compra depois de entrar.

**Site** (`e2e/site/`)

- **`site.spec.ts`** — home e catálogo com o curso publicado (rascunho não aparece), página do curso com
  meta tags, capa, 404, rotas da API fora das públicas barradas, sitemap e robots, validação de
  certificado e termos publicados. O site é regenerado a cada 3 s na stack de e2e; os testes recarregam
  até a mudança aparecer.

**Apoio** (`e2e/support/`): `config.ts` (endereços e trava), `api.ts` (montar dados pela API),
`sessoes.ts` (fixtures de sessão), `contas.ts` e `mailpit.ts` (contas confirmadas pelo e-mail),
`telas.ts` (login pela tela, segundo fator, termos, escolher opção do `CampoSelect`), `totp.ts` (código
do aplicativo autenticador), `amostras.ts` (arquivos de amostra), `pagamentos.ts` (Hotmart e
notificações).

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

`web/e2e/adm/acesso.spec.ts`:

`````ts
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from '../support/config.ts';
import { emailNovo } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';
import { confirmarSegundoFator, entrarPelaTela } from '../support/telas.ts';

test.describe('ADM', () => {
  test('com a sessão do admin, abre o painel com o menu da administração', async ({
    comoAdmin: page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Usuário: Admin do e2e' })).toContainText(
      'Administrador',
    );
  });

  test('o admin entra pela tela, com o código do aplicativo, e volta para onde ia', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
    await entrarPelaTela(page, ADMIN_SEED, SENHA_SEED);
    await confirmarSegundoFator(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
  });

  test('código errado do aplicativo mostra o erro e não entra', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, ADMIN_SEED, SENHA_SEED);
    await confirmarSegundoFator(page, '000000');
    await expect(page.getByText('Código inválido.', { exact: false })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Verificação em duas etapas' })).toBeVisible();
  });

  test('um aluno é barrado na entrada e a sessão dele é encerrada', async ({ page }) => {
    await page.goto('/entrar');
    const sair = page.waitForResponse('**/api/auth/logout');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await sair;

    await expect(page.getByText('Acesso restrito a administradores.')).toBeVisible();
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });

  test('um aluno com sessão aberta vê o acesso restrito, não o painel', async ({
    comoAluno: page,
  }) => {
    await page.goto('/');
    await expect(
      page.getByRole('heading', { name: 'Acesso restrito a administradores' }),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: 'Painel' })).toBeHidden();

    await page.getByRole('button', { name: 'Entrar com outra conta' }).click();
    await expect(page).toHaveURL(/\/entrar(\?|$)/);
  });

  test('senha errada mostra o erro', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, emailNovo(), 'senha-errada-123');
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('a API do ADM recusa o aluno mesmo sem passar pela tela', async ({ request }) => {
    const login = await request.post('/api/auth/login', {
      data: { email: ALUNO_SEED, senha: SENHA_SEED },
    });
    const { accessToken } = (await login.json()) as { accessToken: string };
    const resposta = await request.get('/api/adm/cursos', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(resposta.status()).toBe(403);
  });
});
`````

`web/e2e/aluno/compra.spec.ts`:

`````ts
import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { clienteApi, montarCurso, type ClienteApi, type CursoMontado } from '../support/api.ts';
import { SITE_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';
import {
  criarOferta,
  hotmartAtiva,
  notificar,
  type HotmartConfigurada,
} from '../support/pagamentos.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Compra pela Hotmart: o botão "Comprar" do site leva ao checkout (a ida ao pay.hotmart.com é
 * interceptada), a notificação é mandada pelo teste como a Hotmart faria e a página de retorno
 * acompanha o pedido. O curso e a oferta são montados uma vez; cada teste usa um aluno novo.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;
let slug: string;
let hotmart: HotmartConfigurada;
const produto = stamp();

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
  slug = (await adm.get<{ slug: string }>(`/api/adm/cursos/${curso.cursoId}`)).slug;
  await adm.patch(`/api/adm/cursos/${curso.cursoId}/status`, { status: 'PUBLICADO' });
  hotmart = await hotmartAtiva(adm);
  await criarOferta(adm, curso.turmaId, produto);
});

test.afterAll(async () => {
  await contexto.dispose();
});

/** Clica em "Comprar" no site e devolve o checkout que iria para a Hotmart. */
async function comprarPeloSite(page: Page, email: string): Promise<URL> {
  let checkout: URL | undefined;
  await page.route('https://pay.hotmart.com/**', async (rota) => {
    checkout = new URL(rota.request().url());
    await rota.fulfill({ contentType: 'text/html', body: '<h1>Checkout da Hotmart</h1>' });
  });
  await expect(async () => {
    await page.goto(`${SITE_URL}/cursos/${slug}/?v=${Date.now()}`);
    await expect(page.getByText('R$ 197,00')).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
  await page.getByRole('link', { name: 'Comprar' }).click();
  // Abrir a página não cria pedido: o aluno confere o curso e o valor e confirma
  await expect(page.getByRole('heading', { name: 'Confirmar a compra' })).toBeVisible();
  await expect(page.getByText('R$ 197,00')).toBeVisible();
  await page.getByRole('button', { name: 'Ir para o pagamento' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout da Hotmart' })).toBeVisible();
  expect(checkout?.pathname).toBe(`/${produto}`);
  expect(checkout?.searchParams.get('off')).toBe('e2e');
  expect(checkout?.searchParams.get('email')).toBe(email);
  expect(checkout?.searchParams.get('xcod')).toHaveLength(22);
  return checkout!;
}

async function alunoLogado(page: Page, request: APIRequestContext): Promise<string> {
  const email = await alunoConfirmado(request);
  await entrarPelaApi(page, email, SENHA_NOVA);
  return email;
}

test('compra aprovada: retorno confirma, o curso aparece; reembolso tira o curso', async ({
  page,
  request,
}) => {
  const email = await alunoLogado(page, request);
  const checkout = await comprarPeloSite(page, email);
  const pedido = checkout.searchParams.get('xcod')!;
  const transacao = `HP${stamp()}`;

  await notificar(request, hotmart, {
    evento: 'PURCHASE_APPROVED',
    produto,
    transacao,
    xcod: pedido,
  });
  await page.goto(`/pagamento/retorno?pedido=${pedido}`);
  await expect(page.getByRole('heading', { name: 'Pagamento confirmado' })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole('button', { name: 'Ir para o curso' }).click();
  await aceitarTermosSePedir(page, 'turma');
  await expect(page.getByText('Aula 1')).toBeVisible();

  await page.goto('/');
  await aceitarTermosSePedir(page);
  await expect(page.getByText(curso.titulo)).toBeVisible();

  await notificar(request, hotmart, {
    evento: 'PURCHASE_REFUNDED',
    produto,
    transacao,
    xcod: pedido,
    ocorridoEm: Date.now() + 1_000,
  });
  await expect(async () => {
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Meus cursos' })).toBeVisible();
    await expect(page.getByText(curso.titulo)).toHaveCount(0, { timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
});

test('boleto impresso: a página de retorno mostra que aguarda o pagamento', async ({
  page,
  request,
}) => {
  const email = await alunoLogado(page, request);
  const pedido = (await comprarPeloSite(page, email)).searchParams.get('xcod')!;

  await notificar(request, hotmart, {
    evento: 'PURCHASE_BILLET_PRINTED',
    produto,
    transacao: `HP${stamp()}`,
    xcod: pedido,
  });
  await page.goto(`/pagamento/retorno?pedido=${pedido}`);
  await expect(page.getByRole('heading', { name: 'Aguardando pagamento' })).toBeVisible();
  await expect(page.getByText(/você recebe um e-mail/)).toBeVisible();
});

test('sem login, Comprar leva à entrada e volta para a compra depois de entrar', async ({
  page,
  request,
}) => {
  const email = await alunoConfirmado(request);
  const oferta = (await contexto.get(`${SITE_URL}/api/cursos/${slug}`).then((r) => r.json())) as {
    ofertas: { id: number }[];
  };
  await page.goto(`/comprar/${oferta.ofertas[0]!.id}`);
  await expect(page).toHaveURL(/\/entrar\?voltar=%2Fcomprar%2F/);
  await page.route('https://pay.hotmart.com/**', (rota) =>
    rota.fulfill({ contentType: 'text/html', body: '<h1>Checkout da Hotmart</h1>' }),
  );
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByRole('button', { name: 'Ir para o pagamento' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout da Hotmart' })).toBeVisible();
});
`````

`web/e2e/support/api.ts`:

`````ts
import { expect, type APIRequestContext } from '@playwright/test';
import { ADMIN_SEED, ADM_URL, ALUNO_URL, SENHA_SEED } from './config.ts';
import { stamp } from './dados.ts';
import { codigoTotp } from './totp.ts';

/** Cliente da API com o token de uma conta, para montar dados de teste sem passar pelas telas. */
export interface ClienteApi {
  get<T>(rota: string): Promise<T>;
  post<T>(rota: string, corpo?: unknown): Promise<T>;
  put<T>(rota: string, corpo: unknown): Promise<T>;
  patch<T>(rota: string, corpo: unknown): Promise<T>;
}

export async function clienteApi(
  request: APIRequestContext,
  email = ADMIN_SEED,
  senha = SENHA_SEED,
  base = ADM_URL,
): Promise<ClienteApi> {
  let login = await request.post(`${base}/api/auth/login`, { data: { email, senha } });
  if (!login.ok()) throw new Error(`Login de ${email} recusado: ${login.status()}`);
  const { desafio } = (await login.json()) as { desafio?: string };
  if (desafio) {
    // Admin do seed: segunda etapa com o código do aplicativo autenticador
    login = await request.post(`${base}/api/auth/segundo-fator`, {
      data: { desafio, codigo: codigoTotp() },
    });
    if (!login.ok()) throw new Error(`Segundo fator de ${email} recusado: ${login.status()}`);
  }
  const { accessToken } = (await login.json()) as { accessToken: string };
  const headers = { Authorization: `Bearer ${accessToken}` };

  const conferir = async <T>(
    metodo: string,
    rota: string,
    resposta: Awaited<ReturnType<APIRequestContext['get']>>,
  ) => {
    if (!resposta.ok())
      throw new Error(`${metodo} ${rota}: ${resposta.status()} ${await resposta.text()}`);
    const texto = await resposta.text();
    return (texto ? JSON.parse(texto) : undefined) as T;
  };
  return {
    get: async (rota) => conferir('GET', rota, await request.get(`${base}${rota}`, { headers })),
    post: async (rota, corpo) =>
      conferir('POST', rota, await request.post(`${base}${rota}`, { headers, data: corpo ?? {} })),
    put: async (rota, corpo) =>
      conferir('PUT', rota, await request.put(`${base}${rota}`, { headers, data: corpo })),
    patch: async (rota, corpo) =>
      conferir('PATCH', rota, await request.patch(`${base}${rota}`, { headers, data: corpo })),
  };
}

/** Cliente do aluno com os termos vigentes já aceitos (a área do aluno exige). */
export async function clienteAluno(request: APIRequestContext, email: string, senha: string) {
  const aluno = await clienteApi(request, email, senha, ALUNO_URL);
  for (const termo of await aluno.get<{ id: number }[]>('/api/termos/pendentes')) {
    await aluno.post(`/api/termos/${termo.id}/aceite`);
  }
  return aluno;
}

export interface CursoMontado {
  cursoId: number;
  titulo: string;
  turmaId: number;
  moduloId: number;
  aulaId: number;
}

/** Curso com turma ativa, um módulo e uma aula, criado pela API. */
export async function montarCurso(adm: ClienteApi): Promise<CursoMontado> {
  const titulo = `Curso ${stamp()}`;
  const curso = await adm.post<{ id: number }>('/api/adm/cursos', {
    titulo,
    resumo: 'Curso montado pelo teste.',
  });
  const turma = await adm.post<{ id: number }>(`/api/adm/cursos/${curso.id}/turmas`, {
    nome: 'Turma 1',
  });
  await adm.patch(`/api/adm/turmas/${turma.id}/status`, { status: 'ATIVA' });
  const modulo = await adm.post<{ id: number }>(`/api/adm/turmas/${turma.id}/modulos`, {
    titulo: 'Módulo 1',
  });
  const aula = await adm.post<{ id: number }>(`/api/adm/modulos/${modulo.id}/aulas`, {
    titulo: 'Aula 1',
  });
  return { cursoId: curso.id, titulo, turmaId: turma.id, moduloId: modulo.id, aulaId: aula.id };
}

/** Envia o vídeo da aula pelo mesmo caminho do ADM (partes direto ao armazenamento) e espera ficar pronto. */
export async function enviarVideo(
  request: APIRequestContext,
  adm: ClienteApi,
  aulaId: number,
  video: Buffer,
): Promise<void> {
  const rota = `/api/adm/aulas/${aulaId}/video`;
  const envio = await adm.post<{ tamanhoParte: number; partes: { numero: number; url: string }[] }>(
    `${rota}/envio`,
    { nomeArquivo: 'aula.mp4', contentType: 'video/mp4', tamanho: video.length },
  );
  const partes = [];
  for (const { numero, url } of envio.partes) {
    const inicio = (numero - 1) * envio.tamanhoParte;
    const resposta = await request.put(url, {
      data: video.subarray(inicio, inicio + envio.tamanhoParte),
    });
    if (!resposta.ok()) throw new Error(`Parte ${numero} recusada: ${resposta.status()}`);
    partes.push({ numero, etag: resposta.headers()['etag'] });
  }
  await adm.post(`${rota}/envio/conclusao`, { partes });
  await expect
    .poll(async () => (await adm.get<{ status: string }>(rota)).status, {
      message: 'vídeo convertido pelo worker',
      timeout: 120_000,
      intervals: [1_000],
    })
    .toBe('PRONTO');
}
`````

`web/e2e/support/config.ts`:

`````ts
/** Endereços e contas da stack de e2e (valores de infra/e2e.env; nenhum vale em servidor). */
export const ALUNO_URL = process.env['E2E_ALUNO_URL'] ?? 'http://aluno.localhost:8088';
export const ADM_URL = process.env['E2E_ADM_URL'] ?? 'http://adm.localhost:8088';
export const SITE_URL = process.env['E2E_SITE_URL'] ?? 'http://cursos.localhost:8088';
export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8026';

export const SENHA_SEED = process.env['E2E_SENHA_SEED'] ?? '123456A#b123';
export const ADMIN_SEED = 'admin@celke.com.br';
export const ALUNO_SEED = 'aluno@celke.com.br';
/** Segredo (Base32) do aplicativo autenticador do admin do seed (`SEED_E2E_TOTP`). */
export const TOTP_SEED = process.env['E2E_TOTP_SEED'] ?? 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';

// Trava: os testes criam contas e trocam senhas; só rodam contra a stack local (*.localhost)
for (const url of [ALUNO_URL, ADM_URL, SITE_URL]) {
  if (!/^http:\/\/[a-z]+\.localhost(:\d+)?$/.test(url)) {
    throw new Error(`E2E recusado: ${url} não é da stack local de e2e (*.localhost).`);
  }
}
`````

`web/e2e/support/sessoes.ts`:

`````ts
import { test as base, type Page } from '@playwright/test';
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from './config.ts';
import { codigoTotp } from './totp.ts';

/**
 * Sessão nova por teste. O refresh token é rotativo e um token reutilizado revoga a família inteira,
 * então uma sessão gravada (storageState) só serviria para um teste. O login vai pela API do próprio
 * contexto do navegador, que guarda o cookie HttpOnly; ao abrir a página, o app renova e entra. O
 * admin do seed conclui a segunda etapa com o código do aplicativo autenticador.
 */
export async function entrarPelaApi(page: Page, email: string, senha = SENHA_SEED): Promise<void> {
  const resposta = await page.request.post('/api/auth/login', {
    data: { email, senha },
  });
  if (!resposta.ok()) throw new Error(`Login de ${email} recusado: ${resposta.status()}`);
  const { desafio } = (await resposta.json()) as { desafio?: string };
  if (!desafio) return;
  const segundaEtapa = await page.request.post('/api/auth/segundo-fator', {
    data: { desafio, codigo: codigoTotp() },
  });
  if (!segundaEtapa.ok())
    throw new Error(`Segundo fator de ${email} recusado: ${segundaEtapa.status()}`);
}

export const test = base.extend<{ comoAluno: Page; comoAdmin: Page }>({
  comoAluno: async ({ page }, usar) => {
    await entrarPelaApi(page, ALUNO_SEED);
    await usar(page);
  },
  comoAdmin: async ({ page }, usar) => {
    await entrarPelaApi(page, ADMIN_SEED);
    await usar(page);
  },
});

export { expect } from '@playwright/test';
`````

`web/e2e/support/telas.ts`:

`````ts
import { expect, type Page } from '@playwright/test';
import { codigoTotp } from './totp.ts';

/** Preenche e envia o TelaLogin do kole-react. */
export async function entrarPelaTela(page: Page, email: string, senha: string): Promise<void> {
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/** Segunda etapa do login do admin do seed: o código do aplicativo autenticador. */
export async function confirmarSegundoFator(page: Page, codigo = codigoTotp()): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Verificação em duas etapas' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Código do aplicativo' }).fill(codigo);
  await page.getByRole('button', { name: 'Confirmar' }).click();
}

/**
 * Se houver termos vigentes sem aceite (outra jornada pode ter publicado), aceita. Sem destino,
 * espera a página "Meus cursos"; com destino (outra página da área logada), espera a moldura e o
 * fim da consulta dos termos.
 */
export async function aceitarTermosSePedir(page: Page, destino?: 'aula' | 'turma'): Promise<void> {
  const termos = page.getByRole('heading', { name: 'Termos atualizados' });
  const pronto = destino
    ? page.getByRole('button', { name: /^Usuário: / })
    : page.getByRole('heading', { name: 'Meus cursos' });
  await expect(termos.or(pronto).first()).toBeVisible();
  if (destino) await expect(page.getByText('Carregando...', { exact: true })).toBeHidden();
  if (await termos.isVisible()) {
    await page.getByRole('checkbox', { name: 'Li e concordo com os termos acima' }).check();
    await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
    await expect(termos).toBeHidden();
  }
}

/**
 * Escolhe uma opção num CampoSelect da @celke/kole-react pelo rótulo visível. O rótulo é um
 * <label for> apontando para o combobox (um div), o que não dá nome acessível a ele.
 */
export async function escolher(page: Page, rotulo: string, opcao: string): Promise<void> {
  const label = page.locator('label').filter({ hasText: new RegExp(`^${rotulo}[:*]*$`) });
  const id = await label.first().getAttribute('for');
  await page.locator(`[id="${id}"]`).click();
  await page.getByRole('option', { name: opcao, exact: true }).click();
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
    "qrcode": "^1.5.4",
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
    "@types/qrcode": "^1.5.6",
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

`.github/dependabot.yml`:

`````yaml
# Atualizações de dependências por Pull Request para a homolog: segurança assim que sai a correção e
# versões novas uma vez por semana, agrupadas para não encher a fila de PRs. Cada PR passa pelo
# pipeline completo (testes, e2e e varredura das imagens) antes do merge.
version: 2
updates:
  - package-ecosystem: maven
    directory: /backend
    target-branch: homolog
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: America/Sao_Paulo
    open-pull-requests-limit: 5
    commit-message:
      prefix: chore(deps)
    groups:
      backend-patch:
        update-types: [patch, minor]

  - package-ecosystem: npm
    directory: /web
    target-branch: homolog
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: America/Sao_Paulo
    open-pull-requests-limit: 5
    commit-message:
      prefix: chore(deps)
    groups:
      web-patch:
        update-types: [patch, minor]

  - package-ecosystem: github-actions
    directory: /
    target-branch: homolog
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: America/Sao_Paulo
    commit-message:
      prefix: ci(deps)
    groups:
      actions:
        patterns: ["*"]

  # Imagens base dos Dockerfiles e dos compose (PostgreSQL, Garage, Caddy, Mailpit)
  - package-ecosystem: docker
    directories: [/backend, /web]
    target-branch: homolog
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: America/Sao_Paulo
    commit-message:
      prefix: chore(deps)

  - package-ecosystem: docker-compose
    directories: [/infra, /infra/caddy]
    target-branch: homolog
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
      timezone: America/Sao_Paulo
    commit-message:
      prefix: chore(deps)
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Totp.java`:

`````java
package br.com.celke.cursos.e2e.support;

import java.nio.ByteBuffer;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Código do aplicativo autenticador (TOTP, RFC 6238: HMAC-SHA1, 30 s, 6 dígitos), como o celular calcula. */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    public static String codigoAtual(String segredoBase32) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(segredoBase32), "HmacSHA1"));
            long passo = System.currentTimeMillis() / 1000 / 30;
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int i = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[i] & 0x7f) << 24)
                    | ((hash[i + 1] & 0xff) << 16)
                    | ((hash[i + 2] & 0xff) << 8)
                    | (hash[i + 3] & 0xff);
            return String.format("%06d", binario % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decodificar(String texto) {
        String limpo = texto.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT);
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/ComandoRedefinirSegundoFator.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.auth.application.SegundoFatorService;
import br.com.celke.cursos.usuario.api.ComandoCriarAdmin;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code redefinir-2fa <email>}: para o administrador que perdeu o celular e os códigos de
 * recuperação. Apaga o segundo fator e encerra as sessões; o próximo login pede o cadastro de novo.
 *
 * <pre>docker compose run --rm api redefinir-2fa admin@celke.com.br</pre>
 */
@Component
@Profile(ComandoCriarAdmin.PERFIL)
public class ComandoRedefinirSegundoFator implements ApplicationRunner {

    public static final String NOME = "redefinir-2fa";

    private static final Logger LOG = LoggerFactory.getLogger(ComandoRedefinirSegundoFator.class);

    private final UsuarioService usuarioService;
    private final SegundoFatorService segundoFatorService;
    private final RefreshTokenService refreshTokenService;

    public ComandoRedefinirSegundoFator(
            UsuarioService usuarioService,
            SegundoFatorService segundoFatorService,
            RefreshTokenService refreshTokenService) {
        this.usuarioService = usuarioService;
        this.segundoFatorService = segundoFatorService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var argumentos = args.getNonOptionArgs();
        if (argumentos.isEmpty() || !NOME.equals(argumentos.get(0))) {
            return; // outro comando
        }
        if (argumentos.size() != 2) {
            throw new IllegalArgumentException("Uso: redefinir-2fa <email>");
        }
        Usuario usuario = usuarioService
                .buscarPorEmail(argumentos.get(1))
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma conta com o e-mail " + argumentos.get(1)));
        boolean tinha = segundoFatorService.redefinir(usuario.getId());
        refreshTokenService.revogarTodas(usuario.getId());
        LOG.warn("Segundo fator do usuário {} redefinido pelo comando de linha", usuario.getId());
        System.out.printf(
                "%s: %s O próximo login pede o cadastro do aplicativo autenticador.%n",
                usuario.getEmail(), tinha ? "segundo fator apagado e sessões encerradas." : "não tinha segundo fator.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/SegundoFatorDtos.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService.Desafio;
import br.com.celke.cursos.auth.application.SegundoFatorService.Cadastro;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Corpos e respostas da segunda etapa do login dos administradores. */
public final class SegundoFatorDtos {

    private SegundoFatorDtos() {}

    /** O que falta para entrar: {@code CODIGO} (aplicativo já cadastrado) ou {@code CADASTRO}. */
    public enum Etapa {
        CODIGO,
        CADASTRO
    }

    /** Resposta do login com a senha certa de um administrador: ainda sem sessão nem cookie. */
    public record DesafioResponse(Etapa segundoFator, String desafio, Instant expiraEm) {

        public static DesafioResponse de(Desafio desafio) {
            return new DesafioResponse(
                    desafio.cadastro() ? Etapa.CADASTRO : Etapa.CODIGO, desafio.token(), desafio.expiraEm());
        }
    }

    public record CadastroRequest(@NotBlank @Size(max = 100) String desafio) {}

    /** Segredo para digitar no aplicativo e o endereço do QR code. */
    public record CadastroResponse(String segredo, String uri) {

        public static CadastroResponse de(Cadastro cadastro) {
            return new CadastroResponse(cadastro.segredo(), cadastro.uri());
        }
    }

    /** O código do aplicativo ou, sem o celular, um código de recuperação. */
    public record ConfirmacaoRequest(
            @NotBlank @Size(max = 100) String desafio,
            @Size(max = 10) String codigo,
            @Size(max = 20) String codigoRecuperacao) {

        @AssertTrue(message = "Informe o código do aplicativo ou um código de recuperação.")
        public boolean isCodigoInformado() {
            return (codigo != null && !codigo.isBlank()) || (codigoRecuperacao != null && !codigoRecuperacao.isBlank());
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/UsuariosAdmController.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.UsuariosAdmService;
import br.com.celke.cursos.shared.web.Pagina;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — usuários")
public class UsuariosAdmController {

    private final UsuariosAdmService service;

    public UsuariosAdmController(UsuariosAdmService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Usuários", description = "Busca por nome ou e-mail, papel e situação; ordem por nome.")
    public Pagina<UsuarioAdm> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Papel papel,
            @RequestParam(required = false) Boolean ativo,
            @ParameterObject Pageable pagina) {
        return Pagina.de(service.listar(busca, papel, ativo, pagina).map(this::resposta));
    }

    @PatchMapping("/{id}/situacao")
    @Operation(
            summary = "Bloquear ou desbloquear",
            description = "Bloquear encerra as sessões na hora e impede o login. O admin não bloqueia a si mesmo.")
    public UsuarioAdm alterarSituacao(
            @PathVariable Long id, @Valid @RequestBody Situacao request, @AuthenticationPrincipal Jwt jwt) {
        return resposta(service.alterarSituacao(id, request.ativo(), Long.valueOf(jwt.getSubject())));
    }

    @PostMapping("/{id}/encerrar-sessoes")
    @Operation(
            summary = "Encerrar as sessões",
            description = "Sai de todos os dispositivos: o usuário precisa entrar de novo.")
    public ResponseEntity<Void> encerrarSessoes(@PathVariable Long id) {
        service.encerrarSessoes(id);
        return ResponseEntity.noContent().build();
    }

    private UsuarioAdm resposta(Usuario u) {
        return new UsuarioAdm(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getPapeis().stream().sorted().toList(),
                u.isAtivo(),
                u.isEmailConfirmado(),
                service.segundoFatorAtivo(u.getId()),
                u.getSessoesEncerradasEm(),
                u.getCriadoEm());
    }

    public record Situacao(@NotNull Boolean ativo) {}

    public record UsuarioAdm(
            Long id,
            String nome,
            String email,
            List<Papel> papeis,
            boolean ativo,
            boolean emailConfirmado,
            boolean segundoFatorAtivo,
            Instant sessoesEncerradasEm,
            Instant criadoEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/CodigoInvalidoException.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.CampoInvalidoException;

/** Código do aplicativo ou de recuperação que não confere. */
public class CodigoInvalidoException extends CampoInvalidoException {

    public CodigoInvalidoException() {
        super("codigo", "Código inválido. Confira o código no aplicativo e tente de novo.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/DesafioInvalidoException.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Desafio do segundo fator inexistente, usado ou vencido (5 minutos depois da senha). */
public class DesafioInvalidoException extends RegraNegocioException {

    public DesafioInvalidoException() {
        super(HttpStatus.UNAUTHORIZED, "O tempo para informar o código acabou. Entre de novo.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/SeedSegundoFatorE2e.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.usuario.application.SeedE2e;
import br.com.celke.cursos.usuario.application.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seed da stack de e2e: o admin do seed já entra com o segundo fator ativo, com o segredo de
 * {@code SEED_E2E_TOTP} (Base32), que os testes usam para calcular o código. Nunca ligado nos servidores.
 */
@Component
@Profile("e2e")
@Order(2)
public class SeedSegundoFatorE2e implements ApplicationRunner {

    private final UsuarioService usuarioService;
    private final SegundoFatorService segundoFatorService;
    private final String segredo;

    public SeedSegundoFatorE2e(
            UsuarioService usuarioService,
            SegundoFatorService segundoFatorService,
            @Value("${SEED_E2E_TOTP}") String segredo) {
        this.usuarioService = usuarioService;
        this.segundoFatorService = segundoFatorService;
        this.segredo = segredo;
    }

    @Override
    public void run(ApplicationArguments args) {
        usuarioService
                .buscarPorEmail(SeedE2e.ADMIN_EMAIL)
                .ifPresent(admin -> segundoFatorService.ativarComSegredo(admin.getId(), segredo));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/SegundoFatorProperties.java`:

`````java
package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Segundo fator (TOTP) dos administradores.
 *
 * @param chave chave AES-256 dos segredos TOTP guardados no banco, 32 bytes em Base64 ({@code TOTP_CHAVE})
 * @param emissor nome que o aplicativo autenticador mostra ao lado da conta
 * @param rejeitarReuso recusa um código já usado (desligado só nos testes, que entram várias vezes no
 *     mesmo intervalo de 30 segundos)
 */
@Validated
@ConfigurationProperties("app.auth.segundo-fator")
public record SegundoFatorProperties(
        @NotBlank String chave, @NotBlank String emissor, boolean rejeitarReuso) {

    @AssertTrue(message = "app.auth.segundo-fator.chave deve ter 32 bytes em Base64 (openssl rand -base64 32)")
    public boolean isChaveValida() {
        if (chave == null || chave.isBlank()) {
            return true; // @NotBlank já acusa
        }
        try {
            return Base64.getDecoder().decode(chave).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/SegundoFatorService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.CodigoRecuperacao;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.domain.SegundoFator;
import br.com.celke.cursos.auth.domain.Totp;
import br.com.celke.cursos.auth.infra.CodigoRecuperacaoRepository;
import br.com.celke.cursos.auth.infra.SegundoFatorRepository;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo fator dos administradores: cadastro do aplicativo autenticador (QR code), conferência do
 * código de 6 dígitos e os códigos de recuperação de uso único. O segredo fica cifrado no banco.
 */
@Service
@EnableConfigurationProperties(SegundoFatorProperties.class)
public class SegundoFatorService {

    static final int CODIGOS_RECUPERACAO = 10;
    private static final String ALFABETO_RECUPERACAO = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SegundoFatorRepository repository;
    private final CodigoRecuperacaoRepository codigos;
    private final SegundoFatorProperties props;
    private final CifraSegredos cifra;
    private final Clock clock;

    public SegundoFatorService(
            SegundoFatorRepository repository,
            CodigoRecuperacaoRepository codigos,
            SegundoFatorProperties props,
            Clock clock) {
        this.repository = repository;
        this.codigos = codigos;
        this.props = props;
        this.cifra = new CifraSegredos(props.chave(), "TOTP_CHAVE");
        this.clock = clock;
    }

    /** Todo administrador entra com o segundo fator. */
    public static boolean exigido(Usuario usuario) {
        return usuario.getPapeis().contains(Papel.ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean ativo(Long usuarioId) {
        return repository.findById(usuarioId).filter(SegundoFator::isAtivo).isPresent();
    }

    /**
     * Gera (ou troca) o segredo de um cadastro ainda não confirmado. Quem já tem o segundo fator ativo
     * não pode cadastrar outro por aqui: perdeu o celular, usa um código de recuperação.
     */
    @Transactional
    public Cadastro iniciarCadastro(Usuario usuario) {
        Optional<SegundoFator> atual = repository.travar(usuario.getId());
        if (atual.filter(SegundoFator::isAtivo).isPresent()) {
            throw new RegraNegocioException(HttpStatus.CONFLICT, "O segundo fator desta conta já está cadastrado.");
        }
        Instant agora = clock.instant();
        String segredo = Totp.gerarSegredo();
        byte[] cifrado = cifra.cifrar(segredo, dadoAssociado(usuario.getId()));
        atual.ifPresentOrElse(
                s -> s.trocarSegredo(cifrado, agora),
                () -> repository.save(new SegundoFator(usuario.getId(), cifrado, agora)));
        return new Cadastro(segredo, Totp.uri(props.emissor(), usuario.getEmail(), segredo));
    }

    /**
     * Confere o código do aplicativo (ou um código de recuperação). O primeiro código certo depois do
     * cadastro ativa o segundo fator e gera os códigos de recuperação.
     */
    @Transactional
    public Verificacao verificar(Usuario usuario, String codigo, String codigoRecuperacao) {
        Optional<SegundoFator> registro = repository.travar(usuario.getId());
        if (registro.isEmpty()) {
            return Verificacao.INVALIDA;
        }
        SegundoFator fator = registro.get();
        Instant agora = clock.instant();
        if (codigoRecuperacao != null && !codigoRecuperacao.isBlank()) {
            if (!fator.isAtivo()) {
                return Verificacao.INVALIDA;
            }
            return codigos.pendente(usuario.getId(), TokenSeguro.hash(normalizar(codigoRecuperacao)))
                    .map(c -> {
                        c.marcarUsado(agora);
                        return new Verificacao(ResultadoLogin.CODIGO_RECUPERACAO, List.of());
                    })
                    .orElse(Verificacao.INVALIDA);
        }
        String segredo = cifra.decifrar(fator.getSegredo(), dadoAssociado(usuario.getId()));
        Long ultimo = props.rejeitarReuso() ? fator.getUltimoPasso() : null;
        OptionalLong passo = Totp.verificar(segredo, codigo, agora, ultimo);
        if (passo.isEmpty()) {
            return Verificacao.INVALIDA;
        }
        if (fator.isAtivo()) {
            fator.registrarPasso(passo.getAsLong());
            return new Verificacao(ResultadoLogin.SUCESSO, List.of());
        }
        fator.ativar(passo.getAsLong(), agora);
        return new Verificacao(ResultadoLogin.SEGUNDO_FATOR_CADASTRADO, novosCodigos(usuario.getId(), agora));
    }

    /** Segundo fator já ativo com um segredo conhecido: só no seed da stack de e2e. */
    @Transactional
    public void ativarComSegredo(Long usuarioId, String segredo) {
        if (repository.findById(usuarioId).isPresent()) {
            return;
        }
        Instant agora = clock.instant();
        SegundoFator fator = new SegundoFator(usuarioId, cifra.cifrar(segredo, dadoAssociado(usuarioId)), agora);
        fator.ativar(Totp.passo(agora) - 2, agora);
        repository.save(fator);
    }

    /** Apaga o segundo fator (comando redefinir-2fa): o próximo login pede o cadastro de novo. */
    @Transactional
    public boolean redefinir(Long usuarioId) {
        codigos.apagarDoUsuario(usuarioId);
        return repository
                .findById(usuarioId)
                .map(s -> {
                    repository.delete(s);
                    return true;
                })
                .orElse(false);
    }

    private List<String> novosCodigos(Long usuarioId, Instant agora) {
        codigos.apagarDoUsuario(usuarioId);
        List<String> gerados = new ArrayList<>();
        for (int i = 0; i < CODIGOS_RECUPERACAO; i++) {
            String codigo = codigoRecuperacao();
            gerados.add(codigo);
            codigos.save(new CodigoRecuperacao(usuarioId, TokenSeguro.hash(codigo), agora));
        }
        return gerados;
    }

    /** Dez caracteres sem os que se confundem (0/o, 1/l/i), no formato xxxxx-xxxxx. */
    static String codigoRecuperacao() {
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                codigo.append('-');
            }
            codigo.append(ALFABETO_RECUPERACAO.charAt(ALEATORIO.nextInt(ALFABETO_RECUPERACAO.length())));
        }
        return codigo.toString();
    }

    static String normalizar(String codigo) {
        String limpo = codigo.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
        return limpo.length() == 10 ? limpo.substring(0, 5) + "-" + limpo.substring(5) : limpo;
    }

    private static String dadoAssociado(Long usuarioId) {
        return "segundo-fator:" + usuarioId;
    }

    /** Segredo em Base32 (para digitar) e o endereço otpauth:// do QR code. */
    public record Cadastro(String segredo, String uri) {}

    /** {@code resultado} nulo = código inválido; os códigos de recuperação só vêm na ativação. */
    public record Verificacao(ResultadoLogin resultado, List<String> codigosRecuperacao) {

        static final Verificacao INVALIDA = new Verificacao(null, List.of());

        public boolean valida() {
            return resultado != null;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/SessaoValidaValidator.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.usuario.application.UsuarioService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Recusa o access token de conta bloqueada ou emitido antes do "encerrar sessões" do ADM, sem esperar
 * os 10 minutos de validade dele. A situação de cada conta fica 30 segundos em memória (o bloqueio
 * feito nesta instância vale na hora: ela esquece a conta).
 */
@Component
public class SessaoValidaValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESSAO_ENCERRADA = new OAuth2Error("invalid_token", "Sessão encerrada.", null);

    private final UsuarioService usuarioService;
    private final Cache<Long, Optional<Situacao>> situacoes = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public SessaoValidaValidator(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(SESSAO_ENCERRADA);
        }
        Optional<Situacao> situacao = situacoes.get(id, this::carregar);
        boolean valida = situacao.filter(Situacao::ativo)
                .filter(s -> s.encerradasEm() == null
                        || jwt.getIssuedAt() == null
                        || !jwt.getIssuedAt().isBefore(s.encerradasEm().truncatedTo(ChronoUnit.SECONDS)))
                .isPresent();
        return valida ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(SESSAO_ENCERRADA);
    }

    /** Esquece a situação guardada (depois de bloquear, desbloquear ou encerrar as sessões). */
    public void esquecer(Long usuarioId) {
        situacoes.invalidate(usuarioId);
    }

    private Optional<Situacao> carregar(Long id) {
        return usuarioService.buscarPorId(id).map(u -> new Situacao(u.isAtivo(), u.getSessoesEncerradasEm()));
    }

    private record Situacao(boolean ativo, Instant encerradasEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/UsuariosAdmService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão das contas pelo ADM: bloquear, desbloquear e encerrar as sessões. Bloquear e encerrar revogam
 * os refresh tokens e invalidam na hora os access tokens já emitidos ({@link SessaoValidaValidator}).
 * As mudanças ficam na trilha de auditoria (a entidade do usuário é auditada).
 */
@Service
public class UsuariosAdmService {

    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final SegundoFatorService segundoFatorService;
    private final SessaoValidaValidator sessoes;

    public UsuariosAdmService(
            UsuarioService usuarioService,
            RefreshTokenService refreshTokenService,
            SegundoFatorService segundoFatorService,
            SessaoValidaValidator sessoes) {
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.segundoFatorService = segundoFatorService;
        this.sessoes = sessoes;
    }

    public Page<Usuario> listar(String busca, Papel papel, Boolean ativo, Pageable pagina) {
        return usuarioService.listar(busca, papel, ativo, pagina);
    }

    public boolean segundoFatorAtivo(Long usuarioId) {
        return segundoFatorService.ativo(usuarioId);
    }

    @Transactional
    public Usuario alterarSituacao(Long id, boolean ativo, Long adminId) {
        if (!ativo && id.equals(adminId)) {
            throw new RegraNegocioException(HttpStatus.CONFLICT, "Você não pode bloquear a própria conta.");
        }
        Usuario usuario = ativo ? usuarioService.desbloquear(id) : usuarioService.bloquear(id);
        if (!ativo) {
            refreshTokenService.revogarTodas(id);
        }
        sessoes.esquecer(id);
        return usuario;
    }

    @Transactional
    public Usuario encerrarSessoes(Long id) {
        Usuario usuario = usuarioService.encerrarSessoes(id);
        refreshTokenService.revogarTodas(id);
        sessoes.esquecer(id);
        return usuario;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/CodigoRecuperacao.java`:

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

/** Código de recuperação do segundo fator, de uso único; o banco guarda só o hash. */
@Entity
@SemAuditoria
@Table(name = "codigos_recuperacao")
public class CodigoRecuperacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected CodigoRecuperacao() {}

    public CodigoRecuperacao(Long usuarioId, String hash, Instant agora) {
        this.usuarioId = usuarioId;
        this.hash = hash;
        this.criadoEm = agora;
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/SegundoFator.java`:

`````java
package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Segredo TOTP de um administrador, cifrado. Fora da trilha: o último passo muda a cada login; os
 * eventos (cadastro, código errado, código de recuperação) ficam na auditoria de login.
 */
@Entity
@SemAuditoria
@Table(name = "segundo_fator")
public class SegundoFator {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private byte[] segredo;

    @Column(name = "ativado_em")
    private Instant ativadoEm;

    @Column(name = "ultimo_passo")
    private Long ultimoPasso;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected SegundoFator() {}

    public SegundoFator(Long usuarioId, byte[] segredo, Instant agora) {
        this.usuarioId = usuarioId;
        this.segredo = segredo;
        this.criadoEm = agora;
    }

    /** Novo segredo de um cadastro ainda não confirmado (o anterior é descartado). */
    public void trocarSegredo(byte[] segredo, Instant agora) {
        this.segredo = segredo;
        this.criadoEm = agora;
    }

    public void ativar(long passo, Instant agora) {
        this.ativadoEm = agora;
        this.ultimoPasso = passo;
    }

    public void registrarPasso(long passo) {
        this.ultimoPasso = passo;
    }

    public boolean isAtivo() {
        return ativadoEm != null;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public byte[] getSegredo() {
        return segredo;
    }

    public Long getUltimoPasso() {
        return ultimoPasso;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/Totp.java`:

`````java
package br.com.celke.cursos.auth.domain;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Código de uso único por tempo (TOTP, RFC 6238) no formato dos aplicativos autenticadores: HMAC-SHA1,
 * passos de 30 segundos, 6 dígitos. Aceita um passo antes e um depois do atual (relógio do celular
 * adiantado ou atrasado).
 */
public final class Totp {

    public static final int DIGITOS = 6;
    public static final long PASSO_SEGUNDOS = 30;
    /** Passos aceitos antes e depois do atual. */
    static final int TOLERANCIA = 1;

    private static final int BYTES_SEGREDO = 20;
    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Totp() {}

    /** Segredo novo de 160 bits, em Base32 (o formato que o usuário digita no aplicativo). */
    public static String gerarSegredo() {
        byte[] bytes = new byte[BYTES_SEGREDO];
        ALEATORIO.nextBytes(bytes);
        return base32(bytes);
    }

    /** Endereço {@code otpauth://} do QR code lido pelo aplicativo. */
    public static String uri(String emissor, String conta, String segredo) {
        String rotulo = codificar(emissor) + ":" + codificar(conta);
        return "otpauth://totp/" + rotulo + "?secret=" + segredo + "&issuer=" + codificar(emissor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASSO_SEGUNDOS;
    }

    public static long passo(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), PASSO_SEGUNDOS);
    }

    /** Código de um passo, com zeros à esquerda. */
    public static String codigo(String segredo, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificarBase32(segredo), "HmacSHA1"));
            byte[] hash =
                    mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(passo).array());
            int deslocamento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[deslocamento] & 0x7f) << 24)
                    | ((hash[deslocamento + 1] & 0xff) << 16)
                    | ((hash[deslocamento + 2] & 0xff) << 8)
                    | (hash[deslocamento + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Passo do código informado, se ele vale agora e é posterior a {@code ultimoPassoUsado} (o mesmo
     * código não serve duas vezes); vazio se não vale. {@code ultimoPassoUsado} nulo aceita qualquer passo.
     */
    public static OptionalLong verificar(String segredo, String codigo, Instant agora, Long ultimoPassoUsado) {
        if (codigo == null) {
            return OptionalLong.empty();
        }
        String digitos = codigo.replaceAll("\\s", "");
        if (!digitos.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long atual = passo(agora);
        for (long passo = atual - TOLERANCIA; passo <= atual + TOLERANCIA; passo++) {
            boolean confere = MessageDigest.isEqual(
                    codigo(segredo, passo).getBytes(StandardCharsets.US_ASCII),
                    digitos.getBytes(StandardCharsets.US_ASCII));
            if (confere && (ultimoPassoUsado == null || passo > ultimoPassoUsado)) {
                return OptionalLong.of(passo);
            }
        }
        return OptionalLong.empty();
    }

    static String base32(byte[] bytes) {
        StringBuilder texto = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                texto.append(ALFABETO_BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO_BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return texto.toString();
    }

    static byte[] decodificarBase32(String texto) {
        String limpo = texto.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Segredo fora do Base32.");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/CodigoRecuperacaoRepository.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.CodigoRecuperacao;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CodigoRecuperacaoRepository extends JpaRepository<CodigoRecuperacao, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CodigoRecuperacao c WHERE c.usuarioId = :usuarioId AND c.hash = :hash AND c.usadoEm IS NULL")
    Optional<CodigoRecuperacao> pendente(Long usuarioId, String hash);

    long countByUsuarioIdAndUsadoEmIsNull(Long usuarioId);

    @Modifying
    @Query("DELETE FROM CodigoRecuperacao c WHERE c.usuarioId = :usuarioId")
    int apagarDoUsuario(Long usuarioId);
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/SegundoFatorRepository.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.SegundoFator;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SegundoFatorRepository extends JpaRepository<SegundoFator, Long> {

    /** Trava a linha: dois logins com o mesmo código ao mesmo tempo, só um passa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SegundoFator s WHERE s.usuarioId = :usuarioId")
    Optional<SegundoFator> travar(Long usuarioId);
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/cripto/CifraSegredos.java`:

`````java
package br.com.celke.cursos.shared.cripto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Segredos guardados no banco (credenciais dos meios de pagamento, chave do segundo fator) cifrados com
 * AES-256-GCM. Cada gravação usa um IV aleatório de 12 bytes, guardado na frente do texto cifrado. O
 * dado associado amarra o blob ao dono (o gateway, o usuário): copiado para outro, não decifra.
 */
public class CifraSegredos {

    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKey chave;
    private final String variavel;

    /**
     * @param chaveBase64 32 bytes em Base64; em branco, a cifra fica indisponível
     * @param variavel variável de ambiente da chave, citada nas mensagens de erro
     */
    public CifraSegredos(String chaveBase64, String variavel) {
        this.variavel = variavel;
        this.chave = chaveBase64 == null || chaveBase64.isBlank()
                ? null
                : new SecretKeySpec(Base64.getDecoder().decode(chaveBase64.strip()), "AES");
    }

    public boolean disponivel() {
        return chave != null;
    }

    public byte[] cifrar(String texto, String dadoAssociado) {
        exigirChave();
        byte[] iv = new byte[TAMANHO_IV];
        ALEATORIO.nextBytes(iv);
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            cifra.updateAAD(dadoAssociado.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cifrado.length)
                    .put(iv)
                    .put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível cifrar os segredos.", e);
        }
    }

    /** @throws IllegalStateException com chave errada, dado associado diferente ou blob alterado */
    public String decifrar(byte[] blob, String dadoAssociado) {
        exigirChave();
        if (blob == null || blob.length <= TAMANHO_IV) {
            throw new IllegalStateException("Segredos gravados em formato inválido.");
        }
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, blob, 0, TAMANHO_IV));
            cifra.updateAAD(dadoAssociado.getBytes(StandardCharsets.UTF_8));
            byte[] texto = cifra.doFinal(blob, TAMANHO_IV, blob.length - TAMANHO_IV);
            return new String(texto, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível decifrar os segredos. A " + variavel + " mudou?", e);
        }
    }

    private void exigirChave() {
        if (chave == null) {
            throw new IllegalStateException(variavel + " não está definida.");
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/CampoInvalidoException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Um campo recusado por uma regra que a validação do DTO não alcança (ex.: senha comum): 400 com a
 * mensagem no {@code detail} e na lista {@code erros}, no mesmo formato da validação.
 */
public class CampoInvalidoException extends RegraNegocioException {

    private final ErroCampo erro;

    public CampoInvalidoException(String campo, String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
        this.erro = new ErroCampo(campo, mensagem);
    }

    @Override
    public Map<String, Object> propriedades() {
        return Map.of("erros", List.of(erro));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/LimitePorUsuarioFilter.java`:

`````java
package br.com.celke.cursos.shared.security;

import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite por usuário logado nas gravações da API. Roda depois da segurança (já sabe quem é o usuário
 * pelo JWT); requisição anônima não passa por aqui (as rotas anônimas têm o limite por IP). O checkout
 * tem um limite próprio, menor: cada um abre um pedido no meio de pagamento.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LimitesUsuarioProperties.class)
public class LimitePorUsuarioFilter extends OncePerRequestFilter {

    static final String CHECKOUT = "/api/pagamentos/checkout";
    private static final Set<String> METODOS_ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Logger LOG = LoggerFactory.getLogger(LimitePorUsuarioFilter.class);

    private final LimitesUsuarioProperties props;
    private final JsonMapper jsonMapper;
    private final TimeMeter relogio;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public LimitePorUsuarioFilter(LimitesUsuarioProperties props, JsonMapper jsonMapper, Clock clock) {
        this.props = props;
        this.jsonMapper = jsonMapper;
        this.relogio = new RelogioBucket(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS_ESCRITA.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken jwt)) {
            chain.doFilter(request, response);
            return;
        }
        String usuario = jwt.getToken().getSubject();
        if (!consumir("escrita:" + usuario, props.escritasPorMinuto(), Duration.ofMinutes(1), request, response)) {
            return;
        }
        if (CHECKOUT.equals(request.getRequestURI())
                && !consumir("checkout:" + usuario, props.checkoutsPorHora(), Duration.ofHours(1), request, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean consumir(
            String chave, int limite, Duration periodo, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ConsumptionProbe consumo =
                baldes.get(chave, c -> balde(limite, periodo)).tryConsumeAndReturnRemaining(1);
        if (consumo.isConsumed()) {
            return true;
        }
        MuitasTentativasException limiteAtingido =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn(
                "Limite por usuário atingido ({}) em {} {}",
                chave.substring(0, chave.indexOf(':')),
                request.getMethod(),
                request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limiteAtingido.segundosDeEspera()));
        ProblemDetailJson.escrever(
                jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limiteAtingido.getMessage());
        return false;
    }

    private Bucket balde(int limite, Duration periodo) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limite).refillGreedy(limite, periodo))
                .withCustomTimePrecision(relogio)
                .build();
    }

    /** Relógio do Bucket4j a partir do {@link Clock} da aplicação (testável). */
    record RelogioBucket(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(clock.millis()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/LimitesUsuarioProperties.java`:

`````java
package br.com.celke.cursos.shared.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites por usuário logado nas rotas que gravam (POST, PUT, PATCH, DELETE): uma conta roubada ou um
 * script não enchem o banco, a fila nem o meio de pagamento.
 *
 * @param escritasPorMinuto gravações de um usuário a cada minuto, somando todas as rotas
 * @param checkoutsPorHora pedidos abertos por um usuário a cada hora
 */
@Validated
@ConfigurationProperties("app.limites-usuario")
public record LimitesUsuarioProperties(
        @Min(1) int escritasPorMinuto, @Min(1) int checkoutsPorHora) {}
`````

`backend/src/main/resources/application-migrar.yaml`:

`````yaml
# Perfil do comando migrar (serviço migracao do compose): aplica as migrations com o dono do schema,
# libera o usuário da aplicação (afterMigrate.sql) e termina. Nada de fila nem agendamentos.
spring:
  flyway:
    enabled: true
app:
  fila:
    consumidor-ativo: false
`````

`backend/src/main/resources/db/migration/V17__seguranca.sql`:

`````sql
-- Segundo fator (TOTP) dos administradores: segredo cifrado (AES-256-GCM, chave TOTP_CHAVE do
-- ambiente). ativado_em nulo = cadastro iniciado e ainda não confirmado com um código.
-- ultimo_passo guarda o passo de 30 s do último código aceito: o mesmo código não serve duas vezes.
CREATE TABLE segundo_fator (
    usuario_id   BIGINT      PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    segredo      BYTEA       NOT NULL,
    ativado_em   TIMESTAMPTZ,
    ultimo_passo BIGINT,
    criado_em    TIMESTAMPTZ NOT NULL
);

-- Códigos de recuperação (uso único) para quem perdeu o celular: só o hash SHA-256
CREATE TABLE codigos_recuperacao (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    hash       VARCHAR(64) NOT NULL,
    usado_em   TIMESTAMPTZ,
    criado_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_codigos_recuperacao_usuario ON codigos_recuperacao (usuario_id);

-- Desafio do login em duas etapas: token de uso único válido por poucos minutos
ALTER TABLE tokens_uso_unico DROP CONSTRAINT ck_tokens_uso_unico_tipo;
ALTER TABLE tokens_uso_unico ADD CONSTRAINT ck_tokens_uso_unico_tipo
    CHECK (tipo IN ('CONFIRMACAO_EMAIL', 'RECUPERACAO_SENHA', 'DESAFIO_SEGUNDO_FATOR'));

ALTER TABLE auditoria_login DROP CONSTRAINT ck_auditoria_login_resultado;
ALTER TABLE auditoria_login ADD CONSTRAINT ck_auditoria_login_resultado
    CHECK (resultado IN ('SUCESSO', 'EMAIL_DESCONHECIDO', 'SENHA_INVALIDA', 'USUARIO_INATIVO',
                         'EMAIL_NAO_CONFIRMADO', 'BLOQUEADO', 'SEGUNDO_FATOR_PENDENTE',
                         'SEGUNDO_FATOR_INVALIDO', 'SEGUNDO_FATOR_CADASTRADO', 'CODIGO_RECUPERACAO'));

-- Encerrar as sessões de um usuário pelo ADM: access tokens emitidos antes disso deixam de valer
ALTER TABLE usuarios ADD COLUMN sessoes_encerradas_em TIMESTAMPTZ;
`````

`backend/src/main/resources/db/migration/afterMigrate.sql`:

`````sql
-- Depois de cada migração (callback do Flyway): o usuário da aplicação (API e worker) só lê e grava
-- dados. Não cria, altera nem apaga tabelas, e não mexe no histórico das migrations. Sem usuário
-- informado (desenvolvimento), a aplicação usa o próprio dono do schema e nada é feito.
DO $$
DECLARE
    usuario TEXT := '${usuario_app}';
    senha   TEXT := '${senha_app}';
BEGIN
    IF usuario = '' OR usuario = current_user THEN
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = usuario) THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', usuario, senha);
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', usuario, senha);
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), usuario);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', usuario);
    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', usuario);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', usuario);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', usuario);
    EXECUTE format('REVOKE ALL ON flyway_schema_history FROM %I', usuario);
END
$$;
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthSegundoFatorIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.SegundoFatorService;
import br.com.celke.cursos.auth.domain.Totp;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Segundo fator dos administradores: cadastro no primeiro acesso, código do aplicativo, códigos de
 * recuperação de uso único, código errado e o aluno que entra sem segunda etapa.
 */
class AuthSegundoFatorIT extends IntegrationTest {

    @Autowired
    private SegundoFatorService segundoFatorService;

    @Autowired
    private JdbcClient jdbc;

    private MvcTestResult login(UsuarioTeste usuario) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(usuario.email(), usuario.senha()))
                .exchange();
    }

    private MvcTestResult post(String rota, String corpo) {
        return mvc.post()
                .uri(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    private static String ler(MvcTestResult resposta, String caminho) throws Exception {
        return JsonPath.read(resposta.getResponse().getContentAsString(), caminho);
    }

    @Test
    @DisplayName(
            "primeiro acesso: senha certa dá o desafio sem cookie, o QR code e, com o código, a sessão e os códigos")
    void cadastroNoPrimeiroAcesso() throws Exception {
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);

        MvcTestResult primeira = login(admin);
        assertThat(primeira).hasStatusOk();
        assertThat(primeira.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(ler(primeira, "$.segundoFator")).isEqualTo("CADASTRO");
        String desafio = ler(primeira, "$.desafio");

        MvcTestResult cadastro = post("/api/auth/segundo-fator/cadastro", "{\"desafio\":\"%s\"}".formatted(desafio));
        assertThat(cadastro).hasStatusOk();
        String segredo = ler(cadastro, "$.segredo");
        assertThat(ler(cadastro, "$.uri")).startsWith("otpauth://totp/Plataforma%20de%20Cursos:");

        MvcTestResult errado =
                post("/api/auth/segundo-fator", "{\"desafio\":\"%s\",\"codigo\":\"000000\"}".formatted(desafio));
        assertThat(errado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(errado).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("codigo");

        String codigo = Totp.codigo(segredo, Totp.passo(Instant.now()));
        MvcTestResult certo =
                post("/api/auth/segundo-fator", "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo));
        assertThat(certo).hasStatusOk();
        assertThat(certo.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly");
        List<String> codigos = JsonPath.read(certo.getResponse().getContentAsString(), "$.codigosRecuperacao");
        assertThat(codigos).hasSize(10).allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));

        // O desafio só vale uma vez
        assertThat(post("/api/auth/segundo-fator", "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        // Próximo login pede só o código; o cadastro de outro aplicativo é recusado
        MvcTestResult segunda = login(admin);
        assertThat(ler(segunda, "$.segundoFator")).isEqualTo("CODIGO");
        assertThat(post(
                        "/api/auth/segundo-fator/cadastro",
                        "{\"desafio\":\"%s\"}".formatted(ler(segunda, "$.desafio"))))
                .hasStatus(HttpStatus.CONFLICT);

        // Código de recuperação vale uma vez, com ou sem o hífen e em maiúsculas
        String recuperacao = codigos.get(0);
        MvcTestResult comRecuperacao = post(
                "/api/auth/segundo-fator",
                "{\"desafio\":\"%s\",\"codigoRecuperacao\":\"%s\"}"
                        .formatted(
                                ler(segunda, "$.desafio"),
                                recuperacao.replace("-", "").toUpperCase()));
        assertThat(comRecuperacao).hasStatusOk();
        assertThat(comRecuperacao).bodyJson().hasPath("$.accessToken");
        MvcTestResult terceira = login(admin);
        assertThat(post(
                        "/api/auth/segundo-fator",
                        "{\"desafio\":\"%s\",\"codigoRecuperacao\":\"%s\"}"
                                .formatted(ler(terceira, "$.desafio"), recuperacao)))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(jdbc.sql("""
                        SELECT resultado FROM auditoria_login WHERE usuario_id = ? ORDER BY id
                        """).param(admin.id()).query(String.class).list())
                .containsSubsequence(
                        "SEGUNDO_FATOR_PENDENTE",
                        "SEGUNDO_FATOR_INVALIDO",
                        "SEGUNDO_FATOR_CADASTRADO",
                        "SEGUNDO_FATOR_PENDENTE",
                        "CODIGO_RECUPERACAO",
                        "SEGUNDO_FATOR_PENDENTE",
                        "SEGUNDO_FATOR_INVALIDO");
    }

    @Test
    @DisplayName("o segredo fica cifrado no banco e redefinir volta para o cadastro")
    void segredoCifradoERedefinir() throws Exception {
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);
        entrar(admin.email(), admin.senha());

        byte[] gravado = jdbc.sql("SELECT segredo FROM segundo_fator WHERE usuario_id = ?")
                .param(admin.id())
                .query(byte[].class)
                .single();
        // Cifrado (IV + texto + tag), não o Base32 de 32 caracteres que o aplicativo recebe
        assertThat(gravado).hasSizeGreaterThan(32);
        assertThat(new String(gravado, StandardCharsets.ISO_8859_1)).doesNotMatch("[A-Z2-7]+");

        assertThat(segundoFatorService.redefinir(admin.id())).isTrue();
        assertThat(ler(login(admin), "$.segundoFator")).isEqualTo("CADASTRO");
    }

    @Test
    @DisplayName("aluno entra direto, sem segunda etapa")
    void alunoSemSegundaEtapa() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);

        assertThat(login(aluno)).bodyJson().hasPath("$.accessToken").doesNotHavePath("$.segundoFator");
    }

    @Test
    @DisplayName("sem código nem código de recuperação, a validação recusa")
    void semCodigo() {
        assertThat(post("/api/auth/segundo-fator", "{\"desafio\":\"qualquer\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/UsuariosAdmIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Usuários no ADM: só o admin acessa; bloquear derruba o token na hora e impede o login; encerrar as
 * sessões vale para os tokens já emitidos; o admin não bloqueia a si mesmo; tudo fica na trilha.
 */
class UsuariosAdmIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    private UsuarioTeste admin;
    private String tokenAdmin;

    @BeforeEach
    void entrarComoAdmin() {
        admin = criarUsuario(Papel.ADMIN);
        tokenAdmin = login(admin.email(), admin.senha());
    }

    private MvcTestResult situacao(Long id, boolean ativo) {
        return mvc.patch()
                .uri("/api/adm/usuarios/{id}/situacao", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\":%s}".formatted(ativo))
                .exchange();
    }

    private MvcTestResult perfil(String token) {
        return mvc.get()
                .uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    @Test
    @DisplayName("anônimo 401, aluno 403, admin lista e busca por e-mail")
    void acesso() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);

        assertThat(mvc.get().uri("/api/adm/usuarios")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get()
                        .uri("/api/adm/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(aluno.email(), aluno.senha()))))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get()
                        .uri(
                                "/api/adm/usuarios?busca={b}&papel=ALUNO",
                                aluno.email().toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itens[0].email")
                .isEqualTo(aluno.email());
    }

    @Test
    @DisplayName("bloquear derruba o token na hora e impede o login; desbloquear devolve o acesso")
    void bloquear() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());
        assertThat(perfil(token)).hasStatusOk();

        assertThat(situacao(aluno.id(), false))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ativo")
                .isEqualTo(false);

        assertThat(perfil(token)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(aluno.email(), aluno.senha())))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(situacao(aluno.id(), true)).hasStatusOk();
        assertThat(perfil(login(aluno.email(), aluno.senha()))).hasStatusOk();

        assertThat(jdbc.sql("""
                        SELECT count(*) FROM auditoria
                         WHERE entidade = 'Usuario' AND entidade_id = ? AND operacao = 'ALTERACAO'
                           AND usuario_id = ? AND depois ->> 'ativo' = 'false'
                        """)
                        .param(String.valueOf(aluno.id()))
                        .param(admin.id())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("encerrar as sessões invalida os tokens já emitidos; um login novo funciona")
    void encerrarSessoes() throws Exception {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String antigo = login(aluno.email(), aluno.senha());
        Thread.sleep(1_100); // o iat do JWT tem precisão de segundos

        assertThat(mvc.post()
                        .uri("/api/adm/usuarios/{id}/encerrar-sessoes", aluno.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(perfil(antigo)).hasStatus(HttpStatus.UNAUTHORIZED);
        Thread.sleep(1_100);
        assertThat(perfil(login(aluno.email(), aluno.senha()))).hasStatusOk();
    }

    @Test
    @DisplayName("o admin não bloqueia a própria conta")
    void naoBloqueiaASiMesmo() {
        assertThat(situacao(admin.id(), false)).hasStatus(HttpStatus.CONFLICT);
        assertThat(perfil(tokenAdmin)).hasStatusOk();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/SessaoValidaValidatorTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SessaoValidaValidatorTest {

    private static final Instant EMITIDO = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private UsuarioService usuarioService;

    private SessaoValidaValidator validador;

    @BeforeEach
    void criar() {
        validador = new SessaoValidaValidator(usuarioService);
    }

    private static Jwt jwt(String sub) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(sub)
                .issuedAt(EMITIDO)
                .build();
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    @Test
    @DisplayName("conta ativa e sem encerramento: válido")
    void ativa() {
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana()));
        assertThat(validador.validate(jwt("7")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("conta bloqueada, inexistente ou subject inválido: recusado")
    void recusado() {
        Usuario bloqueada = ana();
        bloqueada.bloquear(EMITIDO.minusSeconds(60));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(bloqueada));
        when(usuarioService.buscarPorId(8L)).thenReturn(Optional.empty());

        assertThat(validador.validate(jwt("7")).hasErrors()).isTrue();
        assertThat(validador.validate(jwt("8")).hasErrors()).isTrue();
        assertThat(validador.validate(jwt("x")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("token emitido antes do encerramento das sessões é recusado; depois, vale")
    void encerramento() {
        Usuario ana = ana();
        ana.encerrarSessoes(EMITIDO.plusSeconds(1));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));
        assertThat(validador.validate(jwt("7")).hasErrors()).isTrue();

        ana.encerrarSessoes(EMITIDO.minusSeconds(1));
        validador.esquecer(7L);
        assertThat(validador.validate(jwt("7")).hasErrors()).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/domain/TotpTest.java`:

`````java
package br.com.celke.cursos.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    /** Segredo dos vetores de teste da RFC 6238 (SHA-1): "12345678901234567890". */
    private static final String SEGREDO_RFC = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @ParameterizedTest(name = "{0} s → {1}")
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037"})
    @DisplayName("gera os códigos dos vetores da RFC 6238 (6 dígitos)")
    void vetoresRfc(long segundos, String esperado) {
        assertThat(Totp.codigo(SEGREDO_RFC, Totp.passo(Instant.ofEpochSecond(segundos))))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("aceita um passo antes e um depois; fora disso, recusa")
    void tolerancia() {
        Instant agora = Instant.ofEpochSecond(1_111_111_111);
        long passo = Totp.passo(agora);

        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo - 1), agora, null))
                .hasValue(passo - 1);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo + 1), agora, null))
                .hasValue(passo + 1);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo - 2), agora, null))
                .isEmpty();
    }

    @Test
    @DisplayName("o mesmo código não serve duas vezes (passo já usado)")
    void semReuso() {
        Instant agora = Instant.ofEpochSecond(1_111_111_111);
        long passo = Totp.passo(agora);
        String codigo = Totp.codigo(SEGREDO_RFC, passo);

        assertThat(Totp.verificar(SEGREDO_RFC, codigo, agora, passo - 1)).hasValue(passo);
        assertThat(Totp.verificar(SEGREDO_RFC, codigo, agora, passo)).isEmpty();
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({"''", "12345", "1234567", "abcdef"})
    @DisplayName("código fora do formato é recusado")
    void formato(String codigo) {
        assertThat(Totp.verificar(SEGREDO_RFC, codigo, Instant.now(), null)).isEmpty();
    }

    @Test
    @DisplayName("Base32 vai e volta; o segredo novo tem 160 bits")
    void base32() {
        byte[] bytes = "qualquer coisa".getBytes(StandardCharsets.UTF_8);
        assertThat(Totp.decodificarBase32(Totp.base32(bytes))).isEqualTo(bytes);
        assertThat(Totp.decodificarBase32(Totp.gerarSegredo())).hasSize(20);
    }

    @Test
    @DisplayName("o endereço do QR code leva emissor, conta e parâmetros")
    void uri() {
        assertThat(Totp.uri("Plataforma de Cursos", "ana@celke.uno", "ABC"))
                .isEqualTo("otpauth://totp/Plataforma%20de%20Cursos:ana%40celke.uno?secret=ABC"
                        + "&issuer=Plataforma%20de%20Cursos&algorithm=SHA1&digits=6&period=30");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/domain/CampoConfiguracaoTest.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CampoConfiguracaoTest {

    private static final List<String> HOTMART = List.of("pay.hotmart.com", "sandbox.hotmart.com");

    @ParameterizedTest(name = "{0}")
    @CsvSource({"https://pay.hotmart.com", "https://sandbox.hotmart.com/", "https://PAY.hotmart.com/x"})
    @DisplayName("endereço HTTPS de um domínio permitido passa")
    void aceita(String url) {
        assertThat(CampoConfiguracao.erroUrl(url, HOTMART)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http://pay.hotmart.com,             'Informe um endereço começando com https://.'",
        "https://pay.hotmart.com.golpe.io,   'O endereço precisa ser de pay.hotmart.com ou sandbox.hotmart.com.'",
        "https://pay.hotmart.com@golpe.io,   'Informe um endereço começando com https://.'",
        "https://user@pay.hotmart.com,       'Informe um endereço começando com https://.'",
        "javascript:alert(1),                'Informe um endereço começando com https://.'",
        "nao é url,                          'Informe um endereço começando com https://.'",
    })
    @DisplayName("HTTP, outro domínio, credencial na URL e lixo são recusados")
    void recusa(String url, String esperado) {
        assertThat(CampoConfiguracao.erroUrl(url, HOTMART)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("sem lista de domínios, vale qualquer HTTPS")
    void semLista() {
        assertThat(CampoConfiguracao.erroUrl("https://qualquer.exemplo", List.of()))
                .isNull();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/banco/UsuarioAplicacaoBancoIT.java`:

`````java
package br.com.celke.cursos.shared.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O usuário da aplicação (criado pelo afterMigrate.sql) lê e grava dados, mas não cria, altera nem apaga
 * tabelas, nem toca no histórico das migrations.
 */
class UsuarioAplicacaoBancoIT extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    private Connection comoAplicacao() throws SQLException {
        String url = ((HikariDataSource) dataSource).getJdbcUrl();
        return DriverManager.getConnection(url, "app_teste", "senha-do-app-de-teste");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "SELECT count(*) FROM usuarios",
                "UPDATE usuarios SET nome = nome WHERE id = -1",
                "DELETE FROM tokens_uso_unico WHERE id = -1",
                "INSERT INTO auditoria_login (email, resultado, ip, criado_em) VALUES ('x@teste.celke.uno', 'SUCESSO', '127.0.0.1', now())"
            })
    @DisplayName("lê e grava dados")
    void leEGravaDados(String sql) throws SQLException {
        try (Connection conexao = comoAplicacao();
                Statement comando = conexao.createStatement()) {
            conexao.setAutoCommit(false);
            comando.execute(sql);
            conexao.rollback();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "CREATE TABLE invasora (id INT)",
                "DROP TABLE usuarios",
                "ALTER TABLE usuarios ADD COLUMN invasora INT",
                "TRUNCATE auditoria",
                "SELECT count(*) FROM flyway_schema_history"
            })
    @DisplayName("não muda a estrutura do banco nem lê o histórico das migrations")
    void semDdl(String sql) throws SQLException {
        try (Connection conexao = comoAplicacao();
                Statement comando = conexao.createStatement()) {
            assertThatThrownBy(() -> comando.execute(sql))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/cripto/CifraSegredosTest.java`:

`````java
package br.com.celke.cursos.shared.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CifraSegredosTest {

    private static final String CHAVE = "dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=";
    private static final String OUTRA_CHAVE = "b3V0cmEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMi1ieXQ=";
    private final CifraSegredos cifra = new CifraSegredos(CHAVE, "PAGAMENTOS_CHAVE");

    @Test
    @DisplayName("ida e volta devolve o texto; o blob não contém o texto")
    void idaEVolta() {
        byte[] blob = cifra.cifrar("{\"hottok\":\"segredo-123\"}", "token-a");

        assertThat(new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("segredo-123");
        assertThat(cifra.decifrar(blob, "token-a")).isEqualTo("{\"hottok\":\"segredo-123\"}");
    }

    @Test
    @DisplayName("cada gravação usa um IV diferente")
    void ivDiferente() {
        byte[] primeiro = cifra.cifrar("igual", "token-a");
        byte[] segundo = cifra.cifrar("igual", "token-a");

        assertThat(Arrays.copyOf(primeiro, 12)).isNotEqualTo(Arrays.copyOf(segundo, 12));
        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("chave errada não decifra")
    void chaveErrada() {
        byte[] blob = cifra.cifrar("segredo", "token-a");

        assertThatThrownBy(() -> new CifraSegredos(OUTRA_CHAVE, "PAGAMENTOS_CHAVE").decifrar(blob, "token-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Não foi possível decifrar os segredos. A PAGAMENTOS_CHAVE mudou?");
    }

    @Test
    @DisplayName("blob de um gateway não decifra em outro (dado associado)")
    void outroGateway() {
        byte[] blob = cifra.cifrar("segredo", "token-a");

        assertThatThrownBy(() -> cifra.decifrar(blob, "token-b")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("blob alterado ou curto demais não decifra")
    void blobInvalido() {
        byte[] blob = cifra.cifrar("segredo", "token-a");
        blob[blob.length - 1] ^= 1;

        assertThatThrownBy(() -> cifra.decifrar(blob, "token-a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifra.decifrar(new byte[12], "token-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Segredos gravados em formato inválido.");
        assertThatThrownBy(() -> cifra.decifrar(null, "token-a")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sem chave a cifra fica indisponível e recusa cifrar e decifrar")
    void semChave() {
        CifraSegredos semChave = new CifraSegredos(" ", "PAGAMENTOS_CHAVE");

        assertThat(semChave.disponivel()).isFalse();
        assertThat(new CifraSegredos(null, "PAGAMENTOS_CHAVE").disponivel()).isFalse();
        assertThatThrownBy(() -> semChave.cifrar("a", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PAGAMENTOS_CHAVE não está definida.");
        assertThatThrownBy(() -> semChave.decifrar(new byte[20], "b")).isInstanceOf(IllegalStateException.class);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/security/LimitePorUsuarioFilterTest.java`:

`````java
package br.com.celke.cursos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/** Filtro testado direto, com limites pequenos: 3 gravações por minuto e 2 checkouts por hora. */
class LimitePorUsuarioFilterTest {

    private RelogioAjustavel relogio;
    private LimitePorUsuarioFilter filtro;

    @BeforeEach
    void criar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-01-10T12:00:00Z"));
        filtro = new LimitePorUsuarioFilter(
                new LimitesUsuarioProperties(3, 2), JsonMapper.builder().build(), relogio);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private int enviar(String metodo, String rota, String usuario) throws Exception {
        SecurityContextHolder.clearContext();
        if (usuario != null) {
            Jwt jwt = Jwt.withTokenValue("t")
                    .header("alg", "HS256")
                    .subject(usuario)
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest(metodo, rota), response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("passou do limite de gravações: 429 com Retry-After; o minuto seguinte libera")
    void limiteDeEscritas() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(enviar("POST", "/api/forum/topicos", "7")).isEqualTo(200);
        }
        assertThat(enviar("PUT", "/api/perfil", "7")).isEqualTo(429);

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(enviar("DELETE", "/api/perfil/foto", "7")).isEqualTo(200);
    }

    @Test
    @DisplayName("cada usuário tem o próprio limite; leitura e anônimo não contam")
    void porUsuario() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar("POST", "/api/forum/topicos", "7");
        }
        assertThat(enviar("POST", "/api/forum/topicos", "8")).isEqualTo(200);
        assertThat(enviar("GET", "/api/perfil", "7")).isEqualTo(200);
        assertThat(enviar("POST", "/api/auth/login", null)).isEqualTo(200);
    }

    @Test
    @DisplayName("o checkout tem limite próprio por hora")
    void limiteDeCheckout() throws Exception {
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(200);
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(200);
        relogio.avancar(Duration.ofMinutes(2));
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(429);

        relogio.avancar(Duration.ofHours(1));
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(200);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/security/RotasProtegidasIT.java`:

`````java
package br.com.celke.cursos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Cobre: varredura de TODAS as rotas registradas no Spring MVC — sem login, só as rotas públicas
 * declaradas aqui respondem sem pedir autenticação; com token de ALUNO, toda rota {@code /api/adm/**}
 * responde 403. Uma rota nova esquecida fora da proteção quebra este teste.
 * Não cobre: regras de cada rota (os *AcessoIT de cada módulo).
 * Dado criado: nenhum (ids inexistentes; a segurança responde antes da regra).
 */
class RotasProtegidasIT extends IntegrationTest {

    /** Rotas que respondem sem login, de propósito (método + padrão da rota). */
    static final Set<String> PUBLICAS = Set.of(
            "POST /api/auth/login",
            "POST /api/auth/segundo-fator",
            "POST /api/auth/segundo-fator/cadastro",
            "POST /api/auth/refresh",
            "POST /api/auth/logout",
            "POST /api/auth/cadastro",
            "POST /api/auth/confirmar-email",
            "POST /api/auth/reenviar-confirmacao",
            "POST /api/auth/recuperar-senha",
            "POST /api/auth/redefinir-senha",
            "GET /api/cursos",
            "GET /api/cursos/{slug}",
            "GET /api/cursos/{slug}/capa",
            "GET /api/termos/vigentes",
            "GET /api/certificados/{codigo}",
            "POST /api/pagamentos/webhooks/{webhookToken}",
            "GET /api/pagamentos/pedidos/{codigo}");

    private static final String DETALHE_401 = "Autenticação necessária.";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    record Rota(HttpMethod metodo, String padrao) {

        String chave() {
            return metodo.name() + " " + padrao;
        }

        /** Caminho concreto: variáveis trocadas por valores que não existem. */
        String caminho() {
            return padrao.replaceAll("\\{variante[^}]*}", "720p")
                    .replaceAll("\\{(slug|codigo|webhookToken)}", "nao-existe")
                    .replaceAll("\\{[^}]+}", "999999999");
        }
    }

    private List<Rota> rotas() {
        List<Rota> rotas = new ArrayList<>();
        mapeamentos.getHandlerMethods().forEach((info, metodo) -> {
            Set<String> padroes = info.getPatternValues();
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String padrao : padroes) {
                if (!padrao.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod m : metodos) {
                    rotas.add(new Rota(HttpMethod.valueOf(m.name()), padrao));
                }
            }
        });
        return rotas;
    }

    private MvcTestResult chamar(Rota rota, String token) {
        var requisicao = mvc.method(rota.metodo()).uri(rota.caminho());
        if (rota.metodo() != HttpMethod.GET && rota.metodo() != HttpMethod.DELETE) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    private static boolean pediuLogin(MvcTestResult resposta) throws Exception {
        return resposta.getResponse().getStatus() == 401
                && resposta.getResponse().getContentAsString().contains(DETALHE_401);
    }

    @Test
    @DisplayName("sem login, toda rota fora da lista pública pede autenticação (401)")
    void anonimo() throws Exception {
        List<Rota> rotas = rotas();
        assertThat(rotas).hasSizeGreaterThan(100);
        Set<String> abertas = new TreeSet<>();
        Set<String> publicasQuePedemLogin = new TreeSet<>();
        for (Rota rota : rotas) {
            boolean pediu = pediuLogin(chamar(rota, null));
            if (PUBLICAS.contains(rota.chave())) {
                if (pediu) {
                    publicasQuePedemLogin.add(rota.chave());
                }
            } else if (!pediu) {
                abertas.add(rota.chave());
            }
        }
        assertThat(abertas).as("rotas restritas abertas sem login").isEmpty();
        assertThat(publicasQuePedemLogin).as("rotas públicas que pedem login").isEmpty();
        assertThat(rotas.stream().map(Rota::chave)).containsAll(PUBLICAS);
    }

    @Test
    @DisplayName("com token de ALUNO, toda rota /api/adm/** responde 403")
    void alunoNoAdm() throws Exception {
        String aluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        Set<String> liberadas = new TreeSet<>();
        int total = 0;
        for (Rota rota : rotas()) {
            if (!rota.padrao().startsWith("/api/adm/")) {
                continue;
            }
            total++;
            if (chamar(rota, aluno).getResponse().getStatus() != 403) {
                liberadas.add(rota.chave());
            }
        }
        assertThat(total).isGreaterThan(60);
        assertThat(liberadas).as("rotas do ADM liberadas para aluno").isEmpty();
    }

    @Test
    @DisplayName("fora da API: actuator só expõe o health; documentação desligada")
    void foraDaApi() throws Exception {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        for (String caminho : List.of(
                "/actuator", "/actuator/env", "/actuator/heapdump", "/actuator/configprops", "/actuator/loggers")) {
            int status = mvc.get().uri(caminho).exchange().getResponse().getStatus();
            assertThat(status).as(caminho).isIn(401, 404);
        }
        for (String caminho : List.of("/v3/api-docs", "/swagger-ui.html")) {
            assertThat(mvc.get().uri(caminho).exchange().getResponse().getStatus())
                    .as(caminho)
                    .isIn(404, 302);
        }
    }
}
`````

`web/adm/src/paginas/usuarios/UsuariosPagina.tsx`:

`````tsx
import {
  AcaoLinha,
  CabecalhoPagina,
  CampoSelect,
  CampoTexto,
  Status,
  Tabela,
  useConfirmacao,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { data } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { PAPEIS, type UsuarioAdm } from '../../tipos.ts';

const TAMANHO = 20;

type Situacao = 'ativos' | 'bloqueados';

/**
 * Contas da plataforma: busca por nome ou e-mail, bloqueio (a pessoa sai na hora e não entra mais) e
 * encerramento das sessões abertas. Cada ação fica na trilha de auditoria.
 */
export function UsuariosPagina() {
  const { estado } = useSessao();
  const meuId = estado.status === 'autenticado' ? estado.usuario.id : null;
  const confirmar = useConfirmacao();
  const { executar } = useAcao();
  const [pagina, setPagina] = useState(1);
  const [busca, setBusca] = useState('');
  const [papel, setPapel] = useState<keyof typeof PAPEIS | null>(null);
  const [situacao, setSituacao] = useState<Situacao | null>(null);
  const termo = busca.trim();

  const usuarios = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (termo) filtro.set('busca', termo);
    if (papel) filtro.set('papel', papel);
    if (situacao) filtro.set('ativo', String(situacao === 'ativos'));
    return api.get<Pagina<UsuarioAdm>>(`/api/adm/usuarios?${filtro}`);
  }, [pagina, termo, papel, situacao]);

  const mudar =
    <T,>(definir: (valor: T) => void) =>
    (valor: T) => {
      definir(valor);
      setPagina(1);
    };

  const alterarSituacao = async (u: UsuarioAdm) => {
    const bloquear = u.ativo;
    const sim = await confirmar({
      titulo: bloquear ? 'Bloquear a conta?' : 'Desbloquear a conta?',
      mensagem: bloquear
        ? `${u.nome} sai de todos os dispositivos na hora e não consegue mais entrar.`
        : `${u.nome} volta a entrar com a própria senha.`,
      severidade: bloquear ? 'err' : 'info',
      rotuloAceitar: bloquear ? 'Bloquear' : 'Desbloquear',
      rotuloRecusar: 'Voltar',
    });
    if (!sim) return;
    const ok = await executar(
      () => api.patch(`/api/adm/usuarios/${u.id}/situacao`, { ativo: !bloquear }).then(() => true),
      bloquear ? 'Conta bloqueada' : 'Conta desbloqueada',
    );
    if (ok) usuarios.recarregar();
  };

  const encerrarSessoes = async (u: UsuarioAdm) => {
    const sim = await confirmar({
      titulo: 'Encerrar as sessões?',
      mensagem: `${u.nome} sai de todos os dispositivos e precisa entrar de novo.`,
      severidade: 'warn',
      rotuloAceitar: 'Encerrar sessões',
      rotuloRecusar: 'Voltar',
    });
    if (!sim) return;
    const ok = await executar(
      () => api.post(`/api/adm/usuarios/${u.id}/encerrar-sessoes`).then(() => true),
      'Sessões encerradas',
    );
    if (ok) usuarios.recarregar();
  };

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Usuários" icone="users">
        Alunos e administradores. Bloquear tira o acesso na hora; encerrar as sessões faz a pessoa
        entrar de novo em todos os dispositivos. As ações ficam na auditoria.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoTexto
          rotulo="Nome ou e-mail"
          valor={busca}
          aoMudar={mudar(setBusca)}
          maxLength={254}
        />
        <CampoSelect
          rotulo="Papel"
          variante="inline"
          placeholder="Todos"
          botaoLimpar
          opcoes={Object.entries(PAPEIS).map(([valor, rotulo]) => ({
            valor: valor as keyof typeof PAPEIS,
            rotulo,
          }))}
          valor={papel}
          aoMudar={mudar(setPapel)}
        />
        <CampoSelect<Situacao>
          rotulo="Situação"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={[
            { valor: 'ativos', rotulo: 'Ativos' },
            { valor: 'bloqueados', rotulo: 'Bloqueados' },
          ]}
          valor={situacao}
          aoMudar={mudar(setSituacao)}
        />
      </div>
      <Tabela<UsuarioAdm>
        rotulo="Usuários"
        colunas={[
          { campo: 'nome', rotulo: 'Nome', destaque: true },
          { campo: 'email', rotulo: 'E-mail' },
          {
            campo: 'papeis',
            rotulo: 'Papel',
            formato: (_, u) => u.papeis.map((p) => PAPEIS[p]).join(', '),
          },
          {
            campo: 'ativo',
            rotulo: 'Situação',
            render: (u) => (
              <Status
                rotulo={u.ativo ? 'Ativo' : 'Bloqueado'}
                severidade={u.ativo ? 'ok' : 'err'}
              />
            ),
          },
          {
            campo: 'segundoFatorAtivo',
            rotulo: 'Duas etapas',
            formato: (_, u) =>
              u.papeis.includes('ADMIN') ? (u.segundoFatorAtivo ? 'Ativa' : 'Pendente') : '—',
          },
          { campo: 'criadoEm', rotulo: 'Desde', formato: (v) => data(v as string) },
        ]}
        registros={usuarios.dados?.itens ?? []}
        carregando={usuarios.carregando && !usuarios.dados}
        atualizando={usuarios.carregando && !!usuarios.dados}
        erro={usuarios.erro}
        aoRecarregar={usuarios.recarregar}
        vazioTitulo="Nenhum usuário"
        vazioDescricao="Nenhuma conta com estes filtros."
        vazioIcone="users"
        paginacao={{
          total: usuarios.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
        acoes={(u) =>
          u.id !== meuId && (
            <>
              <AcaoLinha
                icone="log-out"
                rotulo={`Encerrar as sessões de ${u.nome}`}
                onClick={() => void encerrarSessoes(u)}
              />
              <AcaoLinha
                icone={u.ativo ? 'lock' : 'lock-open'}
                severidade={u.ativo ? 'err' : undefined}
                rotulo={u.ativo ? `Bloquear ${u.nome}` : `Desbloquear ${u.nome}`}
                onClick={() => void alterarSituacao(u)}
              />
            </>
          )
        }
      />
    </div>
  );
}
`````

`web/compartilhado/src/sessao/SegundoFator.tsx`:

`````tsx
import { Alerta, Botao, CampoTexto } from '@celke/kole-react';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { api } from '../api/cliente.ts';
import { mensagemDeErro } from '../api/erros.ts';
import type { CadastroSegundoFator, DesafioSegundoFator, Sessao, Usuario } from '../api/tipos.ts';
import { LayoutConta } from '../ui/LayoutConta.tsx';
import {
  erroCodigo,
  erroCodigoRecuperacao,
  limparCodigo,
  segredoEmGrupos,
} from './segundoFator.ts';

interface SegundoFatorProps {
  marca: string;
  desafio: DesafioSegundoFator;
  /** Sessão aberta: o componente já a entregou ao cliente da API. */
  aoConcluir: (usuario: Usuario) => void;
  /** Volta para o e-mail e a senha (desafio vencido ou "Voltar"). */
  aoVoltar: () => void;
}

/**
 * Segunda etapa do login do administrador. No primeiro acesso, mostra o QR code (e o segredo para
 * digitar) do aplicativo autenticador; depois, pede o código de 6 dígitos ou um código de
 * recuperação. Na ativação, mostra os códigos de recuperação uma única vez antes de entrar.
 */
export function SegundoFator({ marca, desafio, aoConcluir, aoVoltar }: SegundoFatorProps) {
  const cadastro = desafio.segundoFator === 'CADASTRO';
  const [dados, setDados] = useState<CadastroSegundoFator>();
  const [qr, setQr] = useState<string>();
  const [comRecuperacao, setComRecuperacao] = useState(false);
  const [codigo, setCodigo] = useState('');
  const [erroCampo, setErroCampo] = useState<string>();
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [sessao, setSessao] = useState<Sessao>();

  useEffect(() => {
    if (!cadastro) return;
    let ativo = true;
    api
      .post<CadastroSegundoFator>(
        '/api/auth/segundo-fator/cadastro',
        { desafio: desafio.desafio },
        { semRenovar: true },
      )
      .then(async (resposta) => {
        const imagem = await QRCode.toDataURL(resposta.uri, { margin: 1, width: 200 });
        if (!ativo) return;
        setDados(resposta);
        setQr(imagem);
      })
      .catch((erro: unknown) => ativo && setErroGeral(mensagemDeErro(erro)));
    return () => {
      ativo = false;
    };
  }, [cadastro, desafio.desafio]);

  if (sessao?.codigosRecuperacao?.length) {
    return (
      <LayoutConta marca={marca} titulo="Guarde os códigos de recuperação">
        <Alerta
          severidade="warn"
          mensagem="Sem o celular, cada código abaixo permite entrar uma vez. Eles não aparecem de novo: guarde num lugar seguro."
        />
        <ul className="pc-codigos-recuperacao" aria-label="Códigos de recuperação">
          {sessao.codigosRecuperacao.map((c) => (
            <li key={c}>
              <code>{c}</code>
            </li>
          ))}
        </ul>
        <Botao
          rotulo="Guardei os códigos, continuar"
          icone="arrow-right"
          bloco
          onClick={() => {
            api.definirSessao(sessao);
            aoConcluir(sessao.usuario);
          }}
        />
      </LayoutConta>
    );
  }

  const enviar = async () => {
    const erro = comRecuperacao ? erroCodigoRecuperacao(codigo) : erroCodigo(codigo);
    setErroCampo(erro);
    setErroGeral(undefined);
    if (erro) return;
    setEnviando(true);
    try {
      const corpo = comRecuperacao
        ? { desafio: desafio.desafio, codigoRecuperacao: codigo.trim() }
        : { desafio: desafio.desafio, codigo: limparCodigo(codigo) };
      const resposta = await api.post<Sessao>('/api/auth/segundo-fator', corpo, {
        semRenovar: true,
      });
      if (resposta.codigosRecuperacao?.length) {
        setSessao(resposta);
        return;
      }
      api.definirSessao(resposta);
      aoConcluir(resposta.usuario);
    } catch (erro) {
      setErroGeral(mensagemDeErro(erro));
      setCodigo('');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca={marca}
      titulo={cadastro ? 'Ative a verificação em duas etapas' : 'Verificação em duas etapas'}
      descricao={
        cadastro
          ? 'Leia o QR code com um aplicativo autenticador (Google Authenticator, Microsoft Authenticator, 1Password…) e informe o código que ele mostrar.'
          : comRecuperacao
            ? 'Informe um dos códigos de recuperação que você guardou.'
            : 'Informe o código de 6 dígitos do aplicativo autenticador.'
      }
      aoEnviar={enviar}
      rodape={
        <>
          {!cadastro && (
            <button
              type="button"
              className="pc-link"
              onClick={() => {
                setComRecuperacao(!comRecuperacao);
                setCodigo('');
                setErroCampo(undefined);
              }}
            >
              {comRecuperacao
                ? 'Usar o código do aplicativo'
                : 'Sem o celular? Use um código de recuperação'}
            </button>
          )}
          <button type="button" className="pc-link" onClick={aoVoltar}>
            Voltar
          </button>
        </>
      }
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      {cadastro && !qr && !erroGeral && <p role="status">Gerando o QR code…</p>}
      {cadastro && dados && qr && (
        <div className="pc-segundo-fator-qr">
          <img src={qr} alt="QR code para o aplicativo autenticador" width={200} height={200} />
          <p>
            Não consegue ler? Digite a chave no aplicativo:{' '}
            <code aria-label="Chave do aplicativo autenticador">
              {segredoEmGrupos(dados.segredo)}
            </code>
          </p>
        </div>
      )}
      <CampoTexto
        rotulo={comRecuperacao ? 'Código de recuperação' : 'Código do aplicativo'}
        valor={codigo}
        aoMudar={setCodigo}
        erro={erroCampo}
        obrigatorio
        autoComplete="one-time-code"
        inputMode={comRecuperacao ? 'text' : 'numeric'}
        autoFocus
      />
      <Botao type="submit" rotulo="Confirmar" icone="shield-check" carregando={enviando} bloco />
    </LayoutConta>
  );
}
`````

`web/compartilhado/src/sessao/segundoFator.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import {
  erroCodigo,
  erroCodigoRecuperacao,
  limparCodigo,
  segredoEmGrupos,
} from './segundoFator.ts';

describe('erroCodigo', () => {
  it('aceita 6 dígitos, com ou sem o espaço que o aplicativo mostra', () => {
    expect(erroCodigo('123456')).toBeUndefined();
    expect(erroCodigo('123 456')).toBeUndefined();
    expect(limparCodigo(' 123 456 ')).toBe('123456');
  });

  it('recusa vazio, letras e tamanho errado', () => {
    expect(erroCodigo('')).toBe('Informe o código de 6 dígitos do aplicativo.');
    expect(erroCodigo('12345')).toBe('O código tem 6 dígitos.');
    expect(erroCodigo('12a456')).toBe('O código tem 6 dígitos.');
  });
});

describe('erroCodigoRecuperacao', () => {
  it('aceita 10 letras e números, com ou sem hífen', () => {
    expect(erroCodigoRecuperacao('abcde-fghjk')).toBeUndefined();
    expect(erroCodigoRecuperacao('ABCDEFGHJK')).toBeUndefined();
  });

  it('recusa vazio e tamanho errado', () => {
    expect(erroCodigoRecuperacao(' ')).toBe('Informe um dos códigos de recuperação.');
    expect(erroCodigoRecuperacao('abcde')).toBe('O código de recuperação tem 10 letras e números.');
  });
});

describe('segredoEmGrupos', () => {
  it('separa em grupos de 4', () => {
    expect(segredoEmGrupos('JBSWY3DPEHPK3PXP')).toBe('JBSW Y3DP EHPK 3PXP');
    expect(segredoEmGrupos('ABCDE')).toBe('ABCD E');
  });
});
`````

`web/compartilhado/src/sessao/segundoFator.ts`:

`````ts
/** Regras puras da segunda etapa do login (código do aplicativo e de recuperação). */

/** Código do aplicativo sem espaços (o aplicativo mostra "123 456"). */
export function limparCodigo(codigo: string): string {
  return codigo.replace(/\s/g, '');
}

export function erroCodigo(codigo: string): string | undefined {
  const limpo = limparCodigo(codigo);
  if (!limpo) return 'Informe o código de 6 dígitos do aplicativo.';
  if (!/^\d{6}$/.test(limpo)) return 'O código tem 6 dígitos.';
  return undefined;
}

/** Código de recuperação: 10 letras e números, com ou sem o hífen do meio. */
export function erroCodigoRecuperacao(codigo: string): string | undefined {
  const limpo = codigo.replace(/[\s-]/g, '');
  if (!limpo) return 'Informe um dos códigos de recuperação.';
  if (!/^[a-zA-Z0-9]{10}$/.test(limpo)) return 'O código de recuperação tem 10 letras e números.';
  return undefined;
}

/** Segredo em grupos de 4 para digitar no aplicativo sem errar. */
export function segredoEmGrupos(segredo: string): string {
  return segredo.replace(/(.{4})(?=.)/g, '$1 ');
}
`````

`web/compartilhado/src/ui/useTokenDaUrl.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { semToken } from './useTokenDaUrl.ts';

describe('semToken', () => {
  it('tira só o token e mantém os outros parâmetros', () => {
    expect(semToken(new URLSearchParams('token=abc&origem=pagamento')).toString()).toBe(
      'origem=pagamento',
    );
  });

  it('sem token, não muda nada', () => {
    expect(semToken(new URLSearchParams('origem=pagamento')).toString()).toBe('origem=pagamento');
    expect(semToken(new URLSearchParams('')).toString()).toBe('');
  });
});
`````

`web/compartilhado/src/ui/useTokenDaUrl.ts`:

`````ts
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

/** Parâmetros da URL sem o `token` (os outros, como `origem`, ficam). */
export function semToken(parametros: URLSearchParams): URLSearchParams {
  const restantes = new URLSearchParams(parametros);
  restantes.delete('token');
  return restantes;
}

/**
 * Token de uso único que chega no link do e-mail (`?token=`). É lido uma vez e sai da barra de
 * endereço logo depois: não fica no histórico do navegador, em captura de tela nem no `Referer` de
 * um link aberto a partir da página.
 */
export function useTokenDaUrl(): string | null {
  const [parametros, setParametros] = useSearchParams();
  const [token] = useState(() => parametros.get('token'));

  useEffect(() => {
    if (parametros.has('token')) setParametros(semToken(parametros), { replace: true });
  }, [parametros, setParametros]);

  return token;
}
`````

`web/e2e/adm/usuarios.spec.ts`:

`````ts
import { ALUNO_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test('o admin bloqueia um aluno, que não entra mais, e desbloqueia', async ({
  comoAdmin: page,
  request,
}) => {
  const email = await alunoConfirmado(request);
  const entrar = () =>
    request.post(`${ALUNO_URL}/api/auth/login`, { data: { email, senha: SENHA_NOVA } });

  await page.goto('/usuarios');
  await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Nome ou e-mail' }).fill(email);
  const linha = page.getByRole('row').filter({ hasText: email });
  await expect(linha).toHaveCount(1);
  await expect(linha.getByText('Ativo')).toBeVisible();

  await linha.getByRole('button', { name: /^Bloquear / }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Bloquear' }).click();
  await expect(page.getByText('Conta bloqueada', { exact: true })).toBeVisible();
  await expect(linha.getByText('Bloqueado')).toBeVisible();
  expect((await entrar()).status()).toBe(401);

  await linha.getByRole('button', { name: /^Desbloquear / }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Desbloquear' }).click();
  await expect(linha.getByText('Ativo')).toBeVisible();
  expect((await entrar()).status()).toBe(200);
});

test('o admin não tem as ações na própria linha', async ({ comoAdmin: page }) => {
  await page.goto('/usuarios');
  await page.getByRole('textbox', { name: 'Nome ou e-mail' }).fill('admin@celke.com.br');
  const linha = page.getByRole('row').filter({ hasText: 'admin@celke.com.br' });
  await expect(linha).toHaveCount(1);
  await expect(linha.getByRole('button', { name: /^Bloquear / })).toHaveCount(0);
});
`````

`web/e2e/support/totp.ts`:

`````ts
import { createHmac } from 'node:crypto';
import { TOTP_SEED } from './config.ts';

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function decodificarBase32(texto: string): Buffer {
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const c of texto.replace(/[\s=]/g, '').toUpperCase()) {
    buffer = (buffer << 5) | BASE32.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** Código atual do aplicativo autenticador (TOTP, RFC 6238), como o celular calcula. */
export function codigoTotp(segredo = TOTP_SEED): string {
  const passo = Buffer.alloc(8);
  passo.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const hash = createHmac('sha1', decodificarBase32(segredo)).update(passo).digest();
  const i = hash[hash.length - 1]! & 0x0f;
  const binario = hash.readUInt32BE(i) & 0x7fffffff;
  return String(binario % 1_000_000).padStart(6, '0');
}
`````

### Dependências do front

Depois de gravar os arquivos, dentro de `web/`, rode `npm install` para instalar as dependências do `package.json` e gerar o `package-lock.json`.

## Antes de executar

Fase 18 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/seguranca
```

## Verificação

- `./mvnw verify` e as jornadas `-Pe2e`; no `web/`, format, lint, typecheck, test, build e `npm run e2e`.
- Na stack de e2e: o admin entra com o código do aplicativo (chave `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP`);
  em Segurança → Usuários, bloquear um aluno o desconecta na hora; a compra pede "Ir para o pagamento".
- `docker compose ... exec postgres psql -U plataforma_cursos_app plataforma_cursos -c 'DROP TABLE cursos'`
  é recusado.

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md
git commit -m "feat(auth): exigir segundo fator dos administradores e reduzir privilégios"
git push -u origin feature/seguranca
```

Abra o Pull Request de `feature/seguranca` para `homolog`.
