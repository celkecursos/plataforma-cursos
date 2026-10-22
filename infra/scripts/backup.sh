#!/usr/bin/env bash
# Backup diário de um ambiente para fora da VPS, com restic (repositório S3 externo: Cloudflare R2 ou
# Backblaze B2): dump do PostgreSQL e os arquivos do Garage, com a cópia consistente dos metadados.
# Retenção: 7 diários, 4 semanais e 6 mensais.
#
#   backup.sh producao
#   backup.sh homolog
#
# Configuração em /opt/plataforma-cursos/backup.env (permissão 600): RESTIC_REPOSITORY,
# RESTIC_PASSWORD, AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY. Guarde a RESTIC_PASSWORD também fora da
# VPS: sem ela o backup não abre.
# Teste local: ENV_BACKUP=<arquivo> PROJETO=<projeto compose> RESTIC_EXTRA="-v <volume>:/repo".
set -euo pipefail

AMBIENTE="${1:?uso: backup.sh <producao|homolog>}"
BASE="${BASE:-/opt/plataforma-cursos}"
ENV_BACKUP="${ENV_BACKUP:-$BASE/backup.env}"
PROJETO="${PROJETO:-plataforma-cursos-$AMBIENTE}"
RESTIC_IMAGEM="restic/restic:0.19.1"
read -r -a RESTIC_EXTRA <<< "${RESTIC_EXTRA:-}"

[[ -r "$ENV_BACKUP" ]] || { echo "Arquivo de configuração não encontrado: $ENV_BACKUP" >&2; exit 1; }

container() {
    local id
    id=$(docker ps -q --filter "label=com.docker.compose.project=$PROJETO" --filter "label=com.docker.compose.service=$1")
    [[ -n "$id" ]] || { echo "Serviço $1 do projeto $PROJETO não está rodando." >&2; exit 1; }
    echo "$id"
}

# restic num container; volumes extras antes de "--"
restic() {
    local volumes=()
    while [[ $# -gt 0 && "$1" != "--" ]]; do volumes+=("$1"); shift; done
    shift
    docker run --rm -i --env-file "$ENV_BACKUP" --hostname "$AMBIENTE" \
        -v plataforma-cursos-restic-cache:/root/.cache/restic "${RESTIC_EXTRA[@]}" "${volumes[@]}" \
        "$RESTIC_IMAGEM" "$@"
}

POSTGRES=$(container postgres)
GARAGE=$(container garage)
VOLUME_GARAGE="${PROJETO}_garage"

echo "==> Repositório"
restic -- cat config >/dev/null 2>&1 || restic -- init

echo "==> PostgreSQL ($AMBIENTE)"
docker exec "$POSTGRES" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
    | restic -- backup --stdin --stdin-filename postgres.dump --tag "$AMBIENTE" --tag postgres

echo "==> Garage ($AMBIENTE): snapshot dos metadados e arquivos"
docker exec "$GARAGE" /garage meta snapshot >/dev/null
# Só o snapshot mais recente vai para o backup (os anteriores ocupariam espaço à toa)
docker run --rm -v "$VOLUME_GARAGE:/garage" alpine:3.22 sh -c \
    'cd /garage/meta/snapshots && ls -1 | sort | head -n -1 | xargs -r rm -rf'
restic -v "$VOLUME_GARAGE:/garage:ro" -- backup /garage \
    --exclude '/garage/meta/db.sqlite' --exclude '/garage/meta/db.sqlite-*' \
    --tag "$AMBIENTE" --tag garage

echo "==> Retenção"
restic -- forget --tag "$AMBIENTE" --group-by host,tags \
    --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune

echo "Backup de $AMBIENTE concluído."
