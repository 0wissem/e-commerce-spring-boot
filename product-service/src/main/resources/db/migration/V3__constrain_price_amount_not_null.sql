-- V3 — CONSTRAIN phase of the money migration.
--
-- Safe to run only now: V2 backfilled every existing row, and the application deployed
-- between V2 and V3 writes `price_amount` on every insert and update. Had this been folded
-- into V2, an old instance still running during a rolling deploy would have inserted a NULL
-- and failed. Separate migration, separate deploy — that is the whole point of expand/contract.
--
-- Still NOT dropping `price`. That is the CONTRACT phase, deliberately left for later.

-- Belt and braces: if any row slipped through between the deploys, fix it before constraining.
UPDATE products
SET price_amount = ROUND(price::numeric, 2)
WHERE price_amount IS NULL;

ALTER TABLE products ALTER COLUMN price_amount SET NOT NULL;

-- The old column is now redundant. Marking it explicitly so the next reader knows the
-- contract migration is outstanding rather than forgotten.
COMMENT ON COLUMN products.price IS
    'DEPRECATED legacy double price. Superseded by price_amount. Drop in the contract migration.';
