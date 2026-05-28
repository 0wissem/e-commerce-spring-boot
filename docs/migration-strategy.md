# Monolith → Microservices Migration Strategy

## Context

The project is a monolithic e-commerce Spring Boot application being gradually migrated to microservices. The first extracted service is `product-service`, deployed on AWS Elastic Beanstalk with its own PostgreSQL RDS database.

The migration must be **progressive**:
- Start with 1% of traffic routed to the microservice
- Gradually increase to 100%
- At no point should data be lost or inconsistent between the two systems

---

## The Core Problem — Dual-Write

During the migration window, two databases exist simultaneously:

```
Monolith DB (PostgreSQL)         Product-service DB (PostgreSQL)
- products                       - products
- categories                     - categories
- orders                         - (orders stay in monolith)
- customers                      - (customers stay in monolith)
```

Any write to one database must be reflected in the other.
This is called the **Dual-Write Problem** — one of the hardest problems in distributed systems.

### Why the naive approach fails

```java
// looks simple, breaks in production
productRepository.save(product);             // writes to monolith DB
productServiceClient.createProduct(product); // writes to product-service DB
```

- If monolith writes but product-service is down → databases diverge forever
- No ordering guarantee between two independent writes
- If both services publish changes to each other → infinite sync loop

---

## The Missing Piece — API Gateway

Before routing even 1% of traffic, an API Gateway is required.
Without it, there is no mechanism to split traffic between the two services.

```
                    ┌──────────────────────────────────┐
                    │           ALL CLIENTS            │
                    └─────────────┬────────────────────┘
                                  │
                                  ▼
                       ┌──────────────────┐
                       │   API Gateway    │  ← decides who goes where
                       └───────┬──────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
                  99%                    1%
                    │                     │
                    ▼                     ▼
             ┌──────────┐       ┌──────────────────┐
             │ Monolith │       │  product-service │
             └──────────┘       └──────────────────┘
```

This does not exist yet in the project. It must be built first.

---

## Option A — Shared Database

```
┌──────────────────────────────────────────────────────────────────────┐
│                         DURING MIGRATION                             │
└──────────────────────────────────────────────────────────────────────┘

                         100 clients
                              │
                              ▼
                   ┌──────────────────┐
                   │   API Gateway    │
                   └────────┬─────────┘
                            │
                 ┌──────────┴──────────┐
                 │                     │
               99%                    1%
                 │                     │
                 ▼                     ▼
          ┌──────────┐       ┌──────────────────┐
          │ Monolith │       │  product-service │
          └────┬─────┘       └────────┬─────────┘
               │                      │
               └──────────┬───────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │  Monolith DB    │  ← single source of truth
                  │  (shared RDS)   │     no sync problem
                  └─────────────────┘


┌──────────────────────────────────────────────────────────────────────┐
│                         AFTER (100%)                                 │
└──────────────────────────────────────────────────────────────────────┘

                         100 clients
                              │
                              ▼
                   ┌──────────────────┐
                   │   API Gateway    │
                   └────────┬─────────┘
                            │
                          100%
                            │
                            ▼
                  ┌──────────────────┐
                  │  product-service │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │ Product-svc DB  │  ← now separated, migration done
                  └─────────────────┘
```

**Pros:** No sync problem. Works immediately. Fast to implement.

**Cons:** Both services share the same DB — not truly independent. Schema changes affect both. Temporary bridge only.

---

## Option B — Outbox Pattern + Kafka

```
┌──────────────────────────────────────────────────────────────────────┐
│                         DURING MIGRATION                             │
└──────────────────────────────────────────────────────────────────────┘

                         100 clients
                              │
                              ▼
                   ┌──────────────────┐
                   │   API Gateway    │
                   └────────┬─────────┘
                            │
                 ┌──────────┴──────────┐
                 │                     │
               99%                    1%
                 │                     │
                 ▼                     ▼
          ┌──────────┐       ┌──────────────────┐
          │ Monolith │       │  product-service │
          └────┬─────┘       └────────┬─────────┘
               │                      │
     writes to DB            writes to DB
     + outbox table          + outbox table
               │                      │
               ▼                      ▼
          ┌──────────┐       ┌──────────────────┐
          │Monolith  │       │ Product-svc DB   │
          │   DB     │       └──────────────────┘
          └──────────┘
               │                      │
     outbox publisher        outbox publisher
               │                      │
               └──────────┬───────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │      Kafka      │
                  │ product.events  │
                  └────────┬────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
    Monolith consumes           product-service consumes
    (ignores own events)        (ignores own events)
              │                         │
              ▼                         ▼
      Monolith DB                Product-svc DB
      stays in sync  ◄─────────► stays in sync


┌──────────────────────────────────────────────────────────────────────┐
│                    ZOOM: THE OUTBOX PATTERN                          │
└──────────────────────────────────────────────────────────────────────┘

  ┌────────────────────────────────────────────────────────────────┐
  │                  ONE DATABASE TRANSACTION                      │
  │                                                                │
  │  1. INSERT INTO products (...)        → products table         │
  │  2. INSERT INTO outbox_events (...)   → outbox_events table    │
  │                                                                │
  │  COMMIT ──► both writes happen                                 │
  │  ROLLBACK ──► neither write happens                            │
  └────────────────────────────────────────────────────────────────┘
                            │
                            │ async polling (every few seconds)
                            ▼
                  ┌──────────────────┐
                  │ Outbox Publisher │  reads pending events
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │      Kafka       │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │  Other service   │  applies change to its own DB
                  └──────────────────┘
```

**Pros:** Correct architecture. Databases fully independent. Guaranteed delivery via Kafka.

**Cons:** More work to implement. Eventual consistency (small sync delay between the two DBs).

**What already exists in this project:**
- Kafka (Confluent Cloud) is configured with topic `stock.updated`
- `IStockEventPublisher` interface exists
- `NoOpStockEventPublisher` is a placeholder — needs real implementation

---

## Option C — Change Data Capture (Debezium)

```
┌──────────────────────────────────────────────────────────────────────┐
│                         DURING MIGRATION                             │
└──────────────────────────────────────────────────────────────────────┘

                         100 clients
                              │
                              ▼
                   ┌──────────────────┐
                   │   API Gateway    │
                   └────────┬─────────┘
                            │
                 ┌──────────┴──────────┐
                 │                     │
               99%                    1%
                 │                     │
                 ▼                     ▼
          ┌──────────┐       ┌──────────────────┐
          │ Monolith │       │  product-service │
          └────┬─────┘       └────────┬─────────┘
               │                      │
               ▼                      ▼
          ┌──────────┐       ┌──────────────────┐
          │Monolith  │       │ Product-svc DB   │
          │   DB     │       └──────────────────┘
          └────┬─────┘
               │
    PostgreSQL writes to WAL
    (Write-Ahead Log — internal journal)
               │
               ▼
          ┌──────────┐
          │ Debezium │  reads WAL — captures every change
          │Connector │  no application code change needed
          └────┬─────┘
               │
               ▼
          ┌──────────┐
          │   Kafka  │
          └────┬─────┘
               │
               ▼
     ┌──────────────────┐
     │  product-service │  consumes changes, applies to its DB
     └──────────────────┘


┌──────────────────────────────────────────────────────────────────────┐
│                    ZOOM: WHAT DEBEZIUM CAPTURES                      │
└──────────────────────────────────────────────────────────────────────┘

  Monolith DB WAL contains:
  ┌─────────────────────────────────────────────────────────────┐
  │  INSERT products → Debezium captures → Kafka event          │
  │  UPDATE products → Debezium captures → Kafka event          │
  │  DELETE products → Debezium captures → Kafka event          │
  │  (even manual SQL scripts are captured)                     │
  └─────────────────────────────────────────────────────────────┘

  Application code does NOT need to be modified in the monolith.
  Debezium works at the database level, not the application level.
```

**Pros:** Captures everything including manual SQL scripts. Zero application code change on monolith side.

**Cons:** Requires Kafka Connect infrastructure. PostgreSQL must have `wal_level=logical` enabled. Most complex to operate.

---

## Current State of the Project

```
┌──────────────────────────────┬──────────────┬───────────────────────────────────────────┐
│ Component                    │ Status       │ Gap                                       │
├──────────────────────────────┼──────────────┼───────────────────────────────────────────┤
│ product-service              │ ✅ Deployed  │ —                                         │
│ Dedicated RDS                │ ✅ Running   │ —                                         │
│ Kafka (Confluent)            │ ✅ Configured│ —                                         │
│ API Gateway                  │ ✅ Deployed  │ Clients not yet pointed to gateway        │
│ Outbox Pattern (monolith)    │ ✅ Done      │ —                                         │
│ Kafka consumer (product-svc) │ ✅ Done      │ —                                         │
│ Debezium / CDC               │ ❌ Rejected  │ Not needed — Option B chosen              │
└──────────────────────────────┴──────────────┴───────────────────────────────────────────┘
```

---

## Recommended Path — Option B (chosen)

```
PHASE 1 ✅           PHASE 2 ✅            PHASE 3 ← HERE       PHASE 4
  │                     │                    │                   │
  ▼                     ▼                    ▼                   ▼

Build Gateway       Implement Kafka     Route traffic       Clean up
+ deploy on    ──►  consumer in    ──►  1% → 100%      ──► monolith
  own EB env        product-service     via gateway         product code
+ Outbox in         + validate sync
  monolith
```

### Phase 1 — Gateway + Outbox ✅ Done
1. ~~Build and deploy Spring Cloud Gateway~~ → deployed at `Gateway-env`
2. ~~Implement Outbox Pattern in the monolith~~ → done (V14 migration + `OutboxPublisher`)
3. ~~Validate gateway health and routing~~ → `/api/products/**` and `/api/categories/**` route to `product-service`

### Phase 2 — Kafka consumer in product-service ✅ Done
1. ~~Implement Kafka consumer in `product-service` to receive `product.events` topic~~
2. ~~Apply incoming events to the product-service DB~~
3. ~~Validate: write via monolith → data appears in product-service DB~~

**Implementation notes:**
- Spring Boot 4.x does not auto-configure `@EnableKafka` — must be added explicitly via a `@Configuration` class
- `KafkaAdmin` (auto-configured) blocks startup by trying to connect to the broker; fixed with an explicit `KafkaConsumerConfig` that owns `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory` without creating `KafkaAdmin`, and excluded `KafkaAutoConfiguration`
- `@GeneratedValue(strategy = GenerationType.UUID)` on the `Product` entity was silently overwriting the monolith's product ID on every `save()` (Hibernate 7 behaviour); fixed by removing `@GeneratedValue` and generating UUIDs in the application layer (`ProductMapper.toDomain()`)
- `OutboxPublisher` must use `.get()` on the Kafka send future to block until broker acknowledgment — without it, events are marked SENT before delivery is confirmed and are never retried on failure

### Phase 3 — Progressive traffic increase ← current
1. Point clients (frontend, external) to the gateway URL instead of the monolith directly
2. Start at 1% weighted routing, monitor, increase: 1% → 10% → 50% → 100%
3. At 100%, product-service becomes the authoritative source

### Phase 4 — Clean up
1. Remove product code from monolith
2. Remove product tables from monolith DB
3. Decommission outbox sync code

---

## Key Principle

> The monolith is the **source of truth** during the entire migration.
> The product-service DB is a **replica** that progressively takes over.
> Only at 100% does the product-service become the authoritative source.

---

## Decisions

### Sync strategy → Option B (Outbox Pattern + Kafka)

Option A (Shared DB) was rejected. The goal is a real microservices architecture with independent databases from the start, not a temporary shortcut.

Option C (Debezium) was rejected. It requires extra infrastructure (Kafka Connect) and is useful only when the monolith source code cannot be modified. Here we own the monolith code.

**Chosen: Option B — Outbox Pattern + Kafka.**
Both services write to their own DB. Events flow through Kafka to keep both DBs in sync.

---

### Gateway technology → Spring Cloud Gateway

AWS API Gateway was considered but rejected for this project — less educational value, and we want to stay in the Java/Spring ecosystem.

nginx was rejected — too limited for the routing logic we need.

Lambda + AWS API Gateway was considered but adds complexity without adding learning value here.

**Chosen: Spring Cloud Gateway** — a Spring Boot service, consistent with the rest of the stack.

---

### Gateway deployment → Dedicated Elastic Beanstalk environment

Deploying the Gateway on the same EB as the monolith was rejected — port conflicts and mixed responsibilities.

**Chosen: Gateway gets its own EB environment.**

```
EB: spring-boot-0        → Monolith        (port 8080) ✅
EB: Product-service-env  → product-service (port 8081) ✅
EB: Gateway-env          → Spring Cloud Gateway        ✅
```

---

### Long-term vision — Strangler Fig until monolith is empty

The migration does not stop at product-service. Every domain will be extracted progressively into its own microservice. When the last domain is extracted, the monolith contains zero business logic.

```
TODAY                    IN PROGRESS              END STATE

Monolith (everything)    Monolith                 Monolith EB
                         (orders + customers)     hosts only Gateway
product-service ──────►                      ──►
                         product-service          product-service
                         order-service            order-service
                         customer-service         customer-service
```

At end state, the monolith EB becomes the Gateway EB. The monolith application is decommissioned.

---

### Implementation order

1. ~~**Spring Cloud Gateway**~~ — ✅ deployed at `Gateway-env`.
2. ~~**Outbox Pattern (monolith)**~~ — ✅ implemented, events published to Kafka `product.events` topic.
3. ~~**Kafka consumer (product-service)**~~ — ✅ done. Events consumed and applied to product-service DB.
4. **Route traffic** — ← next. Start at 1%, increase progressively once sync is validated.