-- Add image path column to items table
ALTER TABLE items ADD COLUMN image_path VARCHAR(512);

COMMENT ON COLUMN items.image_path IS 'Path to the item thumbnail image';
