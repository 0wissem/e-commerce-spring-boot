# Backstage Developer Portal — Runbook

A [Backstage](https://backstage.io) software catalog for the e-commerce platform: one
browsable place listing every service, who owns it, how the services depend on each
other, and their live OpenAPI docs.

The catalog **descriptors** live in this repo (`catalog-info.yaml` + one per service).
The Backstage **app itself** lives in a sibling directory, `../ecommerce-portal`, kept
out of this repo (it's a large generated Node project).

---

## What's in the catalog

| Entity | Kind | Notes |
|---|---|---|
| `e-commerce` | Domain | the whole platform |
| `ecommerce-platform` | System | the 4 deployable services |
| `monolith` | Component | identity service (users + JWT) — provides `monolith-api` |
| `product-service` | Component | products/categories — provides `products-api` |
| `order-service` | Component | orders — provides `orders-api`, **depends on** product + monolith |
| `gateway` | Component | routing — depends on all three |
| `backend-team` / `wissem` | Group / User | ownership |

Each `API` entity pulls its **live** OpenAPI spec from the running service via
`$text: http://<service>:<port>/v3/api-docs` (docker-compose service names).

---

## Run it (production build — fast, single port 7007)

The services must be up on the `spring-boot-0_default` docker network first:

```bash
# 1. bring the backend services up (from this repo)
docker compose up -d monolith product-service order-service

# 2. build the portal once (from ../ecommerce-portal)
cd ../ecommerce-portal
yarn install
yarn build:all

# 3. serve it — one container, one port, attached to the services' network,
#    with this repo mounted read-only at /repo so it can read catalog-info.yaml
docker run -d --name backstage-prod --init \
  -p 7007:7007 \
  --network spring-boot-0_default \
  -v "$PWD:/app" \
  -v "$PWD/../spring-boot-0:/repo:ro" \
  -w /app node:22 \
  sh -c "corepack enable && yarn workspace backend start --config /app/app-config.yaml"
```

Then open **http://localhost:7007** → **Enter** (Guest) →
**Catalog** → filter **Kind = API** → **products-api** → **Definition** tab renders the Swagger.

---

## Why the production build (the lesson)

`yarn start` runs a **dev server** that compiles chunks **on demand** (lazy) — 25–30s per
page inside a resource-limited container, so the browser times out ("loading" everywhere).
`yarn build:all` compiles **everything ahead of time** into static files that the backend
serves directly, on a **single port (7007)** — pages load in ~30ms.

Same distinction as any frontend: **dev = editing comfort, prod = optimized files served as-is.**

---

## Gotchas already solved

- **`node:22 --init`** — the yarn install fails on node:20 ("empty event loop", exit 42); node:22 + tini (`--init`) fixes it.
- **OpenAPI URLs** must use the **docker service names** (`http://monolith:8080/...`), not `localhost` (that's the container) — and be allow-listed under `backend.reading.allow` in `app-config.yaml`.
- **Guest login** needs `auth.providers.guest.dangerouslyAllowOutsideDevelopment: true` (the container's `NODE_ENV` isn't "development").
- If specs 404, the springdoc endpoint is slow on first generation (~15–25s) — just refresh.
