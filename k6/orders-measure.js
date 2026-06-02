import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = 'http://Dockerized-Monolothic-Env-Prod.eba-vjuzhnjz.eu-north-1.elasticbeanstalk.com';

const ordersLatency = new Trend('orders_latency');
const successRate = new Rate('orders_success_rate');

export const options = {
    discardResponseBodies: true,
    stages: [
        { duration: '1m',  target: 50  },
        { duration: '1m',  target: 100  },
        { duration: '1m',  target: 100 }, // Hold at 1,000 to check for memory leaks
        { duration: '1m',  target: 0    },
    ],
    thresholds: {
        'orders_latency': ['p(95)<3000'],
        'orders_success_rate': ['rate>0.95'],
    },
};

export default function () {
    group('Orders Under Search Load', function () {
        const res = http.get(`${BASE_URL}/api/orders?page=0&size=10`);

        ordersLatency.add(res.timings.duration);
        successRate.add(res.status === 200);

        check(res, {
            'status is 200': (r) => r.status === 200,
            'status is NOT 5xx': (r) => r.status < 500,
        });
    });

    sleep(Math.random() * 1 + 0.5);
}
