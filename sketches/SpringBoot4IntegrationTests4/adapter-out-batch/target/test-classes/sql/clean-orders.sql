-- Run before tests to leave the order tables in a clean state
DELETE FROM order_items;
DELETE FROM orders;
