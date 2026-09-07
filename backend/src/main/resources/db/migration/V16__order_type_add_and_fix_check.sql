-- Fix pre-existing gap: OrderType.EXIT_MANUAL has been used in code since manual exits and
-- closing-basis SL market sells were introduced, but this constraint was never updated for it —
-- those inserts violate this CHECK against a real database. Also adds ADD for the new
-- add/remove-quantity feature.
ALTER TABLE orders DROP CONSTRAINT orders_type_check;
ALTER TABLE orders ADD CONSTRAINT orders_type_check
    CHECK (type IN ('ENTRY', 'EXIT_TARGET', 'EXIT_SL', 'EXIT_MANUAL', 'ADD'));
