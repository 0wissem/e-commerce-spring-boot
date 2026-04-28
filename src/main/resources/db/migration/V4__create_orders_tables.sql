CREATE TABLE orders (
    id           VARCHAR(255)        NOT NULL,
    customer_id  VARCHAR(255)        NOT NULL,
    status       VARCHAR(50)         NOT NULL,
    total_price  DOUBLE PRECISION    NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE order_items (
    id            VARCHAR(255)        NOT NULL,
    order_id      VARCHAR(255)        NOT NULL,
    product_id    VARCHAR(255)        NOT NULL,
    product_name  VARCHAR(255)        NOT NULL,
    quantity      INT                 NOT NULL,
    unit_price    DOUBLE PRECISION    NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders(id)
);