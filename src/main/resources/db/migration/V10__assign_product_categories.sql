INSERT INTO categories (id, name) VALUES
('00000000-0000-0000-0000-000000000001', 'Electronics'),
('00000000-0000-0000-0000-000000000002', 'Computers & Laptops'),
('00000000-0000-0000-0000-000000000003', 'Peripherals'),
('00000000-0000-0000-0000-000000000004', 'Audio & Sound'),
('00000000-0000-0000-0000-000000000005', 'Mobile & Tablets'),
('00000000-0000-0000-0000-000000000006', 'Sports & Fitness'),
('00000000-0000-0000-0000-000000000007', 'Kitchen & Home'),
('00000000-0000-0000-0000-000000000008', 'Gaming')
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
SELECT p.id,
    CASE
        WHEN p.name ILIKE '%laptop%' OR p.name ILIKE '%desktop%' OR p.name ILIKE '%mini pc%' THEN '00000000-0000-0000-0000-000000000002'
        WHEN p.name ILIKE '%keyboard%' OR p.name ILIKE '%mouse%' OR p.name ILIKE '%monitor%' OR p.name ILIKE '%webcam%' OR p.name ILIKE '%hub%' OR p.name ILIKE '%ssd%' OR p.name ILIKE '%ram%' THEN '00000000-0000-0000-0000-000000000003'
        WHEN p.name ILIKE '%headset%' OR p.name ILIKE '%headphones%' OR p.name ILIKE '%speaker%' OR p.name ILIKE '%earbuds%' THEN '00000000-0000-0000-0000-000000000004'
        WHEN p.name ILIKE '%smartphone%' OR p.name ILIKE '%tablet%' OR p.name ILIKE '%smartwatch%' OR p.name ILIKE '%fitness tracker%' THEN '00000000-0000-0000-0000-000000000005'
        WHEN p.name ILIKE '%shoes%' OR p.name ILIKE '%yoga%' OR p.name ILIKE '%dumbbell%' OR p.name ILIKE '%resistance%' OR p.name ILIKE '%helmet%' OR p.name ILIKE '%bike%' OR p.name ILIKE '%tennis%' OR p.name ILIKE '%basketball%' THEN '00000000-0000-0000-0000-000000000006'
        WHEN p.name ILIKE '%coffee%' OR p.name ILIKE '%fryer%' OR p.name ILIKE '%blender%' OR p.name ILIKE '%microwave%' OR p.name ILIKE '%vacuum%' OR p.name ILIKE '%purifier%' OR p.name ILIKE '%toothbrush%' OR p.name ILIKE '%dryer%' OR p.name ILIKE '%lamp%' OR p.name ILIKE '%chair%' OR p.name ILIKE '%desk%' OR p.name ILIKE '%bookshelf%' OR p.name ILIKE '%thermostat%' OR p.name ILIKE '%led strip%' THEN '00000000-0000-0000-0000-000000000007'
        WHEN p.name ILIKE '%gaming%' OR p.name ILIKE '%controller%' OR p.name ILIKE '%vr headset%' THEN '00000000-0000-0000-0000-000000000008'
        ELSE '00000000-0000-0000-0000-000000000001'
    END
FROM products p
WHERE p.deleted_at IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
SELECT p.id, '00000000-0000-0000-0000-000000000001'
FROM products p
WHERE p.deleted_at IS NULL
  AND (p.name ILIKE '%laptop%' OR p.name ILIKE '%desktop%' OR p.name ILIKE '%ssd%' OR p.name ILIKE '%ram%'
    OR p.name ILIKE '%smartphone%' OR p.name ILIKE '%tablet%' OR p.name ILIKE '%drone%'
    OR p.name ILIKE '%camera%' OR p.name ILIKE '%charger%')
ON CONFLICT DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
SELECT p.id, '00000000-0000-0000-0000-000000000008'
FROM products p
WHERE p.deleted_at IS NULL
  AND (p.name ILIKE '%gaming laptop%' OR p.name ILIKE '%gaming desktop%'
    OR p.name ILIKE '%gaming headset%' OR p.name ILIKE '%mechanical keyboard%')
ON CONFLICT DO NOTHING;