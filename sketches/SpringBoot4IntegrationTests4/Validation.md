# Validation & Error Handling

How this application turns everything that can go wrong — a missing field, a business
rule, a duplicate key, a database or broker outage — into one consistent HTTP response
shape. Read this alongside `CLAUDE.md`'s Architecture section; this document assumes
the hexagonal (ports & adapters) layout described there.

## The problem

A request can fail for reasons that live at completely different layers:

| Layer | Example | Who detects it |
|---|---|---|
| Request shape | Missing `customerId`, `quantity` is negative | Bean Validation, before any domain code runs |
| Business rule | Product doesn't exist / isn't orderable / price doesn't match | `OrderDomainService` |
| Resource state | Order id doesn't exist; a product name is already taken | A domain service, via a driven port |
| Infrastructure | The database or IBM MQ is unreachable; a unique constraint fired | A driven adapter (JPA, JMS) |
| Access | No/invalid bearer token; caller lacks the required role | Spring Security |

Left alone, each of these produces a different exception type from a different part of
the stack, and a naive `@ExceptionHandler` per type leads to `adapter-in-web` returning
a different JSON shape (or none at all) depending on which layer happened to reject the
request. The fix is **one response envelope**, and **one place** (`GlobalExceptionHandler`)
that knows how to build it — fed by exceptions that already carry enough structure to
build it *without* string-matching on messages.

## The response envelope

```java
@Data @Builder
class ResponseApiError {
    private final HttpStatus status;
    private final List<ApiError> errors;
}

@Data @Builder
class ApiError {
    private final String code;               // "PRODUCT_NOT_FOUND" - stable, machine-readable
    private final ErrorLevel level;           // BLOCKING | FROCED | INFORMATION
    private final String label;               // "Product Not Found" - human title
    private final String description;         // request-scoped detail
    private final String uriDesc;              // link to this code's docs
    private final List<String> errorsValueList; // affected fields/lines, when applicable
}
```

Every error this API returns — validation, not-found, conflict, infrastructure outage —
comes back as `ResponseApiError` with at least one `ApiError`. A client only ever parses
one shape.

**Field semantics**, roughly RFC 7807 ("Problem Details for HTTP APIs") with project-specific
names:

| Field | RFC 7807 analogue | Meaning |
|---|---|---|
| `code` | short form of `type` | Stable identifier a client can branch on. Never changes wording. |
| `label` | `title` | Fixed per `code`. Safe to render as a heading. |
| `description` | `detail` | The specific, request-scoped message. May embed request data (an id, a submitted value). **Never** a raw exception message or stack trace when `level == FROCED`. |
| `uriDesc` | `type` | Dereferenceable link to `code`'s documentation. Built by `ErrorDocs` from `app.errors.docs-base-uri` + `code`. |
| `errorsValueList` | — (project addition) | Every field path / line identifier this `code` applies to. |

### `ErrorLevel`

```java
public enum ErrorLevel { BLOCKING, FROCED, INFORMATION }
```

- **`BLOCKING`** — an ordinary, expected outcome of validation or a business rule. The
  client sent something that can't succeed as-is, but a different request would. Never
  paged on.
- **`FROCED`** — the request was rejected because infrastructure the domain depends on
  forced it to fail (database/broker unavailable, a non-business constraint violation),
  not because of anything the client did. Always logged with full detail server-side; an
  operator should be alerted if it recurs. *(Implemented exactly as spelled in the
  original spec — flag it if `FORCED` was intended, it's a one-word rename.)*
- **`INFORMATION`** — a non-blocking notice attached to an otherwise-successful response.
  Not currently produced anywhere in this API; the value exists so the contract has room
  for one without a breaking change later.

### `errorsValueList` grouping rule

Field/line-level errors are grouped **by `code`**, not emitted one `ApiError` per field.
Two order lines that both fail with `PRODUCT_NOT_AVAILABLE` come back as **one**
`ApiError` with two entries in `errorsValueList`, not two separate `ApiError`s. This
means a client that only cares about "do I have a `PRODUCT_NOT_AVAILABLE` problem, and
where" doesn't have to aggregate by code itself. `GlobalExceptionHandler.groupByCode(...)`
implements this for both Bean Validation field errors and `DomainValidationException`'s
violations, so the same rule applies whether the request-shape validator or the domain
rejected the request.

## The domain exception hierarchy

```
DomainException (abstract, message [+ cause])
├── ResourceNotFoundException (abstract; resourceName + identifier)
│   ├── ProductNotFoundException
│   └── OrderNotFoundException
├── ResourceAlreadyExistsException (abstract; resourceName + identifier)
│   └── ProductAlreadyExistsException
├── DomainValidationException           (Notification pattern - see below)
├── PersistenceDataValidationException   (constraint violation, not a simple duplicate)
├── OptimisticLockingConflictException  (resourceName + identifier)
└── InfrastructureUnavailableException  (database/broker unreachable)
```

All in each module's root package, or in `shared/` for the cross-cutting ones (or the aggregate's own package for
`IllegalArgumentException`/`IllegalStateException` — see below): **one hierarchy**, not
a "functional" tree and a separate "technical" tree. `PersistenceDataValidationException`,
`OptimisticLockingConflictException`, and `InfrastructureUnavailableException` describe
infrastructure outcomes, but they're still just plain classes with a message and an
optional cause — nothing in their own signature mentions JPA, JMS, or SQL — so they can
live in the framework-agnostic `domain` module without violating the hexagonal dependency
rule ("only adapters know about infrastructure"). From `GlobalExceptionHandler`'s point of
view, "the order doesn't exist" and "the database is unreachable" are both just
"a `DomainException` was thrown, map it" — the thing that differs is each subtype's
*semantics* (`BLOCKING` vs. `FROCED`), not where it lives in the type tree.

**Why `resourceName` + `identifier` instead of a `code` field on every exception:**
`ResourceNotFoundException`/`ResourceAlreadyExistsException` don't carry an explicit code
string. `GlobalExceptionHandler` derives one: `codeFor("Product", "NOT_FOUND")` →
`"PRODUCT_NOT_FOUND"`. This keeps every concrete subtype (`ProductNotFoundException`,
`OrderNotFoundException`, ...) to one line — `super("Product", identifier)` — instead of
repeating a hand-typed code string that could drift from the class name.

### Aggregates keep using plain JDK exceptions

`Order` and `Product` (the rich domain aggregates) still throw
`IllegalArgumentException` (invariant violated while constructing/mutating — e.g. a
non-positive price) and `IllegalStateException` (illegal state transition — e.g.
confirming an already-confirmed order), **not** a `DomainException` subtype. This is a
deliberate, pre-existing project convention (`CLAUDE.md`): these two exceptions guard
low-level aggregate invariants, one level below "a use case's outcome". `GlobalExceptionHandler`
maps both vocabularies into the same envelope (`IllegalArgumentException` → 400
`INVALID_REQUEST`, `IllegalStateException` → 409 `ILLEGAL_STATE`), so the split between
"aggregate guard" and "service/port outcome" is invisible to a client — it only matters
to someone reading the domain code.

### Domain validators — the Notification pattern

Functional rules live in `DomainValidator<T>` implementations (`order/internal/domain`, `customer/internal/domain`, `product/internal/domain`; the base types are in `shared/`). A
validator never throws on the first broken rule: it reports each one into a
`ValidationNotification`, so a client sees every problem in one round trip and several
validators can share one notification.

```java
public record DomainViolation(String code, String propertyPath, String message) {}

public interface DomainValidator<T> {
    void validate(T target, ValidationNotification notification);

    default void assertValid(T target) { /* throws DomainValidationException if any violation */ }
}
```

- `code` is what becomes `ApiError.code`; `propertyPath` becomes an entry of
  `errorsValueList`. The 2-argument `notification.addError(path, message)` uses
  `DomainViolation.DEFAULT_CODE` for rules that don't warrant their own code.
- A validator that needs data it doesn't own takes the **driven port** through its
  constructor. `CreateOrderValidator` receives `ProductRepositoryPort` and first checks the
  command isn't empty (`ITEMS_REQUIRED`) — returning immediately rather than falling through
  to a no-op loop — then checks, per order line, that the product exists, is orderable, and
  that the submitted `unitPrice` matches the catalogue price (a client's price is never
  trusted as the source of truth for money). Each bad line is reported under the product id,
  with the codes `PRODUCT_NOT_FOUND` / `PRODUCT_NOT_AVAILABLE` / `PRICE_MISMATCH`.
- `OrderDomainService.createOrder` is then just `createOrderValidator.assertValid(command)`
  followed by building the aggregate. The aggregate's own `IllegalArgumentException` /
  `IllegalStateException` guards stay as the last line of defence.
- `GlobalExceptionHandler` turns `DomainValidationException.getViolations()` into one
  `ApiError` per code (422 `UNPROCESSABLE_CONTENT`), listing every affected `propertyPath`
  in that code's `errorsValueList` — see the grouping rule above.
- Not every validator reports through the notification. `SaveProductValidator` (also
  `ProductRepositoryPort`-backed) checks the catalogue name is still free via
  `findByName(...)` and, if it's taken by a *different* product id, throws
  `ProductAlreadyExistsException` directly instead of adding a violation — see "Which
  resource can actually produce a duplicate" below for why that's the right exception to
  reuse rather than inventing a 422-shaped duplicate. `validate()` throwing outright (instead
  of signalling) is a deliberate escape hatch for a check whose outcome already has an
  established type and status; it isn't the default and every other validator in this
  codebase still just signals.

## Translating infrastructure exceptions at the port boundary

### Persistence: `AbstractPersistenceAdapter`

Both persistence adapters extend `AbstractPersistenceAdapter` and route every JPA call
through `executeAndTranslate(...)`:

```java
protected <T> T executeAndTranslate(
        Supplier<T> action, String entityName, String identifier,
        BiFunction<String, String, DomainException> duplicateSupplier) {
    try {
        return action.get();
    } catch (DomainException e) {
        throw e; // e.g. a *NotFoundException thrown by the action itself - pass through
    } catch (DataIntegrityViolationException e) {
        if (isDuplicateKeyViolation(e)) throw duplicateSupplier.apply(entityName, identifier);
        throw new PersistenceDataValidationException(...);
    } catch (ConcurrencyFailureException e) {
        throw new OptimisticLockingConflictException(entityName, identifier);
    } catch (TransientDataAccessException | DataAccessResourceFailureException e) {
        throw new InfrastructureUnavailableException("Database infrastructure is temporarily unavailable", e);
    } catch (DataAccessException e) {
        throw new InfrastructureUnavailableException("Persistence error for %s [%s]".formatted(entityName, identifier), e);
    }
}
```

**Why a shared base class instead of a try/catch per adapter method:** this application
already has two persistence adapters across two tables, each with several methods that
can fail the same handful of ways. Repeating the same try/catch in every method is
exactly the kind of "several tables, same kind of error" duplication that invites drift
— one method gets updated when a new failure mode is learned, another is forgotten.
Wrapping every adapter method's body in `executeAndTranslate` gets every current *and
future* persistence adapter the same translation for free, just by extending the base
class. (Spring AOP was the other realistic option here — see "Alternatives considered"
below for why the template-method approach was preferred for this codebase.)

A 3-arg overload (`executeAndTranslate(action, entityName, identifier)`) exists for
reads/deletes, where a duplicate-key violation is inherently unexpected — no
entity-specific "already exists" exception to build, so callers aren't forced to invent
a throwaway `duplicateSupplier` they'll never see invoked.

**The duplicate-key heuristic is a best-effort message sniff, not a constraint-name
parse:**

```java
private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
    String message = Optional.ofNullable(e.getRootCause())
            .map(Throwable::getMessage).orElse("").toLowerCase();
    return message.contains("duplicate") || message.contains("unique");
}
```

This works for PostgreSQL's actual driver messages ("duplicate key value violates
unique constraint..."). It is deliberately simple rather than parsing the specific
constraint name out of the SQLState/message — good enough to distinguish "this write
conflicts with existing data" from "some other constraint fired", which is exactly the
one bit `duplicateSupplier` needs. If the database vendor changes, or a constraint's
wording changes, re-verify this string match still holds.

**Which resource can actually produce a duplicate:** `Product.id` is a client-supplied
UUID that Spring Data JPA's `save()` treats as an **upsert** key — re-saving the same id
updates the row (see `ProductPersistenceAdapterIT.save_updatesExistingProduct`, a
pre-existing, intentional behavior this refactor does not change). So `id` can never
itself be "already taken". `name` is the catalogue's actual business key: migration `V3`
(`uk_products_name`) enforces it at the database level, and
`ProductPersistenceAdapter.save()` passes `product.getName()` as the identifier —
`(entityName, name) -> new ProductAlreadyExistsException(name)`. `Order` never produces
a duplicate at all: `Order.create()` always assigns a fresh server-generated UUID, so its
`save()` uses the 3-arg overload.

**Closed gap:** relying on the DB constraint alone meant a duplicate-name POST did one
wasted round trip to the database before failing. `SaveProductValidator` now pre-checks
via `ProductRepositoryPort.findByName(...)` and throws the same `ProductAlreadyExistsException`
before `ProductDomainService.save` ever calls `Product.create`/`productRepository.save`.
The unique constraint stays in place as the correctness guarantee for the TOCTOU window
the pre-check can't close (two concurrent requests for the same free name) — the pre-check
is purely an optimization for the common, non-racing case, not a replacement for it.

**`save()` alone is not enough to catch the violation — it must be `saveAndFlush()`.**
`JpaRepository.save()` only enqueues the write in the persistence context; Hibernate
defers the actual `INSERT` until the next flush, which by default happens at
`@Transactional` **commit** — i.e. *after* `ProductPersistenceAdapter.save()` (and
`executeAndTranslate`'s try/catch) has already returned control to
`ProductDomainService`. A `DataIntegrityViolationException` from the unique constraint
would then surface at commit time, outside this adapter entirely, and never get
translated into `ProductAlreadyExistsException` — it would instead propagate as a raw,
unmediated exception (falling through to `GlobalExceptionHandler`'s absolute
`Exception` fallback, a 500, not the 409 the client should see). `ProductPersistenceAdapter.save()`
therefore calls `jpaRepository.saveAndFlush(...)`, forcing the `INSERT` — and so the
constraint check — to happen synchronously, inside the try/catch, where it can actually
be caught. This is specific to writes that need their constraint violation translated in
the same call; a plain read has nothing to flush, and `OrderPersistenceAdapter.save()`
doesn't need it today because no unique/business constraint can fire on an order write
(see above) — but keep it in mind for any future write path added to either adapter
where a constraint violation needs to become a domain exception.

### Messaging: `OrderMqPublisher`

The same idea, applied inline since there's only one call site (no repetition to solve
with a shared base class):

```java
} catch (JmsException e) {
    throw new InfrastructureUnavailableException(
            "The order events broker is temporarily unavailable. Please try again later.", e);
}
```

A JSON serialization failure (`JacksonException`) is folded into the *same*
`InfrastructureUnavailableException` type rather than inventing a third exception class
— from the caller's point of view, "the event couldn't be serialized" and "the broker is
down" both mean the same thing: this order event could not be delivered, 503, nothing
the client did wrong.

**A deliberate consequence worth knowing:** `OrderDomainService.createOrder` is
`@Transactional`, and calls `orderEvents.publishOrderCreated(saved)` *after* saving the
order but before returning. If that publish throws `InfrastructureUnavailableException`,
the whole transaction — including the order save — rolls back. This means a broker
outage fails order creation outright rather than leaving an order persisted with no
event ever published. That's the safer default for this codebase today; an outbox
pattern (persist the event in the same transaction as the order, publish it
asynchronously afterward) would decouple the two and is the standard fix if broker
availability turns out to be lower than database availability in practice — not
implemented here, flagged for awareness.

## `GlobalExceptionHandler`: the mapping

| Exception | HTTP | `level` | `code` |
|---|---|---|---|
| `ResourceNotFoundException` | 404 | `BLOCKING` | `<RESOURCE>_NOT_FOUND` |
| `ResourceAlreadyExistsException` | 409 | `BLOCKING` | `<RESOURCE>_ALREADY_EXISTS` |
| `DomainValidationException` | 422 | `BLOCKING` | grouped per violation `code` |
| `IllegalArgumentException` | 400 | `BLOCKING` | `INVALID_REQUEST` |
| `IllegalStateException` | 409 | `BLOCKING` | `ILLEGAL_STATE` |
| `PersistenceDataValidationException` | 409 | `FROCED` | `DATA_CONSTRAINT_VIOLATION` |
| `OptimisticLockingConflictException` | 409 | `FROCED` | `OPTIMISTIC_LOCK_CONFLICT` |
| `InfrastructureUnavailableException` | 503 | `FROCED` | `INFRASTRUCTURE_UNAVAILABLE` |
| `MethodArgumentNotValidException` (`@Valid` body) | 400 | `BLOCKING` | grouped per constraint annotation name |
| `ConstraintViolationException` (`@Validated` params) | 400 | `BLOCKING` | grouped per constraint annotation name |
| `HttpMessageNotReadableException` | 400 | `BLOCKING` | `MALFORMED_REQUEST_BODY` |
| `MethodArgumentTypeMismatchException` | 400 | `BLOCKING` | `TYPE_MISMATCH` |
| `MissingServletRequestParameterException` | 400 | `BLOCKING` | `MISSING_PARAMETER` |
| `HttpRequestMethodNotSupportedException` | 405 | `BLOCKING` | `METHOD_NOT_ALLOWED` |
| `NoResourceFoundException` | 404 | `BLOCKING` | `RESOURCE_NOT_FOUND` |
| `AccessDeniedException` (`@PreAuthorize` denial) | 403 | `BLOCKING` | `ACCESS_DENIED` |
| `AuthenticationException` (rare — see below) | 401 | `BLOCKING` | `UNAUTHENTICATED` |
| any other `DomainException` (safety net) | 400 | `BLOCKING` | `REQUEST_REJECTED` |
| `Exception` (absolute fallback) | 500 | `FROCED` | `INTERNAL_ERROR` + a logged reference id |

Bean Validation's `FieldError.getCode()` returns Spring's *least-specific* generated
message code, which for a `@NotBlank` failure is the constraint's own simple name
(`"NotBlank"`) — already exactly the stable, machine-readable identifier `ApiError.code`
needs, so no extra mapping table is maintained for it. This does mean domain-originated
codes are `SCREAMING_SNAKE_CASE` (`PRODUCT_NOT_FOUND`) while Bean-Validation-originated
codes keep the annotation's own `PascalCase` name (`NotBlank`) — an accepted, intentional
inconsistency: renaming the latter would only obscure which annotation produced it, for
no real benefit.

**`FROCED`-level `description`s are safe to return as-is.** `AbstractPersistenceAdapter`
and `OrderMqPublisher` are written so their exception messages never contain SQL text,
driver detail, or broker internals — only the entity name and business identifier
involved. `GlobalExceptionHandler` trusts that and passes `ex.getMessage()` straight
through; full technical detail (stack trace, driver message) is logged where the adapter
first caught it, not repeated at the web layer.

**The absolute fallback never leaks.** Anything that reaches `handleUnexpected`
(`Exception.class`) is, by definition, a bug — not a modeled outcome. The client gets a
generic message plus a random reference id; the full stack trace is logged server-side
against that same id, so a support conversation can find it without the response body
ever containing exception internals.

## Security: 401/403 outside `GlobalExceptionHandler`

`@RestControllerAdvice` only runs for exceptions Spring MVC's `DispatcherServlet`
catches — i.e. ones thrown *while a controller method is executing*. Two security
failure modes happen **before** that:

- A missing/invalid JWT is rejected by the OAuth2 Resource Server's filters, before
  `DispatcherServlet` ever runs.
- An `authorizeHttpRequests` rule denial happens at the filter-chain level too.

Neither reaches any `@ExceptionHandler` no matter what it's annotated with — Spring
Security's `ExceptionTranslationFilter` handles both, using whatever
`AuthenticationEntryPoint`/`AccessDeniedHandler` is configured. By default that produces
an **empty** 401/403 body, breaking the "always `ResponseApiError`" contract. The fix:
`RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` (in `error/`) write the same
envelope directly to the servlet response, wired in via
`SecurityConfig`'s `.oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(...))`
and `.exceptionHandling(ex -> ex.accessDeniedHandler(...))`.

`@PreAuthorize` denials are the exception (pun intended): they're thrown by a
method-security AOP interceptor around the controller bean — *inside*
`DispatcherServlet`'s handler invocation — so `GlobalExceptionHandler`'s
`@ExceptionHandler(AccessDeniedException.class)` catches those directly, and
`RestAccessDeniedHandler` never sees them. Both are registered so a denial produces an
identical body regardless of which layer caught it.

## Round trip: the frontend client

`frontend` deliberately depends on none of the hexagon's modules (see the root pom's
module comment) — it only talks HTTP+JSON to `adapter-in-web`'s REST API, like any other
external client. It carries its own minimal mirror of the wire shape
(`ResponseApiErrorDto`/`ApiErrorDto` in `frontend/client/dto`) and
`ResponseApiErrorTranslator` turns a failed response back into a typed
`Backend*Exception` the MVC controllers catch — the same idea as
`GlobalExceptionHandler`, one hop downstream:

- 404 → `BackendNotFoundException`
- 409 → `BackendConflictException`
- 422 → `BackendOrderValidationException`, carrying the per-line `OrderItemErrorDto(productId,
  code, message)` list. `OrderMvcController.mapItemErrors` matches each error back to its
  form line by `productId` and calls `bindingResult.rejectValue(...)`, falling back to a
  global error when no line matches.
- anything else → `BackendUnavailableException`

## Alternatives considered

**AOP (`@Aspect`) instead of `AbstractPersistenceAdapter` for the persistence
translation.** Both solve the same duplication problem. An aspect matching
`execution(* ...*PersistenceAdapter.*(..))` would translate every adapter method without
each adapter needing to `extends` anything, and would apply automatically to a future
adapter that forgets to extend the base class. It was not chosen here because: (1) it
needs `spring-boot-starter-aspectj` on the classpath purely for this one cross-cutting
concern in a small module, and (2) a template method keeps the "what gets translated"
decision visible at each call site (`executeAndTranslate(..., entityName, identifier,
duplicateSupplier)`) rather than invisible until you go looking for the aspect. Prefer
AOP instead if this pattern needs to spread across many more adapters/modules than the
two here, where the "invisible until you go looking for it" cost stops paying for itself.

**A single `ApiError` per field instead of grouping by `code`.** Simpler to build, but
pushes the "which fields share this problem" aggregation onto every client instead of
doing it once, centrally. Rejected in favor of the grouping rule documented above.

## Adding a new error — checklist

1. **Functional, resource-shaped** ("X not found" / "X already exists" for a new
   resource type): add a concrete subclass of `ResourceNotFoundException` /
   `ResourceAlreadyExistsException` — one line, `super("ResourceName", identifier)`.
   Nothing to change in `GlobalExceptionHandler`; the code is derived automatically.
2. **Functional, one-off** (doesn't fit "not found"/"already exists"/order-line
   validation): add a new `DomainException` subclass and a matching
   `@ExceptionHandler` in `GlobalExceptionHandler` — pick the `HttpStatus` and
   `ErrorLevel` deliberately, don't default to `BLOCKING`/400 without thinking about it.
3. **A new functional validation rule**: add a check to the relevant `DomainValidator`
   (or a new validator, injecting whatever port it needs) reporting a `code` + `propertyPath`.
   No `GlobalExceptionHandler` change needed.
4. **A new infrastructure failure mode**: extend `AbstractPersistenceAdapter`'s
   `executeAndTranslate` catch clauses (persistence) or add a catch clause where the
   adapter calls its client library (messaging/future outbound HTTP) — translate to an
   existing technical exception type if it fits, or add a new one + handler if the
   distinction is operationally meaningful (would an operator want to tell it apart from
   `InfrastructureUnavailableException` in a dashboard?).
