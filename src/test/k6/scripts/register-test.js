import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const users = new SharedArray('reg users', () => {
    return Array.from({ length: 100 }, (_, i) => {
        const idx = i + 1;
        return {
            email:    `K6TESTUSER${idx}@example.com`,
            username: `K6TESTUSER${idx}`,
            password: '1q2w3e4r!',              // 동일 비밀번호
            name:     `K6TESTUSER${idx}`,
            nickname: `K6TESTUSER${idx}`,
            phone:    `010-1234-${String(1000 + idx).slice(-4)}`,
            address:  'test address'
        };
    });
});

export const options = {
    scenarios: {
        default: {
            executor: 'per-vu-iterations',
            vus: users.length,
            iterations: 1,
        },
    },
};

export default function () {
    const user = users[__VU - 1];
    const url = 'http://localhost:8080/api/auth/register';

    // console.log(`VU #${__VU}: Registering ${user.username}`);

    const payload = {
        email:        user.email,
        username:     user.username,
        password:     user.password,
        name:         user.name,
        nickname:     user.nickname,
        phone:        user.phone,
        address:      user.address,
    };

    const res = http.post(url, payload);

    // console.log(`Response status: ${res.status}`);
    // console.log(`Response body: ${res.body}`);

    // ⑥ 결과 검증
    const ok = check(res, {
        'register status is 200': (r) => r.status === 200,
    });

    if (!ok) {
        console.error(`❌ VU #${__VU} failed to register ${user.email}`);
    }

    sleep(1);
}

// k6 run tests/k6/register-test.js