WITH cust AS (
    SELECT array_agg(id) AS ids FROM customers
)
INSERT INTO orders (id, customer_id, status, total_price)
SELECT
    gen_random_uuid()::text,
    ids[floor(random() * array_length(ids, 1))::int + 1],
    (ARRAY['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'])[floor(random() * 5)::int + 1],
    round((random() * 800 + 20)::numeric, 2)
FROM generate_series(1, 200000), cust;
