ALTER TABLE import_rows
    ADD COLUMN resolution VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN warning_message TEXT,
    ADD COLUMN published_vehicle_id BIGINT;

ALTER TABLE import_rows
    ADD CONSTRAINT ck_import_rows_resolution
    CHECK (resolution IN ('PENDING', 'PUBLISH', 'SKIP', 'ERROR'));

ALTER TABLE import_rows
    ADD CONSTRAINT fk_import_rows_published_vehicle
    FOREIGN KEY (published_vehicle_id) REFERENCES vehicles (id);

ALTER TABLE import_batches
    ADD COLUMN rollback_at TIMESTAMPTZ,
    ADD COLUMN rollback_by BIGINT,
    ADD CONSTRAINT fk_import_batches_rollback_by
    FOREIGN KEY (rollback_by) REFERENCES users (id);
