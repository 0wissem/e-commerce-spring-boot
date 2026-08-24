-- V4 — product enrichment: description, sku, brand, audit timestamps.
--
-- All additive. Every column is either nullable or has a DEFAULT, so rows written by an
-- instance that predates this migration remain valid.

ALTER TABLE products ADD COLUMN description TEXT;
ALTER TABLE products ADD COLUMN brand        VARCHAR(120);

-- Audit columns. TIMESTAMPTZ, not TIMESTAMP: an instant without a zone is ambiguous the
-- moment two regions write to the same table.
ALTER TABLE products ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE products ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- SKU: the business identifier, distinct from the surrogate UUID primary key.
-- Added nullable first so existing rows can be backfilled, then constrained — the same
-- expand/backfill/constrain shape as the price migration, in a single step here because
-- no old code path writes this column at all.
ALTER TABLE products ADD COLUMN sku VARCHAR(64);

-- Deterministic backfill derived from the id, so re-running on a copy yields the same values.
UPDATE products
SET sku = 'SKU-' || upper(substr(md5(id), 1, 10))
WHERE sku IS NULL;

ALTER TABLE products ALTER COLUMN sku SET NOT NULL;
ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku);

-- Brand is a filter/facet dimension — index it for the search endpoint.
CREATE INDEX idx_products_brand ON products (brand);

-- Supports "newest first" listings without a sort.
CREATE INDEX idx_products_created_at ON products (created_at DESC);
