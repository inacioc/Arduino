-- orders.customer_id now references a real Customer (see V5__create_customers_table.sql)
-- instead of being a free-text identifier, so its column type must match Customer.id's
-- (UUID). No FK constraint is added deliberately - same soft-reference choice already made
-- for order_items.product_id (see V1): the customer's existence is checked at the
-- application layer (CreateOrderValidator, via CustomerRepositoryPort), not by the
-- database, consistent with product lookups.
--
-- USING customer_id::uuid only works if every existing row's customer_id already looks like
-- a UUID; on a fresh/dev database (no pre-existing free-text customer ids) this is a no-op.
ALTER TABLE orders ALTER COLUMN customer_id TYPE UUID USING customer_id::uuid;
