-- Fixed UUIDs for reproducible tests. customer_id used to be a free-text string
-- ('customer-1'/'customer-2'); now that Order.customerId references a real Customer
-- (see V5__create_customers_table.sql), these are fixed customer UUIDs instead - no
-- matching customers row is required, since orders.customer_id has no FK (see
-- V6__orders_customer_id_uuid.sql).
INSERT INTO orders (id, customer_id, status, total_amount, created_at, updated_at)
VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001', 'PENDING',    150.00, NOW(), NOW()),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000001', 'CONFIRMED',  200.00, NOW(), NOW()),
  ('aaaaaaaa-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-000000000002', 'COMPLETED',  300.00, NOW(), NOW());

INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price)
VALUES
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'Widget A', 2, 50.00),
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000002', 'Widget B', 1, 50.00),
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', 'Widget A', 4, 50.00),
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000003', 'Gadget C', 3, 100.00);
