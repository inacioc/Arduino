CREATE TABLE orders (
    id          VARCHAR(36)  NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'))
);
