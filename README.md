# Axiom

A headless, microservices-based SaaS subscription and billing engine. Axiom manages the full lifecycle of recurring subscriptions — plan changes, proration, invoicing, and dunning — independently of any payment provider. It does not move money itself; it tells a pluggable payment provider (Stripe by default) what to charge, and reacts to the result.

Think of it as a self-hosted alternative to building Stripe Billing logic from scratch: you run Axiom alongside your own application, and your backend talks to it over REST.

## Why this exists

Payment providers like Stripe are excellent at moving money but provide limited support for the business logic around it — proration on mid-cycle plan changes, retry schedules for failed payments (dunning), and consistent subscription state across services. Every SaaS company ends up building this logic themselves. Axiom is that logic, built once, designed to be reused.

## How it fits into your stack

```
Your App  →  Axiom (REST API)       — you call Axiom to manage plans, subscribers, subscriptions
Axiom     →  Your App (Webhooks)    — Axiom notifies you when billing events happen
Axiom     →  Payment Provider       — Axiom delegates the actual charge to Stripe (or your own implementation)
```

Axiom never touches card data and is not PCI-scoped. It owns the *state and math* of billing; the payment provider owns *moving the money*.

## Architecture

Axiom is a Gradle monorepo of independent Spring Boot services communicating internally over gRPC, with a single REST-facing gateway. Services are event-driven via Kafka for anything that doesn't need a synchronous response.

```
                         ┌──────────────────┐
                         │  gateway-service │   REST, public-facing
                         └────────┬─────────┘
                                  │ gRPC
        ┌──────────────┬──────────┼──────────┬──────────────┐
        │              │          │          │              │
  auth-service  subscription-  billing-  payment-   notification-
                  service       service   service       service
        │              │          │          │              │
     Postgres       Postgres   Postgres   Postgres      (no DB)
     + Redis         + Redis              
                       │          │          │              │
                       └──────────┴────Kafka─┴──────────────┘
```

### Modules

| Module                 | Type                         | Responsibility                                                                                                                                 |
|------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `axiom-contracts`      | Shared library               | `.proto` files and generated gRPC/Kotlin stubs. The source of truth for inter-service contracts.                                               |
| `axiom-common`         | Shared library               | Framework-free Kotlin: `Money` (decimal-safe arithmetic), sealed exception hierarchy, idempotency key validation, injectable `Clock`.          |
| `gateway-service`      | REST (Spring Web)            | The only service exposed publicly. Validates input, handles JWT auth, enforces idempotency keys via Redis, forwards to internal gRPC services. |
| `auth-service`         | gRPC server                  | Identity: account creation, OTP generation/verification, JWT issuance.                                                                         |
| `subscription-service` | gRPC server                  | Core domain: plans, subscribers, subscription lifecycle. Publishes events via the Transactional Outbox pattern.                                |
| `billing-service`      | gRPC server + Kafka consumer | Proration math (BigDecimal-only), invoice generation.                                                                                          |
| `payment-service`      | gRPC server + REST webhooks  | Talks to payment providers via a `PaymentProvider` interface. Stripe is the reference implementation.                                          |
| `notification-service` | Kafka consumer               | Sends emails on billing events. No database.                                                                                                   |

### Why a monorepo

Services share contracts and common code constantly during development. Rather than publishing a new contracts JAR to Reposilite on every change, services depend on `axiom-contracts` and `axiom-common` as Gradle project dependencies (`project(":axiom-contracts")`). Reposilite is reserved for publishing release artifacts, not for the inner dev loop.

## Tech stack

- **Language:** Kotlin 2.3.21, Java 21 toolchain
- **Framework:** Spring Boot 4.1 (native gRPC server/client support, Data JPA, Flyway starters)
- **Inter-service communication:** gRPC + Protocol Buffers (with Kotlin coroutine stubs via `grpckt`)
- **Client communication:** REST (gateway only)
- **Messaging:** Apache Kafka (external, self-hosted on VPS — not run locally)
- **Database:** PostgreSQL, one isolated logical database per service
- **Cache / locking:** Redis (OTP storage, idempotency keys, distributed locks)
- **Migrations:** Flyway
- **Artifact hosting:** Reposilite (self-hosted, for published releases)
- **Infra:** Docker, Docker Compose, Caddy
- **Testing:** JUnit 5, Testcontainers

## Local ports

Every service needs its own gRPC port. Keep this table updated whenever a new service is added — a port collision produces a confusing startup failure, not an obvious one.

| Service                | gRPC port                 | Postgres DB          | Notes                                                               |
|------------------------|---------------------------|----------------------|---------------------------------------------------------------------|
| `auth-service`         | `9090`                    | `axiom_auth`         |                                                                     |
| `subscription-service` | `9091`                    | `axiom_subscription` |                                                                     |
| `billing-service`      | `9092`                    | `axiom_billing`      | gRPC-clients to `subscription-service` at `static://localhost:9091` |
| `gateway-service`      | n/a (REST)                | none                 | will need its own HTTP port, e.g. `8080`                            |
| `payment-service`      | TBD                       | TBD                  |                                                                     |
| `notification-service` | n/a (Kafka consumer only) | none                 |                                                                     |

Postgres itself runs in Docker on host port `5433` (mapped from container `5432`) — see `docker/docker-compose.yml`.

Local dev needs two things IntelliJ doesn't pick up from Gradle automatically:
- **VM options** on every run configuration: `-Duser.timezone=UTC` (Spring Boot's Gradle-launched `bootRun` reads the `systemProperty` set in each service's `build.gradle.kts`, but IntelliJ's own run configs use a separate launcher and need this set explicitly per-configuration)
- Each service needs its own `.env` file in its module root (not `src/main/resources/`) with real `POSTGRES_USER`/`POSTGRES_PASS`/`KAFKA_*` values

## Key design decisions

**Money is never a float.** All monetary values are transmitted as integer cents (`int64`) over gRPC and handled via `BigDecimal` in application code. The `Money` type in `axiom-common` is the only way services should perform monetary arithmetic.

**Eventual consistency via Transactional Outbox.** When a service needs to change its own state and notify other services, both actions happen in a single database transaction — the state change and an outbox row. A separate poller reads the outbox and publishes to Kafka, so a Kafka outage never causes data to drift between services.

**Idempotency by design.** Every mutating request through the gateway requires an `Idempotency-Key` header, checked against Redis before being forwarded, so retried requests and double-clicks never cause duplicate charges.

**Payment providers are pluggable.** `payment-service` depends on a `PaymentProvider` interface, not directly on Stripe. Swapping providers (PayPal, a local bank API, etc.) means implementing that interface — no changes anywhere else in the system.

## Status

This project is under active development. Current progress:

- [x] `axiom-contracts` — proto definitions for `Plan`, `Subscriber`, `Subscription`, `Auth`, common types. Includes `grpckt` for Kotlin coroutine service stubs.
- [x] `axiom-common` — `Money`, exception hierarchy, idempotency validation, `Clock`, `AxiomBootstrap`
- [x] `auth-service` — register, login, OTP (Redis), JWT issuance + refresh
- [x] `subscription-service` — Create/Get/Cancel/Upgrade subscription RPCs, `Plan` CRUD, full Transactional Outbox pattern with real Kafka publishing
- [ ] `billing-service` — scaffolded, entities/proration logic in progress
- [ ] `gateway-service`
- [ ] `payment-service` — Stripe only, no multi-provider abstraction planned for v1
- [ ] `notification-service` — scope decision deferred until `payment-service` exists

## Getting started

> Full setup instructions will be added once `gateway-service` and Docker Compose are in place.

```bash
git clone https://github.com/RoToN4iK/axiom
cd axiom
./gradlew build
```

## License

TBD