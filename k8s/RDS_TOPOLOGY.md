# Externalizing Databases — the "RDS Topology"

A local exercise proving the production database shape: **stateless apps run inside
Kubernetes, every database lives outside the cluster.** In real production "outside"
means AWS RDS; here it's three Postgres containers on the Mac, reached the exact same
way — by a stable DNS name through a Kubernetes Service.

> The core idea: an app connects to a database by **name**, never by IP. Change what
> the name resolves to and the database can move (cluster → Mac → RDS, or primary →
> standby on failover) without the app ever noticing.

---

## Final topology

```
        ┌──────────── k8s cluster (minikube) ────────────┐
        │  gateway   monolith   product-svc   order-svc   │  ← only STATELESS apps
        └────┬───────────┬───────────┬────────────┬───────┘
             │           │           │            │
             │      postgres-     postgres-   postgres-
             │      monolith      products     orders      ← Services pointing OUTSIDE
             │           │           │            │
        ═════╪═══════════╪═══════════╪════════════╪═════  cluster boundary
                         ▼           ▼            ▼
                  ┌──────────┐ ┌──────────┐ ┌──────────┐
                  │rds-      │ │rds-      │ │rds-      │   ← your Mac
                  │monolith  │ │products  │ │orders    │     (= AWS RDS in prod)
                  │ :5440    │ │ :5441    │ │ :5432    │
                  └──────────┘ └──────────┘ └──────────┘
```

The cluster holds **zero database pods**. Each app still asks for `postgres-<name>:5432`;
only what that name resolves to changed.

---

## The swap, before vs after

```
   BEFORE                              AFTER
   ──────                              ─────
   order-svc                           order-svc
      │ DB_URL: postgres-orders:5432      │ DB_URL: postgres-orders:5432   ← identical
      ▼                                   ▼
  ┌─────────────────┐               ┌─────────────────────┐
  │ Service         │               │ Service             │
  │  → pod in cluster│              │  → host (your Mac)  │
  └────────┬────────┘               └──────────┬──────────┘
           ▼                                   ▼
   [ Postgres pod ]                   [ Postgres outside ]
   StatefulSet + PVC                  the cluster

   ONLY the Service definition changed. App config = untouched.
```

---

## Two ways to point a Service outside the cluster

Both are standard Kubernetes. Which you use depends on whether you need a port remap.

| Service used by | Primitive | Why |
|---|---|---|
| **order-service** | `ExternalName` → `host.minikube.internal` | Clean DNS alias, same port 5432. This is what you'd use for **real RDS** — each RDS instance has its own unique hostname. |
| **monolith / products** | `Service (no selector) + Endpoints` → `192.168.65.254:<port>` | Needed **only locally**: all three DBs share one host (the Mac), so they can't all use port 5432. The Endpoints object remaps the Service's 5432 → a distinct Mac port (5440/5441), keeping each app's `DB_URL` on 5432. |

### ExternalName (order-service)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-orders
  namespace: ecommerce
spec:
  type: ExternalName
  externalName: host.minikube.internal   # in prod: orders-db.xxx.eu-west-3.rds.amazonaws.com
```

### Service + Endpoints (monolith / products)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-monolith
  namespace: ecommerce
spec:
  ports:
    - port: 5432        # what the app dials
      targetPort: 5440  # the real port on the host
---
apiVersion: v1
kind: Endpoints          # same name as the Service
metadata:
  name: postgres-monolith
  namespace: ecommerce
subsets:
  - addresses:
      - ip: 192.168.65.254   # the Mac, as seen from the minikube node
    ports:
      - port: 5440
```

---

## How it maps to real production

```
   LOCAL DEMO                          REAL PRODUCTION (when AWS returns)
   ──────────                          ─────────────────────────────────
   ExternalName →                      ExternalName →
     host.minikube.internal              orders-db.xxx.eu-west-3.rds.amazonaws.com
            │                                  │
            ▼                                  ▼
   Postgres container                 ┌──────────── AWS RDS ────────────┐
   on your Mac                        │  primary  +  Multi-AZ standby   │
                                      │           +  read replica       │
                                      └─────────────────────────────────┘

   Same k8s manifest. You change ONE string: externalName.
```

In production all three DBs would be on RDS (consistent), each with its own
hostname on port 5432 — so all three use `ExternalName`; the Endpoints remap
trick is purely a local convenience for sharing one host.

---

## The design principle

```
   ┌──────────────────────────────────────────────────────┐
   │  STATELESS apps  → live IN the cluster (scale freely) │
   │  STATEFUL data   → lives OUTSIDE (managed RDS)         │
   │  Bridge between them → a Kubernetes Service (a name)   │
   └──────────────────────────────────────────────────────┘
```

Why databases stay out of the cluster in production:
- **Safety** — RDS gives automated backups, point-in-time recovery, and automatic
  Multi-AZ failover. A StatefulSet makes *you* the DBA.
- **Blast radius** — a bad `kubectl` command can't delete the data.
- **Convention** — keep stateless in k8s, keep state managed. Databases in k8s are
  possible but you sign up for the hard parts yourself.

---

## How this was built (runbook)

1. Start external Postgres (one container per DB) on the Mac.
2. **Migrate data** with `pg_dump | psql` from the in-cluster pod into the external
   container (preserves customers/products/categories; orders started fresh).
3. Delete the in-cluster `StatefulSet` + `Service` (PVCs kept → rollback possible).
4. Recreate the Service to point outside (`ExternalName`, or `Service + Endpoints`).
5. `kubectl rollout restart` the app so it reconnects.
6. Verify through the gateway — full CRUD + cross-service order create.

### Verification result
- Cluster holds **zero** Postgres pods.
- All three DB Services resolve outside the cluster.
- Full API sweep green, including the **cross-service order create**: order-service
  called the monolith (customer-name snapshot) and product-service (price snapshot),
  both now on external DBs, and wrote the order to its own external DB.

> **Two mutually-exclusive DB layers.** `02-postgres.yaml` describes the all-in-cluster
> setup (StatefulSets); `04-external-db.yaml` describes this externalized "RDS" topology
> (Services pointing outside). Apply **one or the other** — they share the same Service
> names, so they collide. `04-external-db.yaml` is the reference target for production.
