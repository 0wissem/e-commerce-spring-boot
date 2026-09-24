# Low-stock alerts — product-service → Kafka → notification-service

Event-driven notification: when a product's stock **crosses** the threshold on the way down, the stock
team gets one email. Built week of 2026-09-08 as the Kafka learning exercise parked since Phase 5.

## The picture

```
 order-service ──HTTP──▶ product-service                      notification-service
                         │ decrementStock()  (DB transaction)  │
                         │   stock 7 → 4, threshold 5          │ @KafkaListener  (3 threads = 3 partitions)
                         │   LowStockPolicy: crossed? yes      │   └─ NotificationService (idempotent)
                         │   port.publish(StockLowEvent)       │        ├─ notifications row  PENDING
                         │ COMMIT                              │        ├─ email via SMTP ──▶ Mailpit
                         │ @TransactionalEventListener         │        └─ row → SENT
                         │   (AFTER_COMMIT)                    │
                         └── send(key = productId) ──▶ [ stock.low  p0 | p1 | p2 ] ──▶ group "notification-service"
                                                                 │ failure after retries / poison pill
                                                                 ▼
                                                      [ stock.low.DLT  p0 | p1 | p2 ] ──▶ DLT listener: log + counter
```

## Decisions and why

| Decision | Why | What goes wrong otherwise |
|---|---|---|
| Alert on the **crossing** (`before > t && after <= t`) | One situation = one alert | "stock ≤ 5" alerts on every sale of an already-low item |
| Publish **after commit** | A rolled-back decrement must not alert | Optimistic-lock losers alert too (proved: 2 alerts instead of 1) |
| Relay **never throws** | The stock change is already committed | order-service would compensate a decrement that actually happened |
| `max.block.ms=3000` | `send()` runs on the request thread | A dead broker holds each stock request for 60s |
| **Key = productId** | Same key → same partition → per-product order | Round-robin: events for one product processed out of order |
| No `__TypeId__` header | Consumer picks its own type | Consumer coupled to product-service's Java package names |
| `acks=all` + idempotent producer | Durable write, no broker-side duplicates from retries | Ack before replication; retry duplicates |
| Consumer: **UNIQUE(event_id)** | At-least-once ⇒ duplicates will arrive | Lookup-then-insert race sends two emails |
| Row PENDING → email → SENT, **no @Transactional** | Don't hold a DB connection over SMTP | Same bug as HTTP-in-transaction in order-service |
| `ErrorHandlingDeserializer` | Bad bytes become a handleable error | Poison pill blocks the partition forever (proved: unrelated test failed) |
| Retry 1s/2s/4s → DLT; invalid events → DLT at once | Transient vs permanent failures | Infinite retries block the partition; retrying bad data is pointless |
| `idIsGroup = false` | Group must be `notification-service` | Listener id silently became the group (found on the live stack) |
| Own event DTO per service (no shared jar) | Independent deploys | A shared lib release couples both services |
| Tolerant reader + `schemaVersion` | Adding a field is non-breaking | Every new field forces a coordinated deploy |

**Accepted gap:** commit and Kafka send are still two writes. Process dies between them → the alert is
lost. Acceptable for an alert; for money, use the Outbox pattern (see INTERVIEW_STORIES Story 2).

## Run it

```bash
docker compose up -d --build           # whole stack, incl. kafka, kafka-ui, mailpit, notification-service
open http://localhost:8085             # Kafka UI: topics, partitions, consumer groups, lag
open http://localhost:8025             # Mailpit inbox
curl -s localhost:8083/actuator/prometheus | grep -E '^notifications_|records_lag_max'
```

Trigger an alert: create a product with stock 7, place an order for 3 through the gateway
(`POST :8090/api/orders`). One email arrives; ordering 1 more does not send another.
`GET :8090/api/notifications` (ADMIN token) lists what was sent.

### Kafka CLI cheat sheet (inside the broker container)

```bash
K="docker compose exec -T kafka /opt/kafka/bin"
$K/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic stock.low
$K/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic stock.low --from-beginning \
   --property print.key=true --property print.partition=true --property print.offset=true
$K/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service   # LAG column
$K/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic stock.low.DLT --from-beginning \
   --property print.headers=true                                                                          # why it died
```

What the CLI exercise showed: a keyed message always lands on the same partition (`p-1` → partition 1
every time); a consumer group's lag grows when messages arrive and nobody reads; a **new** group re-reads
the whole topic from offset 0. That last one is why renaming a group is dangerous — and why
the consumer must be idempotent: when the group was corrected on the live stack, it re-read everything,
and the database turned 2 re-deliveries into 0 extra emails.

### Replaying the DLT (manual, after fixing the cause)

Read the record and its `kafka_dlt-exception-*` headers, fix the cause, then re-publish the value to
`stock.low` with the same key. Idempotency makes a replay of an already-sent event harmless.

## Tests (all on real infrastructure via Testcontainers)

product-service
- `LowStockPolicyTest` — the crossing rule, 11 cases
- `ProductServiceTest` — which operations publish, with the full event contract
- `StockLowKafkaIntegrationTest` — real Postgres + real Kafka: one keyed JSON record; nothing above the
  threshold; **nothing after rollback**; **exactly one** alert from 10 racing threads with retries

notification-service
- `NotificationServiceTest` — idempotency, retry-after-failure, insert race, invalid events
- `StockLowConsumerIntegrationTest` — real Kafka + Postgres: happy path, duplicate, transient failure
  retried, persistent failure → DLT, poison pill doesn't block the partition, invalid → DLT without
  retries, v2 event with extra fields, consumer group name
- `EmailNotificationSenderTest` — real SMTP (Mailpit container), plus SMTP unreachable → exception
- `NotificationSecurityIntegrationTest` — ADMIN-only API, open Prometheus endpoint

Regression tests were verified by breaking each fix and watching them go red: send-inside-transaction,
no idempotency check, no `ErrorHandlingDeserializer`, `idIsGroup = true`.

## Deploy artifacts

- `k8s/07-kafka.yaml` (Kafka StatefulSet + Mailpit), `k8s/08-notification-service.yaml` (DB, Deployment ×2,
  Service, ServiceMonitor); `01-config.yaml` points product-service at `kafka:9092`. Schema-validated with
  kubeconform; not yet applied to minikube.
- `.github/workflows/deploy-notification-service.yml` — manual-only, like the others; `tests.yml` now
  runs the notification-service suite; ECR repo declared in `terraform/variables.tf` (manual apply).
