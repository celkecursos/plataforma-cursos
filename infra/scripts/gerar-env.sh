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
  -e "s|^GARAGE_RPC_SECRET=.*|GARAGE_RPC_SECRET=$(hex 32)|" \
  -e "s|^GARAGE_ADMIN_TOKEN=.*|GARAGE_ADMIN_TOKEN=$(hex 32)|" \
  -e "s|^S3_ACCESS_KEY=.*|S3_ACCESS_KEY=GK$(hex 12)|" \
  -e "s|^S3_SECRET_KEY=.*|S3_SECRET_KEY=$(hex 32)|" \
  "$INFRA/.env.example" > "$DESTINO"

chmod 600 "$DESTINO"
echo "infra/.env criado com segredos aleatórios."
