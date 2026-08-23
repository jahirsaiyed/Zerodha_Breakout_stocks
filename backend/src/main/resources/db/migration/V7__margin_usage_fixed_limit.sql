ALTER TABLE user_configs
    ADD COLUMN margin_usage_fixed_limit NUMERIC(18,2) DEFAULT NULL
        CHECK (margin_usage_fixed_limit IS NULL OR margin_usage_fixed_limit >= 1000);
