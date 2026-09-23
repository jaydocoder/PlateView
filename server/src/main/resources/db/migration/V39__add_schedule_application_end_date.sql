ALTER TABLE schedule_applications
    ADD COLUMN effective_until DATE;

ALTER TABLE schedule_applications
    ADD CONSTRAINT ck_schedule_application_date_range
    CHECK (effective_until IS NULL OR effective_until >= effective_from);

CREATE INDEX idx_schedule_applications_effective_until
    ON schedule_applications (effective_until);
