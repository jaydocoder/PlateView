ALTER TABLE users
    ADD COLUMN other_long_term_access_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN resident_remarks_access_enabled BOOLEAN NOT NULL DEFAULT TRUE;
