CREATE TABLE product_categories (
    product_id   VARCHAR(255) NOT NULL,
    category_id  VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, category_id),
    FOREIGN KEY (product_id)  REFERENCES products(id),
    FOREIGN KEY (category_id) REFERENCES categories(id)
);