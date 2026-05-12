import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const searchLatency = new Trend('search_latency');
const successRate = new Rate('search_success_rate');

const BASE_URL = 'http://pring-boot-0-docker-main.eba-vjuzhnjz.eu-north-1.elasticbeanstalk.com';
const queries = ['laptop', 'gaming', 'speaker', 'phone', 'monitor', 'shoes', 'coffee', 'tablet'];

export const options = {
    discardResponseBodies: true,
    stages: [
        { duration: '20s',  target: 20  }, // Step 1: Normal load
        { duration: '20s',  target: 40  }, // Step 2: High load
        { duration: '20s',  target: 40 }, // Hold at 1,000 to check for memory leaks
        { duration: '20s',  target: 0    }, // Cool down
    ],
    thresholds: {
        'search_latency': ['p(95)<3000'],     // Relaxed to 3s for extreme stress
        'search_success_rate': ['rate>0.95'], // Allow 5% error tolerance under extreme load
    },
};

export default function () {
    const query = queries[Math.floor(Math.random() * queries.length)];
    const minPrice = Math.floor(Math.random() * 500);
    const maxPrice = minPrice + Math.floor(Math.random() * 1000) + 100;

    const url = http.url`${BASE_URL}/api/products/search?query=${query}&minPrice=${minPrice}&maxPrice=${maxPrice}&inStock=true&page=0&size=20`;

    group('Extreme Stress Search', function () {
        const res = http.get(url);

        searchLatency.add(res.timings.duration);
        successRate.add(res.status === 200);

        check(res, {
            'status is 200': (r) => r.status === 200,
            'status is NOT 5xx': (r) => r.status < 500,
        });
    });

    // Increased sleep slightly to manage 1k VUs from a single machine
    sleep(Math.random() * 1 + 0.5);
}