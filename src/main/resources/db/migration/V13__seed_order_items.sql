WITH ord AS (
    SELECT array_agg(id) AS ids FROM orders
),
prod AS (
    SELECT
        array_agg(id ORDER BY id)    AS pids,
        array_agg(name ORDER BY id)  AS pnames,
        array_agg(price ORDER BY id) AS pprices
    FROM products
)
INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price)
SELECT
    gen_random_uuid()::text,
    ord.ids[floor(random() * array_length(ord.ids, 1))::int + 1],
    prod.pids[idx],
    prod.pnames[idx],
    floor(random() * 5)::int + 1,
    round(prod.pprices[idx]::numeric, 2)
FROM generate_series(1, 500000),
     ord,
     prod,
     LATERAL (SELECT (floor(random() * array_length(prod.pids, 1))::int + 1) AS idx) r;
