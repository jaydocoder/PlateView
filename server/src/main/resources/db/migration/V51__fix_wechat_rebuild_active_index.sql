DROP INDEX IF EXISTS uq_wechat_rebuild_active;

CREATE UNIQUE INDEX uq_wechat_rebuild_active
    ON wechat_rebuild_runs((status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING')))
    WHERE status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING');
