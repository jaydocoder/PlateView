CREATE TABLE client_catalog_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    vehicle_revision BIGINT NOT NULL,
    work_order_revision BIGINT NOT NULL,
    wechat_message_revision BIGINT NOT NULL,
    attachment_manifest_revision BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO client_catalog_state(
    id,
    vehicle_revision,
    work_order_revision,
    wechat_message_revision,
    attachment_manifest_revision
)
SELECT 1, vehicle.revision, work_order.revision, work_order.revision, attachment.revision
FROM vehicle_catalog_state vehicle
CROSS JOIN work_order_catalog_state work_order
CROSS JOIN work_order_attachment_manifest_state attachment
WHERE vehicle.id = 1 AND work_order.id = 1 AND attachment.id = 1;

CREATE OR REPLACE FUNCTION mirror_vehicle_catalog_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE client_catalog_state
    SET vehicle_revision = NEW.revision,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mirror_vehicle_catalog_revision
    AFTER UPDATE OF revision ON vehicle_catalog_state
    FOR EACH ROW EXECUTE FUNCTION mirror_vehicle_catalog_revision();

CREATE OR REPLACE FUNCTION mirror_work_order_catalog_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE client_catalog_state
    SET work_order_revision = NEW.revision,
        wechat_message_revision = NEW.revision,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mirror_work_order_catalog_revision
    AFTER UPDATE OF revision ON work_order_catalog_state
    FOR EACH ROW EXECUTE FUNCTION mirror_work_order_catalog_revision();

CREATE OR REPLACE FUNCTION mirror_attachment_manifest_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE client_catalog_state
    SET attachment_manifest_revision = NEW.revision,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mirror_attachment_manifest_revision
    AFTER UPDATE OF revision ON work_order_attachment_manifest_state
    FOR EACH ROW EXECUTE FUNCTION mirror_attachment_manifest_revision();

CREATE INDEX idx_work_order_records_catalog_revision_id
    ON work_order_records(catalog_revision, id);

CREATE INDEX idx_wechat_messages_received_id
    ON wechat_messages(received_at, id);
