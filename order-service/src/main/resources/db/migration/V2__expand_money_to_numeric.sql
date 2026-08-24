-- V2 — EXPAND phase of the money migration in order-service.
--
-- Same defect as product-service, with an extra twist: order totals are DERIVED
-- (unit_price * quantity, then summed). Floating-point error therefore compounds — an order
-- total could disagree with the sum of its own lines. NUMERIC makes the arithmetic exact.
--
-- Nullable + backfill here; NOT NULL arrives in V4, after the deploy that dual-writes.

ALTER TABLE orders      ADD COLUMN total_amount NUMERIC(12, 2);
ALTER TABLE order_items ADD COLUMN unit_amount  NUMERIC(12, 2);
ALTER TABLE order_items ADD COLUMN line_amount  NUMERIC(12, 2);

UPDATE orders      SET total_amount = ROUND(total_price::numeric, 2) WHERE total_amount IS NULL;
UPDATE order_items SET unit_amount  = ROUND(unit_price::numeric, 2)  WHERE unit_amount  IS NULL;
UPDATE order_items SET line_amount  = ROUND(total_price::numeric, 2) WHERE line_amount  IS NULL;

-- An amount without a currency is not money. Denormalised onto the order (not looked up)
-- because it must stay frozen at what the customer actually paid in.
ALTER TABLE orders ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

ALTER TABLE orders ADD CONSTRAINT orders_currency_check
    CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE orders ADD CONSTRAINT orders_total_amount_non_negative
    CHECK (total_amount IS NULL OR total_amount >= 0);
ALTER TABLE order_items ADD CONSTRAINT order_items_amounts_non_negative
    CHECK ((unit_amount IS NULL OR unit_amount >= 0)
       AND (line_amount IS NULL OR line_amount >= 0));
