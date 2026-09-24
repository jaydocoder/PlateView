DROP TRIGGER IF EXISTS trg_mirror_work_order_catalog_revision ON work_order_catalog_state;

CREATE OR REPLACE FUNCTION mirror_work_order_change_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE client_catalog_state
    SET work_order_revision = GREATEST(work_order_revision, NEW.revision),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION mirror_wechat_message_change_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE client_catalog_state
    SET wechat_message_revision = GREATEST(wechat_message_revision, NEW.revision),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mirror_work_order_change_revision
AFTER INSERT OR UPDATE OF revision ON work_order_catalog_changes
FOR EACH ROW EXECUTE FUNCTION mirror_work_order_change_revision();

CREATE TRIGGER trg_mirror_wechat_message_change_revision
AFTER INSERT OR UPDATE OF revision ON wechat_message_catalog_changes
FOR EACH ROW EXECUTE FUNCTION mirror_wechat_message_change_revision();

UPDATE client_catalog_state
SET work_order_revision = COALESCE((SELECT MAX(revision) FROM work_order_catalog_changes), 0),
    wechat_message_revision = COALESCE((SELECT MAX(revision) FROM wechat_message_catalog_changes), 0),
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;
