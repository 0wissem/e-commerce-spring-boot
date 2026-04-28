CREATE TABLE customers (
    id      VARCHAR(255)    NOT NULL,
    name    VARCHAR(255)    NOT NULL,
    email   VARCHAR(255)    NOT NULL UNIQUE,
    PRIMARY KEY (id)
);