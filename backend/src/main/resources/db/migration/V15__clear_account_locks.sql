-- Username-based account locks are gone (fixed by per-IP login throttling instead); free everyone locked by them.
UPDATE users SET locked_until = NULL, failed_login_attempts = 0
WHERE locked_until IS NOT NULL OR failed_login_attempts <> 0;
