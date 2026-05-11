INSERT INTO customers (id, name, email)
SELECT
    gen_random_uuid()::text,
    (ARRAY[
        'Alice', 'Bob', 'Charlie', 'Diana', 'Edward', 'Fatima', 'George', 'Hannah', 'Ivan', 'Julia',
        'Kevin', 'Laura', 'Michael', 'Nora', 'Oscar', 'Paula', 'Quinn', 'Rachel', 'Sam', 'Tina',
        'Umar', 'Vera', 'William', 'Xena', 'Yusuf', 'Zara', 'Ahmed', 'Bella', 'Carlos', 'Dina'
    ])[floor(random() * 30)::int + 1]
    || ' ' ||
    (ARRAY[
        'Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Miller', 'Davis', 'Martinez', 'Wilson',
        'Anderson', 'Taylor', 'Thomas', 'Jackson', 'White', 'Harris', 'Martin', 'Thompson', 'Lee', 'Walker',
        'Hall', 'Allen', 'Young', 'King', 'Wright', 'Scott', 'Green', 'Baker', 'Adams', 'Nelson'
    ])[floor(random() * 30)::int + 1],
    'user' || i || '@example.com'
FROM generate_series(1, 50000) AS s(i);
