import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: __ENV.VUS ? parseInt(__ENV.VUS) : 100,
    duration: __ENV.DURATION || '10s',
    gracefulStop: '0s',
};

export default function () {
    const baseUrl = __ENV.TARGET_URL || 'http://127.0.0.1:8080/users';
    
    // Generate a random ID for this virtual user's flow
    const userId = `user_${__VU}_${__ITER}`;
    const url = `${baseUrl}/${userId}`;

    const params = {
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
        },
    };

    // 1. Create (POST)
    const payloadCreate = JSON.stringify({
        id: userId,
        name: `Test User ${userId}`,
        email: `${userId}@example.com`
    });
    const resPost = http.post(baseUrl, payloadCreate, Object.assign({}, params, { tags: { name: 'POST /users' } }));
    check(resPost, { 'POST status is 200': (r) => r.status === 200 });

    // 2. Read (GET)
    const resGet = http.get(url, Object.assign({}, params, { tags: { name: 'GET /users/{id}' } }));
    check(resGet, { 'GET status is 200': (r) => r.status === 200 });

    // 3. Update (PUT)
    const payloadUpdate = JSON.stringify({
        id: userId,
        name: `Updated User ${userId}`,
        email: `updated_${userId}@example.com`
    });
    const resPut = http.put(url, payloadUpdate, Object.assign({}, params, { tags: { name: 'PUT /users/{id}' } }));
    check(resPut, { 'PUT status is 200': (r) => r.status === 200 });

    // 4. Delete (DELETE)
    const resDelete = http.del(url, null, Object.assign({}, params, { tags: { name: 'DELETE /users/{id}' } }));
    check(resDelete, { 'DELETE status is 200': (r) => r.status === 200 });
}
