# Manual EKS Deploy — Runbook & Journal

> **Purpose (read this first).** Deploy the whole stack to EKS **by hand** (AWS Console +
> `aws` CLI — deliberately NOT `eksctl`/Terraform), recording every value and every
> annoyance as you go. Then tear it all down. In round two you'll rebuild the *same thing*
> with Terraform — and this journal becomes your spec + your evidence for "what did IaC
> actually buy me."
>
> **How to use it:** work top to bottom. For each step, do the action, tick the box, and
> fill the **Journal** line with the value/ARN it produced and anything that bit you (errors,
> waits, retries). The annoyance notes are the most valuable part — they're your Terraform
> motivation.
>
> ⚠️ **The meter starts in Phase 4 (the EKS control plane).** Everything before that is
> ~free. Do Phases 0–3 unhurried; do Phases 4–9 in one focused session; **do Phase 10
> (teardown) the SAME day.** An idle cluster left overnight is exactly what drained the old
> account.

---

## Decisions for this first pass (flip later if you want)

| Decision | This pass | Why / the "real" version later |
|---|---|---|
| Databases | **In-cluster Postgres** (`02-postgres.yaml`) | Simplest, Flyway re-seeds on boot. RDS is the production-grade follow-up. |
| VPC | **Default VPC** | Public subnets, **no NAT gateway** = a real cost saver. Custom VPC + private subnets is the prod shape (and a NAT gateway cost). |
| External access | **A) `Service type=LoadBalancer` first**, then **B) ALB Ingress Controller** | A proves end-to-end fast; B is the actual cloud-Ingress lesson. |
| Tooling | **Console + `aws` CLI** | `eksctl` hides VPC/IAM (it's CloudFormation under the hood) — which defeats the pain→Terraform comparison. |
| Node arch | **amd64 / x86_64** | EKS managed nodes default to amd64. Build/push amd64 images (you hit arm64 issues locally before). |

---

## Running cost tally (fill as you create billable things)

| Resource | Started (time) | Deleted (time) | Notes |
|---|---|---|---|
| EKS control plane | | | ~$0.10/hr |
| Node group (2× t3.medium) | | | ~$0.08/hr total-ish |
| LoadBalancer / ALB | | | ~$0.025/hr + LCU |
| EBS volumes (if PVCs) | | | per GB-month |
| **NAT gateway (watch for this!)** | | | only if NOT using default VPC |

---

## Phase 0 — Prerequisites & safety  *(no cost)*

- [ ] **Root login works**, then create an **IAM admin user** (don't use root day-to-day).
  - Journal (account ID): `____________`  |  IAM user: `____________`
- [ ] **Set a Budget + billing alarm BEFORE anything else** (Billing → Budgets → $1 / $10 / $50 thresholds with email). This is the seatbelt.
  - Journal (budget set? alert email?): `____________`
- [ ] Install local tools: `aws` CLI v2, `kubectl`, `helm`. (Skip `eksctl` on purpose.)
  - Journal (versions): `aws ____  kubectl ____  helm ____`
- [ ] `aws configure` with the IAM user's access key. Pick **one region and stick to it** — every resource must share it.
  - Journal (region): `____________`
- [ ] Verify: `aws sts get-caller-identity` returns your account/user.
  - Journal: `____________`

## Phase 1 — Images → ECR  *(tiny cost: storage)*

- [ ] Create 4 ECR repos: `monolith`, `product-service`, `order-service`, `gateway`.
  - `aws ecr create-repository --repository-name <name> --region <region>`
  - Journal (4 repo URIs): `____________`
- [ ] Authenticate Docker to ECR:
  - `aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <acct>.dkr.ecr.<region>.amazonaws.com`
- [ ] Build **amd64** images, tag with the ECR URI, push each (monolith, product-service, order-service, gateway).
  - `docker build --platform linux/amd64 -t <ecr-uri>/<name>:v1 <path>`  → `docker push <ecr-uri>/<name>:v1`
  - Journal (pushed tags): `____________`  |  Gotchas: `____________`

## Phase 2 — Networking  *(no cost with default VPC)*

- [ ] Find the **default VPC** and note ≥2 subnet IDs in different AZs (EKS needs 2 AZs).
  - `aws ec2 describe-subnets --filters Name=default-for-az,Values=true --query 'Subnets[].{id:SubnetId,az:AvailabilityZone}'`
  - Journal (VPC ID): `____________`  |  Subnets: `____________`

## Phase 3 — IAM roles  *(no cost)*

- [ ] **Cluster role** — trust `eks.amazonaws.com`, attach `AmazonEKSClusterPolicy`.
  - Journal (role ARN): `____________`
- [ ] **Node role** — trust `ec2.amazonaws.com`, attach `AmazonEKSWorkerNodePolicy`, `AmazonEC2ContainerRegistryReadOnly`, `AmazonEKS_CNI_Policy`.
  - Journal (role ARN): `____________`
- [ ] *(Note for Phase 8B)* The ALB controller will need its own IAM policy + an OIDC/IRSA role — created later.

## Phase 4 — EKS control plane  *(💸 METER STARTS — log the time)*

- [ ] Create the cluster (Console: EKS → Add cluster, or `aws eks create-cluster` with the cluster role + subnets). **Start time:** `______`
  - Journal (cluster name): `____________`
- [ ] Wait ~10–15 min until **ACTIVE**. (Note how long — this wait is part of the Terraform comparison.)
  - Journal (actual wait): `______`
- [ ] Wire kubectl: `aws eks update-kubeconfig --name <cluster> --region <region>`
- [ ] Verify: `kubectl get svc` (should show the `kubernetes` service).
  - Journal: `____________`

## Phase 5 — Managed node group  *(💸 EC2 cost — log time)*

- [ ] Create a managed node group: 2× **t3.medium**, the node role, the same subnets. **Start time:** `______`
  - Journal (node group name): `____________`
- [ ] Wait until ACTIVE, then `kubectl get nodes` → 2 nodes `Ready`.
  - Journal: `____________`  |  Wait: `______`

## Phase 6 — Cluster add-ons

- [ ] **metrics-server** (HPA needs it): `kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml`
  - Journal: `____________`
- [ ] *(Only if DBs use PVCs)* Enable the **EBS CSI driver** add-on (needs an IRSA role). If you switch the DB manifests to `emptyDir`, you can skip this (data is ephemeral — fine, Flyway re-seeds).
  - Journal (PVC or emptyDir?): `____________`

## Phase 7 — Deploy the app

- [ ] `kubectl apply -f k8s/00-namespace.yaml`
- [ ] **Edit `k8s/03-apps.yaml`: replace local image names (`spring-boot-0-*:latest`) with your ECR URIs from Phase 1.** ← classic gotcha, the cluster can't pull local images.
  - Journal (did image refs get updated?): `____________`
- [ ] Apply config + DBs + apps: `01-config.yaml`, `02-postgres.yaml` (in-cluster DBs), `03-apps.yaml`, `05-hpa.yaml`.
  - Journal: `____________`
- [ ] Watch pods: `kubectl get pods -n ecommerce -w` until all `Running`. Note any `ImagePullBackOff` (image/arch/permission), `CrashLoopBackOff` (DB connection/config).
  - Journal (issues + fixes): `____________`

## Phase 8 — External access

### A) Quick win — Service type=LoadBalancer  *(💸 a LoadBalancer — log time)*
- [ ] Expose the **gateway** as `type=LoadBalancer` (AWS auto-provisions an NLB). **Start time:** `______`
  - Journal (external hostname): `____________`
- [ ] `curl http://<external-host>/api/products` → confirm it routes through the gateway.
  - Journal: `____________`

### B) The real lesson — ALB Ingress Controller  *(optional this pass)*
- [ ] Create OIDC provider for the cluster (IRSA prerequisite).
- [ ] Create the **AWSLoadBalancerControllerIAMPolicy** + IRSA service account.
- [ ] `helm install` the AWS Load Balancer Controller into `kube-system`.
- [ ] Create an `Ingress` (alb annotations) → gateway Service. AWS provisions an **ALB**.
  - Journal (ALB DNS name): `____________`
- [ ] `curl http://<alb-dns>/api/products`. Compare the effort vs Phase 8A. **This is the prod cloud-Ingress lesson.**
  - Journal (effort notes): `____________`

## Phase 9 — Verify the stack

- [ ] Full API smoke sweep through the external URL (the endpoint-by-endpoint sweep you always run): products, categories, customers, orders, and an order *create* (cross-service snapshot).
  - Journal (all green?): `____________`
- [ ] Screenshot the running pods + the working URL for your portfolio/daily report.

---

## Phase 10 — TEARDOWN  *(💸 DO NOT SKIP — stops the meter)*

Delete in reverse-dependency order. Tick each and confirm in the Billing console afterward.

- [ ] Delete the **Ingress** (Phase 8B) → confirm the **ALB** is gone.
- [ ] Uninstall the **LB controller** (helm) + delete its IAM policy/role + OIDC provider.
- [ ] Delete the **Service type=LoadBalancer** (Phase 8A) → confirm the **NLB** is gone.
- [ ] `kubectl delete -f k8s/03-apps.yaml -f k8s/02-postgres.yaml -f k8s/01-config.yaml -f k8s/05-hpa.yaml -f k8s/00-namespace.yaml`
- [ ] Delete the **node group** (wait until gone).
- [ ] Delete the **EKS cluster**. **Control-plane stop time:** `______`
- [ ] Delete **IAM roles** (cluster, node), **ECR repos** (or keep — storage is cheap).
- [ ] **Hunt for leftovers** (the silent billers): orphaned **EBS volumes**, leftover **Load Balancers**, stray **ENIs**, and **NAT gateway** (only if you didn't use the default VPC).
- [ ] Open **Billing → Cost Explorer / Bills**: confirm nothing is still accruing.
  - Journal (total cost for the session): `$________`
- [ ] **Journal the teardown pain** — what was tedious, what order errors you hit, what you almost forgot. ← **This is the single best argument for `terraform destroy`.** Write it while it's fresh.

---

## After teardown — the comparison (fill in for round two)

- Manual deploy took: **____ hours**, **____ console screens / commands**, **____ mistakes/retries**.
- Easiest to forget on teardown: `____________`
- The single most tedious step: `____________`
- **Prediction:** Terraform will collapse all of the above into `terraform apply` (~__ min) and `terraform destroy` (~__ min), reproducible every time. Verify this in round two.
