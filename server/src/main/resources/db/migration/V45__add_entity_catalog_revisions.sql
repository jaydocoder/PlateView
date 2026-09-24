ALTER TABLE vehicles
    ADD COLUMN catalog_revision BIGINT,
    ADD COLUMN created_catalog_revision BIGINT;

UPDATE vehicles
SET catalog_revision = state.revision,
    created_catalog_revision = state.revision
FROM vehicle_catalog_state state
WHERE state.id = 1;

ALTER TABLE vehicles
    ALTER COLUMN catalog_revision SET NOT NULL,
    ALTER COLUMN created_catalog_revision SET NOT NULL;

CREATE INDEX idx_vehicles_created_current_revision
    ON vehicles(created_catalog_revision, catalog_revision, id);

ALTER TABLE work_order_records
    ADD COLUMN created_catalog_revision BIGINT;

UPDATE work_order_records
SET created_catalog_revision = catalog_revision;

ALTER TABLE work_order_records
    ALTER COLUMN created_catalog_revision SET NOT NULL;

CREATE INDEX idx_work_order_records_created_current_revision
    ON work_order_records(created_catalog_revision, catalog_revision, id);

ALTER TABLE wechat_messages
    ADD COLUMN created_catalog_revision BIGINT;

UPDATE wechat_messages
SET created_catalog_revision = catalog_revision;

ALTER TABLE wechat_messages
    ALTER COLUMN created_catalog_revision SET NOT NULL;

CREATE INDEX idx_wechat_messages_created_current_revision
    ON wechat_messages(created_catalog_revision, catalog_revision, id);

ALTER TABLE vehicle_catalog_changes ADD COLUMN entity_created_revision BIGINT;
ALTER TABLE work_order_catalog_changes ADD COLUMN entity_created_revision BIGINT;
ALTER TABLE wechat_message_catalog_changes ADD COLUMN entity_created_revision BIGINT;

TRUNCATE vehicle_catalog_changes, work_order_catalog_changes, wechat_message_catalog_changes;

INSERT INTO vehicle_catalog_changes(revision, entity_id, operation, entity_created_revision)
SELECT state.revision, vehicle.id, 'UPSERT', vehicle.created_catalog_revision
FROM vehicles vehicle
CROSS JOIN vehicle_catalog_state state
WHERE state.id = 1 AND vehicle.status <> 'DELETED';

INSERT INTO work_order_catalog_changes(revision, entity_id, operation, entity_created_revision)
SELECT state.revision, record.id, CASE WHEN record.status = 'VOID' THEN 'REVOKE' ELSE 'UPSERT' END, record.created_catalog_revision
FROM work_order_records record
CROSS JOIN work_order_catalog_state state
WHERE state.id = 1;

INSERT INTO wechat_message_catalog_changes(revision, entity_id, operation, entity_created_revision)
SELECT state.revision, message.id, 'UPSERT', message.created_catalog_revision
FROM wechat_messages message
CROSS JOIN work_order_catalog_state state
WHERE state.id = 1 AND message.catalog_revision > 0;

ALTER TABLE vehicle_catalog_changes ALTER COLUMN entity_created_revision SET NOT NULL;
ALTER TABLE work_order_catalog_changes ALTER COLUMN entity_created_revision SET NOT NULL;
ALTER TABLE wechat_message_catalog_changes ALTER COLUMN entity_created_revision SET NOT NULL;

CREATE INDEX idx_vehicle_catalog_changes_target_conflict
    ON vehicle_catalog_changes(revision, entity_created_revision);
CREATE INDEX idx_work_order_catalog_changes_target_conflict
    ON work_order_catalog_changes(revision, entity_created_revision);
CREATE INDEX idx_wechat_message_catalog_changes_target_conflict
    ON wechat_message_catalog_changes(revision, entity_created_revision);

DROP TRIGGER trg_vehicle_catalog_revision ON vehicles;
DROP TRIGGER trg_resident_profile_catalog_change ON resident_profiles;
DROP TRIGGER trg_long_term_profile_catalog_change ON long_term_profiles;

CREATE OR REPLACE FUNCTION record_vehicle_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
    target_id BIGINT;
    target_operation VARCHAR(16);
BEGIN
    target_id := COALESCE(NEW.id, OLD.id);
    UPDATE vehicle_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
    IF TG_OP = 'DELETE' THEN
        target_operation := 'DELETE';
    ELSE
        NEW.catalog_revision := next_revision;
        IF TG_OP = 'INSERT' THEN
            NEW.created_catalog_revision := next_revision;
        END IF;
        target_operation := CASE WHEN NEW.status = 'DELETED' THEN 'DELETE' ELSE 'UPSERT' END;
    END IF;
    INSERT INTO vehicle_catalog_changes(revision, entity_id, operation, entity_created_revision)
    VALUES (next_revision, target_id, target_operation, CASE WHEN TG_OP = 'INSERT' THEN next_revision ELSE OLD.created_catalog_revision END);
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicle_catalog_revision
BEFORE INSERT OR UPDATE OR DELETE ON vehicles
FOR EACH ROW EXECUTE FUNCTION record_vehicle_catalog_change();

CREATE OR REPLACE FUNCTION initialize_work_order_catalog_revision()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.created_catalog_revision IS NULL OR
       (TG_OP = 'UPDATE' AND OLD.catalog_revision = 0 AND NEW.catalog_revision > 0 AND NEW.created_catalog_revision = 0) THEN
        NEW.created_catalog_revision := NEW.catalog_revision;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_initialize_work_order_catalog_revision
BEFORE INSERT OR UPDATE OF catalog_revision ON work_order_records
FOR EACH ROW EXECUTE FUNCTION initialize_work_order_catalog_revision();

CREATE OR REPLACE FUNCTION initialize_wechat_message_catalog_revision()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.created_catalog_revision IS NULL OR
       (TG_OP = 'UPDATE' AND OLD.catalog_revision = 0 AND NEW.catalog_revision > 0 AND NEW.created_catalog_revision = 0) THEN
        NEW.created_catalog_revision := NEW.catalog_revision;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_initialize_wechat_message_catalog_revision
BEFORE INSERT OR UPDATE OF catalog_revision ON wechat_messages
FOR EACH ROW EXECUTE FUNCTION initialize_wechat_message_catalog_revision();

CREATE OR REPLACE FUNCTION record_work_order_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE work_order_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
        INSERT INTO work_order_catalog_changes(revision, entity_id, operation, entity_created_revision)
        VALUES (next_revision, OLD.id, 'DELETE', OLD.created_catalog_revision);
    ELSIF (TG_OP = 'INSERT' AND NEW.catalog_revision > 0) OR
          (TG_OP = 'UPDATE' AND OLD.catalog_revision IS DISTINCT FROM NEW.catalog_revision) THEN
        INSERT INTO work_order_catalog_changes(revision, entity_id, operation, entity_created_revision)
        VALUES (NEW.catalog_revision, NEW.id, CASE WHEN NEW.status = 'VOID' THEN 'REVOKE' ELSE 'UPSERT' END, NEW.created_catalog_revision)
        ON CONFLICT (revision, entity_id) DO UPDATE
        SET operation = EXCLUDED.operation,
            entity_created_revision = EXCLUDED.entity_created_revision,
            changed_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION record_wechat_message_catalog_change()
RETURNS TRIGGER AS $$
DECLARE
    next_revision BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE work_order_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision INTO next_revision;
        INSERT INTO wechat_message_catalog_changes(revision, entity_id, operation, entity_created_revision)
        VALUES (next_revision, OLD.id, 'DELETE', OLD.created_catalog_revision);
    ELSIF (TG_OP = 'INSERT' AND NEW.catalog_revision > 0) OR
          (TG_OP = 'UPDATE' AND OLD.catalog_revision IS DISTINCT FROM NEW.catalog_revision) THEN
        INSERT INTO wechat_message_catalog_changes(revision, entity_id, operation, entity_created_revision)
        VALUES (NEW.catalog_revision, NEW.id, 'UPSERT', NEW.created_catalog_revision)
        ON CONFLICT (revision, entity_id) DO UPDATE
        SET operation = EXCLUDED.operation,
            entity_created_revision = EXCLUDED.entity_created_revision,
            changed_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
