CREATE TABLE order_items (
    id           BIGSERIAL        NOT NULL,
    order_id     VARCHAR(36)      NOT NULL,
    product_id   VARCHAR(255)     NOT NULL,
    product_name VARCHAR(255)     NOT NULL,
    quantity     INTEGER          NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(19, 2)   NOT NULL CHECK (unit_price >= 0),
    currency     VARCHAR(3)       NOT NULL,
    CONSTRAINT pk_order_items     PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
