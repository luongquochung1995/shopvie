// k6: luồng mua hàng (đăng nhập → thêm giỏ → xem trước → đặt COD) với nhiều người mua (P7-5).
//
//   k6 run -e BASE_URL=http://localhost:8080 -e VARIANT_ID=1 loadtest/checkout.js
//
// Cần tắt hoặc nới rate limit đăng nhập/checkout khi chạy từ một IP (RATE_LIMIT_ENABLED=false) và SKU đủ tồn kho.
import http from 'k6/http';
import { check, sleep } from 'k6';

const API = `${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const json = { headers: { 'Content-Type': 'application/json' } };

export const options = {
  scenarios: {
    buyers: { executor: 'ramping-vus', startVUs: 0, stages: [{ duration: '30s', target: 50 }, { duration: '1m', target: 50 }, { duration: '15s', target: 0 }] },
  },
  thresholds: {
    'http_req_duration{name:checkout}': ['p(95)<800'],
    http_req_failed: ['rate<0.02'],
  },
};

export function setup() {
  // Mỗi VU một tài khoản + địa chỉ mặc định (tạo trước để phần đo chỉ gồm luồng mua)
  const users = [];
  for (let i = 0; i < 50; i++) {
    const email = `k6-${Date.now()}-${i}@example.com`;
    const token = http.post(`${API}/auth/register`, JSON.stringify({ email, password: 'secret123', fullName: 'K6 Buyer' }), json).json('data.accessToken');
    const auth = { headers: { ...json.headers, Authorization: `Bearer ${token}` } };
    const addressId = http.post(`${API}/me/addresses`, JSON.stringify({
      receiverName: 'K6', phone: '0901234567', provinceCode: '79', wardCode: '26740', detail: '1 Lê Lợi', isDefault: true,
    }), auth).json('data.id');
    users.push({ token, addressId });
  }
  return { users };
}

export default function (data) {
  const user = data.users[(__VU - 1) % data.users.length];
  const auth = { headers: { ...json.headers, Authorization: `Bearer ${user.token}` } };
  http.post(`${API}/cart/items`, JSON.stringify({ variantId: VARIANT_ID, quantity: 1 }), { ...auth, tags: { name: 'add-to-cart' } });
  const preview = http.post(`${API}/checkout/preview`, JSON.stringify({ addressId: user.addressId }), { ...auth, tags: { name: 'preview' } });
  check(preview, { 'preview 200': (r) => r.status === 200 });
  const placed = http.post(`${API}/checkout`, JSON.stringify({ addressId: user.addressId, paymentMethod: 'COD' }), { ...auth, tags: { name: 'checkout' } });
  check(placed, { 'order created': (r) => r.status === 201 });
  sleep(1);
}
