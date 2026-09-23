ALTER TABLE work_order_images
    ADD COLUMN source_quality VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN';

UPDATE work_order_images
SET source_quality = 'ORIGINAL'
WHERE attachment_kind = 'PDF' AND original_path IS NOT NULL;

ALTER TABLE work_order_images
    DROP CONSTRAINT ck_work_order_images_availability;

ALTER TABLE work_order_images
    ADD CONSTRAINT ck_work_order_images_availability CHECK (
        availability IN ('METADATA_ONLY', 'THUMBNAIL_ONLY', 'AVAILABLE', 'UNAVAILABLE')
    ),
    ADD CONSTRAINT ck_work_order_images_source_quality CHECK (
        source_quality IN ('UNKNOWN', 'THUMBNAIL', 'HIGH_DEFINITION', 'ORIGINAL')
    );

UPDATE work_order_images
SET source_quality = 'THUMBNAIL', availability = 'THUMBNAIL_ONLY'
WHERE original_path IS NULL AND thumbnail_path IS NOT NULL;

CREATE INDEX idx_work_order_images_catalog
    ON work_order_images(id, source_quality)
    WHERE ignored_at IS NULL;
