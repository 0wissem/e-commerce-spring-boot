-- V4 — CONSTRAIN phase. Runs only after the deploy that dual-writes both column sets.

UPDATE orders      SET total_amount = ROUND(total_price::numeric, 2) WHERE total_amount IS NULL;
UPDATE order_items SET unit_amount  = ROUND(unit_price::numeric, 2)  WHERE unit_amount  IS NULL;
UPDATE order_items SET line_amount  = ROUND(total_price::numeric, 2) WHERE line_amount  IS NULL;

ALTER TABLE orders      ALTER COLUMN total_amount SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN unit_amount  SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN line_amount  SET NOT NULL;

COMMENT ON COLUMN orders.total_price IS
    'DEPRECATED legacy double total. Superseded by total_amount. Drop in the contract migration.';
COMMENT ON COLUMN order_items.unit_price IS
    'DEPRECATED legacy double. Superseded by unit_amount. Drop in the contract migration.';
COMMENT ON COLUMN order_items.total_price IS
    'DEPRECATED legacy double. Superseded by line_amount. Drop in the contract migration.';
