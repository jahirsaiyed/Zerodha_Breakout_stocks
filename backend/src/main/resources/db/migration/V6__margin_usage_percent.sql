ALTER TABLE user_configs
    ADD COLUMN margin_usage_percent NUMERIC(5,2) NOT NULL DEFAULT 100.00
        CHECK (margin_usage_percent >= 1.00 AND margin_usage_percent <= 100.00);
