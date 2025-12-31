-- Add image_token column to items table for secure image access
ALTER TABLE items ADD COLUMN image_token VARCHAR(64) UNIQUE;

COMMENT ON COLUMN items.image_token IS 'Unique token for secure image access, generated on item creation';
