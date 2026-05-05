import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = 'http://Spring-boot-0-env-docker.eba-vjuzhnjz.eu-north-1.elasticbeanstalk.com';

const ordersDuration = new Trend('orders_response_time', true);

export const options = {
    vus: 10,
    duration: '4m',
    thresholds: {
        orders_response_time: ['p(95)<1000'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/orders?page=0&size=10`);

    ordersDuration.add(res.timings.duration);

    check(res, {
        'orders status 200': (r) => r.status === 200,
        'orders response < 1s': (r) => r.timings.duration < 1000,
    });

    sleep(1);
}