// Drives the low-stock alert path under load: many products, each ordered until its stock
// crosses the threshold, so the run produces a burst of stock.low events.
//
// The point is NOT latency. It is what the CONSUMER does under a burst:
//   - consumer lag (how far notification-service falls behind)
//   - duplicate emails (idempotency holding or not)
//   - pod restarts / rebalances while it works
//
// Usage (BASE_URL = the gateway):
//   k6 run -e BASE_URL=http://localhost:8090 -e TOKEN=$TOKEN -e PRODUCTS=40 k6/low-stock-load.js
//
// Watch while it runs:
//   kubectl exec -n ecommerce kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh \
//     --bootstrap-server localhost:9092 --describe --group notification-service
//   curl -s localhost:8025/api/v1/messages | jq .total        # Mailpit (port-forwarded)

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const TOKEN = __ENV.TOKEN;
const PRODUCTS = Number(__ENV.PRODUCTS || 40);   // one product per VU iteration
const START_STOCK = 8;                           // threshold is 5 → crossed on the 2nd order
const VUS = Number(__ENV.VUS || 10);

const ordersLatency = new Trend('order_latency');
const crossings = new Counter('threshold_crossings');   // expected number of alerts
const failed = new Counter('failed_requests');

export const options = {
    scenarios: {
        burst: {
            executor: 'shared-iterations',
            vus: VUS,
            iterations: PRODUCTS,
            maxDuration: '5m',
        },
    },
    thresholds: {
        'failed_requests': ['count<1'],
        'order_latency': ['p(95)<3000'],
    },
};

const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${TOKEN}`,
};

export function setup() {
    if (!TOKEN) {
        throw new Error('TOKEN is required: mint an ADMIN JWT and pass -e TOKEN=...');
    }
    // One customer for the whole run — orders need a real customerId (snapshot at create time).
    const res = http.post(`${BASE_URL}/api/customers`, JSON.stringify({
        name: 'k6 Load Buyer',
        email: `k6-${Date.now()}@test.local`,
    }), { headers });
    if (res.status !== 201 && res.status !== 200) {
        throw new Error(`could not create the customer: ${res.status} ${res.body}`);
    }
    return { customerId: res.json('data.id') };
}

export default function (data) {
    const seq = exec.scenario.iterationInTest;

    // A fresh product per iteration, so every iteration owns its own threshold crossing
    // and the alerts can be counted exactly.
    const created = http.post(`${BASE_URL}/api/products`, JSON.stringify({
        name: `k6 Item ${seq}-${Date.now()}`,
        price: 19.99,
        stockQuantity: START_STOCK,
    }), { headers });
    if (!check(created, { 'product created': (r) => r.status === 201 || r.status === 200 })) {
        failed.add(1);
        return;
    }
    const productId = created.json('data.id');

    // 8 → 5 crosses the threshold (one alert), 5 → 2 must NOT alert again.
    for (const quantity of [3, 3]) {
        const order = http.post(`${BASE_URL}/api/orders`, JSON.stringify({
            customerId: data.customerId,
            items: [{ productId, quantity }],
        }), { headers });

        ordersLatency.add(order.timings.duration);
        if (!check(order, { 'order created': (r) => r.status === 201 })) {
            failed.add(1);
            return;
        }
    }
    crossings.add(1);
}
