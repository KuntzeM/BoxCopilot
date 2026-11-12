-- Set (UTC) whenever an admin sets the user's password; sessions that carry an older value are ended.
ALTER TABLE users ADD COLUMN sessions_valid_after TIMESTAMP NULL;
