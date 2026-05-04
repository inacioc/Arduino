-- Orders aggregate root
CREATE TABLE orders (
    id          UUID        NOT NULL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    total_price NUMERIC(19, 4),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);

-- Order items (child collection)
CREATE TABLE order_items (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     UUID         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INT          NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(19, 4) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
