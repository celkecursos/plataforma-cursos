# Base dos fronts: aluno e ADM

**Fase:** 11
**Branch:** `feature/web-base`

## Prompt

Vamos criar os fronts da plataforma em `web/`: a área do aluno e o ADM, em React com Vite e
TypeScript, usando a biblioteca `@celke/kole-react`. Siga o `backend/CLAUDE.md` para o que tocar no
backend. Não faça commit.

O que esta etapa entrega:

- **Pacote `web/`** com `aluno` e `adm` (Vite + React + TypeScript, Node 24), um pacote só, sem
  workspaces; `@celke/kole-react` do GitHub com commit fixo.
- **Cliente HTTP** (`compartilhado`): access token só em memória, refresh silencioso pelo cookie
  `HttpOnly`, uma renovação só para vários 401 ao mesmo tempo, erros da API (ProblemDetail) prontos para
  a tela.
- **Sessão e rotas** protegidas por papel; destino seguro depois do login.
- **Aluno:** login, cadastro com validação, confirmação de e-mail, recuperar e redefinir senha.
  **ADM:** login e o layout `Estrutura`.
- **Proxy do Vite** `/api → localhost:8080`: mesma origem também no desenvolvimento.
- **Imagem `web`**: build dos fronts e um Caddy servindo `/srv/aluno` (8081) e `/srv/adm` (8082), com
  CSP (mídia em `MIDIA_ORIGEM`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no
  `index.html`; o Caddy da VPS manda `/api` para a API e o resto para ela.
- **Testes:** Vitest + Testing Library; Playwright no pipeline (login do aluno e do admin, senha errada,
  cadastro confirmado pelo Mailpit, recuperação de senha, links inválidos, aluno barrado no ADM, sessão
  por refresh, refresh reutilizado, logout).

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

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

      # A transcodificação de vídeo é testada com o ffmpeg de verdade
      - name: Instalar ffmpeg
        run: sudo apt-get update -q && sudo apt-get install -y -q --no-install-recommends ffmpeg

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
          tags: plataforma-cursos-web:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web
          cache-to: type=gha,mode=max,scope=web

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

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker web; do
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

`backend/src/e2e/java/br/com/celke/cursos/e2e/proxy/ProxyE2E.java`:

`````java
package br.com.celke.cursos.e2e.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** O que o Caddy garante na frente da API: headers, rotas expostas e o IP real do cliente. */
class ProxyE2E {

    @Test
    @DisplayName("health check passa pelo Caddy com os headers de segurança e sem identificar o servidor")
    void headersDeSeguranca() {
        var resposta =
                Api.aluno().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));

        resposta.header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Cross-Origin-Resource-Policy", "same-site")
                .header("Strict-Transport-Security", "max-age=31536000");
        assertThat(resposta.extract().header("Server")).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/actuator/env", "/actuator/metrics", "/"})
    @DisplayName("fora de /api e do health check, nada chega à API: quem responde é o front")
    void rotasForaDaApi(String rota) {
        // O container web devolve o index.html do app (o React Router decide a página)
        Api.aluno()
                .get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("id=\"raiz\""));
        Api.adm()
                .get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("id=\"raiz\""));
    }

    @Test
    @DisplayName("os fronts saem com Content-Security-Policy e o index.html sempre revalidado")
    void cabecalhosDosFronts() {
        for (var cliente : List.of(Api.aluno(), Api.adm())) {
            cliente.get("/")
                    .then()
                    .statusCode(200)
                    .header("Content-Security-Policy", containsString("default-src 'self'"))
                    .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                    .header("Cache-Control", "no-cache")
                    .header("Strict-Transport-Security", "max-age=31536000");
        }
    }

    @Test
    @DisplayName("aluno e ADM são apps diferentes")
    void appsSeparados() {
        Api.aluno().get("/").then().body(containsString("Área do aluno"));
        Api.adm().get("/").then().body(containsString("Administração"));
    }

    @Test
    @DisplayName("X-Forwarded-For forjado pelo cliente não muda o IP visto pela API")
    void xForwardedForForjado() {
        // Cada requisição finge vir de um IP diferente. Se a API acreditasse, cada uma cairia num
        // balde novo do limite por IP; como o Caddy substitui o cabeçalho, todas gastam o mesmo balde.
        int primeira = restanteNoLimite("203.0.113.1");
        restanteNoLimite("203.0.113.2");
        int terceira = restanteNoLimite("203.0.113.3");

        assertThat(terceira).isLessThan(primeira);
    }

    private static int restanteNoLimite(String ipForjado) {
        String restante = Api.aluno()
                .header("X-Forwarded-For", ipForjado)
                .body("{\"email\":\"%s\",\"senha\":\"qualquer-senha\"}".formatted(Dados.email()))
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .extract()
                .header("X-RateLimit-Remaining");
        return Integer.parseInt(restante);
    }
}
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
container `web` (área do aluno na porta 8081, ADM na 8082).

| Host | Ambiente | Acesso |
|---|---|---|
| `aluno.celke.uno` | produção | público |
| `adm.celke.uno` | produção | só `ADM_IPS` |
| `media.celke.uno` | produção | público (URLs assinadas) |
| `homolog-aluno.celke.uno`, `homolog-adm.celke.uno`, `homolog-media.celke.uno` | homologação | só `HOMOLOG_IPS`, `noindex`, com Swagger |

## 1. Contratar a VPS na Hostinger

1. Contrate a VPS pelo link https://celke.com.br/page/hostinger e use o cupom **CELKE** no pagamento.
   Recomendado: plano **KVM 4** (4 vCPU, 16 GB de RAM, 200 GB de disco NVMe), com folga para a
   produção e a homologação na mesma VPS e para a conversão dos vídeos. Sistema **Ubuntu 24.04**, sem
   painel de controle; defina uma senha forte para o root (ela só vale até o passo 4).
2. No painel da Hostinger, anote o IP da VPS.
3. Firewall da VPS: libere só 22, 80 e 443 (TCP) e 443 (UDP). O `ufw` faz o mesmo dentro da VPS; o
   painel é a segunda camada.
4. Ative o backup/snapshot semanal do painel (não substitui o backup fora da VPS da fase de operação).

## 2. DNS de celke.uno

Crie registros **A** apontando para o IP da VPS (só subdomínios; o domínio principal continua com as
outras aplicações):

```
aluno           A   <ip-da-vps>
adm             A   <ip-da-vps>
media           A   <ip-da-vps>
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
homologação nunca vale na produção.

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
curl -s https://aluno.celke.uno/actuator/health   # {"groups":[...],"status":"UP"}
```

O `Caddyfile` anterior fica em `caddy/Caddyfile.bak-<data>`; para voltar, copie-o por cima do
`Caddyfile` e rode de novo o `docker compose ... up -d --force-recreate`. Sem SSH do seu computador (só
o terminal do navegador da Hostinger, como root), o repositório privado não pode ser baixado na VPS:
edite `/opt/plataforma-cursos/caddy/Caddyfile` lá mesmo, igual ao `infra/caddy/Caddyfile` da `main`, e
rode o mesmo `docker compose` com `sudo -u deploy`.

Quando a mudança do Caddy depende de um container novo (como na Fase 11, que passou a mandar as
páginas para o `web`), atualize o Caddy **depois** do deploy dos dois ambientes com esse container: antes
disso o Caddy novo não teria para onde mandar as páginas (502), enquanto o antigo continua respondendo
404 fora da API.

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

`infra/caddy/Caddyfile`:

`````caddyfile
# Porta de entrada da plataforma. Um Caddy só atende os dois ambientes da VPS: cada site está em
# sites/<ambiente>.caddy e chega à API, aos fronts e ao Garage do seu ambiente pelos apelidos
# <ambiente>-api, <ambiente>-web e <ambiente>-garage. Com "https://..." o Caddy emite o certificado
# sozinho; com "http://..." atende sem TLS (e2e).
{
	admin off
	email {$ACME_EMAIL:nao-configurado@celke.uno}
}

# Headers de segurança em todas as respostas
(seguranca) {
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		# Respostas só podem ser carregadas por páginas do mesmo site (aluno, adm e mídia são subdomínios dele)
		Cross-Origin-Resource-Policy same-site
		Strict-Transport-Security "max-age=31536000"
		-Server
	}
}

# Um front (aluno ou ADM) com a sua API. Só a API e o health check chegam à API; o resto (Swagger,
# outros endpoints do Actuator) nunca: vai para o container web, que serve o app React.
# O X-Forwarded-For que o cliente mandar é substituído pelo IP real da conexão.
# Argumentos: o ambiente (producao, homolog, e2e) e a porta do app no container web (8081 aluno, 8082 ADM).
(app) {
	@api path /api/* /actuator/health /actuator/health/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			# Durante o deploy a API reinicia: segura a requisição em vez de responder 502
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:{args[1]} {
			lb_try_duration 30s
		}
	}
}

# Como (app), mas também com a documentação (Swagger): só na homologação
(app_com_docs) {
	@api path /api/* /actuator/health /actuator/health/* /v3/api-docs /v3/api-docs/* /swagger-ui.html /swagger-ui/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:{args[1]} {
			lb_try_duration 30s
		}
	}
}

# Arquivos do bucket por URL pré-assinada (o host faz parte da assinatura e é repassado como veio)
(midia) {
	reverse_proxy {args[0]}-garage:3900
}

# Painel administrativo: só os IPs de ADM_IPS (separados por espaço)
(somente_ips_adm) {
	@fora not remote_ip {$ADM_IPS}
	respond @fora "Acesso restrito." 403
}

# Homologação: só os IPs de HOMOLOG_IPS e fora dos buscadores
(homolog) {
	header X-Robots-Tag "noindex, nofollow"
	@fora not remote_ip {$HOMOLOG_IPS}
	respond @fora "Acesso restrito." 403
}

import sites/*.caddy
`````

`infra/caddy/e2e.caddy`:

`````caddyfile
# Sites da stack de e2e (montado em /etc/caddy/sites só nela): HTTP em *.localhost
http://aluno.localhost {
	import seguranca
	import app e2e 8081
}

http://adm.localhost {
	import seguranca
	import app e2e 8082
}

http://media.localhost {
	import seguranca
	import midia e2e
}
`````

`infra/caddy/sites/homolog.caddy`:

`````caddyfile
# Homologação: restrita por IP, fora dos buscadores e com o Swagger
{$HOMOLOG_HOST_ALUNO} {
	import seguranca
	import homolog
	import app_com_docs homolog 8081
}

{$HOMOLOG_HOST_ADM} {
	import seguranca
	import homolog
	import app_com_docs homolog 8082
}

{$HOMOLOG_HOST_MEDIA} {
	import seguranca
	import homolog
	import midia homolog
}
`````

`infra/caddy/sites/producao.caddy`:

`````caddyfile
# Produção
{$PRODUCAO_HOST_ALUNO} {
	import seguranca
	import app producao 8081
}

{$PRODUCAO_HOST_ADM} {
	import seguranca
	import somente_ips_adm
	import app producao 8082
}

{$PRODUCAO_HOST_MEDIA} {
	import seguranca
	import midia producao
}
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

  web:
    image: plataforma-cursos-web:e2e
    build:
      context: ../web

  api:
    image: plataforma-cursos-api:e2e
    build:
      context: ../backend
      target: api
    environment:
      SPRING_PROFILES_ACTIVE: api,e2e
      SEED_E2E_SENHA: ${SEED_E2E_SENHA:?defina SEED_E2E_SENHA}
      # Todas as jornadas saem do mesmo IP
      APP_LIMITES_REQUISICOES_POR_IP_POR_MINUTO: "300"
      # URLs dos segmentos vencem em segundos: a jornada de entrega confere a expiração
      APP_VIDEO_MARGEM_PLAYLIST: PT5S

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
# Stack de um ambiente dos servidores (produção ou homologação): API, worker, fronts (web),
# PostgreSQL e Garage.
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
  # Alertas do worker por e-mail (jobs com erro, disco quase cheio); em branco, desligados
  ALERTA_EMAIL: ${ALERTA_EMAIL:-}

# Logs do Docker com rotação: no máximo 5 arquivos de 10 MB por container
x-logging: &logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  api:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
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
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
    restart: unless-stopped
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx768m
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
    image: ${IMAGEM_WEB:-ghcr.io/celkecursos/plataforma-cursos-web}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      # Origem dos vídeos e arquivos na Content-Security-Policy dos fronts
      MIDIA_ORIGEM: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-web
    deploy:
      resources:
        limits:
          memory: 64M

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
`````

### Arquivos novos

`.gitattributes`:

`````text
# Arquivos que vão para a VPS (scripts, Caddyfile, .env, compose, garage.toml) sempre com LF, mesmo
# no checkout do Windows: com CRLF o bash falha ("$'\r': command not found") e os valores do .env
# ganham um \r no fim.
infra/** text eol=lf
*.sh text eol=lf
# Frontends: o Prettier exige LF
web/** text=auto eol=lf
`````

`web/.dockerignore`:

`````text
node_modules
dist
playwright-report
test-results
Dockerfile
.dockerignore
`````

`web/.gitignore`:

`````text
dist/
node_modules/
playwright-report/
test-results/
*.tsbuildinfo
`````

`web/.prettierignore`:

`````text
dist
node_modules
playwright-report
test-results
package-lock.json
`````

`web/.prettierrc.json`:

`````json
{
  "singleQuote": true,
  "printWidth": 100,
  "trailingComma": "all"
}
`````

`web/Caddyfile`:

`````caddyfile
# Caddy interno do container web: só arquivos estáticos, sem TLS (o TLS e os headers comuns ficam no
# Caddy da VPS). MIDIA_ORIGEM é a origem pública do Garage (vídeos, capas, arquivos de apoio).
{
	admin off
	auto_https off
}

# Argumento: a pasta do app (aluno ou adm)
(spa) {
	root * /srv/{args[0]}
	encode zstd gzip
	header {
		-Server
		Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: {$MIDIA_ORIGEM}; media-src 'self' blob: {$MIDIA_ORIGEM}; connect-src 'self' {$MIDIA_ORIGEM}; font-src 'self' data:; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
		X-Frame-Options DENY
	}

	# Arquivos com hash no nome: nunca mudam
	handle /assets/* {
		header Cache-Control "public, max-age=31536000, immutable"
		file_server
	}

	# Qualquer outra rota é do React Router: devolve o index.html, sempre revalidado
	handle {
		header Cache-Control "no-cache"
		try_files {path} /index.html
		file_server
	}
}

:8081 {
	import spa aluno
}

:8082 {
	import spa adm
}
`````

`web/Dockerfile`:

`````dockerfile
# Frontends estáticos (aluno e ADM) servidos por um Caddy interno. O Caddy da VPS manda /api para a
# API e o resto para este container: aluno na porta 8081, ADM na 8082.

FROM node:24.11-bookworm-slim AS build
RUN apt-get update -q && apt-get install -y -q --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# O lock guarda o kole-react como git+ssh; o repositório é público e é baixado por HTTPS
RUN git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM caddy:2.11.7-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist/aluno /srv/aluno
COPY --from=build /web/dist/adm /srv/adm
EXPOSE 8081 8082
HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
    CMD wget -q --spider http://127.0.0.1:8081/ && wget -q --spider http://127.0.0.1:8082/ || exit 1
`````

`web/adm/index.html`:

`````html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="robots" content="noindex" />
    <title>Administração — Plataforma de Cursos</title>
  </head>
  <body>
    <div id="raiz"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
`````

`web/adm/src/caminhos.ts`:

`````ts
export const CAMINHOS = {
  entrar: '/entrar',
  inicio: '/',
} as const;
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

`web/adm/src/layout/SemPermissao.tsx`:

`````tsx
import { Botao } from '@celke/kole-react';
import { useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../caminhos.ts';

/** Sessão válida, mas sem o papel de administrador (ou rota inexistente). */
export function SemPermissao({
  titulo = 'Acesso restrito a administradores',
}: {
  titulo?: string;
}) {
  const { sair } = useSessao();
  const navigate = useNavigate();
  return (
    <LayoutConta marca="Plataforma de Cursos — Administração" titulo={titulo}>
      <Botao
        rotulo="Entrar com outra conta"
        icone="log-out"
        onClick={() => void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }))}
      />
    </LayoutConta>
  );
}
`````

`web/adm/src/main.tsx`:

`````tsx
import '@celke/kole-react/estilos.css';
import '@compartilhado/ui/estilos.css';
import '@compartilhado/ui/icones.ts';
import { ProvedorUi } from '@celke/kole-react';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { ProvedorSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { roteador } from './rotas.tsx';

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <ProvedorUi>
      <ProvedorSessao>
        <RouterProvider router={roteador} />
      </ProvedorSessao>
    </ProvedorUi>
  </StrictMode>,
);
`````

`web/adm/src/marca.ts`:

`````ts
import type { Marca } from '@celke/kole-react';

export const MARCA: Marca = { nome: 'Plataforma de Cursos', subtitulo: 'Administração', href: '/' };
`````

`web/adm/src/paginas/EntrarPagina.tsx`:

`````tsx
import { TelaLogin } from '@celke/kole-react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { MARCA } from '../marca.ts';

/** Login do ADM. Quem entra sem o papel de administrador sai na hora, com o aviso. */
export function EntrarPagina() {
  const { estado, entrar, sair } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));

  if (estado.status === 'autenticado' && estado.usuario.papeis.includes('ADMIN')) {
    return <Navigate to={destino} replace />;
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
          const conta = await entrar(usuario.trim(), senha);
          if (!conta.papeis.includes('ADMIN')) {
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

`web/adm/src/paginas/PainelPagina.tsx`:

`````tsx
import { BoasVindas } from '@celke/kole-react';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';

export function PainelPagina() {
  const { estado } = useSessao();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';
  return <BoasVindas nome={nome} />;
}
`````

`web/adm/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAdm } from './layout/LayoutAdm.tsx';
import { SemPermissao } from './layout/SemPermissao.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { PainelPagina } from './paginas/PainelPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar} papel="ADMIN" semPermissao={<SemPermissao />}>
        <LayoutAdm />
      </RotaPrivada>
    ),
    children: [{ index: true, element: <PainelPagina /> }],
  },
  { path: '*', element: <SemPermissao titulo="Página não encontrada" /> },
]);
`````

`web/adm/vite.config.ts`:

`````ts
import { configurar } from '../vite.base.ts';

export default configurar('adm', 5174);
`````

`web/aluno/index.html`:

`````html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="robots" content="noindex" />
    <title>Área do aluno — Plataforma de Cursos</title>
  </head>
  <body>
    <div id="raiz"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
`````

`web/aluno/src/caminhos.ts`:

`````ts
/** Caminhos da área do aluno; os de conta são os mesmos dos links que a API manda por e-mail. */
export const CAMINHOS = {
  entrar: '/entrar',
  cadastro: '/cadastro',
  confirmarEmail: '/confirmar-email',
  recuperarSenha: '/recuperar-senha',
  redefinirSenha: '/redefinir-senha',
  inicio: '/',
} as const;
`````

`web/aluno/src/layout/GuardaTermos.tsx`:

`````tsx
import { Alerta, Botao, CabecalhoPagina, Card, Carregando, CampoMarcacao } from '@celke/kole-react';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';

export interface Termo {
  id: number;
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

const NOMES: Record<Termo['tipo'], string> = {
  TERMOS_USO: 'Termos de uso',
  POLITICA_PRIVACIDADE: 'Política de privacidade',
};

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'pendentes'; termos: Termo[] }
  | { tipo: 'livre' }
  | { tipo: 'erro'; mensagem: string };

/**
 * A API bloqueia a área do aluno enquanto houver termo vigente sem aceite (primeiro acesso e cada
 * versão nova). Aqui o aluno lê e aceita antes de seguir. O HTML dos termos já vem filtrado pela API.
 */
export function GuardaTermos({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' });
  const [concordo, setConcordo] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string>();

  const carregar = useCallback(() => {
    api
      .get<Termo[]>('/api/termos/pendentes')
      .then((termos) =>
        setEstado(termos.length ? { tipo: 'pendentes', termos } : { tipo: 'livre' }),
      )
      .catch((falha: unknown) => setEstado({ tipo: 'erro', mensagem: mensagemDeErro(falha) }));
  }, []);

  useEffect(carregar, [carregar]);

  if (estado.tipo === 'carregando') return <Carregando mensagem="Carregando..." />;
  if (estado.tipo === 'erro')
    return (
      <Alerta severidade="err" titulo="Não foi possível carregar" mensagem={estado.mensagem} />
    );
  if (estado.tipo === 'livre') return <>{children}</>;

  const aceitar = async () => {
    if (!concordo) {
      setErro('Marque que leu e concorda para continuar.');
      return;
    }
    setEnviando(true);
    setErro(undefined);
    try {
      for (const termo of estado.termos) {
        await api.post(`/api/termos/${termo.id}/aceite`);
      }
      setEstado({ tipo: 'livre' });
    } catch (falha) {
      setErro(mensagemDeErro(falha));
      carregar();
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Termos atualizados" icone="file-text" />
      <Alerta
        severidade="info"
        mensagem="Para continuar usando a plataforma, leia e aceite os termos abaixo."
      />
      {estado.termos.map((termo) => (
        <Card key={termo.id} titulo={`${NOMES[termo.tipo]} (versão ${termo.versao})`}>
          <div dangerouslySetInnerHTML={{ __html: termo.conteudoHtml }} />
        </Card>
      ))}
      <CampoMarcacao
        rotulo="Li e concordo com os termos acima"
        valor={concordo}
        aoMudar={setConcordo}
        erro={erro}
      />
      <div>
        <Botao
          rotulo="Aceitar e continuar"
          icone="check"
          carregando={enviando}
          onClick={() => void aceitar()}
        />
      </div>
    </div>
  );
}
`````

`web/aluno/src/layout/LayoutAluno.tsx`:

`````tsx
import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';
import { GuardaTermos } from './GuardaTermos.tsx';

const NAV: SecaoNav[] = [
  { itens: [{ rotulo: 'Meus cursos', icone: 'graduation-cap', rota: CAMINHOS.inicio }] },
];

/** Moldura da área logada do aluno: menu, usuário e, antes do conteúdo, o aceite dos termos vigentes. */
export function LayoutAluno() {
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
      usuario={{ nome, cargo: 'Aluno' }}
      menuUsuario={[{ id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true }]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <GuardaTermos>
        <Outlet />
      </GuardaTermos>
    </Estrutura>
  );
}
`````

`web/aluno/src/main.tsx`:

`````tsx
import '@celke/kole-react/estilos.css';
import '@compartilhado/ui/estilos.css';
import '@compartilhado/ui/icones.ts';
import { ProvedorUi } from '@celke/kole-react';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { ProvedorSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { roteador } from './rotas.tsx';

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <ProvedorUi>
      <ProvedorSessao>
        <RouterProvider router={roteador} />
      </ProvedorSessao>
    </ProvedorUi>
  </StrictMode>,
);
`````

`web/aluno/src/marca.ts`:

`````ts
import type { Marca } from '@celke/kole-react';

export const MARCA: Marca = { nome: 'Plataforma de Cursos', subtitulo: 'Área do aluno', href: '/' };
`````

`web/aluno/src/paginas/CadastroPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoEmail, CampoSenha, CampoTexto } from '@celke/kole-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import {
  erroConfirmacao,
  erroEmail,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Erros = Partial<Record<'nome' | 'email' | 'senha' | 'confirmacao', string>>;

/** Cadastro do aluno: a conta só entra depois de confirmar o e-mail (link enviado pela API). */
export function CadastroPagina() {
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
        <Link className="pc-link" to={CAMINHOS.entrar}>
          Voltar para a entrada
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
          Já tem conta? <Link to={CAMINHOS.entrar}>Entrar</Link>
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
        dica="Pelo menos 10 caracteres."
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
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Estado = { tipo: 'confirmando' } | { tipo: 'confirmado' } | { tipo: 'erro'; mensagem: string };

/** Destino do link enviado no cadastro: confirma o e-mail e, se o link venceu, permite pedir outro. */
export function ConfirmarEmailPagina() {
  const [parametros] = useSearchParams();
  const token = parametros.get('token');
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
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao="Acesse seus cursos, aulas e certificados."
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
          Ainda não tem conta? <Link to={CAMINHOS.cadastro}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          await entrar(usuario.trim(), senha);
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}
`````

`web/aluno/src/paginas/MeusCursosPagina.tsx`:

`````tsx
import { Alerta, CabecalhoPagina, Card, Carregando, Status, Vazio } from '@celke/kole-react';
import { useEffect, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';

export interface MeuCurso {
  matriculaId: number;
  turmaId: number;
  cursoTitulo: string;
  cursoSlug: string;
  turmaNome: string;
  capaUrl: string | null;
  inicio: string;
  fim: string | null;
  situacao: 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
  arquivada: boolean;
}

/** Cursos do aluno (aba de ativos). */
export function MeusCursosPagina() {
  const [cursos, setCursos] = useState<MeuCurso[] | null>(null);
  const [erro, setErro] = useState<string>();

  useEffect(() => {
    api
      .get<MeuCurso[]>('/api/aluno/cursos')
      .then(setCursos)
      .catch((falha: unknown) => setErro(mensagemDeErro(falha)));
  }, []);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meus cursos" icone="graduation-cap" />
      {erro && (
        <Alerta severidade="err" titulo="Não foi possível carregar seus cursos" mensagem={erro} />
      )}
      {!erro && cursos === null && <Carregando mensagem="Carregando seus cursos..." />}
      {cursos?.length === 0 && (
        <Vazio
          titulo="Você ainda não tem cursos"
          descricao="Quando a sua matrícula for confirmada, o curso aparece aqui."
          icone="book-open"
        />
      )}
      {!!cursos?.length && (
        <div className="pc-grade-cards">
          {cursos.map((curso) => (
            <Card key={curso.matriculaId} titulo={curso.cursoTitulo} subtitulo={curso.turmaNome}>
              <Status
                rotulo={curso.situacao === 'VIGENTE' ? 'Em andamento' : 'Começa em breve'}
                severidade={curso.situacao === 'VIGENTE' ? 'ok' : 'info'}
              />
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
`````

`web/aluno/src/paginas/NaoEncontradaPagina.tsx`:

`````tsx
import { Link } from 'react-router';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../caminhos.ts';

export function NaoEncontradaPagina() {
  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Página não encontrada"
      descricao="O endereço não existe ou mudou."
    >
      <Link className="pc-link" to={CAMINHOS.inicio}>
        Ir para meus cursos
      </Link>
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/RecuperarSenhaPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoEmail } from '@celke/kole-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

/** Pede o link de nova senha. A resposta é a mesma exista ou não a conta (não revela e-mails). */
export function RecuperarSenhaPagina() {
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
        (await api.post<Mensagem>('/api/auth/recuperar-senha', { email: email.trim() })).mensagem,
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
      titulo="Recuperar senha"
      descricao="Informe o e-mail da conta. Enviaremos um link para criar uma nova senha."
      aoEnviar={resposta ? undefined : enviar}
      rodape={<Link to={CAMINHOS.entrar}>Voltar para a entrada</Link>}
    >
      {resposta ? (
        <Alerta severidade="ok" mensagem={resposta} />
      ) : (
        <>
          <CampoEmail
            rotulo="E-mail"
            valor={email}
            aoMudar={setEmail}
            erro={erro}
            obrigatorio
            autoComplete="email"
          />
          <Botao type="submit" rotulo="Enviar link" icone="mail" carregando={enviando} bloco />
        </>
      )}
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/RedefinirSenhaPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoSenha } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { erroConfirmacao, erroSenhaNova, semErros } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

/** Destino do link de recuperação: cria a nova senha (e encerra as sessões abertas). */
export function RedefinirSenhaPagina() {
  const [parametros] = useSearchParams();
  const token = parametros.get('token');
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
      <LayoutConta marca="Plataforma de Cursos" titulo="Senha alterada">
        <Alerta
          severidade="ok"
          mensagem="Sua nova senha já vale. Por segurança, você saiu de todos os dispositivos."
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
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Criar nova senha"
      aoEnviar={enviar}
      rodape={<Link to={CAMINHOS.recuperarSenha}>Pedir um novo link</Link>}
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoSenha
        rotulo="Nova senha"
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres."
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
      <Botao type="submit" rotulo="Salvar nova senha" icone="save" carregando={enviando} bloco />
    </LayoutConta>
  );
}
`````

`web/aluno/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAluno } from './layout/LayoutAluno.tsx';
import { CadastroPagina } from './paginas/CadastroPagina.tsx';
import { ConfirmarEmailPagina } from './paginas/ConfirmarEmailPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { MeusCursosPagina } from './paginas/MeusCursosPagina.tsx';
import { NaoEncontradaPagina } from './paginas/NaoEncontradaPagina.tsx';
import { RecuperarSenhaPagina } from './paginas/RecuperarSenhaPagina.tsx';
import { RedefinirSenhaPagina } from './paginas/RedefinirSenhaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  { path: CAMINHOS.cadastro, element: <CadastroPagina /> },
  { path: CAMINHOS.confirmarEmail, element: <ConfirmarEmailPagina /> },
  { path: CAMINHOS.recuperarSenha, element: <RecuperarSenhaPagina /> },
  { path: CAMINHOS.redefinirSenha, element: <RedefinirSenhaPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <LayoutAluno />
      </RotaPrivada>
    ),
    children: [{ index: true, element: <MeusCursosPagina /> }],
  },
  { path: '*', element: <NaoEncontradaPagina /> },
]);
`````

`web/aluno/vite.config.ts`:

`````ts
import { configurar } from '../vite.base.ts';

export default configurar('aluno', 5173);
`````

`web/compartilhado/src/api/cliente.test.ts`:

`````ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ClienteApi } from './cliente.ts';
import { ErroApi } from './erros.ts';
import type { Sessao } from './tipos.ts';

function sessao(token: string): Sessao {
  return {
    accessToken: token,
    tipo: 'Bearer',
    expiraEm: '2026-01-01T00:10:00Z',
    usuario: { id: 1, nome: 'Ana', email: 'ana@celke.uno', papeis: ['ALUNO'] },
  };
}

function json(status: number, corpo: unknown, cabecalhos: Record<string, string> = {}) {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json', ...cabecalhos },
  });
}

describe('ClienteApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('manda o token no cabeçalho e o corpo como JSON', async () => {
    const api = new ClienteApi();
    api.definirSessao(sessao('t1'));
    fetchMock.mockResolvedValueOnce(json(201, { id: 9 }));

    await expect(api.post('/api/x', { nome: 'a' })).resolves.toEqual({ id: 9 });

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/x');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>)['Authorization']).toBe('Bearer t1');
    expect(init?.body).toBe('{"nome":"a"}');
  });

  it('no 401 renova a sessão uma vez e repete a requisição com o token novo', async () => {
    const api = new ClienteApi();
    api.definirSessao(sessao('velho'));
    fetchMock
      .mockResolvedValueOnce(json(401, { detail: 'Token expirado.' }))
      .mockResolvedValueOnce(json(200, sessao('novo')))
      .mockResolvedValueOnce(json(200, { ok: true }));

    await expect(api.get('/api/aluno/cursos')).resolves.toEqual({ ok: true });

    expect(fetchMock.mock.calls[1]![0]).toBe('/api/auth/refresh');
    const repetida = fetchMock.mock.calls[2]![1]?.headers as Record<string, string>;
    expect(repetida['Authorization']).toBe('Bearer novo');
  });

  it('dois 401 ao mesmo tempo esperam a mesma renovação (o refresh token não é reutilizado)', async () => {
    const api = new ClienteApi();
    api.definirSessao(sessao('velho'));
    let renovacoes = 0;
    fetchMock.mockImplementation(async (url, init) => {
      if (url === '/api/auth/refresh') {
        renovacoes++;
        return json(200, sessao('novo'));
      }
      const auth = (init?.headers as Record<string, string>)['Authorization'];
      return auth === 'Bearer novo' ? json(200, { url }) : json(401, {});
    });

    await Promise.all([api.get('/api/a'), api.get('/api/b'), api.get('/api/c')]);

    expect(renovacoes).toBe(1);
  });

  it('renovação recusada encerra a sessão e devolve o erro original', async () => {
    const api = new ClienteApi();
    const ouvinte = vi.fn();
    api.definirSessao(sessao('velho'));
    api.aoMudarSessao(ouvinte);
    fetchMock
      .mockResolvedValueOnce(json(401, { detail: 'Token expirado.' }))
      .mockResolvedValueOnce(json(401, { detail: 'Sessão inválida.' }));

    await expect(api.get('/api/aluno/cursos')).rejects.toMatchObject({ status: 401 });
    expect(ouvinte).toHaveBeenCalledWith(null);
    expect(api.autenticado).toBe(false);
  });

  it('sem sessão, o 401 não tenta renovar', async () => {
    const api = new ClienteApi();
    fetchMock.mockResolvedValueOnce(json(401, { detail: 'Autenticação necessária.' }));

    await expect(api.get('/api/aluno/cursos')).rejects.toBeInstanceOf(ErroApi);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('erro da API vira ErroApi com o detail, os campos e o Retry-After', async () => {
    const api = new ClienteApi();
    fetchMock.mockResolvedValueOnce(
      json(
        400,
        { detail: 'Dados inválidos.', erros: [{ campo: 'email', mensagem: 'inválido' }] },
        { 'Retry-After': '30' },
      ),
    );

    const erro = await api.post('/api/auth/cadastro', {}).catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(ErroApi);
    expect((erro as ErroApi).message).toBe('Dados inválidos.');
    expect((erro as ErroApi).erroDoCampo('email')).toBe('inválido');
    expect((erro as ErroApi).tentarEm).toBe(30);
  });

  it('falha de rede vira ErroApi com status 0 e mensagem de conexão', async () => {
    const api = new ClienteApi();
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    const erro = (await api.get('/api/x').catch((e: unknown) => e)) as ErroApi;

    expect(erro.status).toBe(0);
    expect(erro.message).toContain('Sem conexão');
  });

  it('204 devolve undefined', async () => {
    const api = new ClienteApi();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(api.delete('/api/x')).resolves.toBeUndefined();
  });
});
`````

`web/compartilhado/src/api/cliente.ts`:

`````ts
import { ErroApi } from './erros.ts';
import type { Sessao } from './tipos.ts';

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface OpcoesRequisicao {
  corpo?: unknown;
  sinal?: AbortSignal;
  /** Não tenta renovar a sessão num 401 (login, refresh e logout). */
  semRenovar?: boolean;
}

/**
 * Cliente HTTP da API, na mesma origem do front (o Caddy, ou o proxy do Vite, leva `/api` à API).
 *
 * - O access token fica **só em memória** (nunca em localStorage): some ao fechar a aba.
 * - O refresh token é o cookie `HttpOnly` que o navegador manda sozinho para `/api/auth`.
 * - Num 401, renova a sessão uma vez e repete a requisição. Várias requisições que recebem 401 ao
 *   mesmo tempo esperam **a mesma** renovação: renovar em paralelo reutilizaria o refresh token, e o
 *   backend revogaria a sessão inteira por suspeita de roubo.
 */
export class ClienteApi {
  private token: string | null = null;
  private renovacao: Promise<Sessao | null> | null = null;
  private ouvintesSessao = new Set<(sessao: Sessao | null) => void>();

  /** Avisado a cada sessão nova (login ou renovação) e quando ela termina (`null`). */
  aoMudarSessao(ouvinte: (sessao: Sessao | null) => void): () => void {
    this.ouvintesSessao.add(ouvinte);
    return () => this.ouvintesSessao.delete(ouvinte);
  }

  definirSessao(sessao: Sessao | null): void {
    this.token = sessao?.accessToken ?? null;
    this.ouvintesSessao.forEach((ouvinte) => ouvinte(sessao));
  }

  get autenticado(): boolean {
    return this.token !== null;
  }

  get<T>(caminho: string, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('GET', caminho, opcoes);
  }

  post<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('POST', caminho, { ...opcoes, corpo });
  }

  put<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('PUT', caminho, { ...opcoes, corpo });
  }

  patch<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('PATCH', caminho, { ...opcoes, corpo });
  }

  delete<T>(caminho: string, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('DELETE', caminho, opcoes);
  }

  /**
   * Renova a sessão pelo cookie de refresh (também usado ao abrir o app, para retomar a sessão).
   * Devolve a sessão nova, ou `null` se não há sessão válida.
   */
  renovar(): Promise<Sessao | null> {
    this.renovacao ??= this.post<Sessao>('/api/auth/refresh', undefined, { semRenovar: true })
      .then((sessao) => {
        this.definirSessao(sessao);
        return sessao;
      })
      .catch(() => {
        this.definirSessao(null);
        return null;
      })
      .finally(() => {
        this.renovacao = null;
      });
    return this.renovacao;
  }

  async requisitar<T>(metodo: Metodo, caminho: string, opcoes: OpcoesRequisicao = {}): Promise<T> {
    let resposta = await this.enviar(metodo, caminho, opcoes);
    if (resposta.status === 401 && !opcoes.semRenovar && this.token !== null) {
      const sessao = await this.renovar();
      if (sessao === null) throw await this.erro(resposta);
      resposta = await this.enviar(metodo, caminho, opcoes);
    }
    if (!resposta.ok) throw await this.erro(resposta);
    if (resposta.status === 204) return undefined as T;
    const tipo = resposta.headers.get('Content-Type') ?? '';
    return (tipo.includes('json') ? await resposta.json() : await resposta.text()) as T;
  }

  private async enviar(
    metodo: Metodo,
    caminho: string,
    opcoes: OpcoesRequisicao,
  ): Promise<Response> {
    const cabecalhos: Record<string, string> = { Accept: 'application/json' };
    if (opcoes.corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';
    if (this.token) cabecalhos['Authorization'] = `Bearer ${this.token}`;
    try {
      return await fetch(caminho, {
        method: metodo,
        headers: cabecalhos,
        body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
        credentials: 'same-origin',
        signal: opcoes.sinal,
      });
    } catch (falha) {
      if (falha instanceof DOMException && falha.name === 'AbortError') throw falha;
      throw new ErroApi(0, null);
    }
  }

  private async erro(resposta: Response): Promise<ErroApi> {
    let corpo = null;
    try {
      corpo = await resposta.json();
    } catch {
      // corpo vazio ou não-JSON: fica a mensagem padrão do status
    }
    const tentarEm = Number(resposta.headers.get('Retry-After'));
    return new ErroApi(
      resposta.status,
      corpo,
      Number.isFinite(tentarEm) && tentarEm > 0 ? tentarEm : null,
    );
  }
}

/** Cliente único do app: uma instância só, para a renovação da sessão ser compartilhada. */
export const api = new ClienteApi();
`````

`web/compartilhado/src/api/erros.ts`:

`````ts
/** Erro de campo devolvido pela API na validação (lista `erros` do ProblemDetail). */
export interface ErroCampo {
  campo: string;
  mensagem: string;
}

/** Termo vigente que o usuário ainda precisa aceitar (403 com `termosPendentes`). */
export interface TermoPendente {
  id: number;
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
}

interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  erros?: ErroCampo[];
  termosPendentes?: TermoPendente[];
}

/**
 * Resposta de erro da API (ProblemDetail, RFC 9457). A `message` é o `detail`, já em português e
 * pronto para o usuário.
 */
export class ErroApi extends Error {
  readonly status: number;
  readonly erros: ErroCampo[];
  readonly termosPendentes: TermoPendente[];
  /** Segundos do cabeçalho `Retry-After` (limite de tentativas), quando houver. */
  readonly tentarEm: number | null;

  constructor(status: number, corpo: ProblemDetail | null, tentarEm: number | null = null) {
    super(corpo?.detail ?? mensagemPadrao(status));
    this.name = 'ErroApi';
    this.status = status;
    this.erros = corpo?.erros ?? [];
    this.termosPendentes = corpo?.termosPendentes ?? [];
    this.tentarEm = tentarEm;
  }

  /** Mensagem do campo informado, se a API apontou erro nele. */
  erroDoCampo(campo: string): string | undefined {
    return this.erros.find((e) => e.campo === campo)?.mensagem;
  }
}

function mensagemPadrao(status: number): string {
  if (status === 0) return 'Sem conexão com o servidor. Verifique a internet e tente de novo.';
  if (status >= 500) return 'O servidor não conseguiu responder agora. Tente de novo em instantes.';
  return 'Não foi possível concluir a operação.';
}

/** Texto para mostrar ao usuário a partir de qualquer erro. */
export function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ErroApi) return erro.message;
  return 'Ocorreu um erro inesperado. Tente de novo.';
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
import type { Sessao, Usuario } from '../api/tipos.ts';

type Estado =
  { status: 'carregando' } | { status: 'anonimo' } | { status: 'autenticado'; usuario: Usuario };

interface ContextoSessao {
  estado: Estado;
  /** Entra com e-mail e senha; erro da API (credenciais, e-mail não confirmado, limite) é lançado. */
  entrar: (email: string, senha: string) => Promise<Usuario>;
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

  const entrar = useCallback(async (email: string, senha: string) => {
    const sessao = await api.post<Sessao>(
      '/api/auth/login',
      { email, senha },
      { semRenovar: true },
    );
    api.definirSessao(sessao);
    return sessao.usuario;
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

`web/compartilhado/src/sessao/RotaPrivada.tsx`:

`````tsx
import { Carregando } from '@celke/kole-react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import type { Papel } from '../api/tipos.ts';
import { useSessao } from './ProvedorSessao.tsx';

interface RotaPrivadaProps {
  /** Papel exigido; sem ele, basta estar autenticado. */
  papel?: Papel;
  /** Rota do login (a de origem vai em `?voltar=` para retornar depois de entrar). */
  login: string;
  /** O que mostrar a quem está autenticado sem o papel exigido. */
  semPermissao?: ReactNode;
  children: ReactNode;
}

/** Só renderiza o conteúdo com sessão (e o papel, quando pedido); sem sessão, vai para o login. */
export function RotaPrivada({ papel, login, semPermissao = null, children }: RotaPrivadaProps) {
  const { estado } = useSessao();
  const { pathname, search } = useLocation();

  if (estado.status === 'carregando') {
    return <Carregando mensagem="Carregando sua sessão..." />;
  }
  if (estado.status === 'anonimo') {
    const voltar = encodeURIComponent(pathname + search);
    return <Navigate to={`${login}?voltar=${voltar}`} replace />;
  }
  if (papel && !estado.usuario.papeis.includes(papel)) {
    return <>{semPermissao}</>;
  }
  return <>{children}</>;
}

/** Destino seguro depois do login: só caminhos internos do próprio app (evita redirecionamento aberto). */
export function destinoAposLogin(voltar: string | null, padrao = '/'): string {
  if (!voltar || !voltar.startsWith('/') || voltar.startsWith('//')) return padrao;
  return voltar;
}
`````

`web/compartilhado/src/testes/configuracao.ts`:

`````ts
import '@testing-library/jest-dom/vitest';
`````

`web/compartilhado/src/ui/LayoutConta.tsx`:

`````tsx
import type { FormEvent, ReactNode } from 'react';

interface LayoutContaProps {
  marca: string;
  titulo: string;
  descricao?: string;
  /** Envio do formulário (o `preventDefault` já é feito aqui). Sem ele, o conteúdo vai sem `<form>`. */
  aoEnviar?: () => void;
  rodape?: ReactNode;
  children: ReactNode;
}

/**
 * Cartão centralizado das telas fora da área logada (cadastro, confirmação de e-mail, recuperação de
 * senha). O título é o `<h1>` da página.
 */
export function LayoutConta({
  marca,
  titulo,
  descricao,
  aoEnviar,
  rodape,
  children,
}: LayoutContaProps) {
  const enviar = (evento: FormEvent) => {
    evento.preventDefault();
    aoEnviar?.();
  };
  return (
    <main className="pc-conta">
      <section className="pc-conta-cartao" aria-labelledby="pc-conta-titulo">
        <p className="pc-conta-marca">{marca}</p>
        <h1 id="pc-conta-titulo" className="pc-conta-titulo">
          {titulo}
        </h1>
        {descricao && <p className="pc-conta-descricao">{descricao}</p>}
        {aoEnviar ? (
          <form className="pc-conta-form" onSubmit={enviar} noValidate>
            {children}
          </form>
        ) : (
          <div className="pc-conta-form">{children}</div>
        )}
        {rodape && <div className="pc-conta-rodape">{rodape}</div>}
      </section>
    </main>
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
`````

`web/compartilhado/src/ui/icones.ts`:

`````ts
import { registrarIcones } from '@celke/kole-react';
import {
  Award,
  BookOpen,
  CirclePlay,
  GraduationCap,
  LayoutDashboard,
  Pencil,
  Upload,
  Users,
} from 'lucide-react';

/** Ícones usados pelo nome nas telas e que a @celke/kole-react não registra por padrão. */
registrarIcones({
  award: Award,
  'book-open': BookOpen,
  'circle-play': CirclePlay,
  'graduation-cap': GraduationCap,
  'layout-dashboard': LayoutDashboard,
  pencil: Pencil,
  upload: Upload,
  users: Users,
});
`````

`web/compartilhado/src/ui/validacao.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import {
  erroConfirmacao,
  erroEmail,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from './validacao.ts';
import { destinoAposLogin } from '../sessao/RotaPrivada.tsx';

describe('validação de formulários', () => {
  it.each([
    ['', 'Informe o e-mail.'],
    ['ana', 'Informe um e-mail válido.'],
    ['ana@celke', 'Informe um e-mail válido.'],
    [' ana@celke.uno ', undefined],
  ])('e-mail "%s" → %s', (valor, esperado) => {
    expect(erroEmail(valor)).toBe(esperado);
  });

  it.each([
    ['', 'Informe a senha.'],
    ['123456789', 'A senha deve ter pelo menos 10 caracteres.'],
    ['1234567890', undefined],
    ['x'.repeat(129), 'A senha deve ter no máximo 128 caracteres.'],
  ])('senha de %s caracteres', (valor, esperado) => {
    expect(erroSenhaNova(valor)).toBe(esperado);
  });

  it('confirmação precisa ser igual', () => {
    expect(erroConfirmacao('abc', '')).toBe('Repita a senha.');
    expect(erroConfirmacao('abc', 'abd')).toBe('As senhas não conferem.');
    expect(erroConfirmacao('abc', 'abc')).toBeUndefined();
  });

  it('obrigatório com tamanho máximo', () => {
    expect(erroObrigatorio('  ', 'Informe o nome.')).toBe('Informe o nome.');
    expect(erroObrigatorio('abcd', 'x', 3)).toBe('Use no máximo 3 caracteres.');
    expect(semErros({ a: undefined, b: '' })).toBe(true);
    expect(semErros({ a: 'erro' })).toBe(false);
  });

  it.each([
    [null, '/'],
    ['/turmas/5', '/turmas/5'],
    ['https://mal.example', '/'],
    ['//mal.example', '/'],
    ['javascript:alert(1)', '/'],
  ])('destino após o login para "%s" → %s (sem redirecionamento aberto)', (voltar, esperado) => {
    expect(destinoAposLogin(voltar)).toBe(esperado);
  });
});
`````

`web/compartilhado/src/ui/validacao.ts`:

`````ts
/** Regras de formulário iguais às do backend, para o erro aparecer antes de ir à API. */

export const SENHA_MINIMA_ALUNO = 10;
export const SENHA_MAXIMA = 128;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function erroEmail(valor: string): string | undefined {
  const email = valor.trim();
  if (!email) return 'Informe o e-mail.';
  if (email.length > 254 || !EMAIL.test(email)) return 'Informe um e-mail válido.';
  return undefined;
}

export function erroSenhaNova(valor: string, minimo = SENHA_MINIMA_ALUNO): string | undefined {
  if (!valor) return 'Informe a senha.';
  if (valor.length < minimo) return `A senha deve ter pelo menos ${minimo} caracteres.`;
  if (valor.length > SENHA_MAXIMA) return `A senha deve ter no máximo ${SENHA_MAXIMA} caracteres.`;
  return undefined;
}

export function erroConfirmacao(senha: string, confirmacao: string): string | undefined {
  if (!confirmacao) return 'Repita a senha.';
  if (senha !== confirmacao) return 'As senhas não conferem.';
  return undefined;
}

export function erroObrigatorio(
  valor: string,
  mensagem: string,
  maximo?: number,
): string | undefined {
  const texto = valor.trim();
  if (!texto) return mensagem;
  if (maximo && texto.length > maximo) return `Use no máximo ${maximo} caracteres.`;
  return undefined;
}

/** Só as mensagens definidas; vazio = formulário válido. */
export function semErros(erros: Record<string, string | undefined>): boolean {
  return Object.values(erros).every((erro) => !erro);
}
`````

`web/e2e/adm/acesso.spec.ts`:

`````ts
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from '../support/config.ts';
import { emailNovo } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';
import { entrarPelaTela } from '../support/telas.ts';

test.describe('ADM', () => {
  test('com a sessão do admin, abre o painel com o menu da administração', async ({
    comoAdmin: page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
    await expect(page.getByText('Administrador')).toBeVisible();
  });

  test('o admin entra pela tela e volta para onde ia', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
    await entrarPelaTela(page, ADMIN_SEED, SENHA_SEED);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
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

`web/e2e/aluno/cadastro.spec.ts`:

`````ts
import { expect, test } from '@playwright/test';
import { ALUNO_SEED } from '../support/config.ts';
import { emailNovo, SENHA_NOVA } from '../support/dados.ts';
import { linkDoEmail } from '../support/mailpit.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Cadastro e confirmação do e-mail', () => {
  test('cadastra, confirma pelo link do e-mail e entra', async ({ page }) => {
    const email = emailNovo();
    await page.goto('/cadastro');
    await page.getByRole('textbox', { name: 'Nome', exact: true }).fill('Aluna do Playwright');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
    await page.getByRole('textbox', { name: 'Repita a senha', exact: true }).fill(SENHA_NOVA);
    const cadastro = page.waitForResponse('**/api/auth/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();
    expect((await cadastro).status()).toBe(202);
    await expect(page.getByRole('heading', { name: 'Confira o seu e-mail' })).toBeVisible();
    await expect(page.getByText(email)).toBeVisible();

    // Antes de confirmar, a conta não entra
    await page.goto('/entrar');
    await entrarPelaTela(page, email, SENHA_NOVA);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);

    await page.goto(await linkDoEmail(email, 'Confirme o seu e-mail'));
    await expect(page.getByRole('heading', { name: 'E-mail confirmado' })).toBeVisible();

    await page.getByRole('link', { name: 'Entrar' }).click();
    await entrarPelaTela(page, email, SENHA_NOVA);
    await aceitarTermosSePedir(page);
    await expect(page.getByText('Você ainda não tem cursos')).toBeVisible();
  });

  test('formulário vazio mostra os erros e não chama a API', async ({ page }) => {
    let chamou = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/auth/cadastro')) chamou = true;
    });
    await page.goto('/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();

    await expect(page.getByText('Informe o seu nome.')).toBeVisible();
    await expect(page.getByText('Informe o e-mail.')).toBeVisible();
    await expect(page.getByText('Informe a senha.')).toBeVisible();
    await expect(page.getByText('Repita a senha.')).toBeVisible();
    expect(chamou).toBe(false);
  });

  test('e-mail inválido, senha curta e confirmação diferente são barrados na tela', async ({
    page,
  }) => {
    await page.goto('/cadastro');
    await page.getByRole('textbox', { name: 'Nome', exact: true }).fill('Aluno');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill('sem-arroba');
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill('curta');
    await page.getByRole('textbox', { name: 'Repita a senha', exact: true }).fill('outra');
    await page.getByRole('button', { name: 'Criar conta' }).click();

    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
    await expect(page.getByText('A senha deve ter pelo menos 10 caracteres.')).toBeVisible();
    await expect(page.getByText('As senhas não conferem.')).toBeVisible();
  });

  test('e-mail já cadastrado recebe a mesma resposta (não revela quem tem conta)', async ({
    page,
  }) => {
    await page.goto('/cadastro');
    await page.getByRole('textbox', { name: 'Nome', exact: true }).fill('Outra pessoa');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(ALUNO_SEED);
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
    await page.getByRole('textbox', { name: 'Repita a senha', exact: true }).fill(SENHA_NOVA);
    const cadastro = page.waitForResponse('**/api/auth/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();

    expect((await cadastro).status()).toBe(202);
    await expect(page.getByRole('heading', { name: 'Confira o seu e-mail' })).toBeVisible();

    // A conta existente continua com a senha dela
    await page.goto('/entrar');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_NOVA);
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('link de confirmação inválido oferece o reenvio', async ({ page }) => {
    await page.goto('/confirmar-email?token=token-que-nao-existe');
    await expect(page.getByRole('heading', { name: 'Não foi possível confirmar' })).toBeVisible();

    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(emailNovo());
    await page.getByRole('button', { name: 'Enviar novo link' }).click();
    // A resposta não revela se a conta existe
    await expect(page.getByRole('button', { name: 'Enviar novo link' })).toBeHidden();
  });

  test('link de confirmação sem token', async ({ page }) => {
    await page.goto('/confirmar-email');
    await expect(page.getByText('Link inválido ou expirado. Peça um novo.')).toBeVisible();
  });
});
`````

`web/e2e/aluno/entrar.spec.ts`:

`````ts
import { expect, test } from '@playwright/test';
import { ALUNO_SEED, SENHA_SEED } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { emailNovo } from '../support/dados.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Entrar (aluno)', () => {
  test('com e-mail e senha certos, abre os cursos do aluno', async ({ page }) => {
    await page.goto('/entrar');
    const login = page.waitForResponse('**/api/auth/login');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    expect((await login).status()).toBe(200);

    await aceitarTermosSePedir(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByText('Aluno do e2e')).toBeVisible();
  });

  test('com senha errada, mostra o erro e continua na entrada', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await page.goto('/entrar');
    const login = page.waitForResponse('**/api/auth/login');
    await entrarPelaTela(page, email, 'senha-errada-de-proposito');
    expect((await login).status()).toBe(401);

    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);
  });

  test('com e-mail que não existe, a resposta é a mesma da senha errada', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, emailNovo(), 'qualquer-senha-123');
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('página privada sem sessão leva à entrada e volta para ela depois do login', async ({
    page,
  }) => {
    await page.goto('/?aba=ativos');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);

    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await aceitarTermosSePedir(page);
    await expect(page).toHaveURL('/?aba=ativos');
  });

  test('voltar para outro site é ignorado (sem redirecionamento aberto)', async ({ page }) => {
    await page.goto('/entrar?voltar=//exemplo.com/roubo');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await aceitarTermosSePedir(page);
    await expect(page).toHaveURL('/');
  });

  test('endereço que não existe mostra a página de não encontrada', async ({ page }) => {
    await page.goto('/pagina-que-nao-existe');
    await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible();
  });
});
`````

`web/e2e/aluno/senha.spec.ts`:

`````ts
import { expect, test } from '@playwright/test';
import { alunoConfirmado } from '../support/contas.ts';
import { emailNovo, SENHA_NOVA } from '../support/dados.ts';
import { linkDoEmail } from '../support/mailpit.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Recuperação de senha', () => {
  test('pede o link, cria a nova senha e entra com ela', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    const novaSenha = `${SENHA_NOVA}-nova`;

    await page.goto('/entrar');
    await page.getByRole('link', { name: 'Esqueci minha senha' }).click();
    await expect(page.getByRole('heading', { name: 'Recuperar senha' })).toBeVisible();
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
    await page.getByRole('button', { name: 'Enviar link' }).click();
    await expect(page.getByRole('button', { name: 'Enviar link' })).toBeHidden();

    await page.goto(await linkDoEmail(email, 'Crie uma nova senha'));
    await page.getByRole('textbox', { name: 'Nova senha', exact: true }).fill(novaSenha);
    await page.getByRole('textbox', { name: 'Repita a nova senha', exact: true }).fill(novaSenha);
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();
    await expect(page.getByRole('heading', { name: 'Senha alterada' })).toBeVisible();

    // A senha antiga deixou de valer
    await page.getByRole('link', { name: 'Entrar' }).click();
    await entrarPelaTela(page, email, SENHA_NOVA);
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();

    await entrarPelaTela(page, email, novaSenha);
    await aceitarTermosSePedir(page);
  });

  test('e-mail sem conta recebe a mesma resposta (não revela quem é cliente)', async ({ page }) => {
    await page.goto('/recuperar-senha');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(emailNovo());
    const resposta = page.waitForResponse('**/api/auth/recuperar-senha');
    await page.getByRole('button', { name: 'Enviar link' }).click();
    expect((await resposta).status()).toBe(202);
  });

  test('e-mail inválido é barrado na tela', async ({ page }) => {
    await page.goto('/recuperar-senha');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill('invalido');
    await page.getByRole('button', { name: 'Enviar link' }).click();
    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
  });

  test('link sem token', async ({ page }) => {
    await page.goto('/redefinir-senha');
    await expect(page.getByRole('heading', { name: 'Link inválido' })).toBeVisible();
    await page.getByRole('link', { name: 'Pedir um novo link' }).click();
    await expect(page).toHaveURL(/\/recuperar-senha$/);
  });

  test('token inválido é recusado pela API', async ({ page }) => {
    await page.goto('/redefinir-senha?token=token-que-nao-existe');
    await page.getByRole('textbox', { name: 'Nova senha', exact: true }).fill(SENHA_NOVA);
    await page.getByRole('textbox', { name: 'Repita a nova senha', exact: true }).fill(SENHA_NOVA);
    const resposta = page.waitForResponse('**/api/auth/redefinir-senha');
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();

    expect((await resposta).status()).toBe(400);
    await expect(page.getByRole('heading', { name: 'Criar nova senha' })).toBeVisible();
    await expect(page.getByRole('alert').first()).toBeVisible();
  });

  test('confirmação diferente é barrada na tela', async ({ page }) => {
    await page.goto('/redefinir-senha?token=qualquer');
    await page.getByRole('textbox', { name: 'Nova senha', exact: true }).fill(SENHA_NOVA);
    await page
      .getByRole('textbox', { name: 'Repita a nova senha', exact: true })
      .fill('outra-senha-qualquer');
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();
    await expect(page.getByText('As senhas não conferem.')).toBeVisible();
  });
});
`````

`web/e2e/aluno/sessao.spec.ts`:

`````ts
import { expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

test.describe('Sessão do aluno', () => {
  test('ao abrir e ao recarregar, a sessão volta pelo cookie de refresh', async ({
    comoAluno: page,
  }) => {
    const renovar = page.waitForResponse('**/api/auth/refresh');
    await page.goto('/');
    expect((await renovar).status()).toBe(200);
    await aceitarTermosSePedir(page);

    await page.reload();
    await expect(page.getByRole('heading', { name: 'Meus cursos' })).toBeVisible();
    await expect(page.getByText('Aluno do e2e')).toBeVisible();
  });

  test('sem matrícula, a lista de cursos fica vazia', async ({ comoAluno: page }) => {
    await page.goto('/');
    await aceitarTermosSePedir(page);
    await expect(page.getByText('Você ainda não tem cursos')).toBeVisible();
  });

  test('sair encerra a sessão: voltar à área do aluno pede login', async ({ comoAluno: page }) => {
    await page.goto('/');
    await aceitarTermosSePedir(page);
    await page.getByRole('button', { name: /Aluno do e2e/ }).click();
    const sair = page.waitForResponse('**/api/auth/logout');
    await page
      .getByRole('group', { name: 'Menu do usuário' })
      .getByRole('button', { name: 'Sair' })
      .click();
    expect((await sair).status()).toBe(204);
    await expect(page).toHaveURL(/\/entrar(\?|$)/);

    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });

  test('cookie de refresh já usado não abre sessão (e derruba a família)', async ({
    comoAluno: page,
  }) => {
    const [cookie] = await page.context().cookies();
    await page.goto('/');
    await aceitarTermosSePedir(page);

    // Outro navegador com o cookie antigo (já trocado pelo app): recusado
    const outro = await page.context().browser()!.newContext({ baseURL: page.url() });
    await outro.addCookies([cookie!]);
    const pagina = await outro.newPage();
    await pagina.goto('/');
    await expect(pagina).toHaveURL(/\/entrar\?voltar=/);
    await outro.close();

    // A sessão original também caiu: recarregar pede login
    await page.reload();
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });
});
`````

`web/e2e/support/config.ts`:

`````ts
/** Endereços e contas da stack de e2e (valores de infra/e2e.env; nenhum vale em servidor). */
export const ALUNO_URL = process.env['E2E_ALUNO_URL'] ?? 'http://aluno.localhost:8088';
export const ADM_URL = process.env['E2E_ADM_URL'] ?? 'http://adm.localhost:8088';
export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8026';

export const SENHA_SEED = process.env['E2E_SENHA_SEED'] ?? '123456A#b123';
export const ADMIN_SEED = 'admin@celke.com.br';
export const ALUNO_SEED = 'aluno@celke.com.br';

// Trava: os testes criam contas e trocam senhas; só rodam contra a stack local (*.localhost)
for (const url of [ALUNO_URL, ADM_URL]) {
  if (!/^http:\/\/[a-z]+\.localhost(:\d+)?$/.test(url)) {
    throw new Error(`E2E recusado: ${url} não é da stack local de e2e (*.localhost).`);
  }
}
`````

`web/e2e/support/contas.ts`:

`````ts
import type { APIRequestContext } from '@playwright/test';
import { ALUNO_URL } from './config.ts';
import { emailNovo, SENHA_NOVA } from './dados.ts';
import { linkDoEmail } from './mailpit.ts';

/**
 * Aluno novo já confirmado, criado pela API (o caminho pela tela é coberto em cadastro.spec.ts).
 * Para os testes que mexem na conta (senha errada, nova senha) sem tocar nas contas do seed.
 */
export async function alunoConfirmado(request: APIRequestContext): Promise<string> {
  const email = emailNovo();
  const cadastro = await request.post(`${ALUNO_URL}/api/auth/cadastro`, {
    data: { nome: 'Aluno do Playwright', email, senha: SENHA_NOVA },
  });
  if (!cadastro.ok()) throw new Error(`Cadastro recusado: ${cadastro.status()}`);
  const link = await linkDoEmail(email, 'Confirme o seu e-mail');
  const confirmacao = await request.post(`${ALUNO_URL}/api/auth/confirmar-email`, {
    data: { token: new URL(link).searchParams.get('token') },
  });
  if (!confirmacao.ok()) throw new Error(`Confirmação recusada: ${confirmacao.status()}`);
  return email;
}
`````

`web/e2e/support/dados.ts`:

`````ts
/** Dados únicos por execução: a stack não é limpa entre um teste e outro. */
let sequencia = 0;

export function stamp(): string {
  sequencia += 1;
  return `pw${Date.now().toString(36)}${sequencia}`;
}

export function emailNovo(): string {
  return `${stamp()}@teste.celke.uno`;
}

export const SENHA_NOVA = 'senha-forte-do-teste-e2e';
`````

`web/e2e/support/mailpit.ts`:

`````ts
import { expect } from '@playwright/test';
import { MAILPIT_URL } from './config.ts';

interface Busca {
  messages: { ID: string }[] | null;
}

/** Espera o worker entregar o e-mail e devolve o link dele (o primeiro com ?token=). */
export async function linkDoEmail(destinatario: string, assunto: string): Promise<string> {
  let id: string | undefined;
  await expect
    .poll(
      async () => {
        const consulta = encodeURIComponent(`to:"${destinatario}" subject:"${assunto}"`);
        const resposta = await fetch(`${MAILPIT_URL}/api/v1/search?query=${consulta}`);
        id = ((await resposta.json()) as Busca).messages?.[0]?.ID;
        return id;
      },
      { message: `e-mail "${assunto}" para ${destinatario}`, timeout: 30_000 },
    )
    .toBeTruthy();
  const resposta = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`);
  const { HTML } = (await resposta.json()) as { HTML: string };
  const link = /href="([^"]*[?&]token=[A-Za-z0-9_-]+)"/.exec(HTML)?.[1];
  if (!link) throw new Error(`O e-mail "${assunto}" não tem link com token.`);
  return link.replaceAll('&amp;', '&');
}
`````

`web/e2e/support/sessoes.ts`:

`````ts
import { test as base, type Page } from '@playwright/test';
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from './config.ts';

/**
 * Sessão nova por teste. O refresh token é rotativo e um token reutilizado revoga a família inteira,
 * então uma sessão gravada (storageState) só serviria para um teste. O login vai pela API do próprio
 * contexto do navegador, que guarda o cookie HttpOnly; ao abrir a página, o app renova e entra.
 */
async function entrarPelaApi(page: Page, email: string): Promise<void> {
  const resposta = await page.request.post('/api/auth/login', {
    data: { email, senha: SENHA_SEED },
  });
  if (!resposta.ok()) throw new Error(`Login de ${email} recusado: ${resposta.status()}`);
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

/** Preenche e envia o TelaLogin do kole-react. */
export async function entrarPelaTela(page: Page, email: string, senha: string): Promise<void> {
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/** Se houver termos vigentes sem aceite (outra jornada pode ter publicado), aceita. */
export async function aceitarTermosSePedir(page: Page): Promise<void> {
  const termos = page.getByRole('heading', { name: 'Termos atualizados' });
  const cursos = page.getByRole('heading', { name: 'Meus cursos' });
  await expect(termos.or(cursos)).toBeVisible();
  if (await termos.isVisible()) {
    await page.getByRole('checkbox', { name: 'Li e concordo com os termos acima' }).check();
    await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
    await expect(cursos).toBeVisible();
  }
}
`````

`web/eslint.config.js`:

`````js
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2023, globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-explicit-any': 'error',
      // Chamadas HTTP só pelo cliente de compartilhado/src/api (token, refresh e erros num lugar só)
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'Use o cliente da API (@compartilhado/api/cliente).' },
      ],
    },
  },
  {
    files: ['compartilhado/src/api/**', 'e2e/**', '**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off' },
  },
);
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
    "e2e:report": "playwright show-report"
  },
  "dependencies": {
    "@celke/kole-react": "github:celkecursos/kole-react#fc87d5c4409e1e65e8c571321c08d56871d2a4d3",
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

`web/playwright.config.ts`:

`````ts
import { defineConfig, devices } from '@playwright/test';
import { ADM_URL, ALUNO_URL } from './e2e/support/config.ts';

/**
 * Testes e2e dos fronts: navegador real contra a stack de e2e (infra/compose.e2e.yml), com os apps
 * servidos pelo container web atrás do Caddy, a API, o worker e o Mailpit de verdade. Nunca contra
 * um servidor: a stack de e2e é descartável (`down -v`) e o seed só existe nela.
 *
 *   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
 *   npm run e2e
 *
 * Cada teste começa sem sessão; quem precisa de uma usa as fixtures de e2e/support/sessoes.ts.
 */

// Só quando se está assistindo (`--headed`) a janela abre maximizada; fora daí o viewport é fixo
const assistindo = process.argv.includes('--headed');

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // Um worker: os testes escrevem no mesmo banco e gastam o mesmo limite por IP
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env['CI'],
  reporter: process.env['CI']
    ? [['github'], ['html', { open: 'never' }], ['list']]
    : [['html', { open: 'never' }], ['list']],
  use: {
    ...devices['Desktop Chrome'],
    // Artefatos só quando falha
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    locale: 'pt-BR',
    ...(assistindo ? { viewport: null } : {}),
    launchOptions: {
      slowMo: Number(process.env['E2E_SLOWMO'] ?? 0),
      args: assistindo ? ['--start-maximized'] : [],
    },
  },
  projects: [
    { name: 'aluno', testDir: './e2e/aluno', use: { baseURL: ALUNO_URL } },
    { name: 'adm', testDir: './e2e/adm', use: { baseURL: ADM_URL } },
  ],
});
`````

`web/tsconfig.json`:

`````json
{
  "compilerOptions": {
    "target": "ES2023",
    "lib": ["ES2023", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedIndexedAccess": true,
    "verbatimModuleSyntax": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "noEmit": true,
    "allowImportingTsExtensions": true,
    "types": ["vite/client", "node"],
    "paths": {
      "@compartilhado/*": ["./compartilhado/src/*"]
    }
  },
  "include": [
    "aluno",
    "adm",
    "compartilhado",
    "e2e",
    "playwright.config.ts",
    "vitest.config.ts",
    "vite.base.ts"
  ]
}
`````

`web/vite.base.ts`:

`````ts
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig, type UserConfig } from 'vite';

const compartilhado = fileURLToPath(new URL('./compartilhado/src', import.meta.url));

/**
 * Configuração comum das duas SPAs (aluno e ADM). No desenvolvimento, `/api` vai para a API
 * (`VITE_API_PROXY`, padrão: a API do compose local em http://localhost:8080) — mesma origem,
 * como em produção atrás do Caddy, então o cookie de refresh funciona sem CORS.
 */
export function configurar(app: 'aluno' | 'adm', porta: number): UserConfig {
  const raiz = fileURLToPath(new URL(`./${app}`, import.meta.url));
  return defineConfig({
    root: raiz,
    plugins: [react()],
    resolve: { alias: { '@compartilhado': compartilhado } },
    server: {
      port: porta,
      strictPort: true,
      proxy: { '/api': process.env['VITE_API_PROXY'] ?? 'http://localhost:8080' },
    },
    build: { outDir: fileURLToPath(new URL(`./dist/${app}`, import.meta.url)), emptyOutDir: true },
  });
}
`````

`web/vitest.config.ts`:

`````ts
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@compartilhado': fileURLToPath(new URL('./compartilhado/src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    include: ['{aluno,adm,compartilhado}/src/**/*.test.{ts,tsx}'],
    setupFiles: ['./compartilhado/src/testes/configuracao.ts'],
    css: false,
  },
});
`````

### Dependências do front

Depois de gravar os arquivos, dentro de `web/`, rode `npm install` para instalar as dependências do `package.json` e gerar o `package-lock.json`.

## Antes de executar

Fase 10 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/web-base
```

## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### Atualizar o Caddy da VPS

```text
==> FRONTS NA VPS (docs/vps.md, seção 11, "Atualizar o Caddy")

Depois que o deploy desta etapa estiver no ar nos DOIS ambientes (o Caddy novo manda as páginas
para o container web, que só existe a partir desta versão):

1. No SEU COMPUTADOR (não na VPS), na raiz do repositório, com a main atualizada
   (git switch main && git pull), copie os arquivos e recrie o Caddy:

     VPS=cesar@<ip-da-vps>
     ssh $VPS 'rm -rf /tmp/caddy && mkdir -p /tmp/caddy/sites'
     scp infra/caddy/compose.yml infra/caddy/Caddyfile $VPS:/tmp/caddy/
     scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/caddy/sites/
     ssh -t $VPS 'C=/opt/plataforma-cursos/caddy \
       && sudo cp -p $C/Caddyfile $C/Caddyfile.bak-$(date +%F-%H%M) \
       && sudo cp -r /tmp/caddy/. $C/ && sudo chown -R deploy:deploy $C && rm -rf /tmp/caddy \
       && sudo -u deploy docker compose -f $C/compose.yml --env-file $C/.env --project-directory $C up -d --force-recreate'
     curl -s https://aluno.celke.uno/actuator/health   # {"groups":[...],"status":"UP"}

2. Confira: curl -s https://aluno.celke.uno/ | grep -o '<title>.*</title>' mostra a área do aluno.
```

## Verificação

Dentro de `web/`:

```bash
npm run format:check
npm run lint
npm run typecheck
npm test
npm run build
```

Na raiz do repositório, com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
(cd web && npm run e2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- Tudo passa; confira as telas da fase em `http://aluno.localhost:8088` e `http://adm.localhost:8088` (contas do seed em `infra/e2e.env`).

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md
git commit -m "feat(web): criar a base dos fronts do aluno e do ADM"
git push -u origin feature/web-base
```

Abra o Pull Request de `feature/web-base` para `homolog`.
