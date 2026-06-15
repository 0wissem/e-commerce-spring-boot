-- Phase 11: orders extracted to order-service. The monolith now owns only customers.
-- Drop the child table first (FK to orders), then the parent.
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
