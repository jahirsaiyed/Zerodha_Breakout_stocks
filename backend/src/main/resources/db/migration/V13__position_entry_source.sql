ALTER TABLE positions
    ADD COLUMN entry_source VARCHAR(10) NOT NULL DEFAULT 'AUTO'
        CHECK (entry_source IN ('AUTO', 'MANUAL'));
