--liquibase formatted sql

--changeset order-management:002-create-products
--comment: Product catalogue table (local persistence)
CREATE TABLE products (
    id        UUID          NOT NULL PRIMARY KEY,
    name      VARCHAR(200)  NOT NULL,
    price     NUMERIC(10,2) NOT NULL CHECK (price > 0),
    available BOOLEAN       NOT NULL
);
--rollback DROP TABLE products;
