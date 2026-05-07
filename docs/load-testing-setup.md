# Load Testing Setup — k6 + Grafana

This guide explains how to run stress tests and visualize results in Grafana.
Everything runs on an AWS EC2 instance — your local machine only needs a browser and a terminal.

---

## What You Need Before Starting

- The EC2 instance running (check AWS Console → EC2 → Instances → status = Running)
- The PEM key file at `k6/ec2-k6-key-pair.pem` on your local machine
- The EB app deployed and running

---

## Step 1 — SSH Into the EC2 Instance

Open a terminal on your local machine and run:

```bash
ssh -i k6/ec2-k6-key-pair.pem ec2-user@13.51.195.83
```

> If it says "bad permissions", run this first:
> ```bash
> chmod 400 k6/ec2-k6-key-pair.pem
> ```

---

## Step 2 — Start InfluxDB and Grafana on EC2

Every time the EC2 instance restarts, you need to start these services.
Run these commands inside the SSH session:

```bash
sudo systemctl start influxdb
sudo systemctl start grafana-server
```

To verify they are running:
```bash
sudo systemctl status influxdb
sudo systemctl status grafana-server
```

You should see `active (running)` in green for both.

---

## Step 3 — Copy the Test Scripts to EC2

Run this from your **local machine** terminal (not the SSH session):

```bash
scp -i k6/ec2-k6-key-pair.pem k6/search-stress.js k6/orders-measure.js ec2-user@13.51.195.83:~/k6/
```

> You only need to do this once, or when you change the scripts.

---

## Step 4 — Open Grafana in Your Browser

Open this URL in your browser:

```
http://13.51.195.83:3000
```

Login with:
- **Username**: admin
- **Password**: the one you set on first login

---

## Step 5 — Open the k6 Dashboard

1. Left sidebar → **Dashboards**
2. Find and click **k6 Load Testing Results** (dashboard ID 2587)
3. Top right corner — set time range to **Last 15 minutes**
4. Leave this tab open — it will update live during the test

---

## Step 6 — Run the Stress Tests

You need **two SSH sessions** open at the same time.

**Open a second terminal** on your local machine and SSH again:
```bash
ssh -i k6/ec2-k6-key-pair.pem ec2-user@13.51.195.83
```

Now run one command in each session:

**SSH Session 1 — hammers the search endpoint:**
```bash
k6 run --out influxdb=http://localhost:8086/k6 ~/k6/search-stress.js
```

**SSH Session 2 — measures orders response time while search is under load:**
```bash
k6 run --out influxdb=http://localhost:8086/k6 ~/k6/orders-measure.js
```

Both tests run for ~7 minutes total.

---

## Step 7 — Watch Grafana

Go back to your browser at `http://13.51.195.83:3000`.

You will see:
- **search_latency** — goes up as more users hit search
- **orders_latency** — also goes up even though orders have nothing to do with search

This proves the monolith problem: one slow feature drags down the whole app.

> If you see no data, check the time range in the top right — set it to **Last 15 minutes**.

---

## Step 8 — Take a Screenshot

When the test is running and the graphs show degradation, take a screenshot.
This is your **before** proof — the monolith bottleneck.

After the microservices migration, you will run the same test and take an **after** screenshot showing orders staying fast.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| SSH says "bad permissions" | Run `chmod 400 k6/ec2-k6-key-pair.pem` |
| Grafana not loading | Run `sudo systemctl start grafana-server` on EC2 |
| k6 says "connection refused" on InfluxDB | Run `sudo systemctl start influxdb` on EC2 |
| Grafana shows no data | Check time range — set to Last 15 minutes |
| k6 script not found | Make sure you copied scripts to `~/k6/` folder on EC2 |
