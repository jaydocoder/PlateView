ALTER TABLE schedule_configuration
    DROP CONSTRAINT IF EXISTS schedule_configuration_cycle_days_check,
    ADD CONSTRAINT schedule_configuration_cycle_days_check CHECK (cycle_days BETWEEN 1 AND 15);

ALTER TABLE schedule_template_versions
    DROP CONSTRAINT IF EXISTS ck_schedule_template_cycle_days,
    ADD CONSTRAINT ck_schedule_template_cycle_days CHECK (cycle_days BETWEEN 1 AND 15);

ALTER TABLE schedule_shift_assignments
    DROP CONSTRAINT IF EXISTS ck_schedule_assignment_day,
    ADD CONSTRAINT ck_schedule_assignment_day CHECK (cycle_day BETWEEN 1 AND 15);
