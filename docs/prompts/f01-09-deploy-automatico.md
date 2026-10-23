# Deploy automático com rollback

**Fase:** 1 — etapa 1.9
**Branch:** `feature/base-projeto`

## Prompt

Vamos fazer cada merge virar versão no ar: o pipeline publica as imagens que passaram no e2e e implanta
na VPS por SSH, com rollback automático. Não faça commit.

O que esta etapa entrega:

- **Publicação no GHCR**: no job de e2e, só em push e só depois das jornadas, as imagens testadas
  (`plataforma-cursos-api:e2e` e `plataforma-cursos-worker:e2e`) recebem a tag do SHA do commit e vão para
  `ghcr.io/<dono>/plataforma-cursos-{api,worker}`, sem rebuild. O label
  `org.opencontainers.image.source` liga os pacotes ao repositório.
- **`deploy.sh`** (na VPS em `/usr/local/bin/plataforma-cursos-deploy`, dono root, fora da pasta do usuário
  deploy): argumentos `<producao|homolog> <sha completo>` (também por `SSH_ORIGINAL_COMMAND`), extrai da
  entrada só os arquivos de compose esperados, faz o backup do banco (`pg_dump` dentro do container),
  troca a tag no `.env`, sobe com `up --pull missing --wait` e, se algum container não ficar saudável,
  volta para a versão anterior (arquivos e tag) e termina com erro. `flock` (um deploy por vez),
  `trap '' HUP` (continua se a conexão cair), guarda 5 versões e 10 backups, registra em `deploy.log`.
  O rollback não desfaz migrations.
- **Chave do deploy presa ao script**: o `preparar-vps.sh` grava o `authorized_keys` do usuário deploy com
  `command="/usr/local/bin/plataforma-cursos-deploy",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty`.
- **Workflow reutilizável `deploy.yml`**: environment `homolog` ou `production`, um deploy por ambiente e
  nunca cancelado; manda os arquivos por `tar | ssh` com a impressão digital da VPS fixada. No pipeline,
  push em `homolog` → homologação, push em `main` → produção, só com a variável do repositório
  `DEPLOY_ATIVO=true` (sem ela, os deploys são pulados); só PRs cancelam execuções antigas.
- **Roteiro**: secrets do repositório (`VPS_HOST`, `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS`), variável
  `DEPLOY_ATIVO`, primeira subida pelo
  pipeline e operação (histórico, voltar versão, atualizar o Caddy e o script).

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado e deixe o script
executável (`chmod +x infra/scripts/deploy.sh`).

### 1. Arquivos alterados (substitua o conteúdo inteiro)

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

  e2e:
    name: e2e (stack completa)
    needs: testes
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

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker; do
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

`infra/scripts/preparar-vps.sh`:

`````bash
#!/usr/bin/env bash
# Prepara uma VPS Ubuntu 24.04 nova para a plataforma. Rode UMA vez, como root, e mantenha esta
# sessão SSH aberta até confirmar o login do administrador num SEGUNDO terminal.
#
#   ADMIN_USUARIO=cesar \
#   ADMIN_CHAVE="ssh-ed25519 AAAA... seu@computador" \
#   DEPLOY_CHAVE="ssh-ed25519 AAAA... github-deploy" \
#   bash preparar-vps.sh
#
# O que faz: atualiza o sistema, instala o Docker oficial (com rotação de logs), cria o usuário
# administrador (sudo) e o usuário deploy (sem sudo, no grupo docker, com a chave presa ao script de
# deploy), deixa o SSH só por chave e sem root, liga o ufw (22, 80 e 443), o fail2ban, as atualizações
# automáticas e um swap de 2 GB, e cria /opt/plataforma-cursos.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Rode como root." >&2; exit 1; }
: "${ADMIN_USUARIO:?defina ADMIN_USUARIO}" "${ADMIN_CHAVE:?defina ADMIN_CHAVE}" "${DEPLOY_CHAVE:?defina DEPLOY_CHAVE}"

export DEBIAN_FRONTEND=noninteractive
BASE=/opt/plataforma-cursos
# Script de deploy (instalado depois, pelo roteiro): fora de $BASE, numa pasta que o deploy não altera
SCRIPT_DEPLOY=/usr/local/bin/plataforma-cursos-deploy

echo "==> Atualizando o sistema"
apt-get update -q
apt-get upgrade -y -q
apt-get install -y -q ca-certificates curl ufw fail2ban unattended-upgrades

echo "==> Docker (repositório oficial)"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# shellcheck disable=SC1091
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -q
apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
# Logs dos containers com rotação: sem isso eles crescem até encher o disco
cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
systemctl restart docker

criar_usuario() {
  local usuario=$1 chave=$2
  id "$usuario" &>/dev/null || adduser --disabled-password --gecos "" "$usuario"
  install -d -m 700 -o "$usuario" -g "$usuario" "/home/$usuario/.ssh"
  echo "$chave" > "/home/$usuario/.ssh/authorized_keys"
  chown "$usuario:$usuario" "/home/$usuario/.ssh/authorized_keys"
  chmod 600 "/home/$usuario/.ssh/authorized_keys"
}

echo "==> Usuário administrador ($ADMIN_USUARIO): sudo e docker"
criar_usuario "$ADMIN_USUARIO" "$ADMIN_CHAVE"
usermod -aG sudo,docker "$ADMIN_USUARIO"
if [[ -t 0 ]]; then
  echo "Defina a senha do $ADMIN_USUARIO (pedida pelo sudo; o login continua só por chave):"
  passwd "$ADMIN_USUARIO"
else
  echo "AVISO: sem terminal para definir a senha do sudo; rode depois: passwd $ADMIN_USUARIO" >&2
fi

echo "==> Usuário deploy: sem sudo, no grupo docker, chave presa ao script de deploy"
# O grupo docker equivale a root. Por isso a chave do deploy só executa o script de deploy: sem shell,
# sem túnel e sem terminal, mesmo se ela vazar.
criar_usuario deploy \
  "command=\"$SCRIPT_DEPLOY\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $DEPLOY_CHAVE"
usermod -aG docker deploy

echo "==> SSH só por chave e sem root"
# O nome começa com 01 para valer antes dos arquivos da imagem (ex.: 50-cloud-init.conf)
cat > /etc/ssh/sshd_config.d/01-plataforma.conf <<SSH
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AllowUsers $ADMIN_USUARIO deploy
SSH
# No Ubuntu 24.04 o ssh sobe por socket: /run/sshd só existe com o serviço ativo, e o sshd -t exige a pasta
mkdir -p /run/sshd
sshd -t
# Com o socket, cada conexão nova já lê a configuração; o reload só vale se o serviço estiver ativo
systemctl try-reload-or-restart ssh

echo "==> Firewall (ufw): só 22, 80 e 443"
# O Docker contorna o ufw nas portas que publica: por isso só o Caddy publica portas
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> fail2ban e atualizações automáticas de segurança"
systemctl enable --now fail2ban
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Swap de 2 GB"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/90-swappiness.conf
sysctl -q --system

echo "==> Pastas da plataforma em $BASE"
install -d -m 750 -o deploy -g deploy "$BASE" "$BASE/caddy" "$BASE/producao" "$BASE/homolog"

cat <<FIM

Pronto. ANTES de fechar esta sessão, abra outro terminal e confirme:
  ssh $ADMIN_USUARIO@<ip-da-vps>        (deve entrar sem senha)
  sudo -v                               (deve aceitar a senha definida)
Depois disso, root e senha deixam de entrar por SSH.
FIM
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

Só o Caddy publica portas. Ele chega à API e ao Garage de cada ambiente pela rede do ambiente, pelos
apelidos `producao-api`, `producao-garage`, `homolog-api` e `homolog-garage`.

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
curl -s -o /dev/null -w "%{http_code}\n" https://aluno.celke.uno/v3/api-docs   # 404
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
copie os arquivos novos de `infra/caddy/` para `/opt/plataforma-cursos/caddy/` e, como `deploy`:

```bash
cd /opt/plataforma-cursos
docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d --force-recreate
```

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

### 2. Script de deploy

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

ANTERIOR=$(tag_atual)
log "início (versão atual: ${ANTERIOR:-nenhuma})"

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

### 3. Workflow de deploy

`.github/workflows/deploy.yml`:

`````yaml
# Deploy de um ambiente na VPS, chamado pelo pipeline depois do e2e. Secrets: VPS_HOST, VPS_SSH_KEY
# (chave privada do usuário deploy) e VPS_KNOWN_HOSTS (impressão digital da VPS, de ssh-keyscan). O
# environment (homolog ou production) permite exigir aprovação antes do deploy da produção.
name: Deploy

on:
  workflow_call:
    inputs:
      ambiente:
        description: producao ou homolog
        required: true
        type: string

permissions:
  contents: read

jobs:
  deploy:
    name: Deploy (${{ inputs.ambiente }})
    runs-on: ubuntu-latest
    timeout-minutes: 20
    environment: ${{ inputs.ambiente == 'producao' && 'production' || 'homolog' }}
    # Um deploy por ambiente; nunca cancelado no meio
    concurrency:
      group: deploy-${{ inputs.ambiente }}
      cancel-in-progress: false
    steps:
      - uses: actions/checkout@v7

      - name: Chave SSH e impressão digital da VPS
        env:
          SSH_KEY: ${{ secrets.VPS_SSH_KEY }}
          KNOWN_HOSTS: ${{ secrets.VPS_KNOWN_HOSTS }}
        run: |
          install -d -m 700 ~/.ssh
          printf '%s\n' "$SSH_KEY" > ~/.ssh/deploy
          chmod 600 ~/.ssh/deploy
          printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts

      # A chave do deploy só executa o deploy.sh na VPS: os arquivos de compose vão pela entrada
      # e o script recebe o ambiente e o SHA. Se a nova versão não ficar saudável, ele volta para a
      # anterior e este passo falha.
      - name: Implantar
        env:
          HOST: ${{ secrets.VPS_HOST }}
          AMBIENTE: ${{ inputs.ambiente }}
        run: |
          tar -C infra -cz compose.prod.yml compose.homolog.yml garage/garage.toml \
            | ssh -i ~/.ssh/deploy -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes \
                deploy@"$HOST" "$AMBIENTE $GITHUB_SHA"
`````

## Antes de executar

Etapas 1.1 a 1.8 concluídas.



## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### 1. Script de deploy e acesso às imagens

```text
==> DEPLOY AUTOMÁTICO — ETAPA 1 DE 3: NA VPS (docs/vps.md, passos 5 e 7)

1. Instale o script de deploy (do seu computador, na raiz do repositório):

     scp infra/scripts/deploy.sh <seu-usuario>@<ip-da-vps>:/tmp/
     ssh <seu-usuario>@<ip-da-vps> 'sudo install -o root -g root -m 755 /tmp/deploy.sh /usr/local/bin/plataforma-cursos-deploy && rm /tmp/deploy.sh'

2. No GitHub, crie um token clássico só com read:packages (Settings → Developer settings →
   Personal access tokens) e, na VPS, como deploy (sudo -iu deploy):

     echo "<token>" | docker login ghcr.io -u <usuario-github> --password-stdin
```

### 2. Secrets e variável no GitHub

```text
==> DEPLOY AUTOMÁTICO — ETAPA 2 DE 3: GITHUB (docs/vps.md, passo 8)

Com a conta dona do repositório, em Settings → Secrets and variables → Actions:

  Secrets
    VPS_HOST          o IP da VPS
    VPS_SSH_KEY       o conteúdo de ~/.ssh/plataforma-cursos-deploy (a chave PRIVADA do deploy)
    VPS_KNOWN_HOSTS   a saída de: ssh-keyscan -t ed25519 <ip-da-vps>

  Variables
    DEPLOY_ATIVO      true   (sem ela, o pipeline pula os deploys)

Opcional: em Settings → Environments → production, ative "Required reviewers" para o deploy da
produção esperar a sua aprovação.
```

### 3. Primeira subida

```text
==> DEPLOY AUTOMÁTICO — ETAPA 3 DE 3: PRIMEIRA SUBIDA (docs/vps.md, passos 9 e 10)

1. Abra o PR de feature/base-projeto para homolog, espere o pipeline ficar verde e faça o merge:
   a homologação é implantada.
2. Valide a homologação e abra o PR de homolog para main: a produção é implantada.
3. Com os dois ambientes no ar, suba o Caddy na VPS, como deploy:

     cd /opt/plataforma-cursos
     docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d

4. Crie o administrador de cada ambiente: comandos no passo 9 do docs/vps.md.
5. Confira com os comandos do passo 10 (health, HSTS, portas fechadas, root recusado).
```

## Verificação

- `shellcheck infra/scripts/*.sh` e `actionlint` sem avisos.
- Simulação local do deploy (com as imagens do e2e já construídas): crie uma pasta com `homolog/.env`
  (valores do `infra/e2e.env`, `AMBIENTE=homolog`, `TAG=` vazio, `IMAGEM_API=plataforma-cursos-api`,
  `IMAGEM_WORKER=plataforma-cursos-worker`), marque `plataforma-cursos-{api,worker}:e2e` com um SHA de teste e
  rode `tar -C infra -cz compose.prod.yml compose.homolog.yml garage/garage.toml | DEPLOY_BASE=<pasta> infra/scripts/deploy.sh homolog <sha>`:
  a versão sobe saudável. Com uma imagem da API que sai na hora (`ENTRYPOINT ["false"]`) e outro SHA, o
  script registra a falha, volta para a versão anterior e termina com código 1.
- Argumentos inválidos (`staging <sha>`, SHA curto, argumento a mais, `homolog <sha>; rm -rf /`) e um
  segundo deploy simultâneo são recusados.
- Na VPS e no GitHub, siga os passos 5 (script de deploy), 8 e 9 do `docs/vps.md`; o merge na `homolog`
  implanta a homologação.

## Depois de executar

```bash
git add .github/ docs/ infra/
git commit -m "ci: adicionar deploy automático com rollback"
```
