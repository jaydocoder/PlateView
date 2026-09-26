#!/usr/bin/env bash
set -Eeuo pipefail

readonly BACKUP_FILE="${1:?用法：$0 <备份文件> <期望SHA-256>}"
readonly EXPECTED_SHA256="${2:?用法：$0 <备份文件> <期望SHA-256>}"
readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly ENV_FILE="${PLATEVIEW_ENV_FILE:-$APP_DIR/.env}"
readonly POSTGRES_CONTAINER="${PLATEVIEW_POSTGRES_CONTAINER:-plateview-postgres-1}"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly LOCK_FILE="$RUNTIME_DIR/database-restore.lock"

[[ -r "$ENV_FILE" ]] || { printf '环境文件不存在：%s\n' "$ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
: "${POSTGRES_DB:?缺少 POSTGRES_DB}"
: "${POSTGRES_USER:?缺少 POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?缺少 POSTGRES_PASSWORD}"
[[ -s "$BACKUP_FILE" ]] || { printf '备份文件不存在或为空：%s\n' "$BACKUP_FILE" >&2; exit 1; }

actual_sha256=$(sha256sum "$BACKUP_FILE" | awk '{print $1}')
[[ "$actual_sha256" == "$EXPECTED_SHA256" ]] || { printf '备份SHA-256不匹配\n' >&2; exit 1; }
docker exec "$POSTGRES_CONTAINER" pg_restore --list < "$BACKUP_FILE" >/dev/null

mkdir -p "$RUNTIME_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || { printf '已有数据库恢复任务运行中\n' >&2; exit 1; }

emergency_backup="$APP_DIR/backups/pre-restore-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$emergency_backup"
sha256sum "$emergency_backup" > "$emergency_backup.sha256"

docker exec -i -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --exit-on-error < "$BACKUP_FILE"

# 恢复的备份可能包含旧的代次和未完成批次。恢复后必须生成更高代次，
# 让采集器清空本地游标并从最早记录重新上传，同时解除备份中的遗留维护状态。
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c \
    "UPDATE client_catalog_state SET rebuild_generation = rebuild_generation + 1 WHERE id = 1; UPDATE wechat_rebuild_runs SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, last_error = NULL WHERE status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING');"

docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atc \
    "SELECT count(*) FROM users; SELECT count(*) FROM vehicles; SELECT count(*) FROM wechat_messages;"
printf '数据库恢复完成：%s\n' "$BACKUP_FILE"
