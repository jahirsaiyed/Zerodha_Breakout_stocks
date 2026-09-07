ALTER TABLE user_configs
    ADD COLUMN partial_profit_booking_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_configs
    ADD COLUMN partial_profit_booking_percent NUMERIC(5,2) NOT NULL DEFAULT 50.00
        CHECK (partial_profit_booking_percent > 0.00 AND partial_profit_booking_percent < 100.00);

ALTER TABLE positions
    ADD COLUMN gtt_quantity INTEGER;
