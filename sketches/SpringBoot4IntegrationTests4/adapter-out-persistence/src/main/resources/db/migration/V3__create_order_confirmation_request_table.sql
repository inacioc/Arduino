-- Outbox table for the event-driven order-confirmation flow: a row is written here
-- (by the Modulith listener in adapter-events, reacting to OrderCreatedIntegrationEvent)
-- for every newly created order, and read/cleared by adapter-out-batch's scheduled poller.
CREATE TABLE IF NOT EXISTS order_confirmation_request (
    id            UUID          NOT NULL PRIMARY KEY,
    order_id      UUID          NOT NULL,
    target_status VARCHAR(20)   NOT NULL,
    processed     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP     NOT NULL,
    processed_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_confirmation_request_unprocessed
    ON order_confirmation_request (processed)
    WHERE processed = FALSE;
