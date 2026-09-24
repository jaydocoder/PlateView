CREATE TABLE client_attachment_cache_summary (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_instance_id VARCHAR(128) NOT NULL,
    manifest_revision BIGINT NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    completed_pdf_count INTEGER NOT NULL DEFAULT 0,
    pending_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    source_unavailable_count INTEGER NOT NULL DEFAULT 0,
    total_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, client_instance_id)
);

INSERT INTO client_attachment_cache_summary(
    user_id, client_instance_id, manifest_revision, completed_count, completed_pdf_count,
    pending_count, failed_count, source_unavailable_count, total_bytes, updated_at
)
SELECT s.user_id,
       s.client_instance_id,
       MAX(s.manifest_revision),
       COUNT(*) FILTER (WHERE s.status = 'COMPLETED'),
       COUNT(*) FILTER (WHERE s.status = 'COMPLETED' AND i.attachment_kind = 'PDF'),
       COUNT(*) FILTER (WHERE s.status IN ('DISCOVERED','WAITING_NETWORK','DOWNLOADING','PAUSED','RETRY_WAIT')),
       COUNT(*) FILTER (WHERE s.status = 'FAILED'),
       COUNT(*) FILTER (WHERE s.status = 'SOURCE_UNAVAILABLE'),
       COALESCE(SUM(s.downloaded_bytes), 0),
       MAX(s.updated_at)
FROM client_attachment_cache_status s
LEFT JOIN work_order_images i ON i.id = s.attachment_id
GROUP BY s.user_id, s.client_instance_id
ON CONFLICT (user_id, client_instance_id) DO NOTHING;

CREATE INDEX idx_client_attachment_cache_summary_page
    ON client_attachment_cache_summary(updated_at DESC, user_id, client_instance_id);
