-- Product catalogue table (local persistence)
CREATE TABLE IF NOT EXISTS products (
    id        VARCHAR(100)  NOT NULL PRIMARY KEY,
    name      VARCHAR(200)  NOT NULL,
    price     NUMERIC(10,2) NOT NULL CHECK (price > 0),
    available BOOLEAN       NOT NULL
);
