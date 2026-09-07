-- V7 — CONTRACT phase, step 1 of 2: relax the legacy column before anything stops writing it.
--
-- THE SEQUENCING, and why it is not one migration:
--
--   V7 (here)  DROP NOT NULL on `price`          <- old code still writes it, still fine
--   deploy     the entity stops mapping `price`  <- inserts now OMIT the column
--   V8         DROP COLUMN price                 <- nothing references it any more
--
-- Reversed, it breaks. Drop the column while an old instance is still mapping it and every
-- INSERT from that instance fails ("column price does not exist"). Stop writing it while it
-- is still NOT NULL and every INSERT fails too, for the opposite reason. The nullable step in
-- between is what makes both versions valid at once — the same reasoning as V2 on the way in,
-- run in reverse.

ALTER TABLE products ALTER COLUMN price DROP NOT NULL;

COMMENT ON COLUMN products.price IS
    'DEPRECATED, nullable, no longer written. Dropped in V8.';
