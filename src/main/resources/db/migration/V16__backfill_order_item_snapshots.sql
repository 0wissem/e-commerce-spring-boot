UPDATE order_items oi
SET product_snapshot = jsonb_build_object(
    'name', p.name,
    'price', p.price,
    'categoryNames', COALESCE(
        (
            SELECT jsonb_agg(c.name)
            FROM product_categories pc
            JOIN categories c ON c.id = pc.category_id
            WHERE pc.product_id = p.id
        ),
        '[]'::jsonb
    )
)
FROM products p
WHERE oi.product_id = p.id
  AND oi.product_snapshot IS NULL;
