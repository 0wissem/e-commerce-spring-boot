-- V5 — weighted full-text search.
--
-- Before: search_vector indexed `name` only, so every match ranked equally and the new
-- `description`/`brand` columns were invisible to search.
-- After:  all three are indexed with different weights, so ts_rank can order results by
-- WHERE the term matched:
--   A = name        (a name match is the strongest signal)
--   B = brand
--   C = description (a passing mention is the weakest signal)
--
-- Postgres has no ALTER for a generated column's expression, so the column is dropped and
-- recreated. That is safe here specifically because search_vector is GENERATED — it holds no
-- data of its own, only a derivation of columns that already exist. Dropping the column also
-- drops its index, so the GIN index is recreated below.
--
-- Every function used is IMMUTABLE (to_tsvector with an explicit 'english' regconfig, setweight,
-- and the tsvector || operator), which is what a generated column requires.

DROP INDEX IF EXISTS idx_products_search_vector;
ALTER TABLE products DROP COLUMN search_vector;

ALTER TABLE products ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')),        'A') ||
        setweight(to_tsvector('english', coalesce(brand, '')),       'B') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'C')
    ) STORED;

CREATE INDEX idx_products_search_vector ON products USING GIN (search_vector);
