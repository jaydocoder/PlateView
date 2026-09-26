#!/bin/sh
set -eu

BACKUP_FILE="${1:?用法：$0 <备份文件> <期望SHA-256>}"
EXPECTED_SHA256="${2:?用法：$0 <备份文件> <期望SHA-256>}"
BACKUP_DIR="${PLATEVIEW_BACKUP_DIR:-/opt/plateview/backups}"
DB_HOST="${DATABASE_HOST:-postgres}"
DB_PORT="${DATABASE_PORT:-5432}"
DB_NAME="${DATABASE_NAME:-${DATABASE_DB:-${POSTGRES_DB:-plateview}}}"
DB_USER="${DATABASE_USERNAME:-${POSTGRES_USER:-plateview}}"
DB_PASSWORD="${DATABASE_PASSWORD:-${POSTGRES_PASSWORD:?缺少数据库密码}}"

[ -s "$BACKUP_FILE" ] || { printf '备份文件不存在或为空：%s\n' "$BACKUP_FILE" >&2; exit 1; }
actual_sha256=$(sha256sum "$BACKUP_FILE" | awk '{print $1}')
[ "$actual_sha256" = "$EXPECTED_SHA256" ] || { printf '备份SHA-256不匹配\n' >&2; exit 1; }

export PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGUSER="$DB_USER" PGDATABASE="$DB_NAME" PGPASSWORD="$DB_PASSWORD"
pg_restore --list "$BACKUP_FILE" >/dev/null

emergency_backup="$BACKUP_DIR/pre-restore-$(date -u +%Y%m%dT%H%M%SZ).dump"
pg_dump -Fc > "$emergency_backup"
sha256sum "$emergency_backup" > "$emergency_backup.sha256"

# 账号、当前会话和管理员审计记录属于运行态数据，不能被历史备份覆盖。
# pg_restore 没有 --exclude-table 参数，因此从目录清单中排除这些表的所有
# 数据、约束、索引和触发器条目；其余业务表按备份恢复。
restore_list="$BACKUP_DIR/restore-$(date -u +%Y%m%dT%H%M%SZ).list"
pg_restore --list "$BACKUP_FILE" \
    | grep -vE 'users|refresh_sessions|audit_logs|update_updated_at_column' > "$restore_list"
pg_restore --clean --if-exists --no-owner --exit-on-error \
    --use-list="$restore_list" --dbname="$PGDATABASE" "$BACKUP_FILE"
rm -f "$restore_list"
psql -v ON_ERROR_STOP=1 <<'SQL'
ALTER TABLE wechat_rebuild_runs DROP CONSTRAINT IF EXISTS ck_wechat_rebuild_status;
ALTER TABLE wechat_rebuild_runs ADD CONSTRAINT ck_wechat_rebuild_status CHECK (
    status IN ('PREVIEW', 'BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING', 'COMPLETED', 'FAILED', 'CANCELLED')
);
DROP INDEX IF EXISTS uq_wechat_rebuild_active;
CREATE UNIQUE INDEX uq_wechat_rebuild_active
    ON wechat_rebuild_runs((status IN ('LOCKED', 'REBUILDING', 'VERIFYING')))
    WHERE status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING');
UPDATE client_catalog_state
SET rebuild_generation = rebuild_generation + 1
WHERE id = 1;
UPDATE wechat_rebuild_runs
SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, last_error = NULL
WHERE status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING');
SQL

printf '数据库恢复完成：%s\n' "$BACKUP_FILE"
