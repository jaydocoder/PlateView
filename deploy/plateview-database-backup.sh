#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly ENV_FILE="$APP_DIR/.env"
readonly BACKUP_DIR="$APP_DIR/backups"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly LOCK_FILE="$RUNTIME_DIR/database-backup.lock"
readonly POSTGRES_CONTAINER="${PLATEVIEW_POSTGRES_CONTAINER:-plateview-postgres-1}"
readonly RETENTION_DAYS="${PLATEVIEW_BACKUP_RETENTION_DAYS:-14}"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "失败：$*"; exit 1; }

mkdir -p "$BACKUP_DIR" "$RUNTIME_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || { log "已有数据库备份正在运行，跳过本次执行"; exit 0; }
[[ -r "$ENV_FILE" ]] || die "生产环境文件不存在：$ENV_FILE"

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
: "${POSTGRES_DB:?生产环境缺少 POSTGRES_DB}"
: "${POSTGRES_USER:?生产环境缺少 POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?生产环境缺少 POSTGRES_PASSWORD}"

[[ "$(docker inspect -f '{{.State.Health.Status}}' "$POSTGRES_CONTAINER" 2>/dev/null || true)" == "healthy" ]] \
    || die "PostgreSQL 健康检查未通过"

backup_file="$BACKUP_DIR/plateview-daily-$(date -u +%Y%m%dT%H%M%SZ).dump"
log "开始低优先级数据库备份：$backup_file"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    nice -n 15 pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$backup_file"
sha256sum "$backup_file"
find "$BACKUP_DIR" -type f -name 'plateview-*.dump' -mtime +"$RETENTION_DAYS" -delete
log "数据库备份完成"
