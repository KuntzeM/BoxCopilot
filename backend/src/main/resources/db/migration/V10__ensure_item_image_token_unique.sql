-- Ensure image_token has UNIQUE constraint (idempotent migration)
-- This migration handles cases where V9 was executed with different content

-- For PostgreSQL: Add UNIQUE constraint if it doesn't exist
DO $$
BEGIN
    -- Check if constraint already exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'uk_items_image_token'
    ) THEN
        -- Add unique constraint
        ALTER TABLE items ADD CONSTRAINT uk_items_image_token UNIQUE (image_token);
    END IF;
END $$;
