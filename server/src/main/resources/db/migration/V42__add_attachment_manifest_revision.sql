CREATE TABLE work_order_attachment_manifest_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    revision BIGINT NOT NULL DEFAULT 1
);

INSERT INTO work_order_attachment_manifest_state(id, revision) VALUES (1, 1);

CREATE OR REPLACE FUNCTION bump_work_order_attachment_manifest_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE work_order_attachment_manifest_state SET revision = revision + 1 WHERE id = 1;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_order_attachment_manifest_revision
    AFTER INSERT OR UPDATE OR DELETE ON work_order_images
    FOR EACH ROW EXECUTE FUNCTION bump_work_order_attachment_manifest_revision();

ALTER TABLE client_attachment_cache_status
    DROP CONSTRAINT ck_client_attachment_cache_status_status;

ALTER TABLE client_attachment_cache_status
    ADD CONSTRAINT ck_client_attachment_cache_status_status CHECK (
        status IN ('DISCOVERED','WAITING_NETWORK','DOWNLOADING','PAUSED','RETRY_WAIT','COMPLETED','FAILED','REVOKED','SOURCE_UNAVAILABLE')
    );
