ALTER TABLE client_catalog_state
    ADD COLUMN rebuild_generation BIGINT NOT NULL DEFAULT 0;

CREATE TABLE wechat_rebuild_runs (
    run_id UUID PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(24) NOT NULL DEFAULT 'PREVIEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    backup_path TEXT,
    backup_sha256 VARCHAR(64),
    preview_report JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted_counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    file_report JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_error TEXT,
    CONSTRAINT ck_wechat_rebuild_status CHECK (
        status IN ('PREVIEW', 'BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE UNIQUE INDEX uq_wechat_rebuild_active
    ON wechat_rebuild_runs((status IN ('LOCKED', 'REBUILDING', 'VERIFYING')))
    WHERE status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING');

CREATE INDEX idx_wechat_rebuild_runs_created
    ON wechat_rebuild_runs(created_at DESC, run_id);
