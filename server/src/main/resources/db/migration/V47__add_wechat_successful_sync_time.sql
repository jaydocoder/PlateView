ALTER TABLE wechat_sources
    ADD COLUMN last_successful_sync_at TIMESTAMPTZ;

UPDATE wechat_sources
SET last_successful_sync_at = last_uploaded_at
WHERE last_uploaded_at IS NOT NULL;
