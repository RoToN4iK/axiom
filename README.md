# Axiom

A headless, microservices-based SaaS subscription and billing engine. Axiom manages the full lifecycle of recurring subscriptions — plan changes, proration, invoicing — independently of any payment provider. It does not move money itself; it tells a pluggable payment provider (Stripe by default) what to charge, and reacts to the result.

Think of it as a self-hosted alternative to building Stripe Billing logic from scratch: you run Axiom alongside your own application, and your backend talks to it over REST.

## Table of Contents

- [Why this exists](#why-this-exists)
- [How it fits into your stack](#how-it-fits-into-your-stack)
- [Architecture](#architecture)
    - [Modules](#modules)
    - [Why a monorepo](#why-a-monorepo)
- [The REST API surface (`gateway-service`)](#the-rest-api-surface-gateway-service)
- [A real correction: why `auth-service` doesn't do login](#a-real-correction-why-auth-service-doesnt-do-login)
- [Tech stack](#tech-stack)
- [Local ports](#local-ports)
- [Key design decisions](#key-design-decisions)
- [Status](#status)
- [Getting started](#getting-started)
    - [Kafka](#kafka)
- [License](#license)

## Why this exists

Payment providers like Stripe are excellent at moving money but provide limited support for the business logic around it — proration on mid-cycle plan changes, consistent subscription state across services, invoice history. Every SaaS company ends up building this logic themselves. Axiom is that logic, built once, designed to be reused.

## How it fits into your stack

```
Your App  →  Axiom (REST API, X-Api-Key + Idempotency-Key headers) — manage plans, subscriptions
Axiom     →  Your App (Webhooks)                                   — Axiom notifies you when billing events happen
Axiom     →  Payment Provider                                      — Axiom delegates the actual charge to Stripe (or your own implementation)
```

Axiom never touches card data and is not PCI-scoped. It owns the *state and math* of billing; the payment provider owns *moving the money*.

**Axiom has no concept of your end users.** It is a server-to-server API — the caller is your backend, authenticated with an API key, not a human logging in. Your own application owns its own user accounts and login flow entirely; Axiom only ever sees an opaque `externalUserId` you pass it when creating a subscription. See "A real correction" below for why this matters.

## Architecture

Axiom is a Gradle monorepo of independent Spring Boot services communicating internally over gRPC, with a single REST-facing gateway. Services are event-driven via Kafka for anything that doesn't need a synchronous response.

```
                         ┌──────────────────┐
                         │  gateway-service │   REST, public-facing, Swagger UI
                         └────────┬─────────┘
                                  │ gRPC
        ┌──────────────┬──────────┼──────────┬──────────────┐
        │              │          │          │              │
  auth-service  subscription-  billing-  payment-   notification-
                  service       service   service       service
        │              │          │          │              │
     Postgres       Postgres   Postgres   Postgres      (no DB)
        │              │          │          │              │
        └──────────────┴────Kafka─┴──────────┴──────────────┘
                        (external, self-hosted)

billing-service also makes a synchronous gRPC call back to
subscription-service (PlanService.GetPlan) to fetch plan prices
when calculating proration.
```

### Modules

| Module                 | Type                                  | Status      | Responsibility                                                                                                                                                                                                                                                                                   |
|------------------------|---------------------------------------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `axiom-contracts`      | Shared library                        | Done        | `.proto` files and generated gRPC/Kotlin stubs (including `grpckt` for coroutine service stubs). Source of truth for inter-service contracts.                                                                                                                                                    |
| `axiom-common`         | Shared library                        | Done        | Framework-free Kotlin: `Money` (decimal-safe arithmetic), sealed exception hierarchy, idempotency key validation, injectable `Clock`, `AxiomBootstrap`.                                                                                                                                          |
| `auth-service`         | gRPC server                           | Done        | Company-level API key management: create, validate, revoke. **Not** end-user login — see below.                                                                                                                                                                                                  |
| `subscription-service` | gRPC server                           | Done        | Core domain: `Plan` CRUD, subscription lifecycle (create/get/cancel/upgrade). Publishes events via the Transactional Outbox pattern.                                                                                                                                                             |
| `billing-service`      | gRPC client + Kafka consumer/producer | Done        | Consumes `subscription.upgraded`, fetches plan prices via gRPC, calculates proration, generates `Invoice` + `LineItem`s, publishes `invoice.created` via its own Transactional Outbox.                                                                                                           |
| `gateway-service`      | REST (Spring Web)                     | Done        | The only service exposed publicly. Full REST surface over `auth-service`/`subscription-service`, Redis-backed idempotency-key enforcement, global gRPC-to-HTTP error translation, fully documented via Swagger UI. `X-Api-Key` request *enforcement* not yet wired in (deliberate — see Status). |
| `payment-service`      | gRPC server + REST webhooks           | Not started | Talks to Stripe. No multi-provider abstraction planned for v1.                                                                                                                                                                                                                                   |
| `notification-service` | Kafka consumer                        | Not started | Scope decision deferred until `payment-service` exists.                                                                                                                                                                                                                                          |

### Why a monorepo

Services share contracts and common code constantly during development. Rather than publishing a new contracts JAR to Reposilite on every change, services depend on `axiom-contracts` and `axiom-common` as Gradle project dependencies (`project(":axiom-contracts")`). Reposilite is reserved for publishing release artifacts, not for the inner dev loop.

## The REST API surface (`gateway-service`)

All endpoints documented interactively at `/swagger-ui.html`. Every `POST`/`PUT`/`PATCH` requires an `Idempotency-Key` header (Redis-backed, 24h retention) — sending the same key twice replays the cached response instead of processing the request again.

```
POST   /api/auth/keys                  — create a new API key for a company
POST   /api/auth/keys/{id}/revoke      — revoke a key

POST   /api/plans                      — create a plan
GET    /api/plans/{id}                 — get a plan
GET    /api/plans                      — list active plans

POST   /api/subscriptions              — create a subscription
GET    /api/subscriptions/{id}         — get a subscription
POST   /api/subscriptions/{id}/cancel  — cancel a subscription
POST   /api/subscriptions/{id}/upgrade — upgrade a subscription to a new plan
```

gRPC-layer errors (`NOT_FOUND`, `ALREADY_EXISTS`, `FAILED_PRECONDITION`, etc.) are translated to matching HTTP status codes globally, via a single `@RestControllerAdvice` — not repeated per controller.

## A real correction: why `auth-service` doesn't do login

`auth-service` was originally built as a conventional user-login system — email/password registration, OTP verification over Redis, JWT access/refresh tokens. All of it worked correctly. It was also the wrong feature for this product.

Axiom is self-hosted, headless infrastructure that a *company* plugs into their own backend — the same relationship you have with Stripe's API. The caller on every request is that company's server, not a human at a keyboard. A company already has its own user accounts and its own login flow; Axiom has no business owning a second one. The right authentication model for this shape of product is a static **API key**, generated once and sent as a header on every request — not registration, OTP, or per-user sessions.

`auth-service` now manages `ApiKey` records instead: one row per company, the raw key is shown exactly once at creation time, and only its hash is ever stored (SHA-256, not BCrypt — API keys need direct lookup-by-hash, which a salted hash can't support, and don't need slow hashing since they're already high-entropy random data rather than a human-chosen password). The original JWT/OTP/BCrypt implementation is preserved as a reference pattern for the next project where it *is* the right fit — most of the underlying Spring/Kotlin work carried over directly; only the domain model needed to change.

A second, smaller version of the same lesson: `subscriber.proto` (a planned `SubscriberService` for storing subscriber records) was removed entirely, never implemented. Axiom has no legitimate reason to store anything about a subscriber — that data belongs entirely to the company's own system. A `subscriberId` is just an opaque reference passed in wherever it's needed (e.g. `CreateSubscriptionRequest`), the same way `externalUserId` was always meant to work — it never needed to be its own entity or service.

## Tech stack

- **Language:** Kotlin 2.3.21, Java 21 toolchain
- **Framework:** Spring Boot 4.1 (native gRPC server *and* client support, Data JPA, Flyway starters)
- **Inter-service communication:** gRPC + Protocol Buffers (Kotlin coroutine stubs via `grpckt`)
- **Client communication:** REST (gateway only), documented via `springdoc-openapi` (Swagger UI at `/swagger-ui.html`)
- **Messaging:** Apache Kafka (self-hosted; remote by default, with an optional fully-local Docker Compose profile — see Kafka below)
- **Database:** PostgreSQL, one isolated logical database per service
- **Cache:** Redis (idempotency-key storage in `gateway-service`)
- **Migrations:** Flyway
- **Artifact hosting:** Reposilite (self-hosted, for published releases)
- **Infra:** Docker, Docker Compose, Caddy
- **Testing:** JUnit 5, Testcontainers (Postgres), Mockito-Kotlin (gRPC clients, `KafkaTemplate`, `Clock`)

## Local ports

Every service needs its own gRPC port. Keep this table updated whenever a new service is added — a port collision produces a confusing startup failure, not an obvious one.

| Service                | gRPC port                 | Postgres DB          | Notes                                                                                     |
|------------------------|---------------------------|----------------------|-------------------------------------------------------------------------------------------|
| `auth-service`         | `9090`                    | `axiom_auth`         |                                                                                           |
| `subscription-service` | `9091`                    | `axiom_subscription` |                                                                                           |
| `billing-service`      | `9092`                    | `axiom_billing`      | gRPC-client to `subscription-service`                                                     |
| `gateway-service`      | n/a (REST)                | none                 | HTTP on `8080`. gRPC-clients to all three backend services.                               |
| `payment-service`      | `9096`                    | TBD                  | `9093`–`9095` are reserved by the local Kafka profile below — not available for services. |
| `notification-service` | n/a (Kafka consumer only) | none                 |                                                                                           |

Postgres itself runs in Docker on host port `5433` (mapped from container `5432`) — see `docker/docker-compose.yml`.

**gRPC client config — the current, correct property format for Spring Boot 4.1's native client support:**
```properties
spring.grpc.client.channel.subscription-service.target=static://127.0.0.1:9091
```
Not `spring.grpc.client.<name>.address` — that's an older/mismatched key shape that silently falls back to literal DNS resolution instead of the static override, which fails with `UnknownHostException` the moment the channel name isn't a real resolvable hostname (worked by coincidence for `localhost`-based setups, broke immediately once `gateway-service` needed to resolve plain service names like `auth-service`). Also avoid hyphens in the logical channel name used in Kotlin code (`channels.createChannel("auth-service")` vs `"auth"`) — hyphens have caused mapping failures between the property key and the channel factory lookup. This bit both `gateway-service` and `billing-service`; fixed in both.

Local dev needs two things IntelliJ doesn't pick up from Gradle automatically:
- **VM options** on every run configuration: `-Duser.timezone=UTC`. Spring Boot's Gradle-launched `bootRun` reads the `systemProperty("user.timezone", "UTC")` set in each service's `build.gradle.kts` `tasks.named<BootRun>("bootRun")` block — but IntelliJ's own run configurations use a separate launcher that never touches your `main()` function early enough (a `companion object { init { ... } }` block does *not* reliably run before Flyway's autoconfiguration under IntelliJ's launcher, even though it does under `@SpringBootTest`). Setting `-Duser.timezone=UTC` directly as a VM option is the only fix confirmed to work from the IDE.
- Each service needs its own `.env` file in its module root (not `src/main/resources/` — that gets bundled into the built JAR, which you don't want for a file full of secrets) with real `POSTGRES_USER`/`POSTGRES_PASS`/`KAFKA_*` values.

## Key design decisions

**Money is never a float.** All monetary values are transmitted as integer cents (`int64`) over gRPC and handled via `BigDecimal` in application code. The `Money` type in `axiom-common` is the only way services should perform monetary arithmetic. When chaining a multiply and a divide (e.g. proration: `price × daysRemaining / cycleLength`), **multiply first, divide last** — dividing early introduces rounding error that compounds (found and fixed in `billing-service`'s proration calculation).

**Subscription upgrades create a new row, never mutate the old one.** The old row is marked `SUPERSEDED` (a status distinct from `CANCELLED`, since an upgrade and a user-initiated cancellation are different business facts and would otherwise be indistinguishable in churn/history reporting). This preserves the exact old-plan data (price, period end) that `billing-service` needs for proration.

**Eventual consistency via Transactional Outbox.** When a service needs to change its own state and notify other services, both actions happen in a single database transaction — the state change and an outbox row. A separate `@Scheduled` poller reads unpublished rows and publishes to Kafka, blocking on the send's `Future` to confirm delivery before marking a row published, so a Kafka outage never causes silent data drift. Implemented independently in both `subscription-service` and `billing-service` — each service owns its own outbox table; the pattern isn't shared code, since each instance is tied to that service's own database.

**Cross-service reads go through gRPC, never a shared database.** `billing-service` needs plan prices to calculate proration, but has no access to `subscription-service`'s database — it calls `PlanService.GetPlan` via a gRPC client instead. Services never read another service's tables directly.

**Domain enums stay separate from proto enums.** e.g. `subscription-service`'s own `SubscriptionStatus` Kotlin enum is distinct from `axiom.common.SubscriptionStatus` (the generated proto enum), bridged by explicit `toProto()`/`toDomain()` mapping functions. This means persistence and wire format can evolve independently — the same principle applied to `Money`, `BillingCycle`, and `InvoiceStatus`. Proto-to-domain mapping functions are written as exhaustive `when` blocks with **no `else` branch**, so adding a new value to a `.proto` enum later causes a compile error here rather than a silent, wrong default. `gateway-service`'s REST DTOs represent enums as plain, regex-validated strings rather than adding a fourth parallel enum type per concept, converting to the real proto enum only at the point of the gRPC call.

**API keys are hashed with SHA-256, not BCrypt.** Unlike a human password, an API key already is high-entropy random data and needs to be looked up by exact value (`WHERE key_hash = ?`). BCrypt's per-hash random salt makes that lookup impossible without already knowing the row. A fast, deterministic hash is the correct tool here — the opposite conclusion from password hashing, and worth remembering as a genuinely different problem shape, not a downgrade.

**Idempotency-key caching had to be split across a `Filter` and a `ResponseBodyAdvice`, not handled in one place.** A single `Filter` wrapping the response in `ContentCachingResponseWrapper` looked correct but silently cached empty bodies: Kotlin `suspend` controller methods are dispatched onto a separate async execution path in Spring MVC, so `filterChain.doFilter()` can return *before* the coroutine has actually finished writing its response. The check (does this key already have a cached response?) stays in the `Filter`, which can safely short-circuit synchronously; the write (cache the response after it's produced) moved to a `ResponseBodyAdvice`, which Spring MVC invokes only after the coroutine has genuinely completed, regardless of the async dispatch mechanics underneath.

**Payment providers are pluggable (planned).** `payment-service` will depend on a `PaymentProvider` interface, not directly on Stripe, so swapping providers means implementing that interface — no changes elsewhere in the system. Not yet built.

## Status

This project is under active development. Current progress:

- [x] `axiom-contracts`
- [x] `axiom-common`
- [x] `auth-service` — rebuilt around API keys after catching the login-system mismatch above
- [x] `subscription-service`
- [x] `billing-service`
- [x] `gateway-service` — full REST surface, idempotency, error translation, Swagger docs, optional local Kafka profile
- [ ] `payment-service` ← **currently here**
- [ ] `notification-service`

Known, deliberate gaps (documented, not accidental):
- `gateway-service` does not yet enforce `X-Api-Key` validation on incoming requests — `AuthService.ValidateApiKey` exists and works (verified via Postman gRPC calls), but no REST middleware calls it yet. All endpoints are currently open.
- `AuthService.CreateApiKey` itself has no auth guard — anyone who can reach it can mint a new company key. Fine while nothing is deployed publicly; needs a guard (e.g. require an existing valid key, with a one-time bootstrap exception) before any real deployment.
- No automated tests yet for `gateway-service`'s controllers, filter, or exception advice.
- No distributed lock on either service's outbox poller — not needed until a service runs as more than one instance.
- No renewal scheduler — blocked on `billing-service`/`payment-service` existing to actually act on a "period ended" event; building it earlier would mean faking a successful charge that never happened.
- `Plan` prices are assumed single-currency across an upgrade (no cross-currency proration guard) — plans are assumed region-locked at the product level, so a genuine cross-currency upgrade shouldn't be reachable from the UI/API layer above `subscription-service`.

## Getting started

```bash
git clone https://github.com/RoToN4iK/axiom.git
cd axiom
docker compose -f docker/docker-compose.yml up -d
./gradlew build
```

Each service and docker needs its own `.env` (see Local ports above) before it will actually boot. On first boot, `auth-service` auto-generates a bootstrap API key if none exists yet and logs it once to the console — save it immediately, it cannot be retrieved again. Once running, browse the full API at `http://localhost:8080/swagger-ui.html`.

By default, `subscription-service` and `billing-service` expect a real Kafka broker reachable via `.env`. If you don't have one, see the Kafka section below for a fully local, no-external-dependency alternative.

### Kafka

By default, point `.env`'s `KAFKA_*` values at your own Kafka broker (VPS, managed service, etc.).

For a fully local dev environment with no external dependency:
```bash
docker compose -f docker/docker-compose.yml --profile local-kafka up -d
```
This starts a plaintext, unauthenticated Kafka container on `localhost:9094` — local-only, never intended to be exposed publicly.

## License

TBD