ALTER TABLE wechat_rebuild_runs
    DROP CONSTRAINT IF EXISTS ck_wechat_rebuild_status;

ALTER TABLE wechat_rebuild_runs
    ADD CONSTRAINT ck_wechat_rebuild_status CHECK (
        status IN (
            'PREVIEW',
            'BACKUP_VERIFYING',
            'LOCKED',
            'CLEANING',
            'REBUILDING',
            'VERIFYING',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        )
    );
