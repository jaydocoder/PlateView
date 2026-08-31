ALTER TABLE schedule_participants
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE schedule_configuration (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    cycle_days SMALLINT NOT NULL CHECK (cycle_days BETWEEN 1 AND 9),
    updated_by BIGINT REFERENCES users (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schedule_configuration (id, cycle_days)
VALUES (1, 9)
ON CONFLICT (id) DO NOTHING;

CREATE INDEX idx_schedule_participants_enabled ON schedule_participants (enabled);
