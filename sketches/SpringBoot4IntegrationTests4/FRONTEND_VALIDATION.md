# Frontend module: architecture and validation strategy

`frontend` (artifact `order-frontend`) is a new, standalone Maven module: a
server-rendered Thymeleaf UI for orders and products. Unlike the domain's other
driving adapters, **it is not part of the hexagon at all** — it depends on
none of `order-domain`, `order-adapter-out-persistence`,
`order-adapter-out-messaging`, or `order-adapter-events`. It only knows that
`adapter-in-web` exists as an HTTP+JSON API and talks to it over the network,
the same way any external client would.

This document explains why it's built that way, and the validation strategy
that follows from it.

## Why a separate application, not a fifth adapter

Every other driving adapter in this codebase (`OrderController`,
`ProductController`, the batch runner) sits *inside* the hexagon: it depends
on `order-domain`, injects the inbound ports directly, and the only thing
separating it from the domain is a Spring `@Bean` wire-up. `frontend` is
deliberately **outside** it — a second, independent Spring Boot application
that happens to render HTML instead of JSON, running as its own process on
its own port (8081), calling `adapter-in-web` (8080) over HTTP.

That has a real consequence for validation: this module never sees a domain
exception. It cannot catch `OrderDomainService.OrderValidationException` or
`IllegalStateException` — those types live in `order-domain`, which isn't on
its classpath. Everything it knows about a failed order comes back as an
HTTP status code and a JSON body, exactly as any other consumer of the REST
API would see it. So "surfacing domain-layer errors" here means: parse
`adapter-in-web`'s `ProblemDetail` response, translate it into a typed
exception local to this module, and map *that* onto the form.

## Package layout

```
frontend/src/main/java/com/example/ordermanagement/frontend/
  FrontendApplication.java
  client/                     - the only thing that talks to adapter-in-web
    BackendProperties.java      (base-url + auth config)
    RestClientConfig.java       (RestClient bean, attaches the bearer token)
    OrderApiClient.java / ProductApiClient.java
    ProblemDetailTranslator.java (ProblemDetail JSON -> typed exception)
    dto/                       - records mirroring adapter-in-web's JSON shapes
    exception/                 - BackendNotFoundException, BackendConflictException,
                                 BackendOrderValidationException, BackendUnavailableException
  web/                         - MVC controllers, forms, the custom constraint
  config/                      - Thymeleaf layout dialect bean
```

Note there is no `infrastructure.adapter.*` package here, unlike the rest of
the codebase — that naming is reserved for adapters *of the hexagon*, and
this module isn't one. `client/dto` holds plain records that copy the wire
shape of `OrderResponse`/`ProductResponse`/`CreateOrderRequest`/etc.
(`OrderDto`, `ProductDto`, `CreateOrderRequestDto`, `OrderStatus`...) — not
because the two shapes happen to coincide today by accident, but because a
JSON contract has to be modeled by *something* on the client side, and a
small local copy is the standard way to do that without pulling in the
producer's implementation types.

## Talking to adapter-in-web

`RestClientConfig` builds a single `RestClient` bean, base-URL'd to
`app.backend.base-url`, with a request interceptor that attaches
`Authorization: Bearer <app.backend.access-token>` to every call.
`OrderApiClient`/`ProductApiClient` wrap that client's calls to
`/api/orders`/`/api/products` and translate failures via
`ProblemDetailTranslator`.

**Authentication is a static bearer token, not a login flow.**
`adapter-in-web`'s `SecurityConfig` is a stateless OAuth2 resource server
(Keycloak-issued JWTs, `@PreAuthorize("hasRole(...)")` per endpoint) — there
is no Keycloak realm/client registration checked into this repo, so wiring a
real per-user OAuth2 Authorization Code login here would mean inventing
client-id/secret/redirect-URI values nobody could verify. Instead,
`app.backend.access-token` is a token you obtain externally (e.g. a Keycloak
password-grant call) and paste into config/env; it's attached as-is to every
outgoing request. This is a deliberate, documented stand-in — see
"Out of scope" below — not a demonstration of production auth.

## Why the errors package looks the way it does

`adapter-in-web`'s `GlobalExceptionHandler` turns each domain exception into
an RFC 7807 `ProblemDetail`:

| Domain exception (in adapter-in-web) | HTTP status | Body |
|---|---|---|
| `OrderDomainService.OrderNotFoundException` | 404 | `{detail}` |
| `OrderDomainService.OrderValidationException` | 422 | `{detail, errors:[{productId, code, message}]}` |
| `IllegalStateException` (illegal status transition) | 409 | `{detail}` |
| `MethodArgumentNotValidException` (`@Valid` failed) | 400 | `{detail, errors:[...]}` |

`ProblemDetailTranslator.translate(RestClientResponseException, ObjectMapper)`
reads the response body back into `org.springframework.http.ProblemDetail`
(a `spring-web` class — a framework type, not a hexagon type, so depending on
it doesn't reintroduce the coupling this module is avoiding) and maps status
+ shape back to one of:

- `BackendNotFoundException` (404)
- `BackendConflictException` (409)
- `BackendOrderValidationException` (422) — carries the full
  `List<OrderItemErrorDto>` from the `errors` property, same as the domain
  exception carries `List<OrderItemError>` one hop upstream
- `BackendUnavailableException` — anything else: a 5xx, a network failure
  (`RestClientException`), or a body that didn't parse. Always a safety-net
  case for a healthy backend.

`ProblemDetailTranslatorTest` exercises this translation directly (no Spring
context, a bare Jackson mapper with `ProblemDetailJacksonMixin` registered)
against JSON bodies shaped exactly like `GlobalExceptionHandler` produces.

## The five validation layers

The layering itself is unchanged in spirit from a REST-API-agnostic MVC app —
a browser form still needs to come back with the user's input intact and a
specific reason next to the specific field — but layer 4 now runs one HTTP
hop away from the domain instead of catching it directly.

| Layer | What it catches | Where |
|---|---|---|
| 1. Browser (HTML5) | Obviously missing/malformed input | `required`, `min`, `step` in the templates |
| 2. Bean Validation (JSR 380) | Shape/format rules on a single field | `CreateProductForm`, `CreateOrderForm`, `OrderItemForm` |
| 3. Custom class-level constraint | Cross-field rule (no duplicate product per order) | `@UniqueProducts` on `CreateOrderForm` |
| 4. Backend/business rules, via HTTP | Rules only adapter-in-web can enforce (needs a live lookup) | Controllers catch `BackendOrderValidationException`, `BackendConflictException`, `BackendNotFoundException` |
| 5. Global fallback | Anything not caught locally | `MvcExceptionHandler` (`@ControllerAdvice`) |

### Layer 2 — Bean Validation

`CreateProductForm`/`CreateOrderForm`/`OrderItemForm` mirror the constraints
on adapter-in-web's own `CreateProductRequest`/`CreateOrderRequest`/
`OrderItemRequest` (`@NotBlank`, `@NotNull`, `@NotEmpty`, `@Min`,
`@DecimalMin`, `@Size`) — the same rule enforced on both sides of the wire,
because it's a rule about the shape of an order, not about HTTP.

One deliberate correction kept from the original pass: `CreateOrderForm.items`
is `List<@Valid OrderItemForm>` (annotation on the type argument), not
`@Valid List<OrderItemForm>` on the field — the latter is deprecated in
current Hibernate Validator (`HV000271`).

### Layer 3 — `@UniqueProducts`

Bean Validation's field annotations can't express "no two elements of this
list may share the same `productId`." `UniqueProducts` +
`UniqueProductsValidator` is a class-level `@Constraint` on `CreateOrderForm`
— the standard pattern for cross-field rules.

### Layer 4 — backend errors mapped back onto the form

This is what "errors from the hexagonal domain layer" become once the
frontend can only reach the domain through HTTP:

- **`BackendOrderValidationException`** (422, from
  `OrderDomainService.OrderValidationException` upstream) — a product line
  that referenced a product deleted or marked unavailable between page render
  and submit. `OrderMvcController.create` walks `getErrors()` and matches
  each `OrderItemErrorDto.productId()` back to the `items[i]` it came from,
  calling `bindingResult.rejectValue("items[i].productId", ...)` so the user
  sees exactly which row is wrong.
- **`BackendNotFoundException`** (404, from
  `OrderDomainService.OrderNotFoundException` upstream) — thrown by
  `OrderMvcController.detail` when `findById` comes back empty. Since the
  order page is only reachable by following a link to a real order, this is
  treated as navigation-level, not a form problem — it propagates to layer 5
  and renders a 404 page.
- **`BackendConflictException`** (409, from an `IllegalStateException` on the
  `Order` aggregate upstream — e.g. completing an order that isn't
  `CONFIRMED`) — confirm/complete/cancel are plain buttons, not forms with
  fields to redisplay, so `OrderMvcController.transition(...)` catches this
  locally and reports it as a flash error message after a redirect
  (Post-Redirect-Get).

**Redisplaying the order form after a layer-4 failure requires re-adding
every other model attribute the view needs** (the products list for the
dropdown) — Spring only repopulates the form object and `BindingResult`
automatically. `OrderMvcController.create` calls `addProductsToModel(model)`
on every failure path before returning to `orders/form`.

### Layer 5 — global fallback

`MvcExceptionHandler` catches whatever layers 2-4 didn't handle locally:
`BackendNotFoundException` -> 404 page, any other `BackendApiException` (a
5xx from adapter-in-web, or it being unreachable) -> a generic "the order
service could not complete this request" page, and a catch-all `Exception`
-> the same generic page. A browsing user should never see a stack trace or
the Whitelabel error page.

## The Order <-> Product relationship, as the forms model it

`OrderItem` is a denormalized snapshot on the domain side: `Order.create`
copies the product's *name* at creation time, but the *unit price* is
whatever the caller submits — the domain does not re-read
`Product.getPrice()`. The order-creation form reflects this:

- The product `<select>` in `orders/form.html` is populated from
  `ProductApiClient.findAll()` and tags each `<option>` with the product's
  current price (`data-price`).
- `order-form.js` pre-fills the unit-price input from that tag when a
  product is chosen — a layer-1 convenience only; the value that actually
  gets submitted and validated is whatever ends up in the input, matching
  how `adapter-in-web`'s `CreateOrderRequest` itself trusts the submitted
  price rather than recomputing it.
- A stale/incorrect price is therefore a layer-2 concern at best
  (`@DecimalMin("0.01")`) — there's no independent price on this side to
  check it against. That's a property of the existing domain model, not
  something the frontend introduces.

`adapter-in-web` has no product-update endpoint (`SaveProductUseCase.save`
always full-replaces by id, and no PUT is exposed for it), so the products
screen is list + create only.

## Out of scope

- **Real per-user authentication.** See "Talking to adapter-in-web" above —
  a static bearer token stands in for a proper OAuth2 login flow. Don't
  deploy this as-is; anyone with the token in config can act as whatever
  role that token carries.
- **Editing/deleting products or orders.** No endpoint exists for either on
  `adapter-in-web`.
- **Reproducing `GET /api/orders`'s default-to-PENDING-when-no-status-given
  quirk.** `OrderApiClient.findAll()` queries every `OrderStatus` and merges
  the results client-side, since there's no true "find all" endpoint —
  `OrderController.getByStatus` keeps its original REST behavior unchanged.

## Testing

- `ProblemDetailTranslatorTest` — a plain unit test (no Spring context)
  proving 404/409/422 `ProblemDetail` bodies shaped like
  `GlobalExceptionHandler` produces them turn into the right typed exception,
  422 with its line-level errors intact.
- `ProductMvcControllerTest` / `OrderMvcControllerTest` — `@WebMvcTest` +
  `@MockitoBean` on `ProductApiClient`/`OrderApiClient` (no real HTTP calls,
  no database): layer 2 (blank name, non-positive price, no items),
  layer 3 (duplicate products), the success/redirect path, layer 4
  (`BackendOrderValidationException` lands on `items[0].productId`,
  `BackendConflictException` from a confirm action becomes a flash error),
  and layer 5 (a missing order renders the 404 page).

Run just this module: `mvn -pl frontend test`.

## Running it

```bash
mvn -pl frontend -am spring-boot:run
```

Needs `adapter-in-web` running and reachable at `app.backend.base-url`
(default `http://localhost:8080` — adjust if you run adapter-in-web
elsewhere, since its `application.yml` also points its Keycloak issuer-uri at
`localhost:8080`) with its own Postgres/IBM MQ up, plus a valid
`app.backend.access-token` for whatever `adapter-in-web`'s security
currently requires. Listens on **8081**.
