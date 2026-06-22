-- Fixed UUIDs for reproducible tests
INSERT INTO orders (id, customer_id, status, total_amount, created_at, updated_at)
VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', 'customer-1', 'PENDING',    150.00, NOW(), NOW()),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'customer-1', 'CONFIRMED',  200.00, NOW(), NOW()),
  ('aaaaaaaa-0000-0000-0000-000000000003', 'customer-2', 'COMPLETED',  300.00, NOW(), NOW());

INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price)
VALUES
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'PROD-1', 'Widget A', 2, 50.00),
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'PROD-2', 'Widget B', 1, 50.00),
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000002', 'PROD-1', 'Widget A', 4, 50.00),
  (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000003', 'PROD-3', 'Gadget C', 3, 100.00);
