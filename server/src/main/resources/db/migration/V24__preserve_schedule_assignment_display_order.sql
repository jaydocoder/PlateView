ALTER TABLE schedule_shift_assignments
    ADD COLUMN display_order SMALLINT NOT NULL DEFAULT 0;

WITH ordered_assignments AS (
    SELECT
        template_version_id,
        cycle_day,
        shift_type,
        account_id,
        ROW_NUMBER() OVER (
            PARTITION BY template_version_id, cycle_day, shift_type
            ORDER BY account_id
        ) AS position
    FROM schedule_shift_assignments
)
UPDATE schedule_shift_assignments assignment
SET display_order = ordered_assignments.position
FROM ordered_assignments
WHERE assignment.template_version_id = ordered_assignments.template_version_id
  AND assignment.cycle_day = ordered_assignments.cycle_day
  AND assignment.shift_type = ordered_assignments.shift_type
  AND assignment.account_id = ordered_assignments.account_id;

ALTER TABLE schedule_shift_assignments
    DROP CONSTRAINT IF EXISTS uq_schedule_shift_assignments_display_order,
    ADD CONSTRAINT uq_schedule_shift_assignments_display_order
        UNIQUE (template_version_id, cycle_day, shift_type, display_order);

ALTER TABLE schedule_shift_assignments
    ALTER COLUMN display_order DROP DEFAULT;
