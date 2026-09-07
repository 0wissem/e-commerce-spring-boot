-- V6 — CONTRACT phase, step 2 of 2. The legacy doubles are gone.
--
-- Completes the sequence begun in V2:
--   V2  add the numeric columns nullable + backfill   (expand)
--   V4  set them NOT NULL                             (constrain)
--   V5  drop NOT NULL on the doubles                  (contract, step 1)
--   V6  drop the doubles                              (contract, step 2)
--
-- From here the order total is stored exactly once, as NUMERIC, and the line total is DERIVED
-- in the entity from unit_amount * quantity — so the total can no longer disagree with the
-- sum of its own lines, in the type system or in the schema.

ALTER TABLE orders      DROP COLUMN total_price;
ALTER TABLE order_items DROP COLUMN unit_price;
ALTER TABLE order_items DROP COLUMN total_price;
