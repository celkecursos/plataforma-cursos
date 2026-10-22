#!/usr/bin/env bash
# Restaura um ambiente a partir do backup restic (o mais recente, ou o snapshot informado de cada
# parte). Para a API e o worker, recria o banco a partir do dump, troca os arquivos do Garage pelos do
# backup (com os metadados do snapshot) e sobe tudo de novo. Apaga os dados atuais do ambiente.
#
#   restaurar.sh homolog
#   restaurar.sh producao <snapshot-postgres> <snapshot-garage>
#
# Mesma configuração do backup.sh (backup.env, PROJETO e RESTIC_EXTRA para teste local).
set -euo pipefail

AMBIENTE="${1:?uso: restaurar.sh <producao|homolog> [snapshot-postgres] [snapshot-garage]}"
SNAP_POSTGRES="${2:-latest}"
SNAP_GARAGE="${3:-latest}"
BASE="${BASE:-/opt/plataforma-cursos}"
ENV_BACKUP="${ENV_BACKUP:-$BASE/backup.env}"
PROJETO="${PROJETO:-plataforma-cursos-$AMBIENTE}"
RESTIC_IMAGEM="restic/restic:0.19.1"
read -r -a RESTIC_EXTRA <<< "${RESTIC_EXTRA:-}"

if [[ "${CONFIRMAR:-}" != "sim" ]]; then
    read -r -p "Isto apaga os dados atuais de $AMBIENTE. Digite o nome do ambiente para continuar: " resposta
    [[ "$resposta" == "$AMBIENTE" ]] || { echo "Cancelado."; exit 1; }
fi

container() {
    docker ps -aq --filter "label=com.docker.compose.project=$PROJETO" --filter "label=com.docker.compose.service=$1"
}

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
API=$(container api)
WORKER=$(container worker)
VOLUME_GARAGE="${PROJETO}_garage"
[[ -n "$POSTGRES" && -n "$GARAGE" ]] || { echo "PostgreSQL ou Garage do projeto $PROJETO não encontrado." >&2; exit 1; }

# Se algo falhar no meio, API e worker ficam parados de propósito: subir com o banco pela metade é pior
trap 'echo "Restauração interrompida. API e worker continuam parados; corrija e rode de novo." >&2' ERR

echo "==> Parando API e worker"
docker stop $API $WORKER >/dev/null

echo "==> PostgreSQL: recriando a partir do dump ($SNAP_POSTGRES)"
docker exec "$POSTGRES" sh -c 'dropdb -U "$POSTGRES_USER" --force --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
if [[ "$SNAP_POSTGRES" == "latest" ]]; then
    restic -- dump --tag "$AMBIENTE,postgres" latest /postgres.dump
else
    restic -- dump "$SNAP_POSTGRES" /postgres.dump
fi | docker exec -i "$POSTGRES" sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error'

echo "==> Garage: trocando os arquivos pelos do backup ($SNAP_GARAGE)"
docker stop "$GARAGE" >/dev/null
docker run --rm -v "$VOLUME_GARAGE:/garage" alpine:3.22 sh -c 'rm -rf /garage/meta /garage/data'
if [[ "$SNAP_GARAGE" == "latest" ]]; then
    restic -v "$VOLUME_GARAGE:/garage" -- restore latest --tag "$AMBIENTE,garage" --target /
else
    restic -v "$VOLUME_GARAGE:/garage" -- restore "$SNAP_GARAGE" --target /
fi
# Os metadados voltam do snapshot consistente gravado pelo backup
docker run --rm -v "$VOLUME_GARAGE:/garage" alpine:3.22 sh -c \
    'ultimo=$(ls -1 /garage/meta/snapshots | sort | tail -n 1) && cp "/garage/meta/snapshots/$ultimo/db.sqlite" /garage/meta/db.sqlite'
docker start "$GARAGE" >/dev/null

echo "==> Subindo API e worker"
docker start $API $WORKER >/dev/null
echo "Restauração de $AMBIENTE concluída. Confira o health check e uma aula com vídeo."
