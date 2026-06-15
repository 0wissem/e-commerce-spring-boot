# Claude — Session Briefing

## Who you are talking to

**Wissem** — Senior frontend developer (React), no backend experience, **1 month to lead a backend team**.
This project is his learning ground. Every conversation is a coaching session, not a ticket.

**Mode: talk first, code second.** Explain the WHY behind every pattern. Use frontend analogies when introducing new concepts. He already understands clean architecture, components, and API consumption from the frontend side — build on that.

---

## This Project at a Glance

An e-commerce Spring Boot app that has deliberately evolved through the full journey:

```
Monolith → Load-tested → Proved degradation → Extracted microservice → API Gateway → Outbox + Kafka → Weighted routing → Phase 4 cleanup
```

It now has four deployable services:
- **Monolith** (`spring-boot-0`) — owns **customers** only. Orders were extracted to order-service; products/categories are long gone. Its codebase and DB are now customers-only — no Kafka, no mail.
- **product-service** — owns **products** and **categories**, with its own PostgreSQL RDS
- **order-service** — owns **orders** and **order_items**, its own PostgreSQL DB; calls product-service (product snapshot) and the monolith (customer-name snapshot) over HTTP on create
- **gateway** — Spring Cloud Gateway routing `/api/orders/**` → order-service, `/api/products/**` & `/api/categories/**` → product-service, everything else → monolith

**Phase 4 — ✅ COMPLETE**
1. ✅ Remove product code (domain/application/infrastructure/api) from monolith
2. ✅ Remove product + product_categories tables from monolith DB (V20)
3. ✅ Decommission outbox sync — `OutboxPublisher`, `outbox_events` table (V21), Kafka consumer in product-service
4. ✅ Remove category code from monolith — already served by product-service via gateway
5. ✅ Drop categories table from monolith DB (V22)

**Phase 5 — order-service extraction ✅ COMPLETE (locally, 2026-06-15):** order-service built, gateway flipped `/api/orders/**` → order-service, orders data migrated, and orders removed from the monolith (code + tables via V2). The monolith's dead low-stock-alert Kafka feature was also removed. Pending only the AWS deploy (paused — free-tier credits exhausted).

**Long-term vision (Strangler Fig):** Every domain gets extracted progressively. When the last domain is out, the monolith EB becomes the gateway EB and the monolith is decommissioned. Next extraction: **customer-service** (the monolith's last domain).

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
The domain defines `IOrderRepository` (the port). The infrastructure implements `OrderRepositoryAdapter` wrapping `OrderJpaRepository` (the adapter). The service never touches JPA directly.

### 3. DTO / Mapper Pattern
`OrderRequest` (input), `OrderResponse` (output), `OrderMapper` translates between HTTP and domain. The domain entity never leaks to the HTTP layer.

### 4. Soft Delete
`@SQLRestriction("deleted_at IS NULL")` on `Product` in product-service — Hibernate silently adds this condition to every query. No hard deletes. Products are set `deletedAt = now()`.

### 5. Flyway Database Migrations
Versioned SQL files in `src/main/resources/db/migration/`. Applied in order at startup. Never modify an applied migration — always add a new version.
Monolith is at V22. Product-service is at V7.

### 6. Spring Profiles
`dev` = H2 in-memory (no setup), `prod` = AWS RDS PostgreSQL. Configured in `application-dev.properties` / `application-prod.properties`.

### 7. Full-Text Search (PostgreSQL tsvector + GIN index)
Lives in product-service (V5 migration). The `products` table has a `tsvector` column and GIN index. The query uses `@Query` with native SQL `@@` operator. This is the endpoint that broke under load in the monolith (proved by k6) — the reason for extracting it.

### 8. Global Exception Handling
`GlobalExceptionHandler` with `@RestControllerAdvice` — all exceptions caught in one place, translated to a consistent `ApiResponse` shape. Frontend-equivalent: a global error interceptor.

### 9. Outbox Pattern (decommissioned — learned pattern)
Was used to sync products from monolith to product-service: write to `outbox_events` in the same DB transaction, a `@Scheduled` publisher polls and sends to Kafka. Guaranteed at-least-once delivery. Decommissioned in Phase 4 once the monolith stopped owning products.

### 10. Kafka Event-Driven Sync (decommissioned — learned pattern)
`product.events` topic. Monolith published, product-service consumed via `@KafkaListener`. Kept both DBs in sync during the weighted traffic split phase. Decommissioned alongside the Outbox in Phase 4.

### 11. API Gateway + Weighted Routing
`WeightedRoutingFilter` in `gateway/` — a Spring Cloud `GlobalFilter` that rolls a random number against `PRODUCT_SERVICE_WEIGHT` env var. Routes `/api/products/**` and `/api/categories/**` to product-service, everything else to monolith. Weight is now 100 (product-service handles all product/category traffic). Built from scratch because Spring Cloud's built-in Weight predicate is broken in 2024.0.1.

### 12. JSONB Snapshot Pattern
`OrderProductSnapshot` — when an order is created, the product name/price/categories are snapshotted into the order item as TEXT (serialized JSON). If the product is later updated or deleted, the order still knows what was ordered. Cross-service data ownership solved without a shared DB.

### 13. CI/CD with GitHub Actions
Four pipelines: monolith, product-service, gateway, frontend. Monolith pipeline is self-healing (recreates EB env if missing). Frontend: Angular build → S3 → CloudFront invalidation.

### 14. HTTP Client Pattern (ProductServiceClient)
`order/infrastructure/ProductServiceClient` — when creating an order, the monolith calls product-service over HTTP using Spring's `RestClient` to fetch product data and build the snapshot. This replaced the direct DB access after product was extracted.

---

## Key Lessons Already Learned (don't re-explain)

- Why dual-write naively fails (Wissem chose Outbox Pattern)
- Why Shared DB was rejected (not truly independent microservices)
- Why Spring Cloud Weight predicate was replaced (runtime 500 bug in 2024.0.1)
- Why `@GeneratedValue(strategy = UUID)` was removed in product-service (Hibernate 7 silently overwrote monolith IDs on save)
- Why `OutboxPublisher` uses `.get()` to block — async send would mark events SENT before delivery
- Why `@EnableKafka` must be explicit in Spring Boot 4.x (no auto-config)
- Why Outbox + Kafka were decommissioned once the monolith stopped owning products — the sync existed only to serve the weighted traffic split phase

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
- **order-service extraction is done locally** (built + cut over + monolith cleaned). Next up: the AWS deploy when credits return, then the **customer-service** extraction.

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
| Messaging | Kafka (Confluent Cloud) — decommissioned; the monolith's low-stock-alert consumer was removed (monolith no longer depends on Kafka or mail) |
| Gateway | Spring Cloud Gateway |
| CI/CD | GitHub Actions |
| Hosting | AWS Elastic Beanstalk + ECR |
| Frontend | Angular 19 + Tailwind on S3/CloudFront |
| Load Testing | k6 + InfluxDB + Grafana (EC2) |
