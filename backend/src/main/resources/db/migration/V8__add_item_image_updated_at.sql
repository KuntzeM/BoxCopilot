-- Add image_updated_at timestamp to items table for cache busting
ALTER TABLE items ADD COLUMN image_updated_at BIGINT;

COMMENT ON COLUMN items.image_updated_at IS 'Timestamp (epoch millis) when image was last updated, used for cache busting';
