ALTER TABLE vehicles DROP CONSTRAINT ck_vehicles_status;

ALTER TABLE vehicles
    ADD CONSTRAINT ck_vehicles_status CHECK (status IN ('ACTIVE', 'STRICT_CHECK', 'BLACKLISTED', 'INACTIVE', 'DELETED'));
