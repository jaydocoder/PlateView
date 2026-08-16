ALTER TABLE import_rows
    ADD COLUMN before_values JSONB;

ALTER TABLE import_rows
    DROP CONSTRAINT ck_import_rows_planned_action;

ALTER TABLE import_rows
    ADD CONSTRAINT ck_import_rows_planned_action
    CHECK (planned_action IN ('CREATE', 'UPDATE', 'DEACTIVATE', 'REACTIVATE', 'SKIP', 'NONE'));

ALTER TABLE import_effects
    DROP CONSTRAINT ck_import_effects_action;

ALTER TABLE import_effects
    ADD CONSTRAINT ck_import_effects_action
    CHECK (action IN ('CREATED', 'UPDATED', 'DEACTIVATED', 'REACTIVATED'));

CREATE INDEX idx_import_rows_review
    ON import_rows (import_batch_id, planned_action, result_status, id);
