CREATE TABLE vehicle_catalog_changes (
    revision BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (revision, entity_id),
    CONSTRAINT ck_vehicle_catalog_change_operation CHECK (operation IN ('UPSERT', 'DELETE', 'REVOKE'))
);

CREATE TABLE work_order_catalog_changes (
    revision BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (revision, entity_id),
    CONSTRAINT ck_work_order_catalog_change_operation CHECK (operation IN ('UPSERT', 'DELETE', 'REVOKE'))
);

CREATE TABLE wechat_message_catalog_changes (
    revision BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (revision, entity_id),
    CONSTRAINT ck_wechat_message_catalog_change_operation CHECK (operation IN ('UPSERT', 'DELETE', 'REVOKE'))
);

CREATE INDEX idx_vehicle_catalog_changes_time ON vehicle_catalog_changes(changed_at);
CREATE INDEX idx_work_order_catalog_changes_time ON work_order_catalog_changes(changed_at);
CREATE INDEX idx_wechat_message_catalog_changes_time ON wechat_message_catalog_changes(changed_at);

DROP TRIGGER trg_vehicle_catalog_revision ON vehicles;

CREATE OR REPLACE FUNCTION record_vehicle_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
    target_id BIGINT;
    target_operation VARCHAR(16);
BEGIN
    target_id := COALESCE(NEW.id, OLD.id);
    IF TG_OP = 'DELETE' THEN
        target_operation := 'DELETE';
    ELSIF NEW.status = 'DELETED' THEN
        target_operation := 'DELETE';
    ELSE
        target_operation := 'UPSERT';
    END IF;
    UPDATE vehicle_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
    INSERT INTO vehicle_catalog_changes(revision, entity_id, operation)
    VALUES (next_revision, target_id, target_operation);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicle_catalog_revision
AFTER INSERT OR UPDATE OR DELETE ON vehicles
FOR EACH ROW EXECUTE FUNCTION record_vehicle_catalog_change();

CREATE OR REPLACE FUNCTION record_vehicle_profile_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
    target_id BIGINT;
BEGIN
    target_id := COALESCE(NEW.vehicle_id, OLD.vehicle_id);
    IF EXISTS (SELECT 1 FROM vehicles WHERE id = target_id) THEN
        UPDATE vehicle_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
        INSERT INTO vehicle_catalog_changes(revision, entity_id, operation)
        VALUES (next_revision, target_id, 'UPSERT');
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resident_profile_catalog_change
AFTER INSERT OR UPDATE OR DELETE ON resident_profiles
FOR EACH ROW EXECUTE FUNCTION record_vehicle_profile_catalog_change();

CREATE TRIGGER trg_long_term_profile_catalog_change
AFTER INSERT OR UPDATE OR DELETE ON long_term_profiles
FOR EACH ROW EXECUTE FUNCTION record_vehicle_profile_catalog_change();

CREATE OR REPLACE FUNCTION record_work_order_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE work_order_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
        INSERT INTO work_order_catalog_changes(revision, entity_id, operation)
        VALUES (next_revision, OLD.id, 'DELETE');
    ELSIF TG_OP = 'INSERT' OR OLD.catalog_revision IS DISTINCT FROM NEW.catalog_revision THEN
        INSERT INTO work_order_catalog_changes(revision, entity_id, operation)
        VALUES (NEW.catalog_revision, NEW.id, CASE WHEN NEW.status = 'VOID' THEN 'REVOKE' ELSE 'UPSERT' END)
        ON CONFLICT (revision, entity_id) DO UPDATE SET operation = EXCLUDED.operation, changed_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_order_catalog_change
AFTER INSERT OR UPDATE OR DELETE ON work_order_records
FOR EACH ROW EXECUTE FUNCTION record_work_order_catalog_change();

CREATE OR REPLACE FUNCTION record_wechat_message_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE work_order_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
        INSERT INTO wechat_message_catalog_changes(revision, entity_id, operation)
        VALUES (next_revision, OLD.id, 'DELETE');
    ELSIF TG_OP = 'INSERT' OR OLD.catalog_revision IS DISTINCT FROM NEW.catalog_revision THEN
        INSERT INTO wechat_message_catalog_changes(revision, entity_id, operation)
        VALUES (NEW.catalog_revision, NEW.id, 'UPSERT')
        ON CONFLICT (revision, entity_id) DO UPDATE SET operation = EXCLUDED.operation, changed_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wechat_message_catalog_change
AFTER INSERT OR UPDATE OR DELETE ON wechat_messages
FOR EACH ROW EXECUTE FUNCTION record_wechat_message_catalog_change();

INSERT INTO vehicle_catalog_changes(revision, entity_id, operation)
SELECT state.revision, vehicle.id, CASE WHEN vehicle.status = 'DELETED' THEN 'DELETE' ELSE 'UPSERT' END
FROM vehicles vehicle
CROSS JOIN vehicle_catalog_state state
WHERE state.id = 1
ON CONFLICT DO NOTHING;

INSERT INTO work_order_catalog_changes(revision, entity_id, operation)
SELECT catalog_revision, id, CASE WHEN status = 'VOID' THEN 'REVOKE' ELSE 'UPSERT' END
FROM work_order_records
ON CONFLICT DO NOTHING;

INSERT INTO wechat_message_catalog_changes(revision, entity_id, operation)
SELECT catalog_revision, id, 'UPSERT'
FROM wechat_messages
ON CONFLICT DO NOTHING;
