ALTER TABLE oauth_identities
    ADD COLUMN IF NOT EXISTS access_token_enc TEXT;
