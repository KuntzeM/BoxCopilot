-- Create boxes table
CREATE TABLE IF NOT EXISTS boxes (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(255) NOT NULL UNIQUE,
    current_room VARCHAR(255),
    target_room VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE
);

-- Create items table
CREATE TABLE IF NOT EXISTS items (
    id BIGSERIAL PRIMARY KEY,
    box_id BIGINT NOT NULL REFERENCES boxes(id) ON DELETE CASCADE,
    name VARCHAR(255)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_items_box_id ON items(box_id);
