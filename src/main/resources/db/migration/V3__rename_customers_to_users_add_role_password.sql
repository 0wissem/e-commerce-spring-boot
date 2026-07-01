-- The customers table becomes the identity table `users`: every row is a user with a
-- role (CONSUMER by default; ADMIN for staff). A password (BCrypt hash) is added so users
-- can authenticate. Existing rows are consumers with an empty (unusable) password until reset.
ALTER TABLE customers RENAME TO users;

ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CONSUMER';
ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '';
