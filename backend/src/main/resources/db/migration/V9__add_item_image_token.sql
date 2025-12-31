-- Add image_token column to items table for secure image access
ALTER TABLE items ADD COLUMN image_token VARCHAR(64);

-- Add unique constraint separately (works on both H2 and PostgreSQL)
ALTER TABLE items ADD CONSTRAINT uk_items_image_token UNIQUE (image_token);
