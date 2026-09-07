-- V8 — CONTRACT phase, step 2 of 2: the column finally goes.
--
-- Safe only because the deploy between V7 and V8 removed `price` from the entity, so nothing
-- reads or writes it any more.
--
-- This completes the migration begun in V2:
--   V2  add price_amount nullable + backfill   (expand)
--   V3  set price_amount NOT NULL              (constrain)
--   V7  drop NOT NULL on price                 (contract, step 1)
--   V8  drop price                             (contract, step 2)
--
-- Six deploys' worth of care to change one column's type without downtime and without ever
-- losing the ability to roll back. That is the actual cost of the pattern, and it is why you
-- only pay it when the column matters — money did.
--
-- IRREVERSIBLE from here: the double values are gone. price_amount has been the source of
-- truth since V3, so nothing is lost, but there is no going back to the old code after this.

ALTER TABLE products DROP COLUMN price;
