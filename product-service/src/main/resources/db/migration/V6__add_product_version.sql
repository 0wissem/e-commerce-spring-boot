-- V6 — optimistic locking support.
--
-- `version` backs JPA's @Version. Hibernate reads it, then writes with
--   UPDATE products SET ..., version = version + 1 WHERE id = ? AND version = ?
-- If another transaction already bumped the row, that WHERE matches 0 rows and Hibernate
-- raises an OptimisticLockException. No database lock is held — hence "optimistic":
-- we assume conflicts are rare and detect them at write time instead of preventing them.
--
-- Additive and safe: DEFAULT 0 means rows written by an instance that predates this
-- migration still satisfy the NOT NULL.

ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Stock can never be negative. The application enforces this too, but a CHECK constraint
-- is the only thing that holds when several instances write concurrently — it is the
-- last line of defence against overselling.
ALTER TABLE products ADD CONSTRAINT products_stock_non_negative
    CHECK (stock_quantity >= 0);
