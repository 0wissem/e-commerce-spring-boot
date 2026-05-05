INSERT INTO products (id, name, price, stock_quantity)
SELECT
    gen_random_uuid()::text,
    (ARRAY[
        'Gaming Laptop', 'Ultra Slim Laptop', 'Business Laptop', 'Gaming Desktop', 'Mini PC',
        'Mechanical Keyboard', 'Wireless Mouse', '4K Monitor', 'Curved Monitor', 'Webcam HD',
        'USB Hub', 'External SSD', 'RAM DDR5', 'Gaming Headset', 'Noise Cancelling Headphones',
        'Bluetooth Speaker', 'Smart Speaker', 'Smartphone Pro', 'Tablet WiFi', 'Smartwatch',
        'Fitness Tracker', 'Wireless Earbuds', 'Running Shoes', 'Yoga Mat', 'Dumbbell Set',
        'Resistance Bands', 'Cycling Helmet', 'Mountain Bike', 'Tennis Racket', 'Basketball',
        'Coffee Machine', 'Air Fryer', 'Blender', 'Microwave Oven', 'Robot Vacuum',
        'Air Purifier', 'Electric Toothbrush', 'Hair Dryer', 'Desk Lamp', 'Office Chair',
        'Standing Desk', 'Bookshelf', 'Gaming Controller', 'VR Headset', 'Drone Camera',
        'Action Camera', 'Portable Charger', 'Smart Thermostat', 'Security Camera', 'LED Strip'
    ])[1 + floor(random() * 50)::int]
    || ' ' || i,
    round((random() * 1900 + 10)::numeric, 2),
    floor(random() * 500)::int
FROM generate_series(1, 10000) AS s(i);