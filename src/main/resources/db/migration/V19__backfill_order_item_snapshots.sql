UPDATE order_items oi
SET product_snapshot = json_build_object(
    'name', p.name,
    'price', p.price,
    'categories', (
        SELECT coalesce(json_agg(json_build_object('id', c.id, 'name', c.name)), '[]'::json)
        FROM product_categories pc
        JOIN categories c ON pc.category_id = c.id
        WHERE pc.product_id = p.id
    )
)
FROM products p
WHERE oi.product_id = p.id
AND p.deleted_at IS NULL;
