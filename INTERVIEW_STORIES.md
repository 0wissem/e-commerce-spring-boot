# Interview Story Bank

> **What this is.** Every real decision in this project, written as a story you can *say out loud*.
> Not notes to re-read — answers to be **recalled**.
>
> **Why stories.** You forget abstract definitions in three weeks. You don't forget things you lived.
> "The outbox pattern guarantees at-least-once delivery" decays. "I had two databases that had to
> stay in sync during a traffic split, and a naive dual-write silently diverges when the DB commit
> succeeds but the Kafka publish fails — so I wrote the event to an `outbox_events` table in the same
> transaction" does not. One story answers five questions.
>
> **How to use it.** Do NOT re-read this doc. Re-reading feels like learning and does almost nothing.
> Instead: cover the answer, read only the **Q** line, say your answer out loud, *then* uncover and
> compare. The struggle to retrieve is the thing that builds the memory. Getting it wrong and then
> seeing the answer beats reading the answer five times.
>
> **The shape of every answer:** `Situation → Problem → Options → Decision → Tradeoff → Outcome`.
> That shape works for all three interview types: "what's your day-to-day", "tell me about your
> experience", and "explain this pattern".

---

## Your diagnosis (from REVISION.md scores)

| You are STRONG at | You are WEAK at |
|---|---|
| Strangler Fig / extraction strategy (17–20) | **Repository Pattern (4/20, 0/20)** |
| JSONB snapshots (16–20) | **HTTP Client pattern (4/20)** |
| Soft delete (19–20) | **Kafka mechanics (8/20)** |
| CI/CD, profiles (18–20) | **Flyway internals (8/20)** |
| The "why we did it" narrative | Outbox mechanics (11/20), DTO/Mapper (10–14) |

**The pattern: you're strong on _why we built it_, weak on _how it actually works_.**
That's a classic frontend-lead profile — good architectural reasoning, shaky on the plumbing.
Interviewers hit both. You can't bluff the plumbing. So every story below carries a
**⚙️ Mechanism** section — that's the part you must be able to produce, not just the narrative.

---

# PART 1 — The opener (rehearse this until it's automatic)

**Q: "Tell me about a project you're proud of."**
Almost every interview starts here. This 60-second answer plants the hooks they'll ask about, so
you get to choose the rest of the interview. Name-drop deliberately: *load test, strangler fig,
outbox, gateway, snapshot*. Each is bait.

> I built an e-commerce backend in Spring Boot and deliberately took it through the full lifecycle
> a real system goes through — not a greenfield toy.
>
> It started as a monolith: customers, products, categories, orders. I load-tested it with k6 and
> found the product full-text search endpoint degraded hard under concurrency — it was the hot path
> and it was dragging the whole app down. So instead of guessing, I had data telling me exactly
> which domain to pull out first.
>
> I extracted product-service using the **Strangler Fig** approach — the new service grows alongside
> the monolith rather than a big-bang rewrite. I put an **API Gateway** in front, and shifted traffic
> progressively, 1% at a time, so I could roll back instantly. While both systems ran in parallel,
> I kept their databases in sync with the **Outbox Pattern** + Kafka, because a naive dual-write
> silently diverges when one of the two writes fails.
>
> Once product-service took 100% of traffic, I decommissioned the sync and deleted the old code.
> Then I did it again for orders. Today it's four services — monolith (customers), product-service,
> order-service, and a gateway — each with its own database, deployed through GitHub Actions to AWS.
>
> The most interesting problem was cross-service data ownership: an order needs product data, but
> order-service can't read product-service's database. I solved it with a **JSONB snapshot** —
> the product name, price and categories are frozen into the order line at creation time. If the
> product is later renamed or deleted, the order still knows what was actually bought.

**Follow-up traps they will set:**
- *"Why not just optimize the monolith instead of extracting?"* → I did first: the N+1 fixes and the
  GIN index came before the extraction. Extraction was for **independent scaling** — search was
  CPU-hungry, and I didn't want to scale customers and orders just to scale search.
- *"Wasn't a microservice overkill here?"* → Honestly, for the traffic this app really has, yes.
  I built it to learn the pattern properly. But the *trigger* was real and measured — that's the
  part that transfers: extract when you have data, not when you have an opinion.

---

# PART 2 — The war stories

## Story 1 — The load test that chose the first service

**Answers:** *"How do you decide what to extract first?"* / *"Have you done performance work?"* /
*"How do you make technical decisions?"*

- **Situation.** Monolith with customers, products, categories, orders. I wanted to go microservices
  but had no evidence about *where* to cut.
- **Problem.** Splitting on a hunch is how you end up with a distributed monolith — all the
  operational pain, none of the benefit.
- **Action.** I load-tested with **k6**, pushing results into **InfluxDB** and visualizing in
  **Grafana** on an EC2 box. I ramped concurrent users against every endpoint.
- **Finding.** Product **full-text search** degraded first and worst. It's CPU-heavy, it was the hot
  path, and under load it was starving the rest of the app of resources.
- **Decision.** Extract **product-service** first, so search can scale independently.
- **Outcome.** The extraction was justified by a number, not a preference. And I could prove
  afterwards that it worked.

> **The line to land:** *"I didn't pick the first service because it felt right. I load-tested until
> the system told me where it hurt."*

⚙️ **Mechanism** — the search endpoint uses PostgreSQL full-text search: a `tsvector` column on
`products` with a **GIN index**, queried with the `@@` operator via a native `@Query`. GIN is an
inverted index — it maps each lexeme to the rows containing it, which is what makes text search fast.
Without it, every search is a full table scan. (This is also why H2 can't test it — see Story 8.)

---

## Story 2 — Dual-write is a trap → the Outbox Pattern

**Answers:** *"How do you keep two services' data consistent?"* / *"Distributed transactions?"* /
*"Explain the Outbox Pattern."*
**⚠️ You scored 11/20 here. The mechanism is what you dropped.**

- **Situation.** During the migration, the monolith and product-service *both* had a products table.
  Traffic was split between them. They had to stay in sync.
- **Problem.** The obvious approach — **dual-write** — is broken:
  ```java
  productRepository.save(product);   // commits to Postgres ✅
  kafkaTemplate.send("product.events", event);   // network dies ❌
  ```
  Now Postgres has the product and product-service never hears about it. **Silent divergence.**
  You cannot wrap a database transaction and a Kafka publish in one atomic unit — they're two
  different systems. There's no shared commit.
- **Options.**
  1. Dual-write → rejected, silently inconsistent.
  2. Two-phase commit (XA) → heavyweight, poor support, blocks on coordinator failure.
  3. Shared database → rejected: if both services write the same tables, they aren't independent
     services, they're one service with two deployments. You can't change the schema without
     coordinating a release.
  4. **Outbox Pattern** → chosen.
- **Decision — how Outbox works.** Write the event into an `outbox_events` **table in the same
  database, inside the same transaction** as the business write. Now it's one atomic commit — either
  both the product and the event row land, or neither does. A separate `@Scheduled` publisher then
  polls the table and pushes to Kafka, marking rows `SENT`.
- **Tradeoff.** You get **at-least-once** delivery, not exactly-once. If the publisher crashes after
  sending but before marking the row SENT, it re-sends on restart. So **consumers must be
  idempotent** — that's the price of the pattern, and knowing that is the whole point.
- **Outcome.** Both DBs stayed consistent through the traffic split. Once product-service owned 100%
  of products, the sync had no reason to exist and I deleted it (Story 10).

⚙️ **Mechanism / the gotcha you must mention:** the publisher **blocks on `.get()`** when sending:
```java
kafkaTemplate.send(topic, payload).get();   // blocking, deliberately
```
If you send asynchronously, the code marks the row `SENT` immediately, before Kafka has actually
confirmed delivery. Broker rejects it → the row says SENT → **the event is lost forever**. Blocking
means we only mark SENT after the broker acknowledges. Slower, correct.

> **The line:** *"Dual-write doesn't fail loudly, it fails silently — that's what makes it dangerous.
> The outbox turns two writes into one transaction, and pays for it with at-least-once delivery."*

---

## Story 3 — Hibernate silently overwrote my IDs

**Answers:** *"Tell me about a hard bug."* / *"Debugging story?"* / JPA depth.

- **Situation.** product-service consumed `product.events` from Kafka and saved products into its own
  DB. The IDs **had to match** the monolith's — the gateway was splitting traffic, so the same product
  had to be findable by the same UUID in both systems.
- **Symptom.** Products arrived. Products were saved. But the IDs were **different**. A product
  created in the monolith was unreachable by its own ID through product-service. Traffic split →
  random 404s depending on which service answered.
- **Root cause.** The entity had `@GeneratedValue(strategy = GenerationType.UUID)`. That annotation
  means *"Hibernate, you own this ID"* — so on `save()`, **Hibernate generated a fresh UUID and
  overwrote the one I'd just set from the Kafka event.** Silently. No error.
- **Fix.** Remove `@GeneratedValue`. The ID is now **assigned by the application**, so the value from
  the event survives the save.
- **Lesson.** `@GeneratedValue` isn't "give me an ID if I don't have one" — it's "this ID belongs to
  the persistence layer, hands off." When you replicate data across services, the **ID is data**, not
  an implementation detail. The source system owns it.

⚙️ **The subtlety that impresses:** with an assigned ID, Hibernate can no longer use the null-ID check
to tell a new entity from a detached one. `save()` on a Spring Data JPA repository becomes
`merge()`-flavoured — it may issue a `SELECT` before the `INSERT` to find out whether the row exists.
(Fixes: implement `Persistable#isNew`, or accept the extra select.)

---

## Story 4 — The framework was broken, so I wrote the filter myself

**Answers:** *"Tell me about a time a library let you down."* / *"How do you do progressive rollout?"*
/ *"Canary / traffic shifting?"*

- **Situation.** I needed to shift product traffic gradually from monolith to product-service —
  1%, then 10%, 50%, 100% — so I could roll back the instant something broke.
- **Attempt.** Spring Cloud Gateway ships a built-in `Weight` predicate for exactly this. I used it.
  It **500'd at runtime** in version 2024.0.1 — a genuine framework bug, not my config.
- **Options.** Wait for an upstream fix (blocked, no timeline), pin an older version (dependency risk
  across the whole Spring Cloud BOM), or implement it myself.
- **Decision.** I wrote `WeightedRoutingFilter`, a Spring Cloud **`GlobalFilter`**: roll a random
  number against a `PRODUCT_SERVICE_WEIGHT` env var, then rewrite the request's target URI to either
  product-service or the monolith. ~30 lines. The weight is an **environment variable**, so shifting
  traffic is a config change, not a redeploy.
- **Outcome.** Rolled out 1% → 100% safely, with instant rollback by setting the weight back. Once
  product-service was at 100%, I **deleted the filter** — it existed only to serve the migration.
- **Lesson.** Read the framework's source when it misbehaves. A `GlobalFilter` is an interface with
  one method; "the framework can't do it" usually means "I haven't looked at how the framework does it."

> **The line:** *"The built-in weight predicate was broken in that release, so I read how the gateway
> filter chain works and wrote a 30-line GlobalFilter. Shipping mattered more than being pure."*

---

## Story 5 — Cross-service data ownership: the JSONB snapshot

**Answers:** *"How do services share data without a shared DB?"* / *"How do you handle joins across
services?"* / your strongest block — but nail the mechanism.

- **Problem.** An order line needs product name, price, categories. But **order-service cannot read
  product-service's database** — that's the whole point of separate services. And a foreign key across
  two databases doesn't exist.
- **The deeper problem.** Even if I *could* join, I **shouldn't**. If a product's price changes from
  €10 to €15 next month, an order placed at €10 must still say €10. A live join would rewrite history.
  An invoice is a **historical fact**, not a live view.
- **Decision.** At order creation, order-service calls product-service over HTTP, takes the product's
  name/price/categories, and **freezes them into the order item** as a serialized JSON snapshot
  (`OrderProductSnapshot`, persisted as TEXT/JSONB).
- **Tradeoff.** Data is duplicated and it's a *point-in-time copy* — deliberately stale. That's not a
  bug, that's the requirement. It also means the order survives the product being **deleted**.
- **Outcome.** Cross-service data ownership solved with no shared DB and no distributed join.

> **The line:** *"An order isn't a view over products, it's a record of what happened. So I snapshot
> instead of join. The product can change or be deleted — the order doesn't care."*

⚙️ **Mechanism.** A JPA `AttributeConverter` serializes the snapshot object to JSON on write and
back on read, so the domain works with a real object while the DB stores one column. Why JSONB and
not five columns (`product_name`, `product_price`, ...)? Because the snapshot's shape will keep
growing — add a field and it's a code change, not a migration on a huge table.

---

## Story 6 — Replacing a DB join with an HTTP call (and the bug I introduced)

**Answers:** *"What changes when you extract a service?"* / *"Resilience / failure handling?"*
**⚠️ You scored 4/20 here. This is your weakest block. Learn this one cold.**

- **Situation.** Before extraction, the monolith's `OrderService` did `productRepository.findById()` —
  a local DB call, in-process, transactional, ~1ms, and it either worked or threw.
- **After extraction**, that same line became `productServiceClient.getById()` — an **HTTP call over
  the network to another service**. Same intent, a completely different set of failure modes.
- **What actually changes** (this is the real answer to "what's hard about microservices"):
  | Local DB call | Cross-service HTTP call |
  |---|---|
  | ~1 ms | tens/hundreds of ms |
  | Fails = exception | Fails = timeout, 500, 404, connection refused, **or hangs forever** |
  | Inside your transaction | **Cannot** be rolled back |
  | Always available | The other service can be **down** |
- **Implementation.** `ProductServiceClient` uses Spring's `RestClient`, with `.onStatus(4xx → throw
  ResourceNotFoundException)` so a missing product surfaces as a clean 404 to the caller rather than
  a raw 500.

**🔥 The self-critique that wins the room.** My `OrderService.create()` is annotated
`@Transactional`, and it makes those HTTP calls **inside the transaction**:
```java
@Transactional
public OrderResponse create(OrderRequest request) {
    customerServiceClient.getById(...);   // network I/O — inside the tx 😱
    productServiceClient.getById(...);    // network I/O, once PER ITEM 😱
    orderRepository.save(order);          // the only thing that needs the tx
}
```
This holds a **database connection open for the entire duration of the network calls**. Under load,
if product-service gets slow, every in-flight order is squatting on a pooled connection waiting on
HTTP — and the connection pool exhausts. **A slowdown in product-service becomes a total outage in
order-service.** That's cascading failure, and it's self-inflicted.

**The fix:** do the lookups *before* opening the transaction, keep the transaction around the write
only. And since the product lookups are independent, run them **in parallel** with
`CompletableFuture` instead of sequentially — latency goes from `sum(calls)` to `max(calls)`.
Then add **timeouts** (a call with no timeout can hang forever), a **retry** with backoff, and
ideally a **circuit breaker** so a dead product-service fails fast instead of dragging you down.

> **The line:** *"Extracting the service turned a 1ms in-process call into a network call, and I left
> it inside the transaction. It works fine at low traffic and it's a connection-pool exhaustion bug
> waiting to happen. I know exactly how I'd fix it: lookups outside the tx, parallelized, with
> timeouts and a circuit breaker."*
> Interviewers *love* this. A candidate who finds the flaw in their own code is a candidate who will
> find flaws in production.

---

## Story 7 — Repository Pattern: port vs adapter

**Answers:** *"Explain the repository pattern."* / *"Why an interface if there's only one impl?"* /
*"What's dependency inversion?"*
**⚠️ You scored 4/20 and 0/20 here. THIS IS YOUR WORST TOPIC. Drill it hardest.**

There are **three** types, and you must be able to name all three and say who depends on whom:

```
domain/         IOrderRepository          ← the PORT: an interface. Pure Java. No JPA.
infrastructure/ OrderRepositoryAdapter    ← the ADAPTER: implements the port, wraps the JPA one.
infrastructure/ OrderJpaRepository        ← Spring Data: extends JpaRepository. Spring writes the impl.
```

**The flow:** `OrderService` (application) depends on `IOrderRepository` (domain interface).
At runtime Spring injects `OrderRepositoryAdapter`, which delegates to `OrderJpaRepository`, which
Spring Data implemented for us at startup by generating a proxy from the method names.

**Why bother — the four real reasons** (any one alone sounds weak; give two):
1. **Dependency inversion.** Without the port, `OrderService` (business logic) would import JPA —
   business logic would depend on infrastructure. With it, **infrastructure depends on the domain**:
   the adapter implements an interface the domain owns. The arrow is inverted. That's the *whole idea*
   of hexagonal architecture.
2. **Testability.** `OrderServiceTest` runs with plain Mockito, no Spring, no database — because it
   mocks the interface. That test is only possible because the service never names a concrete class.
3. **Swappability.** Move to MongoDB and you write a new adapter. `OrderService` doesn't change,
   doesn't recompile, doesn't know.
4. **A curated API.** `JpaRepository` gives you ~20 methods you never wanted (`flush`, `saveAll`,
   `deleteAllInBatch`). The port exposes only the 7 the domain actually needs.

**The pushback you WILL get: *"Isn't that just a pointless extra layer? You have one implementation
and you'll never swap Postgres."*** Don't fold, and don't be dogmatic either:
> *"Honestly, the swappability argument is the weakest one — I'm not going to change database.
> The two that actually pay for themselves are the dependency direction and the tests. My unit tests
> run without a Spring context or a container because the service only knows an interface. On a small
> CRUD app I'd skip the adapter and use Spring Data directly — the indirection isn't free."*
> That answer shows judgment. Reciting "clean architecture!" shows you read a blog post.

🔥 **The honest flaw in MY code** — bring this up yourself:
```java
package org.example.orderservice.order.domain;
import org.springframework.data.domain.Page;      // ← Spring, in the domain layer
import org.springframework.data.domain.Pageable;  // ← my own rule says zero framework imports
public interface IOrderRepository { Page<Order> findAll(Pageable pageable); ... }
```
My rule is *"the domain has zero framework imports"* — and my port imports Spring Data's `Page` and
`Pageable`, so the framework leaked into the pure layer. The purist fix is a domain-owned
`PageQuery`/`PageResult` type, with the adapter translating. I judged that not worth the ceremony
here, but **it is a real violation and I know it's there.** Saying that = you understand the rule.
Not noticing = you memorized it.

---

## Story 8 — Why Testcontainers, not H2

**Answers:** *"How do you test the persistence layer?"* / *"In-memory DB for tests?"* / testing depth.

- **The naive approach:** H2 in-memory for tests. Fast, zero setup, no Docker.
- **Why it fails, concretely, in MY project:** the product search uses PostgreSQL `tsvector` + a
  **GIN index** + the `@@` operator. **H2 does not have any of that.** The single most important,
  most performance-critical query in the entire system — the one whose degradation *caused the
  microservice extraction* — is literally untestable on H2.
- **The general principle:** H2 tests give you *confidence*, not *coverage*. You're testing against a
  database you will never deploy. Anything vendor-specific — full-text search, JSONB, window
  functions, `@SQLRestriction`, sequences, locking, even error messages — behaves differently or not
  at all. Green tests, broken production.
- **Decision.** **Testcontainers**: tests boot a **real PostgreSQL 16** in Docker, and **Flyway runs
  the real migrations** against it. The test schema is production's schema, by construction.
- **Tradeoff.** Slower (seconds to start a container) and Docker must exist on the machine — including
  the CI runner. Mitigation: the **singleton container pattern** — `AbstractIntegrationTest` starts
  **one** container in a `static` block, shared by every test class, so you pay the startup cost once.
- **Outcome.** I test full-text search, JSONB and soft-delete for real. It runs on the GitHub Actions
  ubuntu runner too.
- **We were so right about this** that H2 is now **removed from the project entirely** — local dev
  runs on a real Postgres in Docker Compose. Dev/prod parity.

⚙️ **Mechanism.** The container's JDBC URL is random (random host port), so it can't be hardcoded in
`application.properties`. `@DynamicPropertySource` injects `spring.datasource.url/username/password`
into the Spring Environment *after* the container starts but *before* the context loads.

---

## Story 9 — N+1: found by counting, not by guessing

**Answers:** *"What's the N+1 problem?"* / *"How do you find a slow endpoint?"* / JPA depth.

- **The problem.** `findAll()` on orders → 1 query for the orders, then **one more query per order**
  to lazily load its items. 100 orders = **101 queries**. The endpoint looks fine with 10 rows in dev
  and dies in production.
- **How I *proved* it** (this is the part that separates you from someone who read about N+1):
  I enabled **Hibernate `Statistics`** and asserted on the **query count** in a test
  (`ProductNPlusOneTest`). `findAll()` → 1+N queries. The fixed version → **1**. The test fails if
  someone reintroduces the regression. *You can't fix what you don't measure.*
- **The fix.** `@EntityGraph(attributePaths = {"orderItems"})` on the repository method — tells
  Hibernate to **fetch-join** the association in the same query instead of lazily loading it later.
- **Detection in the wild:** `spring.jpa.show-sql`, Hibernate Statistics, or p6spy. The tell is a
  wall of near-identical `SELECT ... WHERE order_id = ?` lines.
- **Other fixes and when:** `@BatchSize(size=25)` (turns N queries into N/25 — good when you *do*
  want laziness), or a two-step manual fetch. `@EntityGraph` is per-query, which is why it beats
  `FetchType.EAGER` — **eager is a global decision that punishes every query that didn't need the
  association.**

⚠️ **The trap they'll spring on you:** *"So just fetch-join everything?"* No —
1. **Fetch-join + pagination don't mix.** A join returns one row per *item*, not per *order*, so
   `LIMIT 20` slices the wrong thing. Hibernate detects it and pages **in memory** — it loads the
   entire table into the JVM and paginates there. It even warns you: `HHH90003004: firstResult/
   maxResults specified with collection fetch; applying in memory`. Silent catastrophe at scale.
2. **Cartesian explosion.** Fetch-join *two* collections and you get `rows(A) × rows(B)`.
3. You need `DISTINCT` (or `Set`) or you get duplicated parents.

🔥 **My code still has this bug:** `OrderJpaRepository` has `@EntityGraph` on the **paginated**
`findAll(Pageable)` — but the plain `findAll()` has none. So the paginated endpoint is fixed and the
unpaginated one still does 1+N. Worth saying out loud: *"I fixed the endpoint the frontend actually
calls; the unpaginated one is still N+1 and honestly should just be deleted."*

---

## Story 10 — Knowing when to *delete* a pattern

**Answers:** *"Tell me about tech debt."* / *"When do you remove something?"* / seniority signal.

- **Situation.** Outbox + Kafka existed for exactly one reason: keeping two products tables in sync
  **while traffic was split** between monolith and product-service.
- **The trigger.** Once the gateway sent 100% of product traffic to product-service, the monolith
  stopped owning products. The sync had **nothing left to synchronize**.
- **Decision.** Delete it. `OutboxPublisher` gone, `outbox_events` table dropped (migration V21),
  the Kafka consumer in product-service gone, the product tables dropped from the monolith DB (V20),
  categories dropped (V22). Later, order-service extracted and orders removed the same way.
- **Why this matters more than it sounds.** Migration scaffolding that outlives the migration is the
  worst kind of tech debt: it's *load-bearing-looking* code that does nothing. The next dev sees Kafka
  in the stack and assumes the system is event-driven. Every future change pays a tax to a pattern
  that has no purpose.
- **Outcome.** The monolith today has **no Kafka dependency, no mail, no outbox** — it's a
  customers-only service, and it's the last thing left to extract.

> **The line:** *"Adding the outbox was the easy call. The senior call was deleting it the moment
> the traffic split ended, instead of leaving Kafka in the stack forever because it looked impressive."*

---

## Story 11 — Transactions: what @Transactional actually does

**Answers:** *"Explain @Transactional."* / *"Propagation? Isolation?"* / *"How did you test rollback?"*

- **What I proved.** `OrderTransactionalTest`: `create()` fetches a product mid-flight; I made that
  lookup throw; **no partial order is persisted** — no order row, no item rows. On success, everything
  commits. Rollback proven with a real Postgres via Testcontainers, HTTP clients mocked with
  `@MockitoBean`.
- **⚠️ The default that catches everyone:** Spring rolls back on **unchecked** exceptions
  (`RuntimeException`, `Error`) **only**. A **checked** exception does NOT trigger rollback — the
  transaction *commits*. If you need it: `@Transactional(rollbackFor = Exception.class)`.
- **Propagation** — what happens when a transactional method calls another one:
  - `REQUIRED` (default) — join the caller's transaction if there is one, else start one.
  - `REQUIRES_NEW` — always suspend the caller's and start an independent one. Use for something that
    must survive the caller's rollback (audit log, for example).
- **Isolation** — how much concurrent transactions see of each other: `READ_COMMITTED` (Postgres
  default) prevents dirty reads. Higher levels prevent non-repeatable / phantom reads, at the cost of
  contention.
- **`readOnly = true`** — a hint: Hibernate can skip dirty-checking, and it can be routed to a replica.

🔥 **The self-invocation trap — the #1 @Transactional interview question:**
```java
public void a() { this.b(); }          // b() runs with NO transaction!
@Transactional public void b() { ... }
```
`@Transactional` works via a **proxy**. Spring wraps your bean in a proxy that opens the transaction
*before* delegating to the real object. But `this.b()` is an **internal call on the raw object** — it
never goes through the proxy, so the annotation is simply not seen. Same trap applies to
`@Cacheable` and `@Async`, for exactly the same reason. Fix: call it from another bean, or
self-inject the proxy.

**And the anti-pattern I found in my own code** — HTTP calls inside the transaction. See Story 6.

---

## Story 12 — Caching, and the part everyone forgets

**Answers:** *"How do you cache?"* / *"Redis or local?"* / *"What's hard about caching?"*

- **What I did.** product-service `getById` is `@Cacheable("products")`; update and delete are
  `@CacheEvict`. Backed by **Caffeine**, `expireAfterWrite(10min)`, `maximumSize(10_000)`.
- **How I proved it works.** `ProductCacheTest` **counts DB reads**: two `getById` calls → **one**
  `findById`. Then an update evicts, and the next read hits the DB again. Behaviour, not vibes.
- **⚠️ The bounds are the point.** An unbounded cache is a **memory leak with a stale-data problem**.
  `expireAfterWrite` is your staleness ceiling; `maximumSize` triggers LRU eviction. A cache with
  neither will eventually OOM the service and serve wrong data until it does.
- **Providers, and when.** `simple` (a `ConcurrentHashMap` — fine for a demo), **Caffeine** (in-process,
  fast, bounded — what I used), **Redis** (out-of-process, **shared across instances**).
- **🔥 The scaling flaw in what I built, which I'd volunteer:** Caffeine is **per-instance**. Run three
  replicas of product-service and you have **three independent caches**. Instance A updates a product
  and evicts *its own* cache — B and C keep serving the stale one for up to 10 minutes. For a
  multi-instance deployment you need Redis (a shared cache) or event-based invalidation. My TTL bounds
  the damage; it doesn't eliminate it.
- **Same proxy caveat as `@Transactional`:** a `this.getById()` self-call bypasses the cache entirely.

> **The line:** *"There are only two hard things in computer science, and I hit one of them: cache
> invalidation. My eviction is correct on one instance and wrong on three."*

---

## Story 13 — Auth across services: shared-secret JWT

**Answers:** *"How do you secure microservices?"* / *"Where do you validate the token?"* / *"Stateless auth?"*

- **Design.** The **monolith** owns identity: users table, login endpoint, and it **signs** a JWT
  (HMAC). product-service and order-service are **resource servers** — they hold the same shared
  secret and **verify** the token themselves. Roles are carried as claims; `@PreAuthorize` enforces
  them per endpoint.
- **Why validate in each service rather than only at the gateway?** Gateway-only auth means anything
  that reaches a service *behind* the gateway is trusted implicitly. That's a soft interior — one
  misconfigured security group or one pod inside the cluster and you're wide open. Each service
  validating its own token = **defense in depth**, and the services stay independently deployable and
  independently testable.
- **Why JWT rather than sessions?** **Stateless.** No shared session store, so any instance can serve
  any request — which is what makes horizontal scaling trivial. The token carries its own claims.
- **The tradeoff you must name: you cannot revoke a JWT.** It's valid until it expires — that's the
  cost of statelessness. Mitigations: short TTLs + refresh tokens, or a revocation list (which drags
  state back in and partially defeats the point).
- **🔥 The honest weakness:** HMAC = a **symmetric shared secret**, so every service can *sign* tokens,
  not just verify them. Compromise any one service and the attacker can mint tokens for all of them.
  The correct fix is asymmetric **RS256**: the monolith holds the private key and signs; the others
  hold only the public key and can *only* verify.

---

## Story 14 — Flyway, and the migration that broke production

**Answers:** *"How do you manage schema changes?"* / *"How do you roll back a migration?"*
**⚠️ You scored 8/20 here.**

- **The model.** Versioned SQL files (`V1__init.sql`, `V2__...`). Flyway keeps a
  **`flyway_schema_history`** table in the database recording which versions have run **and a checksum
  of each**. At startup it compares the files on the classpath to that table and applies what's missing,
  in order.
- **The iron rule: never modify an applied migration.** The checksum won't match what's recorded, and
  Flyway refuses to start — deliberately. Fixing a mistake means writing a **new version** that
  corrects it. This is exactly like git: you don't rewrite pushed history, you commit a fix.
- **"How do you roll back?"** The honest, senior answer: **you generally don't.** You write a forward
  migration that undoes it. And crucially you make migrations **backward-compatible with the currently
  deployed code**, because during a rolling deploy old and new code run against the same schema at the
  same time. Dropping a column the old pods still SELECT = outage. That's why a column rename becomes:
  add new → backfill → deploy code using both → stop using old → drop old, across releases.
- **🔥 My real production incident.** I defined a **custom Flyway bean** in product-service to control
  migration behavior. It **broke prod**: my custom configuration meant `flyway_schema_history` was
  never created properly, so the migration state was lost. The fix was to **delete the custom bean**
  and let Boot's autoconfiguration do its job. Commit `eb5df6c`, literally titled *"remove custom
  Flyway bean — broke prod"*.
- **The deeper lesson** (and the Boot 4 gotcha): the actual root cause of my whole family of Flyway
  problems was that **Spring Boot 4 split autoconfiguration into per-technology modules**. Flyway
  autoconfig no longer comes for free — you must add the `spring-boot-flyway` module. Without it,
  Flyway silently doesn't run and Hibernate's `ddl-auto` quietly creates the schema instead, so it
  *looks* like it works. Fighting that with a hand-rolled bean was treating the symptom.

> **The line:** *"I tried to hand-roll a Flyway bean and took down prod, because I was working around
> a missing autoconfig module instead of adding it. Now I let the framework do its job and I add the
> module."*

---

## Story 15 — Environments, seeds, and the free-tier constraint

**Answers:** *"How do you manage config across environments?"* / *"Dev/prod parity?"*

- **Spring Profiles.** `prod` and `preprod` — separate `application-{profile}.properties`. Secrets and
  URLs come from **environment variables**, never committed. `dev` branch → **preprod** (eu-west-3);
  `master` → **prod** (eu-north-1).
- **Environment-specific seed data.** prod gets a 10k-product seed (realistic load-testing volume),
  preprod gets 20. Same migrations, different seed — selected by pointing
  `SPRING_FLYWAY_LOCATIONS` at `db/seed-prod` vs `db/seed-preprod`. The schema is identical
  everywhere; only the data differs.
- **A real ops scar:** deploys to the shared preprod Elastic Beanstalk app kept failing because two
  services generated the **same `version_label`** — one service's image would clobber the other's.
  Fix: make the label unique **per service**. Boring, and exactly the sort of thing that eats a day.
- **CI/CD.** GitHub Actions per service → build → push to ECR (OIDC auth, no long-lived AWS keys) →
  deploy to Elastic Beanstalk. The monolith pipeline is **self-healing**: if the EB environment is
  missing or broken, it recreates it from scratch.
- **🔥 The constraint that shaped everything:** this ran on a **strictly free** AWS account. I got the
  full stack onto free-tier `t3.micro` nodes, which forced real capacity engineering (EKS prefix
  delegation, `maxPods`, trimmed resource requests to fit four apps on tiny nodes). When EKS proved
  impossible to keep free — the **control plane is billed per hour, there is no free tier for it** —
  I tore it down and moved the runtime to **minikube** locally. *Knowing when to stop is engineering too.*

---

## Story 16 — The low-stock alert: Kafka done properly, and the tests I broke on purpose

**Answers:** *"Design an event-driven feature."* / *"How do you handle failures with Kafka?"* /
*"What is an idempotent consumer?"* / *"What's a dead-letter queue for?"*
**This is the Kafka block (8/20) turned into a lived story. Tell it; don't recite definitions.**

- **Situation.** product-service owns stock. When stock runs low, the stock team should get an email.
  The monolith once had a half-broken version (removed in Phase 5). I rebuilt it as
  `product-service → stock.low topic → notification-service` (a new service, its own Postgres).
- **Why Kafka here, and not an HTTP call?** product-service shouldn't know who cares about low stock.
  Today it's email; tomorrow it's a restock bot and a dashboard. With a topic, adding a consumer is a new
  service, not a change to product-service. And if notification-service is down, events wait in Kafka
  instead of failing the sale. *Loose coupling in time AND in knowledge.*
- **Producer decision 1: fire on the CROSSING, not the level.** `before > 5 && after <= 5`. "Stock ≤ 5"
  would alert on every sale of an already-low item.
- **Producer decision 2: publish AFTER COMMIT (`@TransactionalEventListener(AFTER_COMMIT)`).** With
  optimistic locking, the version conflict only appears at flush — *after* my publish line. Sending
  inside the transaction = alerts for decrements that get rolled back and retried.
  **I proved it:** switched to a plain `@EventListener`, and two tests went red: a phantom alert after a
  rollback, and **2 alerts instead of 1** when 10 threads raced through the threshold.
- **Producer decision 3: key = productId.** Same key → same partition → per-product ordering.
  I watched it on the CLI: `p-1` always landed on partition 1.
- **Consumer decision 1: idempotent.** At-least-once means duplicates *will* come. The row is written
  `PENDING` before the email, flipped to `SENT` after, and `event_id` has a **UNIQUE constraint** — the
  lookup is only a fast path; the constraint is what actually closes the check-then-insert race.
- **Consumer decision 2: no `@Transactional` around the email.** Same lesson as the HTTP-in-transaction
  bug in order-service: never hold a DB connection across a network round trip.
- **Consumer decision 3: retries + dead-letter topic.** Transient (SMTP down) → retry 1s/2s/4s → DLT.
  Permanent (bad JSON, missing productId) → straight to the DLT, no retries.
  **I proved why the DLT exists:** I removed the `ErrorHandlingDeserializer` and sent one malformed
  message. It didn't only fail its own test — the **retry test failed too**, because the poison pill
  blocked the whole partition and every event behind it waited forever.
- **Tradeoff I say out loud.** After-commit still leaves a gap: commit succeeds, process dies before the
  send → alert lost. For an alert that's acceptable. For money it isn't — that's where the Outbox
  (Story 2) comes back.
- **A gotcha the tests missed and the running stack caught.** `@KafkaListener(id = "stock-low-listener")`
  silently used the id AS the consumer group. Kafka UI showed group `stock-low-listener`, not
  `notification-service`. Fix: `idIsGroup = false`, plus a test pinning the group name. When the corrected
  group started, it re-read the whole topic from offset 0 — and idempotency turned 2 re-deliveries into
  0 extra emails. *The pattern paid for itself the same afternoon.*
- **Outcome.** 20 new tests in product-service, 22 in notification-service, all on real Postgres + real
  Kafka + real SMTP (Mailpit) via Testcontainers. Verified end to end on docker compose: order through the
  gateway → one email; second order → no email; re-published duplicate → ignored; poison pill → DLT.

> **The line:** *"Kafka gives you at-least-once, in order per partition. Everything else is on you:
> publish after commit, key for ordering, dedupe on an event id, and give poison pills somewhere to go."*

---

# PART 3 — Rapid-fire, from your weak blocks

Cover the answers. Say each out loud. Anything you can't produce in ~20 seconds goes on the re-drill list.

**Repository (your 4/20 — drill until automatic)**
- *Three types, in order?* → `IOrderRepository` (port, domain, plain interface) → `OrderRepositoryAdapter`
  (adapter, infrastructure, `@Repository`) → `OrderJpaRepository` (Spring Data, `extends JpaRepository`).
- *Who implements `OrderJpaRepository`?* → Nobody writes it. **Spring Data generates a proxy at startup**,
  deriving the SQL from the method *names* (`findByCustomerId` → `WHERE customer_id = ?`).
- *Why an interface with one implementation?* → Dependency inversion + unit tests without a DB. And
  concede the swappability argument is weak.
- *What does `@Repository` actually add over `@Component`?* → Exception translation: vendor
  JDBC/Hibernate exceptions → Spring's `DataAccessException` hierarchy.

**DTO / Mapper (your 10–14)**
- *Why not return the JPA entity from the controller?* → (1) It **leaks your DB schema** to the client —
  the API contract becomes your table structure, so you can't refactor the DB without breaking clients.
  (2) **Security**: fields like a password hash serialize by accident. (3) **Lazy-loading blows up** —
  Jackson touches a lazy collection outside the session → `LazyInitializationException`. (4) Entities
  are mutable and identity-based; DTOs are immutable records.
- *Request vs Response DTO — why two?* → They're different shapes. The request has no id and no
  timestamps; the response has both and no password. One class doing both means nullable fields
  everywhere and validation you can't express.

**HTTP client (your 4/20)** → See Story 6. Say the four-row table. Then say the anti-pattern.

**Kafka (your 8/20)**
- *Topic, partition, offset, consumer group — in one breath?* → A **topic** is a named log. It's split
  into **partitions** (the unit of parallelism); ordering is guaranteed **within a partition, not across
  them**. Each message has an **offset** (its position). A **consumer group** shares the work: each
  partition is consumed by exactly one member of the group, so adding consumers beyond the partition
  count does nothing.
- *Why did the key matter to me?* → Keying by product id sends all events for one product to the **same
  partition**, so updates for that product stay **in order**. Without a key it's round-robin and an
  "update" can be processed before the "create".
- *At-least-once → what does it force on you?* → **Idempotent consumers.** Processing the same event
  twice must be safe (upsert, not blind insert).
- *Why did Kafka "need" `@EnableKafka` in Boot 4?* → It didn't, really: the pom had bare `spring-kafka`
  instead of `spring-boot-starter-kafka`. Boot 4 split auto-configuration into modules, so no starter =
  no auto-config. **Same root cause as the Flyway incident** (Story 14). Fixed in the Story 16 work.
- *What is a poison pill, and what stops it?* → A record that can never be processed. Kafka reads a
  partition in order, so it blocks everything behind it. `ErrorHandlingDeserializer` + a
  **dead-letter topic** move it aside.
- *Retryable vs not?* → SMTP timeout: retry with backoff. Bad JSON / missing field: never — same bytes,
  same failure. Straight to the DLT.
- *Why publish after commit?* → A rolled-back transaction must not have told the world anything.
  Optimistic-lock conflicts appear at flush, after your code "succeeded".
- *Consumer lag?* → Log-end offset minus the group's committed offset: how far behind the consumer is.
  `kafka_consumer_fetch_manager_records_lag_max` on /actuator/prometheus; also visible in Kafka UI.
- *Adding a field to the event breaks consumers?* → Not if they're **tolerant readers** (ignore unknown
  fields). Renaming/removing a field does → bump `schemaVersion`.

**Flyway (your 8/20)** → See Story 14: history table + checksum, never edit an applied migration,
forward-fix not rollback, backward-compatible during rolling deploys, and my custom-bean prod incident.

---

# PART 4 — Meta-answers (asked in almost every interview)

**"What would you do differently?"**
> Three things. I'd move the HTTP calls out of the transaction in `OrderService.create()` — it's a
> connection-pool exhaustion bug waiting for traffic. I'd switch the JWT from a symmetric shared secret
> to RS256, so only the identity service can mint tokens. And honestly, I'd have questioned whether
> this app needed microservices at all — I built them to learn the pattern, and the load test justified
> *one* extraction, not four services.

**"What's the hardest bug you've fixed?"** → Story 3 (Hibernate overwriting UUIDs). Silent, no error,
only visible as intermittent 404s that depended on which service the gateway happened to route to.

**"How do you make a technical decision?"** → Story 1. *"I load-tested until the system told me where
it hurt."* Then options → tradeoff → measure again.

**"How do you know your code works?"** → 48 tests across three services, gated in CI on every push.
Unit (Mockito, no Spring) → web (MockMvc) → integration (**real Postgres** via Testcontainers, real
Flyway migrations). I test *behaviour*: I proved the N+1 by counting queries and proved the cache by
counting DB reads.

**"What are you weakest at?"** → Be honest and specific, then show the plan:
> My depth is frontend — I'm a senior React dev moving to lead a backend team. So my instinct for
> *architecture* is good and my gaps are in the plumbing: JVM concurrency, and the operational side of
> Postgres. That's exactly why I built this project instead of reading about it — I wanted to hit the
> failure modes myself. The transaction-boundary bug I just described, I found in my own code.

---

# PART 5 — The re-drill list

The forgetting curve is real: you'll lose most of this in ~2 weeks without retrieval. Don't re-read —
**get quizzed**. Ask me to run a cold-call drill and I'll grade you.

Start with the blocks you bombed:
- [ ] Repository port/adapter/JPA — the three types, the dependency inversion (**4/20 → target 16+**)
- [ ] HTTP client + what changes when a DB call becomes a network call (**4/20**)
- [ ] Kafka mechanics — partitions, ordering, consumer groups, idempotency (**8/20**) → now drill it
  through **Story 16**: after-commit, key, UNIQUE event_id, retry vs DLT, poison pill
- [ ] Flyway — history table, checksums, forward-fix (**8/20**)
- [ ] Outbox mechanism — same transaction, the blocking `.get()`, at-least-once (**11/20**)
- [ ] DTO/Mapper — the four reasons (**10–14/20**)
