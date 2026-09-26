#!/usr/bin/env bash
set -Eeuo pipefail

# 在生产主机上验证备份文件可读取，并在临时数据库中恢复关键结构。
readonly BACKUP_FILE="${1:?用法：$0 <备份文件> [容器名]}"
readonly POSTGRES_CONTAINER="${2:-${PLATEVIEW_POSTGRES_CONTAINER:-plateview-postgres-1}}"
readonly VERIFY_DB="plateview_backup_verify_${RANDOM}_$(date +%s)"

[[ -s "$BACKUP_FILE" ]] || { printf '备份文件不存在或为空：%s\n' "$BACKUP_FILE" >&2; exit 1; }
sha256sum "$BACKUP_FILE"
docker exec "$POSTGRES_CONTAINER" pg_restore --list - < "$BACKUP_FILE" >/dev/null

set -a
if [[ -r "${PLATEVIEW_ENV_FILE:-/opt/plateview/.env}" ]]; then
    # shellcheck disable=SC1090
    . "${PLATEVIEW_ENV_FILE:-/opt/plateview/.env}"
fi
set +a
: "${POSTGRES_USER:?缺少 POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?缺少 POSTGRES_PASSWORD}"

cleanup() {
    docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" dropdb -U "$POSTGRES_USER" --if-exists "$VERIFY_DB" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" createdb -U "$POSTGRES_USER" "$VERIFY_DB"
docker exec -i -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" pg_restore -U "$POSTGRES_USER" -d "$VERIFY_DB" --no-owner --exit-on-error < "$BACKUP_FILE"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$VERIFY_DB" -v ON_ERROR_STOP=1 -Atc \
    "SELECT 'wechat_messages=' || count(*) FROM wechat_messages; SELECT 'work_order_records=' || count(*) FROM work_order_records; SELECT 'users=' || count(*) FROM users; SELECT 'vehicles=' || count(*) FROM vehicles;"
printf '备份恢复验证通过：%s\n' "$BACKUP_FILE"
