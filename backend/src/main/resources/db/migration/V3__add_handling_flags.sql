-- Add handling flags to boxes table
ALTER TABLE boxes 
ADD COLUMN is_fragile BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE boxes 
ADD COLUMN no_stack BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN boxes.is_fragile IS 'Box contains fragile items';
COMMENT ON COLUMN boxes.no_stack IS 'Box must not be stacked';
