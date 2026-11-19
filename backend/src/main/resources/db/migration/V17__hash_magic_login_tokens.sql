-- Magic-login tokens are now stored as SHA-256 hex digests. Existing rows hold raw (plaintext) tokens
-- that can no longer be matched, so drop them; admins can issue new links.
DELETE FROM magic_login_tokens;
