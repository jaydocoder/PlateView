ALTER TABLE vehicles DROP CONSTRAINT ck_vehicles_status;

ALTER TABLE vehicles
    ADD CONSTRAINT ck_vehicles_status CHECK (status IN ('ACTIVE', 'BLACKLISTED', 'INACTIVE', 'DELETED'));

CREATE INDEX idx_vehicles_status_normalized_plate
    ON vehicles (status, normalized_plate, id);
