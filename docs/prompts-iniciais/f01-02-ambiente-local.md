# Ambiente local com Docker

**Fase:** 1 — etapa 1.2
**Branch:** `feature/base-projeto`

## Prompt

Vamos criar o ambiente local da Plataforma de Cursos em `infra/`: um Docker Compose que sobe
PostgreSQL 18, Garage (S3), Mailpit, a API e o worker, sem nenhum segredo versionado. Não faça commit.

Decisões desta etapa:

- **Garage** em servidor único com `--single-node --default-bucket`: na primeira subida ele cria o
  layout, o bucket privado `midia` (prefixos `uploads/` e `videos/`) e a chave da aplicação a partir
  das variáveis `GARAGE_DEFAULT_*`. Os segredos do Garage vêm de `GARAGE_RPC_SECRET` e
  `GARAGE_ADMIN_TOKEN`, nunca do `garage.toml`.
- **Variáveis obrigatórias** com `${VARIAVEL:?mensagem}`: sem o `infra/.env`, o compose recusa subir e
  diz o que falta. A conexão do banco fica num bloco compartilhado (`x-banco`) pela API e pelo worker.
- **PostgreSQL 18** grava os dados em `/var/lib/postgresql/18/docker`, por isso o volume é montado em
  `/var/lib/postgresql`.
- **Worker** sobe depois da API, que aplica as migrations.
- **Portas** ligadas só em `127.0.0.1` e configuráveis no `.env`, para quando a porta padrão já
  estiver em uso na máquina.

Crie os arquivos abaixo com **exatamente** o conteúdo indicado e deixe o script executável
(`chmod +x infra/scripts/gerar-env.sh`).

`infra/compose.yml`:

`````yaml
# Ambiente local. Uso, na raiz do repositório:
#   ./infra/scripts/gerar-env.sh          (uma vez: cria infra/.env com segredos aleatórios)
#   docker compose -f infra/compose.yml up -d --build --wait
name: plataforma-cursos

# Conexão com o banco, compartilhada pela API e pelo worker
x-banco: &banco
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}

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
      <<: *banco
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
    environment:
      <<: *banco
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy

volumes:
  postgres:
  garage:
`````

`infra/garage/garage.toml`:

`````toml
# Garage em servidor único (sem replicação). Os segredos vêm do ambiente:
# GARAGE_RPC_SECRET e GARAGE_ADMIN_TOKEN.
metadata_dir = "/var/lib/garage/meta"
data_dir = "/var/lib/garage/data"
db_engine = "sqlite"
replication_factor = 1

rpc_bind_addr = "[::]:3901"
rpc_public_addr = "127.0.0.1:3901"

[s3_api]
s3_region = "garage"
api_bind_addr = "[::]:3900"

[admin]
api_bind_addr = "[::]:3903"
`````

`infra/.env.example`:

`````dotenv
# Copie para infra/.env (ou rode ./infra/scripts/gerar-env.sh, que gera os segredos).
# Nunca faça commit do infra/.env.

# PostgreSQL
POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=                 # openssl rand -hex 24

# Garage (S3)
GARAGE_RPC_SECRET=                 # openssl rand -hex 32
GARAGE_ADMIN_TOKEN=                # openssl rand -hex 32
S3_BUCKET=midia
S3_ACCESS_KEY=                     # GK + openssl rand -hex 12
S3_SECRET_KEY=                     # openssl rand -hex 32

# Portas no host (opcional; só mude se a porta padrão já estiver em uso)
# API_PORTA=8080
# POSTGRES_PORTA=5432
# S3_PORTA=3900
# MAILPIT_PORTA=8025
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
  -e "s|^GARAGE_RPC_SECRET=.*|GARAGE_RPC_SECRET=$(hex 32)|" \
  -e "s|^GARAGE_ADMIN_TOKEN=.*|GARAGE_ADMIN_TOKEN=$(hex 32)|" \
  -e "s|^S3_ACCESS_KEY=.*|S3_ACCESS_KEY=GK$(hex 12)|" \
  -e "s|^S3_SECRET_KEY=.*|S3_SECRET_KEY=$(hex 32)|" \
  "$INFRA/.env.example" > "$DESTINO"

chmod 600 "$DESTINO"
echo "infra/.env criado com segredos aleatórios."
`````

## Antes de executar

Etapa 1.1 concluída (backend com as imagens `api` e `worker`). Docker e `openssl` instalados.

## Verificação

Na raiz do repositório:

```bash
docker compose -f infra/compose.yml config -q      # sem infra/.env: lista as variáveis que faltam
./infra/scripts/gerar-env.sh                       # cria infra/.env (permissão 600)
./infra/scripts/gerar-env.sh                       # segunda vez: recusa sobrescrever
docker compose -f infra/compose.yml up -d --build --wait
docker compose -f infra/compose.yml ps
```

- `postgres`, `garage`, `mailpit` e `api` ficam `healthy`; o `worker` fica `Up` (sem reiniciar).
- `curl -s localhost:8080/actuator/health` responde `{"groups":["liveness","readiness"],"status":"UP"}`.
- `http://localhost:8080/swagger-ui/index.html` abre; `curl -i localhost:8080/api/x` responde 401.
- `http://localhost:8025` abre o painel do Mailpit.
- `docker compose -f infra/compose.yml exec garage /garage bucket list` mostra o bucket `midia`, e
  `... /garage key list` mostra a `default access key` com o ID de `S3_ACCESS_KEY`.
- `docker compose -f infra/compose.yml down` seguido de `up -d --wait` mantém o mesmo bucket (dados
  persistidos nos volumes).

Se a porta 8080 (ou outra) já estiver em uso, defina `API_PORTA=8090` (ou a variável da porta) no
`infra/.env` e use essa porta nas verificações.

## Depois de executar

```bash
git add infra/
git commit -m "Ambiente local com Docker"
```
