# Site público

**Fase:** 14
**Branch:** `feature/site`

> **Configuração do projeto:** troque os marcadores `<dominio>`, `<sub-site>`, `<sub-aluno>`,
> `<sub-adm>`, `<sub-media>` e `<dono-github>` pelos valores de `docs/configuracao.md` (criado no
> primeiro prompt) em todo o conteúdo deste prompt, inclusive nos testes. Se o arquivo não existir,
> pergunte esses valores ao usuário antes de começar.

## Prompt

### Especificação e tasks (SDD)

Antes do código, registre a funcionalidade no SDD: crie a spec e as tasks abaixo com **exatamente** este conteúdo (troque os marcadores pelos valores de `docs/configuracao.md`). As tasks nascem com `Status: Pronta`; ao fim da **Verificação**, passam para `Concluída`.

`.sdd/specs/site-publico.spec.md`:

`````markdown
---
feature: site-publico
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Site público

## 1. Resumo Executivo

O site `<sub-site>.<dominio>` apresenta a plataforma e o catálogo aos visitantes e aos buscadores: cada
curso publicado no painel aparece no site sozinho, com página própria, dados para os buscadores e boa
nota de desempenho e acessibilidade.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Visitante | Navegar, ver os cursos, validar certificados, ler termos e privacidade |
| Buscador | Indexar a produção; a homologação fica fora |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE oferecer as páginas início, catálogo, curso, validar certificado, termos,
  privacidade e "não encontrada".
- **RF-02** — O sistema DEVE montar as páginas a partir da API do próprio ambiente e atualizá-las a cada
  5 minutos, trocando a versão inteira de uma vez.
- **RF-03** — O sistema DEVE publicar título, descrição, endereço canônico, dados de compartilhamento e
  dados estruturados de curso em cada página.
- **RF-04** — O sistema DEVE publicar o mapa do site e as regras para buscadores, mantendo a homologação
  (`homolog-<sub-site>.<dominio>`) fora dos buscadores.
- **RF-05** — O sistema DEVE atingir no Lighthouse (desktop) desempenho ≥ 90, acessibilidade ≥ 95, boas
  práticas ≥ 95 e SEO ≥ 90 em todas as páginas.
- **RF-06** — O sistema DEVE usar os tokens visuais da biblioteca de componentes da Celke.

## 4. Modelo de Domínio

Usa os cursos publicados (spec `catalogo-de-cursos`), a validação de certificado (spec `certificado`) e
os termos vigentes (spec `perfil-e-termos`).

## 5. Estados e Fluxos

- **Publicação:** curso publicado no painel → próxima atualização do site → página no ar e no mapa.

## 6. Contratos de Interface

| Página | Caminho |
|---|---|
| Início | `/` |
| Catálogo | `/cursos` |
| Curso | `/cursos/<slug>` |
| Validar certificado | `/certificados/validar` |
| Termos / privacidade | `/termos`, `/privacidade` |
| Mapa do site / buscadores | `/sitemap.xml`, `/robots.txt` |

Endereço canônico: `https://<sub-site>.<dominio>`.

## 7. Casos de Borda

- Curso despublicado: sai do site na próxima atualização.
- API fora do ar na atualização: a versão anterior continua no ar.

## 8. Premissas Validadas

- A mesma imagem serve homologação e produção; o ambiente define os endereços.

## 9. Riscos

- **Conteúdo desatualizado:** atualização a cada 5 minutos.
`````

`.sdd/tasks/site-publico-v1.0/README.md`:

`````markdown
# Tasks — Site público (v1.0)

Spec: [`.sdd/specs/site-publico.spec.md`](../../specs/site-publico.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f14-01-site-publico.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [SITE-001](SITE-001-paginas-e-seo.md) | Páginas e SEO | Pleno | — |
| 2 | [SITE-002](SITE-002-geracao.md) | Geração periódica com troca atômica | Sênior | SITE-001 |
| 3 | [SITE-003](SITE-003-fiacao.md) | Fiação do site | Pleno | SITE-002 |

## Grafo

```
SITE-001 (início)
SITE-001 → SITE-002
SITE-002 → SITE-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | SITE-001 |
| RF-02 | SITE-002 |
| RF-03 | SITE-001 |
| RF-04 | SITE-001 |
| RF-05 | SITE-003 |
| RF-06 | SITE-001 |

## Resumo por nível

- Pleno: 2
- Sênior: 1
`````

`.sdd/tasks/site-publico-v1.0/SITE-001-paginas-e-seo.md`:

`````markdown
# SITE-001 — Páginas e SEO

- **Spec:** .sdd/specs/site-publico.spec.md (v1.0) · atende RF-01, RF-03, RF-04, RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/site/` — páginas, meta tags, dados estruturados, sitemap e robots

## Descrição técnica

Tokens visuais da `@celke/kole-react`; homologação com `noindex`.

## Restrições

Canônico em `https://<sub-site>.<dominio>`.

## Critérios de aceite

- [ ] testes unitários do SEO verdes
`````

`.sdd/tasks/site-publico-v1.0/SITE-002-geracao.md`:

`````markdown
# SITE-002 — Geração periódica com troca atômica

- **Spec:** .sdd/specs/site-publico.spec.md (v1.0) · atende RF-02
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Pronta
- **Depende de:** SITE-001

## Arquivos a criar/alterar

- `web/site/` — gerador a partir da API do ambiente
- `infra/compose.prod.yml` — serviço `site`

## Descrição técnica

Gera ao subir e a cada `SITE_INTERVALO` (5 min), trocando a versão inteira.

## Critérios de aceite

- [ ] site gerado na stack de e2e

## Armadilhas

Trocar arquivo por arquivo serve páginas misturadas.
`````

`.sdd/tasks/site-publico-v1.0/SITE-003-fiacao.md`:

`````markdown
# SITE-003 — Fiação do site

- **Spec:** .sdd/specs/site-publico.spec.md (v1.0) · atende RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** SITE-002

## Arquivos a criar/alterar

- `infra/caddy/sites/producao.caddy`
- `infra/caddy/sites/homolog.caddy`
- `.github/workflows/pipeline.yml` — Lighthouse

## Descrição técnica

Metas do Lighthouse no CI.

## Critérios de aceite

- [ ] Lighthouse verde
- [ ] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
`````

Acrescente ao fim da tabela do índice `.sdd/README.md` a linha:

```text
| [site-publico](specs/site-publico.spec.md) | 1.0 | [v1.0](tasks/site-publico-v1.0/README.md) | `f14-01-site-publico.md` |
```

Vamos publicar a vitrine em `<sub-site>.<dominio>`: páginas HTML geradas a partir da API, rápidas e bem
indexadas. Não faça commit.

O que esta etapa entrega:

- **Container `site`**: gera o site estático a partir da API do próprio ambiente ao subir e a cada
  `SITE_INTERVALO` (5 min), com troca atômica da versão; se a API falhar, a versão anterior continua no
  ar. A mesma imagem serve homologação e produção.
- **Páginas**: home, catálogo, curso, validar certificado, termos, privacidade e 404, com os tokens da
  `@celke/kole-react` e sem JavaScript de layout.
- **SEO**: canônico, Open Graph, dados estruturados (`Course`), `sitemap.xml` e `robots.txt`
  (homologação com `noindex`).
- **Caddy**: o container `web` serve o site na 8083; em `cursos.*` só as leituras públicas da API passam.
- **Pipeline**: imagem `site` (Trivy e GHCR) e Lighthouse no CI (desempenho ≥ 90, acessibilidade ≥ 95,
  boas práticas ≥ 95, SEO ≥ 90).

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

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

`infra/caddy/.env.example`:

`````bash
# Copie para /opt/plataforma-cursos/caddy/.env na VPS (permissão 600).

# E-mail da conta no Let's Encrypt (avisos de certificado)
ACME_EMAIL=

# Hosts (só subdomínios de <dominio>; o domínio principal é de outras aplicações)
PRODUCAO_HOST_ALUNO=https://<sub-aluno>.<dominio>
PRODUCAO_HOST_ADM=https://<sub-adm>.<dominio>
PRODUCAO_HOST_MEDIA=https://<sub-media>.<dominio>
PRODUCAO_HOST_SITE=https://<sub-site>.<dominio>
HOMOLOG_HOST_ALUNO=https://homolog-<sub-aluno>.<dominio>
HOMOLOG_HOST_ADM=https://homolog-<sub-adm>.<dominio>
HOMOLOG_HOST_MEDIA=https://homolog-<sub-media>.<dominio>
HOMOLOG_HOST_SITE=https://homolog-<sub-site>.<dominio>

# IPs liberados, separados por espaço (IPv4/IPv6, com ou sem máscara: 203.0.113.7 198.51.100.0/24)
ADM_IPS=
HOMOLOG_IPS=
`````

`infra/caddy/Caddyfile`:

`````caddyfile
# Porta de entrada da plataforma. Um Caddy só atende os dois ambientes da VPS: cada site está em
# sites/<ambiente>.caddy e chega à API, aos fronts e ao Garage do seu ambiente pelos apelidos
# <ambiente>-api, <ambiente>-web e <ambiente>-garage. Com "https://..." o Caddy emite o certificado
# sozinho; com "http://..." atende sem TLS (e2e).
{
	admin off
	email {$ACME_EMAIL:nao-configurado@<dominio>}
	# Sem isso, o handle dos sites roda antes do respond e a restrição por IP (somente_ips_adm,
	# homolog) só valeria na mídia
	order respond before handle
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

# Site público (cursos.*): só as leituras públicas da API (catálogo, capas, termos vigentes e
# validação de certificado) chegam a ela; o resto é o site gerado, servido pelo container web.
# Argumento: o ambiente.
(site) {
	@api {
		method GET HEAD
		path /api/cursos /api/cursos/* /api/termos/vigentes /api/certificados/*
	}
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:8083 {
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

`infra/caddy/compose.yml`:

`````yaml
# Caddy da VPS: único processo nas portas 80/443, ligado às redes da produção e da homologação.
# Sobe depois das duas stacks (as redes são criadas por elas).
#   docker compose up -d
name: plataforma-cursos-caddy

services:
  caddy:
    image: caddy:2.11.7-alpine
    restart: unless-stopped
    environment:
      ACME_EMAIL: ${ACME_EMAIL:?defina ACME_EMAIL}
      PRODUCAO_HOST_ALUNO: ${PRODUCAO_HOST_ALUNO:?defina PRODUCAO_HOST_ALUNO}
      PRODUCAO_HOST_ADM: ${PRODUCAO_HOST_ADM:?defina PRODUCAO_HOST_ADM}
      PRODUCAO_HOST_MEDIA: ${PRODUCAO_HOST_MEDIA:?defina PRODUCAO_HOST_MEDIA}
      PRODUCAO_HOST_SITE: ${PRODUCAO_HOST_SITE:?defina PRODUCAO_HOST_SITE}
      HOMOLOG_HOST_ALUNO: ${HOMOLOG_HOST_ALUNO:?defina HOMOLOG_HOST_ALUNO}
      HOMOLOG_HOST_ADM: ${HOMOLOG_HOST_ADM:?defina HOMOLOG_HOST_ADM}
      HOMOLOG_HOST_MEDIA: ${HOMOLOG_HOST_MEDIA:?defina HOMOLOG_HOST_MEDIA}
      HOMOLOG_HOST_SITE: ${HOMOLOG_HOST_SITE:?defina HOMOLOG_HOST_SITE}
      ADM_IPS: ${ADM_IPS:?defina ADM_IPS}
      HOMOLOG_IPS: ${HOMOLOG_IPS:?defina HOMOLOG_IPS}
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./sites:/etc/caddy/sites:ro
      - dados:/data
      - config:/config
    networks:
      - producao
      - homolog
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"
    deploy:
      resources:
        limits:
          memory: 128M

networks:
  producao:
    external: true
    name: plataforma-cursos-producao
  homolog:
    external: true
    name: plataforma-cursos-homolog

volumes:
  dados:
  config:
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

http://cursos.localhost {
	import seguranca
	import site e2e
}

http://media.localhost {
	# Os mesmos headers do (seguranca), menos o Cross-Origin-Resource-Policy same-site: para o
	# navegador, cada *.localhost é um site diferente (o TLD desconhecido conta como sufixo público) e
	# as imagens da mídia não carregariam no aluno, no ADM e no site. Nos servidores, *.<dominio> é um
	# site só e vale o (seguranca) completo.
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		Cross-Origin-Resource-Policy cross-origin
		Strict-Transport-Security "max-age=31536000"
		-Server
	}
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

{$HOMOLOG_HOST_SITE} {
	import seguranca
	import homolog
	import site homolog
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

{$PRODUCAO_HOST_SITE} {
	import seguranca
	import site producao
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

`infra/compose.homolog.yml`:

`````yaml
# Homologação: a mesma stack da produção com limites menores (divide a VPS com a produção) e o
# Swagger ligado (o Caddy só deixa chegar quem está em HOMOLOG_IPS).
services:
  api:
    environment:
      JAVA_TOOL_OPTIONS: -Xmx384m
      DOCS_ENABLED: "true"
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 768M

  worker:
    environment:
      JAVA_TOOL_OPTIONS: -Xmx256m
    deploy:
      resources:
        limits:
          # Envia os e-mails da homologação; transcodificação lenta de propósito
          cpus: "0.5"
          memory: 768M

  # Homologação fica fora dos buscadores
  site:
    environment:
      SITE_INDEXAVEL: "false"

  postgres:
    deploy:
      resources:
        limits:
          memory: 512M

  garage:
    deploy:
      resources:
        limits:
          memory: 512M
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
    image: ${REGISTRO:?defina REGISTRO}/plataforma-cursos-api:${TAG:?defina TAG}
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
    image: ${REGISTRO:?defina REGISTRO}/plataforma-cursos-worker:${TAG:?defina TAG}
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
`````

`web/Caddyfile`:

`````caddyfile
# Caddy interno do container web: só arquivos estáticos (aluno, ADM e site), sem TLS (o TLS e os headers comuns ficam no
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

# Site público: páginas geradas pelo container site no volume montado em /srv/site (o link `atual`
# aponta para a versão mais recente). Sem cookies, então pode ficar em cache por pouco tempo.
:8083 {
	root * /srv/site/atual
	encode zstd gzip
	header {
		-Server
		Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: {$MIDIA_ORIGEM}; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
		X-Frame-Options DENY
	}

	handle /assets/* {
		header Cache-Control "public, max-age=3600"
		file_server
	}

	# /cursos/x e /cursos/x/ servem /cursos/x/index.html; o HTML muda a cada geração
	handle {
		header Cache-Control "public, max-age=60"
		try_files {path} {path}/index.html
		file_server
	}

	handle_errors 404 {
		rewrite * /404.html
		file_server
	}
}
`````

`web/Dockerfile`:

`````dockerfile
# Duas imagens a partir do mesmo build:
#   site (alvo `site`): gera o site público a partir da API do ambiente e grava num volume
#   web  (padrão):      Caddy interno com a área do aluno (8081), o ADM (8082) e o site gerado (8083)
# O Caddy da VPS manda /api para a API e o resto para o container web.

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

# Gerador do site: só Node, sem dependências (o Node 24 roda o TypeScript direto)
FROM node:24.19-alpine AS site
# Correções do sistema; npm e yarn saem da imagem (o gerador não instala nada)
RUN apk upgrade --no-cache \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx \
        /opt/yarn-* /usr/local/bin/yarn /usr/local/bin/yarnpkg
WORKDIR /app
COPY site/html.ts site/paginas.ts site/gerar.ts ./site/
COPY site/assets ./site/assets
COPY --from=build /web/node_modules/@celke/kole-react/dist/tokens.css ./site/assets/tokens.css
# O volume /site nasce com o dono desta pasta
RUN mkdir /site && chown node:node /site
USER node
ENV SITE_DESTINO=/site
HEALTHCHECK --interval=5s --timeout=3s --start-period=60s --retries=20 \
    CMD test -e /site/atual/index.html || exit 1
CMD ["node", "site/gerar.ts"]

FROM caddy:2.11.7-alpine AS web
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist/aluno /srv/aluno
COPY --from=build /web/dist/adm /srv/adm
EXPOSE 8081 8082 8083
HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
    CMD wget -q --spider http://127.0.0.1:8081/ && wget -q --spider http://127.0.0.1:8082/ || exit 1
`````

`web/e2e/adm/curso-completo.spec.ts`:

`````ts
import { escolher } from '../support/telas.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { stamp } from '../support/dados.ts';
import { PNG, TXT, VIDEO_3S } from '../support/amostras.ts';
import { expect, test } from '../support/sessoes.ts';

test('admin cria um curso completo e envia o vídeo da aula até ficar pronto', async ({
  comoAdmin: page,
  request,
}) => {
  // A conversão do vídeo pelo worker leva alguns segundos
  test.slow();
  const titulo = `Curso Playwright ${stamp()}`;
  const aluno = await alunoConfirmado(request);

  await page.goto('/cursos');
  await page.getByRole('button', { name: 'Novo curso' }).click();
  const formCurso = page.getByRole('dialog');
  await formCurso.getByRole('textbox', { name: 'Título', exact: true }).fill(titulo);
  await formCurso
    .getByRole('textbox', { name: 'Resumo', exact: true })
    .fill('Curso criado pela jornada do ADM.');
  const criar = page.waitForResponse(
    (r) => r.url().endsWith('/api/adm/cursos') && r.request().method() === 'POST',
  );
  await formCurso.getByRole('button', { name: 'Salvar' }).click();
  expect((await criar).status()).toBe(201);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  // Capa: o navegador envia direto ao armazenamento pela URL pré-assinada
  await page.getByLabel('Enviar capa').setInputFiles(PNG);
  const capa = page.getByRole('img', { name: `Capa de ${titulo}` });
  await expect(capa).toBeVisible();
  await expect
    .poll(() => capa.evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBeGreaterThan(0);

  // Turma, módulo e aula
  await page.getByRole('tab', { name: 'Turmas' }).click();
  await page.getByRole('button', { name: 'Nova turma' }).click();
  await page.getByRole('textbox', { name: 'Nome da turma' }).fill('Turma de outubro');
  await page.getByRole('button', { name: 'Criar turma' }).click();
  await expect(page.getByRole('heading', { name: 'Turma de outubro' })).toBeVisible();

  await page.getByRole('button', { name: 'Novo módulo' }).click();
  await page.getByRole('textbox', { name: 'Título do módulo' }).fill('Primeiros passos');
  await page.getByRole('button', { name: 'Criar módulo' }).click();
  await expect(page.getByText('1. Primeiros passos')).toBeVisible();

  await page.getByRole('button', { name: 'Nova aula' }).click();
  await page.getByRole('textbox', { name: 'Título da aula' }).fill('Boas-vindas');
  await page.getByRole('button', { name: 'Criar aula' }).click();
  await expect(page.getByRole('heading', { name: 'Boas-vindas' })).toBeVisible();

  // Vídeo em partes e conversão
  await page.getByLabel('Enviar vídeo').setInputFiles(VIDEO_3S);
  await expect(page.getByText('Na fila').or(page.getByText('Processando'))).toBeVisible();
  await expect(page.getByText('Pronto', { exact: true })).toBeVisible({ timeout: 150_000 });
  await expect(page.getByRole('img', { name: 'Quadro do vídeo' })).toBeVisible();
  await expect(page.getByText('0:03', { exact: true })).toBeVisible();

  // Arquivo de apoio da aula
  const arquivosAula = page.getByRole('region', { name: 'Arquivos de apoio da aula' });
  await arquivosAula.getByRole('textbox', { name: 'Título do arquivo' }).fill('Roteiro');
  await arquivosAula.getByLabel('Enviar arquivo').setInputFiles(TXT);
  await expect(arquivosAula.getByText('roteiro.txt')).toBeVisible();

  // Matrícula de um aluno
  await page.getByRole('button', { name: 'Voltar à turma' }).click();
  await page.getByRole('tab', { name: 'Matrículas' }).click();
  await page.getByRole('button', { name: 'Matricular aluno' }).click();
  await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(aluno);
  await page.getByRole('button', { name: 'Matricular', exact: true }).click();
  await expect(page.getByRole('cell', { name: aluno })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Vigente' })).toBeVisible();

  // Publica: turma ativa e curso publicado
  await page.getByRole('tab', { name: 'Configurações' }).click();
  await escolher(page, 'Status', 'Ativa');
  await expect(page.getByText('Status da turma alterado', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Voltar' }).click();
  await page.getByRole('tab', { name: 'Dados' }).click();
  await escolher(page, 'Alterar status', 'Publicado');
  await expect(page.getByText('Status do curso alterado', { exact: true })).toBeVisible();

  // O curso aparece no catálogo público
  const catalogo = await page.request.get('/api/cursos');
  expect(JSON.stringify(await catalogo.json())).toContain(titulo);
});
`````

`web/e2e/support/api.ts`:

`````ts
import { expect, type APIRequestContext } from '@playwright/test';
import { ADMIN_SEED, ADM_URL, ALUNO_URL, SENHA_SEED } from './config.ts';
import { stamp } from './dados.ts';

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
  const login = await request.post(`${base}/api/auth/login`, { data: { email, senha } });
  if (!login.ok()) throw new Error(`Login de ${email} recusado: ${login.status()}`);
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
export const ADMIN_SEED = 'admin@<dominio>';
export const ALUNO_SEED = 'aluno@<dominio>';

// Trava: os testes criam contas e trocam senhas; só rodam contra a stack local (*.localhost)
for (const url of [ALUNO_URL, ADM_URL, SITE_URL]) {
  if (!/^http:\/\/[a-z]+\.localhost(:\d+)?$/.test(url)) {
    throw new Error(`E2E recusado: ${url} não é da stack local de e2e (*.localhost).`);
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
    // O gerador do site roda no Node, fora do app: busca a API direto
    files: ['compartilhado/src/api/**', 'e2e/**', 'site/**', '**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off' },
  },
  {
    // Script do site que roda no navegador (validar certificado)
    files: ['site/assets/**/*.js'],
    extends: [js.configs.recommended],
    languageOptions: { ecmaVersion: 2023, sourceType: 'script', globals: globals.browser },
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
    "e2e:report": "playwright show-report",
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

`web/playwright.config.ts`:

`````ts
import { defineConfig, devices } from '@playwright/test';
import { ADM_URL, ALUNO_URL, SITE_URL } from './e2e/support/config.ts';

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
    { name: 'site', testDir: './e2e/site', use: { baseURL: SITE_URL } },
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
    "site",
    "e2e",
    "playwright.config.ts",
    "vitest.config.ts",
    "vite.base.ts"
  ]
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
    include: ['{aluno,adm,compartilhado}/src/**/*.test.{ts,tsx}', 'site/**/*.test.ts'],
    setupFiles: ['./compartilhado/src/testes/configuracao.ts'],
    css: false,
  },
});
`````

### Arquivos novos

`web/e2e/lighthouse.ts`:

`````ts
/**
 * Meta de Lighthouse do site público, verificada no CI contra a stack de e2e (perfil desktop).
 * Falha se alguma página ficar abaixo da nota mínima de alguma categoria.
 *
 *   npm run lighthouse
 */
import { chromium } from '@playwright/test';
import lighthouse from 'lighthouse';

const SITE = process.env['E2E_SITE_URL'] ?? 'http://cursos.localhost:8088';
const PORTA = 9333;

/** Notas mínimas (0 a 100). */
export const METAS: Record<string, number> = {
  performance: 90,
  accessibility: 95,
  'best-practices': 95,
  seo: 90,
};

async function paginas(): Promise<string[]> {
  const resposta = await fetch(`${SITE}/api/cursos?size=1`);
  const { itens } = (await resposta.json()) as { itens: { slug: string }[] };
  return [
    '/',
    '/cursos/',
    ...itens.map((c) => `/cursos/${c.slug}/`),
    '/certificados/validar/',
    '/termos/',
  ];
}

const navegador = await chromium.launch({ args: [`--remote-debugging-port=${PORTA}`] });
const falhas: string[] = [];
try {
  for (const caminho of await paginas()) {
    const resultado = await lighthouse(SITE + caminho, {
      port: PORTA,
      output: 'json',
      logLevel: 'error',
      onlyCategories: Object.keys(METAS),
      formFactor: 'desktop',
      screenEmulation: {
        mobile: false,
        width: 1350,
        height: 940,
        deviceScaleFactor: 1,
        disabled: false,
      },
      throttlingMethod: 'provided',
    });
    if (!resultado) throw new Error(`Lighthouse não rodou em ${caminho}`);
    const notas = Object.entries(METAS).map(([categoria, minimo]) => {
      const nota = Math.round((resultado.lhr.categories[categoria]?.score ?? 0) * 100);
      if (nota < minimo) {
        const auditorias = Object.values(resultado.lhr.audits)
          .filter((a) => a.score !== null && a.score < 1 && a.scoreDisplayMode !== 'informative')
          .map((a) => `      - ${a.title}`)
          .slice(0, 8);
        falhas.push(`${caminho} ${categoria}: ${nota} < ${minimo}\n${auditorias.join('\n')}`);
      }
      return `${categoria} ${nota}`;
    });
    console.log(`${caminho.padEnd(40)} ${notas.join(' · ')}`);
  }
} finally {
  await navegador.close();
}

if (falhas.length) {
  console.error(`\nAbaixo da meta:\n${falhas.join('\n')}`);
  process.exit(1);
}
console.log('\nTodas as páginas atingiram a meta.');
`````

`web/e2e/site/site.spec.ts`:

`````ts
import { expect, request as criarRequest, test, type Page } from '@playwright/test';
import { PNG } from '../support/amostras.ts';
import {
  clienteAluno,
  clienteApi,
  montarCurso,
  type ClienteApi,
  type CursoMontado,
} from '../support/api.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';

/**
 * Site público gerado pelo container site a partir da API (na stack de e2e, a cada 3 s). Um curso
 * publicado pelo ADM aparece sozinho; rascunho nunca aparece.
 */
let adm: ClienteApi;
let publicado: CursoMontado;
let slug: string;
let rascunho: string;

test.beforeAll(async () => {
  const contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  publicado = await montarCurso(adm);
  const curso = await adm.get<{ slug: string }>(`/api/adm/cursos/${publicado.cursoId}`);
  slug = curso.slug;
  await adm.patch(`/api/adm/cursos/${publicado.cursoId}/status`, { status: 'PUBLICADO' });
  rascunho = `Rascunho ${stamp()}`;
  await adm.post('/api/adm/cursos', { titulo: rascunho, resumo: 'Não publicado.' });
});

/** Recarrega até o gerador incluir o que acabou de mudar na API (o parâmetro fura o cache de 60 s do HTML). */
async function aguardarGeracao(page: Page, caminho: string, texto: string | RegExp) {
  await expect(async () => {
    await page.goto(`${caminho}?v=${Date.now()}`);
    await expect(page.getByText(texto).first()).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
}

test('a home e o catálogo mostram o curso publicado, não o rascunho', async ({ page }) => {
  await aguardarGeracao(page, '/cursos/', publicado.titulo);
  await expect(page.getByText(rascunho)).toHaveCount(0);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Aprenda no seu ritmo' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Criar conta' })).toHaveAttribute(
    'href',
    'http://aluno.localhost:8088/cadastro',
  );
});

test('página do curso com o conteúdo e as meta tags', async ({ page }) => {
  await aguardarGeracao(page, '/cursos/', publicado.titulo);
  await page.getByRole('link', { name: new RegExp(publicado.titulo) }).click();
  await expect(page).toHaveURL(`/cursos/${slug}/`);
  await page.goto(`/cursos/${slug}/?v=${Date.now()}`);
  await expect(page.getByRole('heading', { level: 1, name: publicado.titulo })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Módulo 1' })).toBeVisible();
  await expect(page.getByText('Aula 1')).toBeVisible();

  await expect(page).toHaveTitle(`${publicado.titulo} — Plataforma de Cursos`);
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
    'href',
    `http://cursos.localhost:8088/cursos/${slug}/`,
  );
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute(
    'content',
    publicado.titulo,
  );
  const dados = JSON.parse(
    (await page.locator('script[type="application/ld+json"]').textContent())!,
  );
  expect(dados['@type']).toBe('Course');
});

test('a capa enviada pelo ADM aparece no site', async ({ page }) => {
  const contexto = await criarRequest.newContext();
  const envio = await adm.post<{ chave: string; url: string; contentType: string }>(
    `/api/adm/cursos/${publicado.cursoId}/capa/envio`,
    { contentType: PNG.mimeType, tamanho: PNG.buffer.length },
  );
  await contexto.put(envio.url, {
    data: PNG.buffer,
    headers: { 'Content-Type': envio.contentType },
  });
  await contexto.dispose();
  await adm.put(`/api/adm/cursos/${publicado.cursoId}/capa`, { chave: envio.chave });

  await page.goto(`/cursos/${slug}/`);
  const capa = page.getByRole('img', { name: `Capa do curso ${publicado.titulo}` });
  await expect(async () => {
    await page.goto(`/cursos/${slug}/?v=${Date.now()}`);
    await expect(capa).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
  await expect
    .poll(() => capa.evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBeGreaterThan(0);
});

test('curso que não existe mostra a página 404 do site', async ({ page }) => {
  const resposta = await page.goto('/cursos/curso-que-nao-existe/');
  expect(resposta!.status()).toBe(404);
  await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible();
});

test('rotas da API fora das públicas não passam pelo site', async ({ request }) => {
  expect((await request.post('/api/auth/login', { data: {} })).status()).toBe(404);
  expect((await request.get('/api/aluno/cursos')).status()).toBe(404);
  expect((await request.get('/api/cursos')).status()).toBe(200);
});

test('sitemap e robots', async ({ request }) => {
  await expect(async () => {
    const mapa = await (await request.get('/sitemap.xml')).text();
    expect(mapa).toContain(`<loc>http://cursos.localhost:8088/cursos/${slug}/</loc>`);
  }).toPass({ timeout: 30_000 });
  expect(await (await request.get('/robots.txt')).text()).toContain(
    'Sitemap: http://cursos.localhost:8088/sitemap.xml',
  );
});

test.describe('Validar certificado', () => {
  test('código válido mostra os dados; código errado avisa', async ({ page, request }) => {
    // Aluno conclui o curso e emite o certificado pela API
    const email = await alunoConfirmado(request);
    await adm.post(`/api/adm/turmas/${publicado.turmaId}/matriculas`, {
      email,
      formaPagamento: 'CORTESIA',
      inicio: new Date(Date.now() - 86_400_000).toISOString(),
    });
    const aluno = await clienteAluno(request, email, SENHA_NOVA);
    await aluno.patch(`/api/aluno/aulas/${publicado.aulaId}/progresso`, { vista: true });
    const certificado = await aluno.post<{ codigo: string }>(
      `/api/aluno/turmas/${publicado.turmaId}/certificado`,
      {
        nomeCompleto: 'Pessoa Validada da Silva',
      },
    );

    await page.goto('/certificados/validar/');
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByText('Informe o código do certificado.')).toBeVisible();

    await page.getByRole('textbox', { name: 'Código do certificado' }).fill('CODIGO-INEXISTENTE');
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByText('Certificado não encontrado.')).toBeVisible();

    await page.getByRole('textbox', { name: 'Código do certificado' }).fill(certificado.codigo);
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByText('Certificado válido.')).toBeVisible();
    await expect(page.getByText('Pessoa Validada da Silva')).toBeVisible();
    await expect(page).toHaveURL(`/certificados/validar/?codigo=${certificado.codigo}`);

    // Link direto (impresso no certificado) já valida ao abrir
    await page.goto(`/certificados/validar/?codigo=${certificado.codigo}`);
    await expect(page.getByText('Certificado válido.')).toBeVisible();
  });
});

test('termos de uso publicados aparecem no site', async ({ page }) => {
  await page.goto('/termos/');
  await expect(page.getByRole('heading', { level: 1, name: 'Termos de uso' })).toBeVisible();
  await expect(
    page.getByText(/Versão \d+, publicada em/).or(page.getByText('Em breve.')),
  ).toBeVisible();
});
`````

`web/site/assets/icone.svg`:

`````xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="7" fill="#0b1f4b"/><path d="M12 9.5v13l10-6.5z" fill="#f7931e"/></svg>
`````

`web/site/assets/site.css`:

`````css
/* Site público: HTML estático com os tokens da @celke/kole-react (tokens.css), sem JavaScript de
   layout. O tema escuro segue o sistema do visitante. */
*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  -webkit-text-size-adjust: 100%;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: var(--fs-corpo);
  line-height: 1.6;
}

img {
  max-width: 100%;
  height: auto;
}

a {
  color: var(--pri-ink);
}

h1,
h2,
h3 {
  line-height: 1.25;
  margin: 0 0 0.6em;
}

h1 {
  font-size: clamp(1.75rem, 4vw, 2.5rem);
}

h2 {
  font-size: 1.4rem;
}

h3 {
  font-size: 1.1rem;
}

.s-largura {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 16px;
}

.s-estreito {
  max-width: 760px;
}

.s-secao {
  padding-top: 40px;
  padding-bottom: 40px;
}

.s-pular {
  position: absolute;
  left: -9999px;
}

.s-pular:focus {
  left: 16px;
  top: 8px;
  z-index: 10;
  background: var(--surface);
  padding: 8px 12px;
}

/* Topo e rodapé na cor da marca */
.s-topo,
.s-rodape {
  background: var(--navy);
  color: #fff;
}

.s-topo-linha,
.s-rodape-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  padding-top: 14px;
  padding-bottom: 14px;
}

.s-marca {
  color: #fff;
  font-weight: 700;
  font-size: 1.1rem;
  text-decoration: none;
}

.s-menu {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 20px;
}

.s-topo .s-menu a:not(.s-botao),
.s-rodape a {
  color: #fff;
}

.s-rodape {
  margin-top: 40px;
  font-size: var(--fs-corpo-2);
}

/* Botões */
.s-botao {
  display: inline-block;
  padding: 10px 20px;
  border-radius: var(--r-pill);
  background: var(--pri);
  color: var(--on-pri);
  font-weight: 600;
  text-decoration: none;
  border: 2px solid var(--pri);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}

.s-botao-contorno {
  background: transparent;
  color: var(--pri-ink);
}

.s-botao-claro {
  background: #fff;
  border-color: #fff;
  color: var(--navy);
  padding: 6px 16px;
}

.s-acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

/* Abertura */
.s-heroi {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  padding: 56px 0;
}

.s-heroi-texto {
  font-size: 1.15rem;
  color: var(--t2);
  max-width: 680px;
}

.s-detalhe {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
}

/* Cartões de curso */
.s-grade {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

.s-cartao {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  overflow: hidden;
}

.s-cartao-link {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: inherit;
  text-decoration: none;
}

.s-cartao-link:hover .s-cartao-titulo,
.s-cartao-link:focus-visible .s-cartao-titulo {
  text-decoration: underline;
}

.s-cartao-capa {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.s-cartao-sem-capa {
  background: linear-gradient(135deg, var(--navy), var(--pri));
}

.s-cartao-corpo {
  display: grid;
  gap: 6px;
  padding: 16px;
}

.s-cartao-titulo {
  font-weight: 700;
  font-size: 1.05rem;
}

.s-cartao-resumo {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
}

.s-vazio {
  color: var(--t2);
}

.s-passos {
  display: grid;
  gap: 12px;
  padding-left: 20px;
}

/* Página do curso */
.s-trilha {
  font-size: var(--fs-corpo-2);
  color: var(--t2);
  margin-bottom: 16px;
}

.s-curso-topo {
  display: grid;
  gap: 24px;
  align-items: start;
  margin-bottom: 32px;
}

@media (min-width: 860px) {
  .s-curso-topo {
    grid-template-columns: 3fr 2fr;
  }
}

.s-curso-capa {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: var(--r-card);
}

.s-modulos {
  display: grid;
  gap: 16px;
  padding: 0;
  list-style: none;
  counter-reset: modulo;
}

.s-modulos > li {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 16px 20px;
}

.s-aulas {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 4px;
}

.s-selo {
  display: inline-block;
  margin-left: 6px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  background: var(--ok-bg);
  color: var(--ok-fg);
  font-size: var(--fs-caption);
  font-weight: 600;
}

/* Validar certificado */
.s-form {
  display: grid;
  gap: 8px;
  max-width: 420px;
  margin: 24px 0;
}

.s-form label {
  font-weight: 600;
}

.s-form input {
  font: inherit;
  padding: 10px 12px;
  border: 1px solid var(--border-2);
  border-radius: var(--r-field);
  background: var(--surface);
  color: var(--ink);
}

.s-form .s-botao {
  justify-self: start;
}

.s-resultado {
  padding: 16px 20px;
  border-radius: var(--r-card);
  border: 1px solid var(--border);
}

.s-resultado:empty {
  display: none;
}

.s-resultado-ok {
  background: var(--ok-bg);
  border-color: var(--ok-bd);
  color: var(--ok-fg);
}

.s-resultado-erro {
  background: var(--err-bg);
  border-color: var(--err-bd);
  color: var(--err-fg);
}

.s-resultado dl {
  display: grid;
  gap: 4px;
  margin: 12px 0 0;
}

.s-resultado dl div {
  display: flex;
  gap: 8px;
}

.s-resultado dt {
  font-weight: 600;
}

.s-resultado dd {
  margin: 0;
}

.s-texto {
  overflow-wrap: anywhere;
}
`````

`web/site/assets/validar.js`:

`````js
// Validação pública do certificado: consulta /api/certificados/{codigo} no mesmo domínio.
(function () {
  const form = document.getElementById('validar');
  const campo = document.getElementById('codigo');
  const resultado = document.getElementById('resultado');

  function mostrar(classe, filhos) {
    resultado.className = classe;
    resultado.replaceChildren(...filhos);
  }

  function texto(tag, conteudo) {
    const el = document.createElement(tag);
    el.textContent = conteudo;
    return el;
  }

  function linha(rotulo, valor) {
    const item = document.createElement('div');
    item.append(texto('dt', rotulo), texto('dd', valor));
    return item;
  }

  async function validar(codigo) {
    mostrar('s-resultado', [texto('p', 'Consultando...')]);
    try {
      const resposta = await fetch('/api/certificados/' + encodeURIComponent(codigo), {
        headers: { Accept: 'application/json' },
      });
      if (resposta.status === 404) {
        mostrar('s-resultado s-resultado-erro', [
          texto('strong', 'Certificado não encontrado.'),
          texto('p', 'Confira o código digitado.'),
        ]);
        return;
      }
      if (!resposta.ok) throw new Error(String(resposta.status));
      const c = await resposta.json();
      const dados = document.createElement('dl');
      dados.append(
        linha('Aluno', c.nomeCompleto),
        linha('Curso', c.cursoTitulo),
        linha('Turma', c.turmaNome),
        linha('Emitido em', new Date(c.emitidoEm).toLocaleDateString('pt-BR')),
      );
      if (c.cargaHorariaHoras != null)
        dados.append(linha('Carga horária', c.cargaHorariaHoras + ' h'));
      mostrar('s-resultado s-resultado-ok', [texto('strong', 'Certificado válido.'), dados]);
    } catch {
      mostrar('s-resultado s-resultado-erro', [
        texto('strong', 'Não foi possível consultar agora.'),
        texto('p', 'Tente de novo em instantes.'),
      ]);
    }
  }

  form.addEventListener('submit', function (evento) {
    evento.preventDefault();
    const codigo = campo.value.trim();
    if (!codigo) {
      mostrar('s-resultado s-resultado-erro', [
        texto('strong', 'Informe o código do certificado.'),
      ]);
      campo.focus();
      return;
    }
    history.replaceState(null, '', '?codigo=' + encodeURIComponent(codigo));
    validar(codigo);
  });

  // Link direto do certificado: /certificados/validar/?codigo=...
  const inicial = new URLSearchParams(location.search).get('codigo');
  if (inicial) {
    campo.value = inicial;
    validar(inicial.trim());
  }
})();
`````

`web/site/gerar.ts`:

`````ts
/**
 * Gerador do site público (<sub-site>.<dominio>): busca o catálogo na API do próprio ambiente e grava
 * uma página HTML por rota. Roda no container `site` ao subir e de novo a cada SITE_INTERVALO
 * segundos; o Caddy do container `web` serve a versão mais recente.
 *
 * Cada geração vai para uma pasta nova em versoes/ e só então o link `atual` passa a apontar para
 * ela (troca atômica): o Caddy nunca serve um site pela metade. Se a API falhar, a versão anterior
 * continua no ar e a geração é tentada de novo em pouco tempo.
 *
 *   node site/gerar.ts            (laço)
 *   node site/gerar.ts --uma-vez  (uma geração e sai)
 */
import { cp, mkdir, readdir, rename, rm, symlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  paginaCatalogo,
  paginaCurso,
  paginaInicial,
  paginaNaoEncontrada,
  paginaTermo,
  paginaValidar,
  robots,
  sitemap,
  type Contexto,
  type CursoCatalogo,
  type CursoDetalhe,
  type Termo,
} from './paginas.ts';

const API_URL = (process.env['API_URL'] ?? 'http://localhost:8080').replace(/\/$/, '');
const ALUNO_URL = (process.env['FRONT_ALUNO_URL'] ?? 'http://localhost:5173').replace(/\/$/, '');
/** Sem SITE_URL, deriva do endereço do aluno (<sub-aluno>.<dominio> → <sub-site>.<dominio>). */
const SITE_URL = (process.env['SITE_URL'] || ALUNO_URL.replace('aluno.', 'cursos.')).replace(
  /\/$/,
  '',
);
const DESTINO = process.env['SITE_DESTINO'] ?? '/site';
const ASSETS = process.env['SITE_ASSETS'] ?? path.join(import.meta.dirname, 'assets');
const INTERVALO = Number(process.env['SITE_INTERVALO'] ?? 300) * 1000;
const NOVA_TENTATIVA = 30_000;
const VERSOES_GUARDADAS = 3;

async function buscar<T>(caminho: string): Promise<T> {
  const resposta = await fetch(API_URL + caminho, { headers: { Accept: 'application/json' } });
  if (!resposta.ok) throw new Error(`GET ${caminho}: ${resposta.status}`);
  return (await resposta.json()) as T;
}

/** Todos os cursos publicados (o catálogo é paginado, até 100 por página). */
async function todosOsCursos(): Promise<CursoCatalogo[]> {
  const cursos: CursoCatalogo[] = [];
  for (let pagina = 0; ; pagina++) {
    const resposta = await buscar<{ itens: CursoCatalogo[]; totalPaginas: number }>(
      `/api/cursos?page=${pagina}&size=100`,
    );
    cursos.push(...resposta.itens);
    if (pagina + 1 >= resposta.totalPaginas) return cursos;
  }
}

async function gravar(raiz: string, caminho: string, conteudo: string): Promise<void> {
  const arquivo = path.join(raiz, caminho.endsWith('/') ? `${caminho}index.html` : caminho);
  await mkdir(path.dirname(arquivo), { recursive: true });
  await writeFile(arquivo, conteudo);
}

export async function gerar(): Promise<number> {
  const ctx: Contexto = {
    siteUrl: SITE_URL,
    alunoUrl: ALUNO_URL,
    indexavel: process.env['SITE_INDEXAVEL'] !== 'false',
    ano: new Date().getFullYear(),
  };
  const [cursos, termos] = await Promise.all([
    todosOsCursos(),
    buscar<Termo[]>('/api/termos/vigentes'),
  ]);
  const detalhes = await Promise.all(
    cursos.map((c) => buscar<CursoDetalhe>(`/api/cursos/${c.slug}`)),
  );

  const versao = `v${Date.now()}`;
  const raiz = path.join(DESTINO, 'versoes', versao);
  await cp(ASSETS, path.join(raiz, 'assets'), { recursive: true });
  await gravar(raiz, '/', paginaInicial(ctx, cursos));
  await gravar(raiz, '/cursos/', paginaCatalogo(ctx, cursos));
  for (const curso of detalhes)
    await gravar(raiz, `/cursos/${curso.slug}/`, paginaCurso(ctx, curso));
  await gravar(raiz, '/certificados/validar/', paginaValidar(ctx));
  await gravar(
    raiz,
    '/termos/',
    paginaTermo(
      ctx,
      termos.find((t) => t.tipo === 'TERMOS_USO'),
      '/termos/',
    ),
  );
  await gravar(
    raiz,
    '/privacidade/',
    paginaTermo(
      ctx,
      termos.find((t) => t.tipo === 'POLITICA_PRIVACIDADE'),
      '/privacidade/',
    ),
  );
  await gravar(raiz, '/404.html', paginaNaoEncontrada(ctx));
  await gravar(raiz, '/sitemap.xml', sitemap(ctx, cursos));
  await gravar(raiz, '/robots.txt', robots(ctx));

  // Troca atômica: link temporário renomeado por cima do `atual`
  const temporario = path.join(DESTINO, `atual.${versao}`);
  await symlink(path.join('versoes', versao), temporario);
  await rename(temporario, path.join(DESTINO, 'atual'));

  const antigas = (await readdir(path.join(DESTINO, 'versoes')))
    .sort()
    .slice(0, -VERSOES_GUARDADAS);
  await Promise.all(
    antigas.map((v) => rm(path.join(DESTINO, 'versoes', v), { recursive: true, force: true })),
  );
  return cursos.length;
}

function registrar(mensagem: string): void {
  console.log(
    JSON.stringify({ '@timestamp': new Date().toISOString(), servico: 'site', mensagem }),
  );
}

async function laco(): Promise<void> {
  for (;;) {
    let espera = INTERVALO;
    try {
      const total = await gerar();
      registrar(`site gerado com ${total} cursos`);
    } catch (erro) {
      registrar(`falha ao gerar o site (a versão anterior continua no ar): ${String(erro)}`);
      espera = Math.min(INTERVALO, NOVA_TENTATIVA);
    }
    await new Promise((fim) => setTimeout(fim, espera));
  }
}

if (import.meta.main) {
  if (process.argv.includes('--uma-vez')) {
    registrar(`site gerado com ${await gerar()} cursos`);
  } else {
    process.on('SIGTERM', () => process.exit(0));
    await laco();
  }
}
`````

`web/site/html.ts`:

`````ts
/** HTML montado com escape automático: só o que já é `Html` entra sem escapar. */
export class Html {
  readonly valor: string;
  constructor(valor: string) {
    this.valor = valor;
  }
  toString(): string {
    return this.valor;
  }
}

const ENTIDADES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => ENTIDADES[c]!);
}

type Valor = Html | string | number | boolean | null | undefined | Valor[];

function parte(valor: Valor): string {
  if (valor === null || valor === undefined || valor === false || valor === true) return '';
  if (Array.isArray(valor)) return valor.map(parte).join('');
  if (valor instanceof Html) return valor.valor;
  return escapar(String(valor));
}

export function html(textos: TemplateStringsArray, ...valores: Valor[]): Html {
  let saida = textos[0]!;
  valores.forEach((valor, i) => {
    saida += parte(valor) + textos[i + 1]!;
  });
  return new Html(saida);
}

/** HTML que já vem filtrado (termos convertidos pela API). */
export function confiavel(texto: string): Html {
  return new Html(texto);
}

/** Texto puro em parágrafos (linha em branco separa parágrafos). */
export function paragrafos(texto: string | null | undefined): Html {
  if (!texto?.trim()) return new Html('');
  return html`${texto
    .trim()
    .split(/\n\s*\n/)
    .map((p) => html`<p>${p.trim()}</p>`)}`;
}

/** JSON dentro de <script>: sem `<` cru, para não fechar a tag. */
export function jsonEmScript(dados: unknown): Html {
  return new Html(JSON.stringify(dados).replace(/</g, '\\u003c'));
}
`````

`web/site/paginas.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { html, jsonEmScript, paragrafos } from './html.ts';
import {
  paginaCurso,
  paginaInicial,
  robots,
  sitemap,
  type Contexto,
  type CursoDetalhe,
} from './paginas.ts';

const ctx: Contexto = {
  siteUrl: 'https://<sub-site>.<dominio>',
  alunoUrl: 'https://<sub-aluno>.<dominio>',
  indexavel: true,
  ano: 2026,
};

const curso: CursoDetalhe = {
  slug: 'java-do-zero',
  titulo: 'Java <do zero>',
  resumo: 'Aprenda "Java" & Spring',
  descricao: 'Primeiro parágrafo.\n\nSegundo parágrafo.',
  capaUrl: '/api/cursos/java-do-zero/capa',
  modulos: [{ titulo: 'Introdução', aulas: [{ id: 1, titulo: 'Boas-vindas', gratuita: true }] }],
};

describe('html', () => {
  it('escapa o que vem de dados e mantém o que já é HTML', () => {
    expect(html`<p>${'<script>alert(1)</script>'}</p>`.toString()).toBe(
      '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>',
    );
    const itens = ['a', 'b'].map((x) => html`<li>${x}</li>`);
    expect(
      html`<ul>
        ${itens}
      </ul>`
        .toString()
        .replace(/\s+/g, ''),
    ).toBe('<ul><li>a</li><li>b</li></ul>');
    expect(html`${null}${undefined}${false}`.toString()).toBe('');
  });

  it('parágrafos e JSON seguro dentro de script', () => {
    expect(paragrafos('um\n\ndois').toString()).toBe('<p>um</p><p>dois</p>');
    expect(jsonEmScript({ t: '</script>' }).toString()).toBe('{"t":"\\u003c/script>"}');
  });
});

describe('páginas', () => {
  it('página do curso com SEO: título, descrição, canônico, Open Graph e dados estruturados', () => {
    const pagina = paginaCurso(ctx, curso);
    expect(pagina).toContain('<title>Java &lt;do zero&gt; — Plataforma de Cursos</title>');
    expect(pagina).toContain(
      '<meta name="description" content="Aprenda &quot;Java&quot; &amp; Spring" />',
    );
    expect(pagina).toContain(
      '<link rel="canonical" href="https://<sub-site>.<dominio>/cursos/java-do-zero/" />',
    );
    expect(pagina).toContain('content="https://<sub-site>.<dominio>/api/cursos/java-do-zero/capa"');
    expect(pagina).toContain('"@type":"Course"');
    expect(pagina).toContain('Aula gratuita');
    expect(pagina).toContain('<p>Segundo parágrafo.</p>');
    expect(pagina).not.toContain('noindex');
  });

  it('homologação fica fora dos buscadores', () => {
    const fora = { ...ctx, indexavel: false };
    expect(paginaInicial(fora, [])).toContain('<meta name="robots" content="noindex, nofollow" />');
    expect(robots(fora)).toBe('User-agent: *\nDisallow: /\n');
    expect(robots(ctx)).toContain('Sitemap: https://<sub-site>.<dominio>/sitemap.xml');
  });

  it('home sem cursos e sitemap com cada curso', () => {
    expect(paginaInicial(ctx, [])).toContain('Novos cursos em breve');
    const mapa = sitemap(ctx, [curso]);
    expect(mapa).toContain('<loc>https://<sub-site>.<dominio>/cursos/java-do-zero/</loc>');
    expect(mapa).toContain('<loc>https://<sub-site>.<dominio>/certificados/validar/</loc>');
  });
});
`````

`web/site/paginas.ts`:

`````ts
import { confiavel, html, jsonEmScript, paragrafos, type Html } from './html.ts';

/** Dados do catálogo público da API (/api/cursos), espelhando os records do backend. */
export interface CursoCatalogo {
  slug: string;
  titulo: string;
  resumo: string;
  capaUrl: string | null;
}

export interface CursoDetalhe extends CursoCatalogo {
  descricao: string | null;
  modulos: { titulo: string; aulas: { id: number; titulo: string; gratuita: boolean }[] }[];
}

export interface Termo {
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

export interface Contexto {
  /** Endereço público do site, sem barra no fim (ex.: https://<sub-site>.<dominio>). */
  siteUrl: string;
  /** Área do aluno (entrar, criar conta). */
  alunoUrl: string;
  /** Homologação: fora dos buscadores. */
  indexavel: boolean;
  /** Ano do rodapé. */
  ano: number;
}

const NOME = 'Plataforma de Cursos';

interface Pagina {
  caminho: string;
  titulo: string;
  descricao: string;
  corpo: Html;
  imagem?: string | null;
  dadosEstruturados?: unknown;
}

function layout(ctx: Contexto, p: Pagina): string {
  const canonico = ctx.siteUrl + p.caminho;
  const titulo = p.caminho === '/' ? `${NOME} — ${p.titulo}` : `${p.titulo} — ${NOME}`;
  return `<!doctype html>\n${html`<html lang="pt-BR">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>${titulo}</title>
      <meta name="description" content="${p.descricao}" />
      ${ctx.indexavel ? '' : html`<meta name="robots" content="noindex, nofollow" />`}
      <link rel="canonical" href="${canonico}" />
      <meta property="og:type" content="website" />
      <meta property="og:site_name" content="${NOME}" />
      <meta property="og:locale" content="pt_BR" />
      <meta property="og:title" content="${p.titulo}" />
      <meta property="og:description" content="${p.descricao}" />
      <meta property="og:url" content="${canonico}" />
      ${p.imagem ? html`<meta property="og:image" content="${ctx.siteUrl + p.imagem}" />` : ''}
      <meta name="twitter:card" content="${p.imagem ? 'summary_large_image' : 'summary'}" />
      <link rel="icon" href="/assets/icone.svg" type="image/svg+xml" />
      <link rel="stylesheet" href="/assets/tokens.css" />
      <link rel="stylesheet" href="/assets/site.css" />
      ${
        p.dadosEstruturados
          ? html`<script type="application/ld+json">
              ${jsonEmScript(p.dadosEstruturados)}
            </script>`
          : ''
      }
    </head>
    <body>
      <a class="s-pular" href="#conteudo">Pular para o conteúdo</a>
      <header class="s-topo">
        <div class="s-largura s-topo-linha">
          <a class="s-marca" href="/">${NOME}</a>
          <nav aria-label="Principal" class="s-menu">
            <a href="/cursos/">Cursos</a>
            <a href="/certificados/validar/">Validar certificado</a>
            <a class="s-botao s-botao-claro" href="${ctx.alunoUrl}/entrar">Entrar</a>
          </nav>
        </div>
      </header>
      <main id="conteudo">${p.corpo}</main>
      <footer class="s-rodape">
        <div class="s-largura s-rodape-linha">
          <span>© ${ctx.ano} ${NOME}</span>
          <nav aria-label="Rodapé" class="s-menu">
            <a href="/termos/">Termos de uso</a>
            <a href="/privacidade/">Privacidade</a>
            <a href="/certificados/validar/">Validar certificado</a>
          </nav>
        </div>
      </footer>
    </body>
  </html>`}\n`;
}

function cartaoCurso(curso: CursoCatalogo): Html {
  return html`<li class="s-cartao">
    <a href="/cursos/${curso.slug}/" class="s-cartao-link">
      ${
        curso.capaUrl
          ? html`<img
              class="s-cartao-capa"
              src="${curso.capaUrl}"
              alt=""
              width="640"
              height="360"
              loading="lazy"
            />`
          : html`<span class="s-cartao-capa s-cartao-sem-capa" aria-hidden="true"></span>`
      }
      <span class="s-cartao-corpo">
        <span class="s-cartao-titulo">${curso.titulo}</span>
        <span class="s-cartao-resumo">${curso.resumo}</span>
      </span>
    </a>
  </li>`;
}

function listaCursos(cursos: CursoCatalogo[]): Html {
  if (cursos.length === 0) {
    return html`<p class="s-vazio">Novos cursos em breve. Volte logo!</p>`;
  }
  return html`<ul class="s-grade" role="list">
    ${cursos.map(cartaoCurso)}
  </ul>`;
}

export function paginaInicial(ctx: Contexto, cursos: CursoCatalogo[]): string {
  return layout(ctx, {
    caminho: '/',
    titulo: 'Aprenda no seu ritmo',
    descricao:
      'Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado.',
    corpo: html`<section class="s-heroi">
        <div class="s-largura">
          <h1>Aprenda no seu ritmo</h1>
          <p class="s-heroi-texto">
            Aulas em vídeo para assistir de qualquer lugar, materiais de apoio, dúvidas respondidas
            pelo instrutor e certificado com código de validação.
          </p>
          <p class="s-acoes">
            <a class="s-botao" href="/cursos/">Ver os cursos</a>
            <a class="s-botao s-botao-contorno" href="${ctx.alunoUrl}/cadastro">Criar conta</a>
          </p>
        </div>
      </section>
      <section class="s-largura s-secao" aria-labelledby="titulo-cursos">
        <h2 id="titulo-cursos">Cursos</h2>
        ${listaCursos(cursos.slice(0, 6))}
        ${cursos.length > 6 ? html`<p><a href="/cursos/">Ver todos os ${cursos.length} cursos</a></p>` : ''}
      </section>
      <section class="s-largura s-secao" aria-labelledby="titulo-como">
        <h2 id="titulo-como">Como funciona</h2>
        <ol class="s-passos">
          <li><strong>Crie a sua conta</strong> e confirme o e-mail.</li>
          <li><strong>Matricule-se</strong> no curso e acesse as aulas pela área do aluno.</li>
          <li>
            <strong>Assista no seu tempo:</strong> o progresso fica salvo e você continua de onde
            parou.
          </li>
          <li>
            <strong>Conclua e emita o certificado</strong>, que qualquer pessoa pode validar aqui no
            site.
          </li>
        </ol>
      </section>`,
  });
}

export function paginaCatalogo(ctx: Contexto, cursos: CursoCatalogo[]): string {
  return layout(ctx, {
    caminho: '/cursos/',
    titulo: 'Cursos',
    descricao: `Conheça os ${cursos.length} cursos disponíveis na ${NOME}.`,
    corpo: html`<div class="s-largura s-secao">
      <h1>Cursos</h1>
      ${listaCursos(cursos)}
    </div>`,
  });
}

export function paginaCurso(ctx: Contexto, curso: CursoDetalhe): string {
  const totalAulas = curso.modulos.reduce((total, m) => total + m.aulas.length, 0);
  const caminho = `/cursos/${curso.slug}/`;
  return layout(ctx, {
    caminho,
    titulo: curso.titulo,
    descricao: curso.resumo,
    imagem: curso.capaUrl,
    dadosEstruturados: {
      '@context': 'https://schema.org',
      '@type': 'Course',
      name: curso.titulo,
      description: curso.resumo,
      url: ctx.siteUrl + caminho,
      provider: { '@type': 'Organization', name: NOME, sameAs: ctx.siteUrl },
    },
    corpo: html`<article class="s-largura s-secao">
      <nav aria-label="Trilha" class="s-trilha">
        <a href="/cursos/">Cursos</a> › <span>${curso.titulo}</span>
      </nav>
      <div class="s-curso-topo">
        <div>
          <h1>${curso.titulo}</h1>
          <p class="s-heroi-texto">${curso.resumo}</p>
          <p class="s-detalhe">${curso.modulos.length} módulos · ${totalAulas} aulas</p>
          <p class="s-acoes">
            <a class="s-botao" href="${ctx.alunoUrl}/cadastro">Quero fazer este curso</a>
            <a class="s-botao s-botao-contorno" href="${ctx.alunoUrl}/entrar">Já sou aluno</a>
          </p>
        </div>
        ${curso.capaUrl ? html`<img class="s-curso-capa" src="${curso.capaUrl}" alt="Capa do curso ${curso.titulo}" width="640" height="360" />` : ''}
      </div>
      ${
        curso.descricao
          ? html`<section aria-labelledby="titulo-sobre">
              <h2 id="titulo-sobre">Sobre o curso</h2>
              ${paragrafos(curso.descricao)}
            </section>`
          : ''
      }
      <section aria-labelledby="titulo-conteudo">
        <h2 id="titulo-conteudo">Conteúdo</h2>
        ${
          curso.modulos.length === 0
            ? html`<p class="s-vazio">O conteúdo deste curso está em preparação.</p>`
            : html`<ol class="s-modulos">
                ${curso.modulos.map(
                  (m) =>
                    html`<li>
                      <h3>${m.titulo}</h3>
                      <ol class="s-aulas">
                        ${m.aulas.map((a) => html`<li>${a.titulo}${a.gratuita ? html` <span class="s-selo">Aula gratuita</span>` : ''}</li>`)}
                      </ol>
                    </li>`,
                )}
              </ol>`
        }
      </section>
    </article>`,
  });
}

export function paginaValidar(ctx: Contexto): string {
  return layout(ctx, {
    caminho: '/certificados/validar/',
    titulo: 'Validar certificado',
    descricao:
      'Confira se um certificado foi emitido pela plataforma usando o código impresso nele.',
    corpo: html`<div class="s-largura s-secao s-estreito">
      <h1>Validar certificado</h1>
      <p>Informe o código impresso no certificado para conferir os dados dele.</p>
      <form id="validar" class="s-form" novalidate>
        <label for="codigo">Código do certificado</label>
        <input id="codigo" name="codigo" autocomplete="off" required maxlength="64" />
        <button class="s-botao" type="submit">Validar</button>
      </form>
      <div id="resultado" role="status" aria-live="polite"></div>
      <script src="/assets/validar.js" defer></script>
    </div>`,
  });
}

export function paginaTermo(
  ctx: Contexto,
  termo: Termo | undefined,
  caminho: '/termos/' | '/privacidade/',
): string {
  const titulo = caminho === '/termos/' ? 'Termos de uso' : 'Política de privacidade';
  return layout(ctx, {
    caminho,
    titulo,
    descricao: `${titulo} da ${NOME}.`,
    corpo: html`<article class="s-largura s-secao s-estreito s-texto">
      <h1>${titulo}</h1>
      ${
        termo
          ? html`<p class="s-detalhe">
                Versão ${termo.versao}, publicada em
                ${new Date(termo.publicadoEm).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' })}.
              </p>
              ${confiavel(termo.conteudoHtml)}`
          : html`<p class="s-vazio">Em breve.</p>`
      }
    </article>`,
  });
}

export function paginaNaoEncontrada(ctx: Contexto): string {
  return layout(ctx, {
    caminho: '/404.html',
    titulo: 'Página não encontrada',
    descricao: 'O endereço não existe ou mudou.',
    corpo: html`<div class="s-largura s-secao">
      <h1>Página não encontrada</h1>
      <p>O endereço não existe ou mudou. <a href="/cursos/">Veja os cursos</a>.</p>
    </div>`,
  });
}

export function sitemap(ctx: Contexto, cursos: CursoCatalogo[]): string {
  const caminhos = [
    '/',
    '/cursos/',
    ...cursos.map((c) => `/cursos/${c.slug}/`),
    '/certificados/validar/',
    '/termos/',
    '/privacidade/',
  ];
  return `<?xml version="1.0" encoding="UTF-8"?>\n${html`<urlset
    xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
  >
    ${caminhos.map((c) => html` <url><loc>${ctx.siteUrl + c}</loc></url> `)}</urlset
  >`}\n`;
}

export function robots(ctx: Contexto): string {
  return ctx.indexavel
    ? `User-agent: *\nAllow: /\n\nSitemap: ${ctx.siteUrl}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n';
}
`````

### Dependências do front

Depois de gravar os arquivos, dentro de `web/`, rode `npm install` para instalar as dependências do `package.json` e gerar o `package-lock.json`.

## Antes de executar

Fase 13 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/site
```

## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### 1. DNS do site

```text
==> SITE PÚBLICO — ETAPA 1 DE 2: DNS

No painel do seu domínio, crie mais dois registros do tipo A apontando para o IP da VPS:

  Nome              Tipo   Valor
  cursos            A      <ip-da-vps>
  homolog-cursos    A      <ip-da-vps>

Para conferir (deve responder o IP da VPS): dig +short <sub-site>.<dominio>
Me avise quando estiverem criados: eu confiro com o dig antes de seguir.
```

### 2. Caddy do site

```text
==> SITE PÚBLICO — ETAPA 2 DE 2: CADDY (docs/vps.md, seção 11)

1. Na VPS, em /opt/plataforma-cursos/caddy/.env, acrescente:

     PRODUCAO_HOST_SITE=https://<sub-site>.<dominio>
     HOMOLOG_HOST_SITE=https://homolog-<sub-site>.<dominio>

2. Depois do deploy desta etapa nos dois ambientes, no SEU COMPUTADOR (não na VPS), na raiz do
   repositório, com a main atualizada (git switch main && git pull):

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
- Com tudo verde, as tasks de `.sdd/tasks/site-publico-v1.0/` passam para `Status: Concluída` e os critérios de aceite
  são marcados (`- [x]`).

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md .sdd/
git commit -m "feat(site): gerar o site público a partir da API"
git push -u origin feature/site
```

Abra o Pull Request de `feature/site` para `homolog`.
