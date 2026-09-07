-- V5 — CONTRACT phase, step 1 of 2. Same three-step sequence as product-service V7/V8.
--
--   V5 (here)  DROP NOT NULL on the legacy doubles   <- both code versions still valid
--   deploy     entities stop mapping them
--   V6         DROP COLUMN
--
-- Three columns here rather than one, because an order carries money in three places: the
-- order total and, per line, the unit price and the line total.

ALTER TABLE orders      ALTER COLUMN total_price DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN unit_price  DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN total_price DROP NOT NULL;

COMMENT ON COLUMN orders.total_price IS      'DEPRECATED, nullable, no longer written. Dropped in V6.';
COMMENT ON COLUMN order_items.unit_price IS  'DEPRECATED, nullable, no longer written. Dropped in V6.';
COMMENT ON COLUMN order_items.total_price IS 'DEPRECATED, nullable, no longer written. Dropped in V6.';
