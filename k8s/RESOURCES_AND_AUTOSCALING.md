# Resource Requests/Limits + Autoscaling (HPA)

How product-service scales itself under load, and why every service declares a CPU/memory
budget. This is the production-grade version of the manual scaling demos — instead of
`kubectl scale`, the cluster decides.

> **Declarative, not imperative.** These live in `03-apps.yaml` (resources) and
> `05-hpa.yaml` (the autoscaler) — committed to git, applied with `kubectl apply`. They are
> NOT set with one-off `kubectl set resources` / `kubectl autoscale` commands. Live commands
> change the running cluster but not the repo, so they vanish on a rebuild and drift from
> source control. The YAML is the source of truth.

---

## 1. Resource requests & limits (in `03-apps.yaml`)

Every container now declares:
```yaml
resources:
  requests: { cpu: 250m, memory: 384Mi }   # reserved for the pod
  limits:   { cpu: "1",  memory: 768Mi }    # hard ceiling
```

- **request** = what the scheduler *reserves* for the pod. It guarantees the pod that much
  and uses it to decide which node has room. It is also the **denominator the HPA measures
  against** (see below).
- **limit** = the hard ceiling. CPU over the limit is throttled; memory over the limit is an
  OOM-kill.
- Setting **both** (with request < limit) gives the pod a **Burstable** QoS class — it gets
  its request guaranteed and may burst up to the limit if the node has spare capacity.

**Why it matters:** without requests, the scheduler is flying blind (can overcommit a node)
and the HPA cannot compute utilization at all. This was the root cause of the crash loops on
the under-sized cluster — pods with no budget, starved of CPU, failing their health checks.

`250m` = a quarter of a CPU core; `m` = millicores. `384Mi` = 384 mebibytes.

---

## 2. HorizontalPodAutoscaler (in `05-hpa.yaml`)

```yaml
minReplicas: 2          # never a single point of failure (HA)
maxReplicas: 6          # cap blast radius / cost
metric: cpu @ 70%       # keep each pod near 70% of its CPU *request*
```

### How it decides
```
desiredReplicas = ceil( currentReplicas × currentCPU% / targetCPU% )
```
Example seen live: 2 pods at 120% CPU, target 70% → `ceil(2 × 120/70) = ceil(3.4) = 4` pods.
The percentage is **of the request** (250m), so "70%" means ~175m per pod.

### Production choices in this file
| Setting | Value | Why |
|---|---|---|
| `minReplicas` | **2** | High availability — survive losing one pod/node without an outage. |
| `maxReplicas` | **6** | Cap how far it can grow (cost + cluster capacity). |
| target CPU | **70%** | Headroom — scale *before* pods are maxed, not after. |
| scaleUp stabilization | **0s** | React to load fast. |
| scaleDown stabilization | **300s** | Wait 5 min of low load before shrinking — avoids "flapping". |

### Asymmetric behavior (the key idea)
```
load spikes → scale UP fast      (eager: protect the user)
load drops  → scale DOWN slowly  (cautious: don't thrash on a brief dip)
```

---

## 3. Prerequisite: metrics-server

The HPA reads live CPU from **metrics-server** (`minikube addons enable metrics-server`;
in a managed cluster it's built in). `kubectl top pods` working is the proof it's healthy.
No metrics-server → the HPA shows `<unknown>` and cannot scale.

---

## 4. Why only product-service has an HPA

It's the **read-heavy** service (catalog browsing + full-text search) — the one that melted
under load in the monolith and the reason it was extracted. order-service is write- and
consistency-sensitive (you must read your own writes), so it's a poorer autoscaling target;
the monolith is low-traffic. Scaling is a per-service decision driven by its traffic shape —
the same judgement as deciding which service needs Kafka or read replicas.

---

## 5. Apply / verify

```bash
kubectl apply -f 03-apps.yaml -f 05-hpa.yaml
kubectl get hpa -n ecommerce            # TARGETS shows cpu: x%/70%, REPLICAS the count
kubectl top pods -n ecommerce           # live CPU/memory (needs metrics-server)
```
Drive load at `/api/products/search` and watch `REPLICAS` climb from 2 toward 6, then settle
back to 2 about five minutes after the load stops.

> Maps to production unchanged: same HPA on EKS, with metrics-server already present and the
> cluster autoscaler adding nodes if the 6 replicas don't fit on the current ones.
