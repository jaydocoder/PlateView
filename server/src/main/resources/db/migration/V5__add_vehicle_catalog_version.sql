CREATE TABLE vehicle_catalog_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    revision BIGINT NOT NULL DEFAULT 0
);

INSERT INTO vehicle_catalog_state (id, revision) VALUES (1, 0);

CREATE OR REPLACE FUNCTION bump_vehicle_catalog_revision()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE vehicle_catalog_state SET revision = revision + 1 WHERE id = 1;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicle_catalog_revision
AFTER INSERT OR UPDATE OR DELETE ON vehicles
FOR EACH ROW EXECUTE FUNCTION bump_vehicle_catalog_revision();
