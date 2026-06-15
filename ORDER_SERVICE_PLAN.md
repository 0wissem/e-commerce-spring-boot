# Order Service — Extraction Strategy
## Big-Bang Cutover: monolith → order-service

> This document is **the strategy** for extracting the `orders` domain out of the monolith into a
> standalone `order-service`. It explains the *why* and the *how* — the decisions, the sequence,
> the risks — not the code. Each section is a phase. Read the "Why" as carefully as the "What":
> in a migration, understanding the reasoning is what keeps you safe under pressure.

---

## ⚠️ Strategy change: why we're dropping the Strangler Fig

For **product-service**, we used the **Strangler Fig** pattern: both services ran in parallel,
traffic shifted gradually (weighted routing), and the two databases were kept in sync with an
Outbox + Kafka. Zero downtime — but a *mountain* of complexity.

For **order-service**, the context has changed: we now have a **preprod environment**. That lets
us **rehearse** the entire migration in preprod, validate it, then **replay it in prod during a
short maintenance window**. If it breaks, we **roll back**.

| Criterion | Strangler Fig (product-service) | Big-Bang Cutover (order-service) |
|---|---|---|
| Downtime | None | A few minutes (planned) |
| Data sync | Outbox + Kafka (continuous) | **One** dump → load, one-shot |
| Routing | Weighted, gradual | **One** route flip |
| Machinery to build | Outbox, Kafka, WeightedFilter | None of it |
| Complexity | 🔴 Very high | 🟢 Low |
| Rollback | Lower the routing weight | Re-flip the route (before reopening) |
| Prerequisite | — | A preprod env to rehearse in |

**The key insight:** the hard part of the Strangler Fig was never the code — it was keeping **two
live databases in sync** while traffic was split between them. The moment we accept a few minutes
of downtime, all of that machinery disappears. We move the data **once**, while **nobody is
writing to it**.

---

## The promotion path: local → preprod → prod

We never touch prod directly. Every change climbs the same ladder, and prod is just a **rehearsal
we've already performed** in preprod.

```
┌─────────────┐      ┌──────────────────┐      ┌──────────────────────┐
│   LOCAL     │      │     PREPROD      │      │        PROD          │
│  (H2 / dev) │ ───► │   (dev branch)   │ ───► │   (master branch)    │
│             │      │   eu-west-3      │      │   maintenance window │
├─────────────┤      ├──────────────────┤      ├──────────────────────┤
│ • build     │      │ • FULL DRESS     │      │ • REPLAY the exact   │
│ • migrations│      │   REHEARSAL of   │      │   same sequence      │
│ • test the  │      │   the whole      │      │ • validate BEFORE    │
│   service   │      │   cutover        │      │   reopening          │
│             │      │ • validate data  │      │ • roll back if KO    │
│             │      │   migration +    │      │                      │
│             │      │   flip + rollback│      │                      │
└─────────────┘      └──────────────────┘      └──────────────────────┘
                          │                          │
                     if KO → fix locally        if KO → roll back
                     and replay                 (re-flip the route)
```

**Golden rule:** anything we do in prod (Phase 9) must have been done **identically** in preprod
(Phase 8) at least once, with no surprises. Preprod isn't a "test" — it's the **actual rehearsal**
of the real day.

---

## The end state we're aiming for

The monolith still owns two domains: **orders** and **customers**. We extract **orders** into a
standalone `order-service`. The end state is identical to what the Strangler Fig would have given
us — only the **path** to get there changes.

```
BEFORE                             AFTER
─────────────────────────────      ──────────────────────────────────────────
Gateway                            Gateway
  │                                  │── /api/orders/**     → order-service
  │── /api/products/**  → PS         │── /api/products/**   → product-service
  │── /api/categories/** → PS        │── /api/categories/** → product-service
  └── /api/**           → Monolith   └── /api/**            → Monolith (customers only)

Monolith DB                        Monolith DB        order-service DB
  - orders               ──────►      - customers        - orders
  - order_items                                          - order_items
  - customers
```

**What the monolith keeps for now:** customers (until Phase 6 of the larger plan: customer-service).

---

## The real design problem: the customer foreign key

In the monolith, an `Order` has a JPA relationship to `Customer` — a real foreign key in the
database. In order-service there is **no** `customers` table, so there **cannot** be a foreign key.

**Solution: the customer snapshot pattern** — the same idea we already use for the product snapshot
on order items.

```
When an order is created:
  1. order-service calls the monolith over HTTP:  GET /api/customers/{id}
  2. it reads back the customer's name
  3. it stores that name directly on the order row (customer_name column)

When orders are later read:
  → order-service calls nobody. The name is already on the row.
    The snapshot was taken at creation time.
```

**Why this is correct, not a hack:** an order is a historical record. The customer's name *at the
time of purchase* is part of that record. Denormalizing it onto the order isn't duplication — it's
capturing a fact that must never change even if the customer is later renamed or deleted. This is
exactly the reasoning behind the product snapshot already used on order items.

---

## What the service looks like (hexagonal architecture)

order-service follows the same Ports & Adapters layering as every other module in this codebase.
No new patterns to learn — the goal is to *re-anchor* the ones you already know in a fresh context.

```
order-service
├── domain          ← pure business model + repository interfaces (ports). No framework.
├── application     ← use cases, DTOs, mappers. Depends only on domain.
├── infrastructure  ← JPA adapters + HTTP clients. Implements the ports.
└── api             ← REST controllers. The HTTP adapter.
```

| Layer | Responsibility | Frontend analogy |
|---|---|---|
| `domain` | Order / OrderItem model, the repository *interface* (port), statuses | TypeScript types + business logic in a hook |
| `application` | Create-order use case, request/response DTOs, mapper | the service layer |
| `infrastructure` | JPA adapter, the customer & product HTTP clients, JSON converter | the `*.service.ts` files doing real fetches |
| `api` | The `/api/orders/**` REST endpoints | the route handlers |

**The contract does not change.** Same endpoints, same paths, same HTTP behaviour as the monolith
had. That's the whole point of the extraction: the gateway flips where the request goes, and the
frontend sees nothing.

---

## The data model (no foreign keys to other services)

**`orders`**

| Column | Meaning | Note |
|---|---|---|
| `id` | primary key | |
| `customer_id` | which customer | cross-service reference — **no FK** |
| `customer_name` | snapshot of the name | denormalized at creation time |
| `status` | PENDING / CONFIRMED / SHIPPED / DELIVERED / CANCELLED | |
| `total_price` | order total | |

**`order_items`**

| Column | Meaning | Note |
|---|---|---|
| `id` | primary key | |
| `order_id` | parent order | **FK → orders** (same DB, so a real FK is fine) |
| `product_id` | which product | cross-service reference — **no FK** |
| `product_name` | snapshot of the name | |
| `quantity` | how many | |
| `unit_price` | price at purchase | |
| `product_snapshot` | name + price + categories at purchase time | stored as JSON text |

**The rule to internalize:** a service can have foreign keys *inside its own database*
(`order_items → orders`) but **never** to a table owned by another service (`customer`, `product`).
Those become bare ID references plus a snapshot.

---

## Cross-service calls at runtime

order-service is not self-sufficient at **write** time — to build an order it needs data that lives
elsewhere. At **read** time it needs nobody, because everything was snapshotted at creation.

```
Client (Angular)
    │
    ▼
Gateway
    │
    ├── GET/POST /api/orders/**      → order-service
    │                                      │  (only on CREATE)
    │                                      ├── → GET /api/products/{id}   → product-service
    │                                      │       (price, name, categories → product snapshot)
    │                                      └── → GET /api/customers/{id}   → monolith
    │                                              (name → customer snapshot)
    │
    ├── GET /api/products/**         → product-service
    ├── GET /api/categories/**       → product-service
    └──     /api/customers/**        → monolith
```

This is exactly what "loose coupling" looks like in practice: each service owns its data, and
reaches across a network boundary — never a shared database — when it needs someone else's.

**No Kafka here.** order-service consumes no events. The product and customer data it needs is
fetched on demand over HTTP and frozen into snapshots. That's a deliberate simplification compared
to the (now decommissioned) Outbox/Kafka sync.

---

## Infrastructure to stand up (same playbook as product-service)

None of this is new — you did every one of these steps for product-service. It's a checklist, not a
lesson.

| Piece | Purpose |
|---|---|
| ECR repository | holds the order-service Docker image |
| RDS PostgreSQL `order-service-db` | the service's own database (private, VPC-only) |
| Elastic Beanstalk app + env | runs the container; created by the self-healing CI step on first deploy |
| CI/CD pipeline | build image → push to ECR → deploy to EB (mirror of the product-service pipeline) |
| GitHub secrets | the new DB URL/credentials + the monolith URL (as the "customer service" URL) |
| Flyway | owns the schema; **starts again at V1** — each service has its own independent migration history |

**One thing worth stating out loud:** Flyway history is per-database. order-service's migrations
begin at V1 even though the monolith is at V22. The two histories never meet.

---

## Phase 8 — Dress rehearsal in preprod

This is where the new strategy earns its keep. **Before touching prod, we play the entire cutover
in preprod.** If anything breaks, we find it here, with zero customer impact.

**The problem to solve:** order-service starts with an empty database. We must move the existing
`orders` + `order_items` from the monolith's database into order-service's database.

**The catch:** the monolith's `orders` table has **no** `customer_name` column (that name lived
behind the FK to `customers`). So during the export we join with `customers` to reconstitute the
snapshot.

**The mental model of the cutover — three moments:**

```
   WRITES FROZEN                              WRITES FROZEN            REOPEN
   ┌──────────────┐                          ┌──────────────┐         ┌──────────┐
   │ orders = old │                          │ orders = old │         │ orders = │
   │ source of    │   dump ──► load ──►      │ still intact │  flip   │ NEW =    │
   │ truth        │   (one-shot copy)        │ (rollback OK)│  route  │ source   │
   └──────────────┘                          └──────────────┘         └──────────┘
        T0                                         T1                       T2

   Until we reopen (T2), the monolith holds the truth → rollback is free.
   After T2, order-service takes the writes → rollback means data divergence.
```

**What the rehearsal is for:** time the dump/load, confirm the row counts match, test the route
flip, AND test the rollback. The real day in prod must contain **no discoveries**.

### Step 8.1 — Export from the monolith DB

From your machine (or a bastion that can reach the RDS), pull the data out into two files. The
tricky one is `orders`: the `customer_name` column doesn't exist, so it must be reconstituted via a
join with `customers`.

| Source table (monolith) | What we pull | Transformation |
|---|---|---|
| `orders` | id, customer_id, status, total_price | **+ `customer_name`** via join on `customers` |
| `order_items` | id, order_id, product_id, product_name, quantity, unit_price, product_snapshot | none (direct copy) |

### Step 8.2 — Import into the order-service DB

The target tables already exist (created by Flyway V1). Load the two exports **in order**: `orders`
first, then `order_items` (because of the `order_id → orders` foreign key).

| Order | Target table | Columns loaded |
|---|---|---|
| 1️⃣ | `orders` | id, customer_id, customer_name, status, total_price |
| 2️⃣ | `order_items` | id, order_id, product_id, product_name, quantity, unit_price, product_snapshot |

Then **count the rows** in each table on the order-service side.

**Mandatory check before going further:** both counts (`orders` and `order_items`) must be
**identical** on both sides. If a single number differs, we **stop** and investigate — we never
flip a route on top of a partial migration.

> ⚠️ **In preprod, this dump/load runs while writes are frozen** (see the three-moments model
> above). It's the same window as in prod — which is exactly why we time it here. In prod, **this
> copy IS the downtime**: it's what defines the length of the maintenance window.

---

## Phase 9 — Game day: the prod cutover

Everything was rehearsed in preprod (Phase 8). Now we replay **the exact same sequence** in prod,
this time inside an **announced maintenance window**.

### The timed runbook

| # | Step | Writes | Rollback possible? | Duration ~ |
|---|---|---|---|---|
| 1 | Announce the maintenance window | open | — | before |
| 2 | **Freeze writes** on `/api/orders/**` (maintenance at the gateway) | 🔒 frozen | n/a (nothing moved yet) | instant |
| 3 | Dump `orders` + `order_items` from the monolith DB (join customers) | 🔒 frozen | ✅ yes (reopen) | 1–3 min |
| 4 | Load into the order-service DB | 🔒 frozen | ✅ yes | 1–3 min |
| 5 | **Verify the counts match** on both sides | 🔒 frozen | ✅ yes | <1 min |
| 6 | **Flip** the gateway route `/api/orders/**` → order-service | 🔒 frozen | ✅ yes (re-flip) | instant |
| 7 | Smoke test order-service **through the gateway** (read + create) | 🔒 frozen | ✅ yes (re-flip) | 2–5 min |
| 8 | ✅ All green → **reopen writes** | 🔓 open | ❌ **point of no return** | instant |
| 9 | Monitor logs + errors for 5–15 min | 🔓 open | ❌ (see Phase 10) | 5–15 min |

```
  T0 ──────────────► freeze writes (step 2)
       │
       ├── dump / load / verify (3-5)   ← THE maintenance window
       │
       ├── flip route (6)
       │
       ├── smoke test (7)               ← last chance for a free rollback
       │
  T2 ──┴──► reopen (8)                  ← BEYOND THIS: order-service is the source of truth
```

**The critical moment is step 8.** Until we reopen, the monolith holds the truth intact and a
rollback = re-flip the route, zero loss. The instant we reopen, real orders land in order-service;
going back would mean re-migrating them by hand. **So all the confidence is earned in step 7,
before reopening.**

### The route flip (step 6), in plain terms

The "flip" means adding **one new route** to the gateway: `/api/orders/**` must point to
order-service instead of falling through to the `/api/**` catch-all that goes to the monolith.

```
BEFORE the flip                       AFTER the flip
─────────────────────────             ─────────────────────────────────────
/api/products/**   → product-service  /api/orders/**      → order-service   ◄ NEW
/api/categories/** → product-service  /api/products/**    → product-service
/api/**            → monolith          /api/categories/** → product-service
  (also catches /api/orders/**)       /api/**             → monolith
                                         (no longer catches orders)
```

**The gotcha to know — route order:** the gateway evaluates routes **top to bottom** and takes the
first match. So `/api/orders/**` must be declared **before** the `/api/**` catch-all, otherwise the
catch-all grabs it first and everything goes back to the monolith. Same mechanic as product-service.

The gateway also needs the **order-service URL** (an environment variable), just like it already
has the product-service URL. Conceptually nothing new: one extra route + one extra env var.

> 💡 **Rolling back this step** = remove the new route (or point `/api/orders/**` back at the
> monolith). As long as writes haven't reopened, this re-flip is free.

---

## Phase 10 — Rollback plan

Rollback isn't a single action: it depends on **which side of step 8** (the reopen) you're on.
This is THE thing to keep in your head on game day.

```
                    Something breaks during the cutover
                                   │
                 ┌─────────────────┴──────────────────┐
                 │                                     │
        BEFORE reopen (≤ step 7)              AFTER reopen (≥ step 8)
                 │                                     │
                 ▼                                     ▼
        ✅ CLEAN ROLLBACK                     ⚠️ EXPENSIVE ROLLBACK
        ───────────────────                  ─────────────────────
        1. Re-flip the route                 Real orders already exist
           /api/orders/** → monolith         in order-service.
        2. Reopen writes                     Options:
        3. Monolith resumes, its              a) Re-migrate those new
           data never moved → ZERO              orders back to the monolith
           loss                                  (reverse script)
                                              b) Fix order-service in place
        Duration: ~30 seconds                    and do NOT roll back
                                              → we ALWAYS prefer to never get
                                                here (hence the exhaustive
                                                smoke test at step 7)
```

**The rule that falls out of this:** we reopen writes (step 8) **only** when the step-7 smoke test
is 100% green. That's the only moment we truly "commit" the migration. Before that, we can abandon
for free, as many times as we want.

**Precondition for the clean rollback to work:** **never** drop the monolith's `orders` /
`order_items` tables during the cutover. They are your safety net. They only get dropped in
Phase 11, **several days later**, once prod is stable.

---

## Phase 11 — Monolith cleanup

**When:** several days **after** a stable cutover — never on game day. As long as the monolith's
`orders` tables exist, the clean rollback stays possible. We only remove them once confidence is
earned. Same playbook as the product-service removal we did together.

### 11.1 — Remove the order code from the monolith

Delete from the monolith:
- the entire **order** domain (domain / application / infrastructure / api);
- the **backfill** helpers (`BackfillController`, `BackfillService`) if any existed — no longer useful.

### 11.2 — Drop the tables via Flyway

We **never** drop a table by hand in prod: we add a new Flyway migration to the monolith (the next
one after V22) that drops `order_items` **then** `orders` — in that order, because of the
`order_id → orders` foreign key. Same rule as everywhere: every schema change goes through Flyway,
never a manual edit.

### 11.3 — Confirm the monolith still compiles

After removal, Spring will complain if anything still references the orders domain. Search for
leftover references to "order / Order" in the monolith's source and make sure only the ones tied to
the **customer** domain remain.

---

## Final validation checklist (step 7 of the runbook — the last gate before reopening)

This is exactly the test we run **first in preprod** (Phase 8), then **in prod** right after the
route flip, **before reopening writes** (step 8). Until every line is green, we do not reopen — and
rollback stays free (re-flip the route).

Test order-service directly, then re-test through the gateway once the route is flipped. Every line
must be **green**:

| # | What we check | Expected result | What it proves |
|---|---|---|---|
| 1 | List orders (`GET /api/orders`) | the paginated list of migrated orders shows up | the **data migration** worked |
| 2 | `customerName` present on every order | non-null field | the **customer snapshot** is filled (no broken join) |
| 3 | Create an order (`POST /api/orders`) | 201 + order created | the **HTTP calls** to monolith + product-service work |
| 4 | `productSnapshot` present (name, price, categories) | non-null on the new order | the **product snapshot** builds correctly on create |
| 5 | Change status (`PATCH /api/orders/{id}/status`) | status updated | **writes** work end-to-end in order-service |

**As long as a single line isn't green → we don't reopen, and rollback stays free (re-flip the
route).** Only when everything passes → reopen writes (step 8 of the runbook).

---

## What this extraction will teach you

- **The customer snapshot pattern** — storing the customer's name on the order to avoid a
  read-time dependency between services. You already did it for products; same idea.
- **Inter-service HTTP calls** — order-service talks to two services (monolith + product-service).
  You'll see concretely what "loose coupling" means: each service owns one HTTP client.
- **Hexagonal architecture, again** — the same four layers in a new context. Repetition is what
  turns a pattern into a reflex.
- **Big-bang cutover with a maintenance window** — the simple alternative to the Strangler Fig when
  you have a preprod env. Rehearse in preprod, replay in prod, roll back if KO. You see firsthand
  why accepting a few minutes of downtime deletes the entire sync machinery.
- **The point of no return** — understanding that in a migration, rollback is free up to one precise
  instant (reopening writes) and expensive after. Knowing exactly where that line is.
- **One-shot data migration** — the real challenge of microservices isn't the code, it's moving the
  data. Here: freeze, dump, load, verify counts, flip.
- **Flyway in a new service** — migrations restart at V1 in the new database. Each service owns its
  own independent Flyway history.
