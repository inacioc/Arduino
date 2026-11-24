# Events — Choices II: Customer, `adapter-events`, and simulated mail

This documents the design choices behind three connected changes:

1. A new `Customer` domain entity, with `Order.customerId` refactored from a free-text
   `String` to a real `UUID` referencing it.
2. Recreating a dedicated `adapter-events` Maven module and moving the Spring Modulith
   event-publishing/listening code out of `adapter-in-web` into it.
3. Simulating an email to the customer whenever their order reaches `CONFIRMED`,
   `COMPLETED` or `CANCELLED`, as a second, independent event reaction alongside the
   existing status-change audit logging.

It follows on from the general Modulith best-practice discussion the project already has;
the choices below are the concrete application of that discussion to a real feature.

## 1. Why `Customer` is a full aggregate, not a bigger string

`Order.customerId` used to be an arbitrary `String` the caller supplied — nothing checked
it referred to anything real. That's fine for a demo up to a point, but it means "send the
customer an email" has nothing to send: no name, no address. Introducing `Customer`
(`customer/Customer.java`, in the `domain` Maven module) follows the exact shape `Product` already uses in this
codebase:

- A private constructor + validating factory method (`Customer.create`), guard-clause
  validation (`IllegalArgumentException`), and behaviour (`rename`, `updateContactDetails`)
  instead of bare setters — matches `CLAUDE.md`'s "rich aggregates guard their own
  invariants" rule.
- A `reconstitute` factory for the persistence adapter to rebuild the aggregate without
  re-running validation on data that's already valid (mirrors `Order.reconstitute`).

**One deliberate difference from `Product`:** `Customer.id` is server-generated
(`UUID.randomUUID()` inside `create`), not client-supplied. `Product.id` is intentionally
client-supplied and treated as an upsert key (see `ProductPersistenceAdapter`'s javadoc) —
that's a specific, documented simplification for the product catalogue. A customer is a
genuinely new identity every time it's registered, never an upsert target, so it follows
`Order.create`'s pattern (which also generates its own id) instead.

**Email is the business key**, enforced the same way `Product.name` is: a unique index
(`V5__create_customers_table.sql`, `uk_customers_email`) as the source of truth, with a
fast pre-check in `SaveCustomerValidator` reported as `CustomerAlreadyExistsException`
(409) rather than through the validation notification — see that class's javadoc for why
(it mirrors `SaveProductValidator` exactly, including the race-condition note).

### What was deliberately left out

- **No customer-management UI beyond a plain text field.** The order form
  (`frontend/.../orders/form.html`) still takes `customerId` as a single text input; it
  now has to be a real customer UUID instead of an arbitrary string, and the label says so
  (`Customer id (UUID - see /api/customers)`). A proper customer picker (search-by-name
  dropdown, a "create customer" link) is a real UX improvement but is out of scope for
  this change — the point here was the domain/event modelling, not a frontend rebuild.
- **No update/delete customer use cases.** Only `SaveCustomerUseCase` (create) and
  `GetCustomerUseCase` (read) exist, mirroring the minimum `Product` needed for orders to
  reference it. `Customer.rename`/`updateContactDetails` exist on the aggregate (for
  symmetry with `Product.rename`/`changePrice`) but nothing calls them yet.

## 2. `Order.customerId`: `String` → `UUID`

This is the refactor that ties `Order` to a real `Customer`. It touches every layer,
mechanically, but the design decisions are:

- **No foreign key from `orders.customer_id` to `customers.id`.** This mirrors a choice
  already made in this codebase for `order_items.product_id` (see `V1`): the referenced
  aggregate's existence is checked at the *application* layer, not enforced by the
  database. Concretely, `CreateOrderValidator` now checks
  `customerRepository.findById(command.customerId())` up front, adding a
  `CUSTOMER_NOT_FOUND` violation into the *same* accumulating notification the product
  checks already use (see below) — not a database constraint. `V6__orders_customer_id_uuid.sql`
  is a plain `ALTER COLUMN ... TYPE UUID`, nothing more.
- **`CUSTOMER_NOT_FOUND` accumulates alongside product errors, in one exception.** This
  was the single most important consistency call: the project has already settled (see the
  earlier PowerPoint feedback that started the validation-notification pattern) that a
  client should get *every* problem with a request in one response, not a first-error-wins
  404. Adding the customer check as a `notification.addError(...)` at the top of
  `CreateOrderValidator.validate` — instead of, say, throwing `CustomerNotFoundException`
  (404) before the item loop even runs — means "unknown customer AND two bad product
  lines" comes back as one 422 listing all three, exactly like unknown-product and
  unavailable-product already do together.
- **Domain events' `customerId` field also became `UUID`**, not left as `String`, for the
  obvious reason: `OrderConfirmedEvent`/`OrderCompletedEvent`/`OrderCancelledEvent` exist
  specifically so a listener can act on the order and its customer — a listener holding a
  bare string it can't look anything up with would defeat the point. This is also what
  makes the mail-simulation listener (section 4) possible without an extra database call
  to translate a string into something usable.

## 3. Recreating `adapter-events` as a library module

The Modulith event-publishing code (`OrderStatusEventPublisher`,
`OrderStatusAuditListener`) was living directly inside `adapter-in-web`. It's now its own
Maven module, `adapter-events`, that `adapter-in-web` depends on.

**This is not a return to the cross-process `adapter-event-process` module that was built
and then reverted earlier.** That's an important enough distinction to spell out, because
on the surface "a separate module for events" looks like the same idea:

| | Reverted `adapter-event-process` | This `adapter-events` |
|---|---|---|
| Deployable? | Yes — its own `@SpringBootApplication`, runnable standalone | No — a plain library jar, like `adapter-out-persistence`/`adapter-out-messaging`, no main class |
| How it consumes events | Polled another process's `EventPublicationRegistry` table directly (`findIncompletePublications`/`markCompleted`) as an improvised cross-process outbox | `@ApplicationModuleListener`, same process only, same as it always was in `adapter-in-web` |
| Where it runs | A second, independent JVM | Inside whichever app embeds it (today: `adapter-in-web`'s own `WebApplication` process) |

The only thing that changed is *which Maven module the `.java` files live in* — not the
runtime architecture. The package stays
`com.example.ordermanagement.infrastructure.adapter.events`, which is still covered by
`WebApplication`'s existing `scanBasePackages = {"com.example.ordermanagement.domain",
"com.example.ordermanagement.infrastructure"}`, so no scan configuration changed and these
beans still run exactly where they always did: inside the web app's own process, reacting
to events published in that same process.

> **Update — package layout.** The code was later repackaged by business capability (Spring
> Modulith modules). `OrderStatusEventPublisher` now lives in
> `com.example.ordermanagement.order.internal.adapter.events`, and the two listeners
> (`OrderStatusAuditListener`, `CustomerNotificationListener`) in
> `com.example.ordermanagement.notification`. The events themselves (`OrderConfirmedEvent`, …)
> are part of the `order` module's public API (its root package) and stay in the `domain` Maven
> module. The publisher and listeners stay in the `adapter-events` Maven module and are covered
> by `WebApplication`'s `scanBasePackages` (`order`, `customer`, `product`, `notification`,
> `shared`, `infrastructure`). The text above describes the original choice and uses the old
> package names.

**Why bother splitting it out at all, then?** Because it's genuinely reusable library code
now, the same reason `adapter-out-persistence` and `adapter-out-messaging` are their own
modules rather than being folded into `adapter-in-web`: if `adapter-out-batch` is ever
changed to go through `OrderDomainService` for status changes (today it deliberately
bypasses it — see `OrderStatusChangeProcessor`), it could add the same `order-adapter-events`
dependency and get the same audit/notification listeners for free, in its own process,
with zero code duplication. That's a "when needed" argument, not a "needed now" one — it's
not being done speculatively for `adapter-out-batch` today, just noted as the reason the
module boundary is worth having.

`spring-modulith-starter-jpa` moved from a direct `adapter-in-web` dependency to a direct
`adapter-events` dependency; `adapter-in-web` gets it transitively now. The
`event_publication` table's migration (`V4__create_event_publication_table.sql`) moved
with it, into `adapter-events/src/main/resources/db/migration/` — Flyway merges migration
files from every jar sharing the `classpath:db/migration` location, so this still gets
picked up automatically by whichever app embeds the module, exactly as `V1`–`V3` already do
for `adapter-out-persistence`'s tables.

## 4. Simulated mail: a second listener, not a fourth event type

The request was specifically to *keep* the existing status-changing flow and *add* an
asynchronous reaction for email. Two designs were possible:

- **(a)** Introduce a new event type per transition (e.g. `OrderConfirmedMailRequestedEvent`)
  published alongside the existing ones.
- **(b)** Add a second `@ApplicationModuleListener` reacting to the *same* three events
  `OrderStatusAuditListener` already consumes.

**(b) was chosen** — that's `CustomerNotificationListener`
(`adapter-events/.../CustomerNotificationListener.java`). Reasons:

- It's the textbook case for Modulith's multi-listener model: several independent
  reactions to one published fact, each with its own durably-tracked `event_publication`
  row, its own retry, and its own failure isolation. That's precisely what the "one
  listener per side effect" principle from the earlier best-practices discussion is for —
  adding a listener is the intended way to add a new reaction, not a new event.
- `OrderDomainService` and `OrderStatusEventPublisher` — the actual status-changing flow —
  are **completely untouched** by this feature. That satisfies "keep the existing
  status-changing [code]" literally: the publishing side has no idea mail simulation was
  added, and no way to know if it's later removed.
- A fourth event type would have meant either duplicating `orderId`/`customerId` into a new
  record for no new information, or overloading the existing three events with a
  mail-specific concern they don't otherwise carry. Neither adds anything a second listener
  doesn't already give for free.

**What "simulate" means concretely:** `CustomerNotificationListener` looks up the
`Customer` via `CustomerRepositoryPort` (using the event's `customerId`) and logs what
would have been sent — recipient, subject, body — at `INFO` level. There is no SMTP/mail
dependency anywhere in the project; swapping the `log.info` calls for a real `MailSender`
call is the only change a genuine implementation would need. Everything about delivery
guarantees stays the same either way, which is also why the listener's javadoc calls out
its idempotency explicitly:

- Logging a duplicate line is harmless, so this listener needs no de-duplication today.
- A **real** mail send would not have that property — Modulith's at-least-once delivery
  (retried on restart, or if completion bookkeeping fails after a successful run) means a
  genuine implementation reusing this shape would need its own "already notified for this
  event" guard. This is called out directly in the class's javadoc as the one thing that
  would have to change beyond the transport itself.
- If the customer can't be found (shouldn't normally happen — `CreateOrderValidator`
  already checked this at order-creation time — but nothing stops a customer being removed
  later), the listener logs a warning and returns rather than throwing. Throwing would mark
  the publication failed and retry it forever over something a retry can't fix.

## 5. Summary of best practices applied (cross-reference)

| Practice | Where |
|---|---|
| Typed events, not a generic `Object` listener | `OrderConfirmedEvent`/`OrderCompletedEvent`/`OrderCancelledEvent`, unchanged from before this feature |
| One listener method per side effect | `OrderStatusAuditListener` (logging) and `CustomerNotificationListener` (mail) are two separate classes, each with three single-purpose methods |
| Publish through a port, never `ApplicationEventPublisher` from `domain` | `OrderStatusEventPort` / `OrderStatusEventPublisher`, unchanged |
| In-process only — never a substitute for the real cross-process channel | Section 3's comparison table; IBM MQ (`OrderEventPort`) remains the only cross-process transport |
| Idempotent listener bodies, given at-least-once delivery | `CustomerNotificationListener`'s javadoc; logging is naturally idempotent, called out as the reason a real implementation would need more |
| Accumulate every functional error in one response | `CreateOrderValidator`'s new `CUSTOMER_NOT_FOUND` check joins `PRODUCT_NOT_FOUND`/etc. in the same notification |
| Soft (application-checked) references over database FKs, consistently | `orders.customer_id` follows `order_items.product_id`'s existing precedent |
| Mirror existing patterns instead of inventing new ones | `Customer`/`CustomerRepositoryPort`/`CustomerPersistenceAdapter`/`SaveCustomerValidator`/`CustomerController` each mirror their `Product` equivalent field-for-field where the shape actually matches |
