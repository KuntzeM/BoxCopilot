-- Create magic_login_tokens table
CREATE TABLE magic_login_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_magic_login_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index on token for fast lookups
CREATE INDEX idx_magic_token ON magic_login_tokens(token);

-- Create index on user_id for finding user's tokens
CREATE INDEX idx_magic_user ON magic_login_tokens(user_id);

-- Create index on expiry_date for cleanup of expired tokens
CREATE INDEX idx_magic_expiry ON magic_login_tokens(expiry_date);
