ALTER TABLE users
    ALTER COLUMN other_long_term_access_enabled SET DEFAULT FALSE,
    ALTER COLUMN resident_remarks_access_enabled SET DEFAULT FALSE;

UPDATE users
SET other_long_term_access_enabled = FALSE,
    resident_remarks_access_enabled = FALSE
WHERE NOT (username = 'admin' AND role = 'ADMIN');
