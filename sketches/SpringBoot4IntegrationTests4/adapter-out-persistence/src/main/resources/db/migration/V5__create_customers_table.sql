-- Customer table (local persistence)
CREATE TABLE IF NOT EXISTS customers (
    id         UUID         NOT NULL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    telephone  VARCHAR(30)  NOT NULL,
    email      VARCHAR(200) NOT NULL
);

-- Email is the customer's natural business key - same role V3's uk_products_name plays
-- for the product catalogue (see ProductAlreadyExistsException / CustomerAlreadyExistsException).
ALTER TABLE customers ADD CONSTRAINT uk_customers_email UNIQUE (email);
