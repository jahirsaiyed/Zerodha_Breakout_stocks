ALTER TABLE signals
    ADD COLUMN closing_basis VARCHAR(10) NOT NULL DEFAULT 'DAILY'
        CHECK (closing_basis IN ('DAILY', 'HOURLY', 'WEEKLY'));
