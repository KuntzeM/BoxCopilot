-- Spring Authorization Server 1.5 JDBC stores.
-- Column names and order follow the reference schemas shipped in the SAS jar
-- (org/springframework/security/oauth2/server/authorization/*.sql); JdbcRegisteredClientRepository and
-- JdbcOAuth2AuthorizationService address every column by name, so none may be renamed or dropped.
-- Deviations from the reference DDL, both required here:
--   * every 'blob' column becomes 'text' (Postgres has no blob, and SAS binds and reads these values as
--     java.sql.Types.VARCHAR strings, so 'bytea' would not work),
--   * length-limited columns that hold serialised state become 'text': the reference lengths are too small for
--     a fully populated authorization (attributes carry the serialised principal).
-- oauth2_authorization_consent is deliberately absent: consent is never stored (no-op consent service).

CREATE TABLE oauth2_registered_client (
    id varchar(100) NOT NULL,
    client_id varchar(100) NOT NULL,
    client_id_issued_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret text DEFAULT NULL,
    client_secret_expires_at timestamp DEFAULT NULL,
    client_name varchar(200) NOT NULL,
    client_authentication_methods text NOT NULL,
    authorization_grant_types text NOT NULL,
    redirect_uris text DEFAULT NULL,
    post_logout_redirect_uris text DEFAULT NULL,
    scopes text NOT NULL,
    client_settings text NOT NULL,
    token_settings text NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_oauth2_registered_client_client_id ON oauth2_registered_client (client_id);

CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes text DEFAULT NULL,
    attributes text DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value text DEFAULT NULL,
    authorization_code_issued_at timestamp DEFAULT NULL,
    authorization_code_expires_at timestamp DEFAULT NULL,
    authorization_code_metadata text DEFAULT NULL,
    access_token_value text DEFAULT NULL,
    access_token_issued_at timestamp DEFAULT NULL,
    access_token_expires_at timestamp DEFAULT NULL,
    access_token_metadata text DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes text DEFAULT NULL,
    oidc_id_token_value text DEFAULT NULL,
    oidc_id_token_issued_at timestamp DEFAULT NULL,
    oidc_id_token_expires_at timestamp DEFAULT NULL,
    oidc_id_token_metadata text DEFAULT NULL,
    refresh_token_value text DEFAULT NULL,
    refresh_token_issued_at timestamp DEFAULT NULL,
    refresh_token_expires_at timestamp DEFAULT NULL,
    refresh_token_metadata text DEFAULT NULL,
    user_code_value text DEFAULT NULL,
    user_code_issued_at timestamp DEFAULT NULL,
    user_code_expires_at timestamp DEFAULT NULL,
    user_code_metadata text DEFAULT NULL,
    device_code_value text DEFAULT NULL,
    device_code_issued_at timestamp DEFAULT NULL,
    device_code_expires_at timestamp DEFAULT NULL,
    device_code_metadata text DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_oauth2_authorization_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);

-- Every request resolves a token value; revocation and cleanup work per principal.
CREATE INDEX ix_oauth2_authorization_access_token ON oauth2_authorization (access_token_value);
CREATE INDEX ix_oauth2_authorization_refresh_token ON oauth2_authorization (refresh_token_value);
CREATE INDEX ix_oauth2_authorization_code ON oauth2_authorization (authorization_code_value);
CREATE INDEX ix_oauth2_authorization_state ON oauth2_authorization (state);
CREATE INDEX ix_oauth2_authorization_principal_name ON oauth2_authorization (principal_name);
-- Without this the cascading delete of a client scans the whole table.
CREATE INDEX ix_oauth2_authorization_registered_client ON oauth2_authorization (registered_client_id);
