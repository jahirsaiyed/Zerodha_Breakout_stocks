ALTER TABLE orders
    DROP CONSTRAINT orders_order_kind_check;

ALTER TABLE orders
    ADD CONSTRAINT orders_order_kind_check
        CHECK (order_kind IN ('LIMIT', 'GTT_OCO', 'MARKET', 'MANUAL'));
