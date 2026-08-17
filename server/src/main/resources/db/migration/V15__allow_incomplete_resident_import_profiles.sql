ALTER TABLE resident_profiles
    ALTER COLUMN owner_name DROP NOT NULL,
    ALTER COLUMN identity_card_number DROP NOT NULL;
