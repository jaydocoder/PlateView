#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly ENV_FILE="$APP_DIR/.env"
readonly BACKUP_DIR="$APP_DIR/backups"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly LOCK_FILE="$RUNTIME_DIR/database-backup.lock"
readonly POSTGRES_CONTAINER="${PLATEVIEW_POSTGRES_CONTAINER:-plateview-postgres-1}"
DAILY_RETENTION_DAYS="${PLATEVIEW_BACKUP_DAILY_RETENTION_DAYS:-14}"
WEEKLY_RETENTION_WEEKS="${PLATEVIEW_BACKUP_WEEKLY_RETENTION_WEEKS:-8}"
MONTHLY_RETENTION_MONTHS="${PLATEVIEW_BACKUP_MONTHLY_RETENTION_MONTHS:-12}"
MAX_USAGE_PERCENT="${PLATEVIEW_BACKUP_MAX_USAGE_PERCENT:-85}"

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
DAILY_RETENTION_DAYS="${PLATEVIEW_BACKUP_DAILY_RETENTION_DAYS:-14}"
WEEKLY_RETENTION_WEEKS="${PLATEVIEW_BACKUP_WEEKLY_RETENTION_WEEKS:-8}"
MONTHLY_RETENTION_MONTHS="${PLATEVIEW_BACKUP_MONTHLY_RETENTION_MONTHS:-12}"
MAX_USAGE_PERCENT="${PLATEVIEW_BACKUP_MAX_USAGE_PERCENT:-85}"
: "${POSTGRES_DB:?生产环境缺少 POSTGRES_DB}"
: "${POSTGRES_USER:?生产环境缺少 POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?生产环境缺少 POSTGRES_PASSWORD}"
usage_percent=$(df -P "$BACKUP_DIR" | awk 'NR==2 {gsub("%", "", $5); print $5}')
[[ "$usage_percent" =~ ^[0-9]+$ ]] || die "无法读取备份目录磁盘使用率"
(( usage_percent < MAX_USAGE_PERCENT )) || die "备份目录磁盘使用率达到 ${usage_percent}%，超过 ${MAX_USAGE_PERCENT}% 阈值，停止备份"

[[ "$(docker inspect -f '{{.State.Health.Status}}' "$POSTGRES_CONTAINER" 2>/dev/null || true)" == "healthy" ]] \
    || die "PostgreSQL 健康检查未通过"

backup_file="$BACKUP_DIR/plateview-daily-$(date -u +%Y%m%dT%H%M%SZ).dump"
log "开始低优先级数据库备份：$backup_file"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
    nice -n 15 pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$backup_file"
[[ -s "$backup_file" ]] || die "备份文件为空"
sha256sum "$backup_file" | tee "$backup_file.sha256"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" pg_restore --list < "$backup_file" >/dev/null \
    || die "备份归档校验失败，禁止清理旧备份"

# 分层保留：每日保留最近N天，周日备份按周保留，月初备份按月保留。
prune_before_daily=$(date -u -d "-${DAILY_RETENTION_DAYS} days" +%s)
prune_before_weekly=$(date -u -d "-${WEEKLY_RETENTION_WEEKS} weeks" +%s)
prune_before_monthly=$(date -u -d "-${MONTHLY_RETENTION_MONTHS} months" +%s)
for file in "$BACKUP_DIR"/plateview-*.dump; do
    [[ -f "$file" ]] || continue
    timestamp=$(stat -c %Y "$file")
    day=$(date -u -d "@$timestamp" +%u)
    month_day=$(date -u -d "@$timestamp" +%d)
    keep=0
    (( timestamp >= prune_before_daily )) && keep=1
    (( day == 7 && timestamp >= prune_before_weekly )) && keep=1
    (( month_day == 01 && timestamp >= prune_before_monthly )) && keep=1
    if (( keep == 0 )); then
        rm -f -- "$file" "$file.sha256"
        log "已清理过期备份：$(basename "$file")"
    fi
done
log "数据库备份完成"
