-- V1: monolith schema baseline (customers, orders, order_items).
-- The monolith now owns only customers and orders; products/categories/outbox were
-- extracted or decommissioned in earlier phases. Structure only — no sample data;
-- data is created through the API.

CREATE TABLE customers (
    id    VARCHAR(255) NOT NULL,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    CONSTRAINT customers_pkey PRIMARY KEY (id),
    CONSTRAINT customers_email_key UNIQUE (email)
);

CREATE TABLE orders (
    id          VARCHAR(255)     NOT NULL,
    customer_id VARCHAR(255)     NOT NULL,
    status      VARCHAR(50)      NOT NULL,
    total_price DOUBLE PRECISION NOT NULL,
    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE TABLE order_items (
    id               VARCHAR(255)     NOT NULL,
    order_id         VARCHAR(255)     NOT NULL,
    product_id       VARCHAR(255)     NOT NULL,   -- cross-service reference (no FK; products live in product-service)
    product_name     VARCHAR(255)     NOT NULL,
    quantity         INTEGER          NOT NULL,
    unit_price       DOUBLE PRECISION NOT NULL,
    product_snapshot TEXT,                        -- JSON snapshot of the product at order time
    CONSTRAINT order_items_pkey PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
