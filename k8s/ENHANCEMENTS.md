# Kubernetes Enhancements — Backlog

The current manifests (`00`–`04`) are a working "make it run" baseline. This is the
list of changes that take it from *runs on minikube* to *production-grade*. Ordered
into a recommended arc; each item notes **what**, **why it matters**, and **how to
demo / verify** it locally.

> Status: planned, not started. Everything here runs on the existing minikube cluster
> with no AWS needed.

---

## Recommended arc (do in this order — each unlocks the next)

### 1. Resource requests & limits  ⭐ most important
- **What:** add `resources.requests` (CPU/memory the scheduler reserves) and
  `resources.limits` (the hard ceiling) to every container in `03-apps.yaml` and the DBs.
- **Why:** none are declared today. Without `requests` the scheduler can't bin-pack and
  may overcommit a node; without `limits` one pod can starve the others. Also sets the
  pod's **QoS class** (Guaranteed / Burstable / BestEffort) and is a **prerequisite for
  HPA** (autoscaling needs a request to compute % utilization against).
- **Demo/verify:** `kubectl describe pod <p>` shows the requests/limits and QoS class;
  `kubectl top pods` shows usage against them (needs metrics-server, see #2).

### 2. metrics-server + HorizontalPodAutoscaler (HPA)
- **What:** enable metrics-server (`minikube addons enable metrics-server`), then add an
  HPA targeting e.g. order-service: min 1 / max 5 replicas at 50% CPU.
- **Why:** today scaling is manual (`kubectl scale`). HPA scales automatically off load —
  the real elasticity story. This is the sequel to the manual 1→3 scaling demo.
- **Demo/verify:** drive load at order-service (hey/k6/loop of curls), then
  `kubectl get hpa -w` and `kubectl get pods -w` — watch replicas climb under load and
  scale back down when it stops.

### 3. Ingress
- **What:** `minikube addons enable ingress`, add an `Ingress` resource routing a host
  (e.g. `ecommerce.local`) to the gateway Service. Drop the port-forward.
- **Why:** today the only way in is `kubectl port-forward`. Ingress is the real front
  door — one stable URL, host/path routing, where TLS would terminate in prod.
- **Demo/verify:** map the host in `/etc/hosts` to `minikube ip`, then
  `curl http://ecommerce.local/api/products` — no port-forward.

### 4. startupProbe
- **What:** add a `startupProbe` to each Spring Boot container; tighten the
  `liveness`/`readiness` `initialDelaySeconds` back down.
- **Why:** Spring Boot boots slowly, so today we paper over it with large
  `initialDelaySeconds` on liveness — which *also* delays detection of a real hang. The
  correct pattern: a `startupProbe` gives slow startup generous time, and once it passes,
  liveness runs on a tight interval.
- **Demo/verify:** `kubectl describe pod` shows the startup gate; a slow-starting pod is
  no longer killed prematurely, but a hung running pod is caught fast.

---

## Production hygiene (do after the arc)

### Secret handling
- **What:** `01-config.yaml` stores the DB password in plaintext `stringData`, committed
  to git. Replace with **Sealed Secrets** or **External Secrets** (or, minimally,
  understand and document why the current form is wrong).
- **Why:** a Secret is only base64, not encrypted — anyone with repo or cluster read
  access sees the password. Real anti-pattern; the #1 thing a reviewer would flag.

### PodDisruptionBudget (PDB)
- **What:** add a PDB per app (e.g. `minAvailable: 1`).
- **Why:** during a node drain or voluntary disruption, k8s won't evict pods below the
  budget — keeps the service alive through rollouts/maintenance.

### NetworkPolicy
- **What:** default-deny, then allow only the calls that should exist (gateway→apps,
  order-service→product-service, order-service→monolith, apps→their DB Service).
- **Why:** today any pod can talk to any pod. Real clusters lock the east-west traffic
  down so a compromised pod can't reach everything.

### Image tagging (drop `:latest`)
- **What:** tag images by version/commit instead of `:latest` + `imagePullPolicy:
  IfNotPresent`.
- **Why:** `:latest` is mutable — you can't tell which build is running and can't roll
  back to a known-good tag. Versioned tags make rollouts and rollbacks deterministic.

### Misc cleanup
- product-service ConfigMap still carries dummy Kafka values (`KAFKA_*`) — leftover from
  the decommissioned sync; remove once confirmed unused.
- monolith uses a `tcpSocket` probe because it has no actuator endpoint; consider adding
  actuator for a real `/actuator/health` check like the other services.

---

## Quick reference — gap checklist

| Area | Today | Target |
|---|---|---|
| Resource requests/limits | none | set per container (#1) |
| Autoscaling | manual `kubectl scale` | HPA on CPU (#2) |
| External access | `port-forward` | Ingress (#3) |
| Slow-start handling | big `initialDelaySeconds` | `startupProbe` (#4) |
| Secrets | plaintext in git | Sealed/External Secrets |
| Disruption safety | none | PodDisruptionBudget |
| East-west traffic | wide open | NetworkPolicy default-deny |
| Image tags | `:latest` | versioned tags |
