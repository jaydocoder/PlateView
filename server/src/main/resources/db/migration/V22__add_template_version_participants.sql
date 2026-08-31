CREATE TABLE schedule_template_version_participants (
    template_version_id BIGINT NOT NULL REFERENCES schedule_template_versions (id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES schedule_participants (account_id) ON DELETE RESTRICT,
    PRIMARY KEY (template_version_id, account_id)
);

INSERT INTO schedule_template_version_participants (template_version_id, account_id)
SELECT DISTINCT template_version_id, account_id
FROM schedule_shift_assignments
ON CONFLICT DO NOTHING;

CREATE INDEX idx_schedule_template_version_participants_account
    ON schedule_template_version_participants (account_id);
