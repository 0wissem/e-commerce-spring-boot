ALTER TABLE order_items ALTER COLUMN product_snapshot TYPE TEXT USING product_snapshot::text;
