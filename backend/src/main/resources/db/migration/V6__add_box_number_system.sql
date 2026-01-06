-- Create box number pool table
CREATE TABLE box_number_pool (
    box_number INT PRIMARY KEY,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_box_number_available ON box_number_pool(is_available, box_number);

-- Add box_number column to boxes table
ALTER TABLE boxes ADD COLUMN box_number INT;

-- Ensure box_number is unique per box
CREATE UNIQUE INDEX idx_boxes_box_number_unique ON boxes(box_number);

-- Note: Migration of existing boxes will be handled by application code on startup
-- This is because H2 doesn't support anonymous DO blocks like PostgreSQL
-- The BoxNumberService will assign numbers to any boxes that don't have one yet

