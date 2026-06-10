CREATE TABLE admin_settings (
    setting_key   VARCHAR(100)  PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
