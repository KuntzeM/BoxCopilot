-- Ensure boxes.box_number exists without altering past migrations
ALTER TABLE boxes ADD COLUMN IF NOT EXISTS box_number INT;

-- Ensure box_number_pool exists (safe in both PostgreSQL and H2)
CREATE TABLE IF NOT EXISTS box_number_pool (
    box_number INT PRIMARY KEY,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ensure index exists for efficient allocation
CREATE INDEX IF NOT EXISTS idx_box_number_available ON box_number_pool(is_available, box_number);
