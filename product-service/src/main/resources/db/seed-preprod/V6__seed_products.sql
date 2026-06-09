-- Preprod seed: a small, fixed set of 20 products (vs the 10k in seed-prod).
-- Names are chosen to match the category-assignment patterns in V7.
INSERT INTO products (id, name, price, stock_quantity) VALUES
    (gen_random_uuid()::text, 'Gaming Laptop',        1499.99, 25),
    (gen_random_uuid()::text, 'Business Laptop',       1099.00, 40),
    (gen_random_uuid()::text, 'Mini PC',                649.50, 30),
    (gen_random_uuid()::text, 'Mechanical Keyboard',     89.99, 120),
    (gen_random_uuid()::text, 'Wireless Mouse',          39.90, 200),
    (gen_random_uuid()::text, '4K Monitor',             329.00, 60),
    (gen_random_uuid()::text, 'External SSD',           129.99, 150),
    (gen_random_uuid()::text, 'Gaming Headset',          79.99, 90),
    (gen_random_uuid()::text, 'Bluetooth Speaker',       59.99, 110),
    (gen_random_uuid()::text, 'Wireless Earbuds',        99.00, 140),
    (gen_random_uuid()::text, 'Smartphone Pro',         899.00, 75),
    (gen_random_uuid()::text, 'Tablet WiFi',            449.00, 80),
    (gen_random_uuid()::text, 'Smartwatch',             199.00, 95),
    (gen_random_uuid()::text, 'Running Shoes',           89.00, 130),
    (gen_random_uuid()::text, 'Yoga Mat',                29.99, 160),
    (gen_random_uuid()::text, 'Mountain Bike',          749.00, 15),
    (gen_random_uuid()::text, 'Coffee Machine',         149.00, 50),
    (gen_random_uuid()::text, 'Air Fryer',               99.99, 70),
    (gen_random_uuid()::text, 'Gaming Controller',       59.00, 100),
    (gen_random_uuid()::text, 'VR Headset',             399.00, 35);
