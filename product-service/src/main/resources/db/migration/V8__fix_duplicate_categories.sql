-- Remap product_categories from duplicate random-UUID categories to their seeded equivalents
INSERT INTO product_categories (product_id, category_id)
SELECT pc.product_id, mapping.seeded_id
FROM product_categories pc
JOIN (VALUES
    ('5913d6f6-60e7-4819-9e3e-043d11566799', '00000000-0000-0000-0000-000000000008'),
    ('68951c1b-2fbb-4926-a887-4ef9f351a7b4', '00000000-0000-0000-0000-000000000007'),
    ('76fc2d53-03be-4e75-8247-8c0ef931dfeb', '00000000-0000-0000-0000-000000000004'),
    ('9cf4e9ff-a1d9-4a90-a03b-cf5ce7549261', '00000000-0000-0000-0000-000000000001'),
    ('be30a58f-94b4-4bc4-b940-8d389f6c0509', '00000000-0000-0000-0000-000000000005'),
    ('cc72e447-a1b0-48c0-8261-9062a353480e', '00000000-0000-0000-0000-000000000006'),
    ('e9a25468-b963-430d-b48a-105373152f99', '00000000-0000-0000-0000-000000000002'),
    ('f83e8c0a-b95c-40fb-adb3-ca206a43522e', '00000000-0000-0000-0000-000000000003')
) AS mapping(random_id, seeded_id) ON pc.category_id = mapping.random_id
WHERE NOT EXISTS (
    SELECT 1 FROM product_categories pc2
    WHERE pc2.product_id = pc.product_id
      AND pc2.category_id = mapping.seeded_id
);

-- Remove old product_categories entries pointing to the duplicate categories
DELETE FROM product_categories
WHERE category_id IN (
    '5913d6f6-60e7-4819-9e3e-043d11566799',
    '68951c1b-2fbb-4926-a887-4ef9f351a7b4',
    '76fc2d53-03be-4e75-8247-8c0ef931dfeb',
    '9cf4e9ff-a1d9-4a90-a03b-cf5ce7549261',
    'be30a58f-94b4-4bc4-b940-8d389f6c0509',
    'cc72e447-a1b0-48c0-8261-9062a353480e',
    'e9a25468-b963-430d-b48a-105373152f99',
    'f83e8c0a-b95c-40fb-adb3-ca206a43522e'
);

-- Delete the duplicate categories
DELETE FROM categories
WHERE id IN (
    '5913d6f6-60e7-4819-9e3e-043d11566799',
    '68951c1b-2fbb-4926-a887-4ef9f351a7b4',
    '76fc2d53-03be-4e75-8247-8c0ef931dfeb',
    '9cf4e9ff-a1d9-4a90-a03b-cf5ce7549261',
    'be30a58f-94b4-4bc4-b940-8d389f6c0509',
    'cc72e447-a1b0-48c0-8261-9062a353480e',
    'e9a25468-b963-430d-b48a-105373152f99',
    'f83e8c0a-b95c-40fb-adb3-ca206a43522e'
);
