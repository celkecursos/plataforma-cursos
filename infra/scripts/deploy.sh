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
