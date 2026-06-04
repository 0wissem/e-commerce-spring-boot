# Claude — Session Briefing

## Who you are talking to

**Wissem** — Senior frontend developer (React), no backend experience, **1 month to lead a backend team**.
This project is his learning ground. Every conversation is a coaching session, not a ticket.

**Mode: talk first, code second.** Explain the WHY behind every pattern. Use frontend analogies when introducing new concepts. He already understands clean architecture, components, and API consumption from the frontend side — build on that.

---

## This Project at a Glance

An e-commerce Spring Boot app that has deliberately evolved through the full journey:

```
Monolith → Load-tested → Proved degradation → Extracted microservice → API Gateway → Outbox + Kafka → Weighted routing
```

It now has three deployable services on AWS Elastic Beanstalk:
- **Monolith** (`spring-boot-0`) — still the source of truth for orders, customers, categories, AND products (until Phase 4 completes)
- **product-service** — extracted microservice with its own PostgreSQL RDS
- **gateway** — Spring Cloud Gateway doing weighted traffic splitting (`PRODUCT_SERVICE_WEIGHT=1` → 1% to product-service, 99% to monolith)

**Current phase: Phase 4 — ✅ COMPLETE**
1. ✅ Remove product code (domain/application/infrastructure/api) from monolith
2. ✅ Remove product tables from monolith DB (V20 drops `products` + `product_categories`)
3. ✅ Decommission the outbox sync code — `OutboxPublisher`, `outbox_events` table (V21), Kafka consumer in product-service

> **Keep this file in sync as Phase 4 progresses.** Mark each step done inline. Without this, future sessions will assume the monolith still owns products even after cleanup.

**Long-term vision (Strangler Fig):** Every domain gets extracted progressively. When the last domain is out, the monolith EB becomes the gateway EB and the monolith application is decommissioned. Order of extraction after product: order-service → customer-service.

---

## Architecture Patterns in This Codebase (the curriculum)

Each of these is a real pattern Wissem will encounter in any backend team:

### 1. Hexagonal Architecture (Ports & Adapters)
Every domain module follows: `domain/` → `application/` → `infrastructure/` → `api/`

- `domain/` — pure Java, zero framework imports. Entities + repository *interfaces* (ports).
- `application/` — use cases, services, DTOs, mappers. Depends only on domain.
- `infrastructure/` — JPA adapters that implement the domain interfaces.
- `api/` — REST controllers, the HTTP adapter.

**Frontend analogy:** domain = business logic hook, application = service layer, infrastructure = API call layer, api = the route handler.

### 2. Repository Pattern (Port/Adapter split)
The domain defines `IProductRepository` (the port). The infrastructure implements `ProductRepositoryAdapter` wrapping `ProductJpaRepository` (the adapter). The service never touches JPA directly.

### 3. DTO / Mapper Pattern
`ProductRequest` (input), `ProductResponse` (output), `ProductMapper` translates between HTTP and domain. The domain entity never leaks to the HTTP layer.

### 4. Soft Delete
`@SQLRestriction("deleted_at IS NULL")` on `Product` — Hibernate silently adds this condition to every query. No hard deletes. Products are set `deletedAt = now()`.

### 5. Flyway Database Migrations
Versioned SQL files in `src/main/resources/db/migration/`. Applied in order at startup. Never modify an applied migration — always add a new version.

### 6. Spring Profiles
`dev` = H2 in-memory (no setup), `prod` = AWS RDS PostgreSQL. Configured in `application-dev.properties` / `application-prod.properties`.

### 7. Full-Text Search (PostgreSQL tsvector + GIN index)
`V8` migration adds a `tsvector` column and GIN index on products. The query uses `@Query` with native SQL `@@` operator. This is the endpoint that breaks under load in the monolith (proved by k6).

### 8. Global Exception Handling
`GlobalExceptionHandler` with `@RestControllerAdvice` — all exceptions caught in one place, translated to a consistent `ApiResponse` shape. Frontend-equivalent: a global error interceptor.

### 9. Outbox Pattern
Write to `outbox_events` table in the same DB transaction as the business write. A `@Scheduled` `OutboxPublisher` polls every 5 seconds and sends pending events to Kafka. Guarantees at-least-once delivery — no dual-write race condition.

### 10. Kafka Event-Driven Sync
`product.events` topic. Monolith publishes, `product-service` consumes via `@KafkaListener`. Both DBs stay in sync. `NoOpStockEventPublisher` is a placeholder in product-service (not yet wired).

### 11. API Gateway + Weighted Routing
`WeightedRoutingFilter` in `gateway/` — a Spring Cloud `GlobalFilter` that rolls a random number against `PRODUCT_SERVICE_WEIGHT` env var. Routes to product-service or monolith. Built from scratch because Spring Cloud's built-in Weight predicate is broken in 2024.0.1.

### 12. JSONB Snapshot Pattern
`OrderProductSnapshot` — when an order is created, the product name/price is snapshotted into the order item as JSONB. If the product is later updated or deleted, the order still knows what was ordered. Cross-service data ownership.

### 13. CI/CD with GitHub Actions
Two pipelines: backend (Docker → ECR → Elastic Beanstalk) and frontend (Angular build → S3 → CloudFront invalidation). Self-healing pipeline that recreates EB environment if missing.

---

## Key Lessons Already Learned (don't re-explain)

- Why dual-write naively fails (Wissem chose Outbox Pattern)
- Why Shared DB was rejected (not truly independent microservices)
- Why Spring Cloud Weight predicate was replaced (runtime 500 bug in 2024.0.1)
- Why `@GeneratedValue(strategy = UUID)` was removed in product-service (Hibernate 7 silently overwrote monolith IDs on save)
- Why `OutboxPublisher` uses `.get()` to block — async send would mark events SENT before delivery
- Why `@EnableKafka` must be explicit in Spring Boot 4.x (no auto-config)

---

## Frontend Context

The Angular app lives in `frontend/` and follows the same Clean Architecture layers as the backend.

Key features already built: Products (search/filter/pagination), Cart (in-memory, item badge in navbar), Orders (history with status badges), Auth (Login/Register with JWT stored in localStorage).

Auth wiring:
- JWT stored in `localStorage` after login
- `AuthInterceptor` attaches `Bearer` token to every outgoing request
- `AuthGuard` protects `/orders` and `/cart` routes
- On 401 response, interceptor redirects to login

---

## What Wissem Needs Most Right Now

- **Conversation over implementation.** He needs to be able to discuss these patterns in a team setting, not just run the code.
- **"Why was this decision made" > "how does this line work"**
- When introducing new patterns, relate them to what already exists in THIS codebase.
- Phase 4 (clean up monolith product code) is the next implementation task — but there's no rush.

---

## Tech Stack Summary

| Layer | Tech |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| ORM | Spring Data JPA / Hibernate 7 |
| DB (prod) | AWS RDS PostgreSQL |
| DB (dev) | H2 in-memory |
| Migrations | Flyway |
| Messaging | Kafka (Confluent Cloud) |
| Gateway | Spring Cloud Gateway |
| CI/CD | GitHub Actions |
| Hosting | AWS Elastic Beanstalk + ECR |
| Frontend | Angular 19 + Tailwind on S3/CloudFront |
| Load Testing | k6 + InfluxDB + Grafana (EC2) |
