ALTER TABLE users
    ADD COLUMN update_policy VARCHAR(16) NOT NULL DEFAULT 'OPTIONAL';

ALTER TABLE users
    ADD CONSTRAINT ck_users_update_policy CHECK (update_policy IN ('OPTIONAL', 'FORCED'));
