ALTER TABLE vehicles
    ADD COLUMN searchable_text TEXT NOT NULL DEFAULT '';

CREATE OR REPLACE FUNCTION normalize_home_search_text(value TEXT)
RETURNS TEXT AS $$
    SELECT UPPER(REGEXP_REPLACE(COALESCE(value, ''), '[[:space:]，。；：、,.;:()（）【】\[\]_-]+', '', 'g'));
$$ LANGUAGE SQL IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION build_vehicle_searchable_text(target_vehicle_id BIGINT, target_plate TEXT)
RETURNS TEXT AS $$
    SELECT normalize_home_search_text(CONCAT_WS(' ',
        target_plate,
        rp.owner_name,
        rp.remarks,
        lp.organization_name,
        lp.pass_holder,
        lp.remarks
    ))
    FROM (SELECT 1) seed
    LEFT JOIN resident_profiles rp ON rp.vehicle_id = target_vehicle_id
    LEFT JOIN long_term_profiles lp ON lp.vehicle_id = target_vehicle_id;
$$ LANGUAGE SQL STABLE;

UPDATE vehicles v
SET searchable_text = build_vehicle_searchable_text(v.id, v.normalized_plate);

CREATE OR REPLACE FUNCTION refresh_vehicle_searchable_text_from_vehicle()
RETURNS TRIGGER AS $$
BEGIN
    NEW.searchable_text = build_vehicle_searchable_text(NEW.id, NEW.normalized_plate);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicles_searchable_text
    BEFORE INSERT OR UPDATE OF normalized_plate ON vehicles
    FOR EACH ROW EXECUTE FUNCTION refresh_vehicle_searchable_text_from_vehicle();

CREATE OR REPLACE FUNCTION refresh_vehicle_searchable_text_from_profile()
RETURNS TRIGGER AS $$
DECLARE
    target_vehicle_id BIGINT;
BEGIN
    target_vehicle_id = CASE WHEN TG_OP = 'DELETE' THEN OLD.vehicle_id ELSE NEW.vehicle_id END;
    UPDATE vehicles
    SET searchable_text = build_vehicle_searchable_text(id, normalized_plate)
    WHERE id = target_vehicle_id;

    IF TG_OP = 'UPDATE' AND OLD.vehicle_id IS DISTINCT FROM NEW.vehicle_id THEN
        UPDATE vehicles
        SET searchable_text = build_vehicle_searchable_text(id, normalized_plate)
        WHERE id = OLD.vehicle_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resident_profiles_searchable_text
    AFTER INSERT OR UPDATE OR DELETE ON resident_profiles
    FOR EACH ROW EXECUTE FUNCTION refresh_vehicle_searchable_text_from_profile();

CREATE TRIGGER trg_long_term_profiles_searchable_text
    AFTER INSERT OR UPDATE OR DELETE ON long_term_profiles
    FOR EACH ROW EXECUTE FUNCTION refresh_vehicle_searchable_text_from_profile();

CREATE INDEX idx_vehicles_searchable_text_trgm
    ON vehicles USING GIN(searchable_text gin_trgm_ops);

CREATE INDEX idx_wechat_messages_sender_display_upper_trgm
    ON wechat_messages USING GIN((UPPER(COALESCE(sender_display, ''))) gin_trgm_ops);

CREATE INDEX idx_wechat_messages_group_nickname_upper_trgm
    ON wechat_messages USING GIN((UPPER(COALESCE(sender_group_nickname, ''))) gin_trgm_ops);

CREATE INDEX idx_work_order_images_file_name_upper_trgm
    ON work_order_images USING GIN((UPPER(COALESCE(file_name, ''))) gin_trgm_ops);

CREATE INDEX idx_wechat_messages_business_sender_sent
    ON wechat_messages(sender_username, sent_at DESC, id DESC)
    WHERE business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE');

CREATE INDEX idx_work_order_vehicles_plate_trgm
    ON work_order_vehicles USING GIN(normalized_plate gin_trgm_ops);

CREATE INDEX idx_work_order_latest_number
    ON work_order_records(order_number, id DESC)
    WHERE order_number IS NOT NULL;

ANALYZE vehicles;
ANALYZE wechat_messages;
ANALYZE work_order_records;
ANALYZE work_order_vehicles;
ANALYZE work_order_images;
