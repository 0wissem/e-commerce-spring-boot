-- V1: order-service schema (orders + order_items).
-- Structure only — no seed/test data. Data is created through the API.

CREATE TABLE orders (
    id            VARCHAR(255)     NOT NULL,
    customer_id   VARCHAR(255)     NOT NULL,   -- cross-service reference (no FK)
    customer_name VARCHAR(255)     NOT NULL,   -- snapshot at creation time
    status        VARCHAR(255)     NOT NULL,
    total_price   DOUBLE PRECISION NOT NULL,
    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT orders_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE TABLE order_items (
    id                     VARCHAR(255)     NOT NULL,
    order_id               VARCHAR(255)     NOT NULL,
    product_id             VARCHAR(255)     NOT NULL,   -- cross-service reference (no FK)
    product_name           VARCHAR(255)     NOT NULL,
    unit_price             DOUBLE PRECISION NOT NULL,
    quantity               INTEGER          NOT NULL,
    total_price            DOUBLE PRECISION NOT NULL,
    order_product_snapshot TEXT,                        -- JSON snapshot (name + price + categories)
    CONSTRAINT order_items_pkey PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- Speeds up GET /api/orders/customer/{customerId}
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
