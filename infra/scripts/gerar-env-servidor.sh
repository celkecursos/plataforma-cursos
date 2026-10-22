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

JWT_SEGREDO=$(openssl rand -base64 48)
AUTH_ORIGENS=$ALUNO,$ADM
FRONT_ALUNO_URL=$ALUNO

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
