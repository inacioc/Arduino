-- Orders aggregate root table
CREATE TABLE IF NOT EXISTS orders (
    id           UUID         NOT NULL PRIMARY KEY,
    customer_id  VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);

-- Order line items
CREATE TABLE IF NOT EXISTS order_items (
    id           UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity     INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(10,2) NOT NULL CHECK (unit_price > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
