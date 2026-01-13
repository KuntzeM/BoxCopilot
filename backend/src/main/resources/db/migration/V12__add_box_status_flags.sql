-- Add status flags to boxes table
ALTER TABLE boxes 
ADD COLUMN is_moved_to_target BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE boxes 
ADD COLUMN label_printed BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN boxes.is_moved_to_target IS 'Box has been moved to target location';
COMMENT ON COLUMN boxes.label_printed IS 'Box label has been printed';
