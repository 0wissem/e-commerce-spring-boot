-- V1: product-service schema baseline (categories, products, product_categories).
-- Structure + fixed reference data only. No sample products — those are created via the API.

CREATE TABLE categories (
    id          VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE products (
    id             VARCHAR(255)     NOT NULL,
    name           VARCHAR(255)     NOT NULL,
    price          DOUBLE PRECISION NOT NULL,
    stock_quantity INT              NOT NULL,
    deleted_at     TIMESTAMP        NULL,
    -- Full-text search column (generated from name) + GIN index. This is the
    -- machinery that broke under load in the monolith and justified the extraction.
    search_vector  tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(name, ''))) STORED,
    PRIMARY KEY (id)
);

CREATE INDEX idx_products_search_vector ON products USING GIN (search_vector);
CREATE INDEX idx_products_price ON products (price);
CREATE INDEX idx_products_stock ON products (stock_quantity);

CREATE TABLE product_categories (
    product_id  VARCHAR(255) NOT NULL,
    category_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, category_id),
    FOREIGN KEY (product_id)  REFERENCES products (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Reference data: the fixed category taxonomy. These IDs are stable constants
-- referenced across services (e.g. frozen into order product snapshots), so they
-- belong with the schema, not with sample data.
INSERT INTO categories (id, name, description) VALUES
('00000000-0000-0000-0000-000000000001', 'Electronics', 'General electronics and gadgets'),
('00000000-0000-0000-0000-000000000002', 'Computers & Laptops', 'Desktop and laptop computers'),
('00000000-0000-0000-0000-000000000003', 'Peripherals', 'Computer peripherals and accessories'),
('00000000-0000-0000-0000-000000000004', 'Audio & Sound', 'Headphones, speakers and audio equipment'),
('00000000-0000-0000-0000-000000000005', 'Mobile & Tablets', 'Smartphones, tablets and wearables'),
('00000000-0000-0000-0000-000000000006', 'Sports & Fitness', 'Sports equipment and fitness gear'),
('00000000-0000-0000-0000-000000000007', 'Kitchen & Home', 'Kitchen appliances and home decor'),
('00000000-0000-0000-0000-000000000008', 'Gaming', 'Gaming hardware and accessories')
ON CONFLICT (id) DO NOTHING;
