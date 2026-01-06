-- Erstelle Box-Nummern Pool
CREATE TABLE box_number_pool (
    box_number INT PRIMARY KEY,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_box_number_available ON box_number_pool(is_available, box_number);

-- Erweitere Box-Tabelle
ALTER TABLE boxes ADD COLUMN box_number INT;

-- Note: Migration of existing boxes will be handled by application code on startup
-- This is because H2 doesn't support anonymous DO blocks like PostgreSQL
-- The BoxNumberService will assign numbers to any boxes that don't have one yet

