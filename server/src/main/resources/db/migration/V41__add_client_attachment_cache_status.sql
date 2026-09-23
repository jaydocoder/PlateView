CREATE TABLE client_attachment_cache_status (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_instance_id VARCHAR(128) NOT NULL,
    manifest_revision BIGINT NOT NULL DEFAULT 0,
    attachment_id BIGINT NOT NULL REFERENCES work_order_images(id) ON DELETE CASCADE,
    variant VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    downloaded_bytes BIGINT NOT NULL DEFAULT 0,
    expected_size BIGINT,
    sha256 VARCHAR(64),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, client_instance_id, attachment_id, variant),
    CONSTRAINT ck_client_attachment_cache_status_status CHECK (status IN ('DISCOVERED','WAITING_NETWORK','DOWNLOADING','PAUSED','RETRY_WAIT','COMPLETED','FAILED','REVOKED'))
);
CREATE INDEX idx_client_attachment_cache_status_user ON client_attachment_cache_status(user_id, updated_at DESC);
