CREATE TABLE products (
    id              VARCHAR(255)        NOT NULL,
    name            VARCHAR(255)        NOT NULL,
    price           DOUBLE PRECISION    NOT NULL,
    stock_quantity  INT                 NOT NULL,
    PRIMARY KEY (id)
);