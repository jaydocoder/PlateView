ALTER TABLE vehicles DROP CONSTRAINT ck_vehicles_category;

ALTER TABLE vehicles
    ADD CONSTRAINT ck_vehicles_category CHECK (
        category IN (
            'RESIDENT',
            'SCENIC_UNIT',
            'SCENIC_ENTERPRISE',
            'CADRE',
            'KANAS_TOURISM_DEVELOPMENT',
            'OTHER_LONG_TERM'
        )
    );
