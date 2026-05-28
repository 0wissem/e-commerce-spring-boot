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
┌─────────────────────┬──────────────┬──────────────────────────────┐
│ Component           │ Status       │ Gap                          │
├─────────────────────┼──────────────┼──────────────────────────────┤
│ product-service     │ ✅ Deployed  │ Empty DB, no sync            │
│ Dedicated RDS       │ ✅ Running   │ No replication               │
│ Kafka (Confluent)   │ ✅ Configured│ NoOpPublisher — does nothing │
│ API Gateway         │ ❌ Missing   │ Cannot route traffic         │
│ Outbox Pattern      │ ❌ Partial   │ Interface only, no impl      │
│ Debezium / CDC      │ ❌ Missing   │ Not started                  │
└─────────────────────┴──────────────┴──────────────────────────────┘
```

---

## Recommended Path — Option A then Option B

```
TODAY                PHASE 1              PHASE 2              DONE
  │                     │                    │                   │
  ▼                     ▼                    ▼                   ▼

product-service    Build Gateway       Implement Outbox    Route 100%
deployed but  ──►  + point both   ──►  + separate DBs  ──► to product-
no traffic         services to         + real Kafka         service
                   shared DB           publisher
                   + route 1%
```

### Phase 1 — Shared DB + Gateway
1. Point product-service to the monolith's RDS
2. Build and deploy Spring Cloud Gateway
3. Route 1% of product traffic to product-service
4. Validate: same data, same behavior

### Phase 2 — Real Sync + DB Separation
1. Implement Outbox Pattern in the monolith
2. Implement Kafka consumer in product-service
3. Switch product-service back to its own RDS
4. Validate: write via monolith, read via product-service → data matches

### Phase 3 — Progressive increase
1. 1% → 10% → 50% → 100%
2. Monitor at each step

### Phase 4 — Clean up
1. Remove product code from monolith
2. Remove product tables from monolith DB
3. Decommission sync code

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
EB: spring-boot-0        → Monolith       (port 8080)
EB: Product-service-env  → product-service (port 8081)
EB: gateway-env          → Spring Cloud Gateway  ← to be created
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

1. **Spring Cloud Gateway** — build and deploy first. No traffic routing until sync is in place.
2. **Outbox Pattern + Kafka** — implement sync between monolith DB and product-service DB.
3. **Route traffic** — start at 1%, increase progressively once sync is validated.