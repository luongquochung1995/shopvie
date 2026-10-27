// k6 load test cho flash sale (P5-5).
// Chuẩn bị: một phiên flash sale đang chạy (admin tạo qua POST /api/v1/admin/flash-sales) và file tokens.json chứa
// mảng access token của người mua (mỗi người đã có địa chỉ mặc định).
//
//   k6 run -e BASE_URL=http://localhost:8080 -e ITEM_ID=1 -e QUOTA=100 loadtest/flash-sale.js
//
// Kỳ vọng: đúng QUOTA request trả 202, phần còn lại 409 FLASH_SALE_SOLD_OUT; p95 < 500ms.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ITEM_ID = __ENV.ITEM_ID;
const QUOTA = Number(__ENV.QUOTA || 100);
const tokens = new SharedArray('tokens', () => JSON.parse(open(__ENV.TOKENS || './tokens.json')));

const accepted = new Counter('flash_sale_accepted');
const soldOut = new Counter('flash_sale_sold_out');
const other = new Counter('flash_sale_other');

export const options = {
  scenarios: {
    rush: {
      executor: 'shared-iterations',
      vus: Math.min(tokens.length, 500),
      iterations: tokens.length,
      maxDuration: '1m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    flash_sale_accepted: [`count==${QUOTA}`],
  },
};

export default function () {
  const token = tokens[__ITER % tokens.length];
  const res = http.post(`${BASE_URL}/api/v1/flash-sales/items/${ITEM_ID}/purchase`, JSON.stringify({ quantity: 1 }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  });
  if (res.status === 202) {
    accepted.add(1);
  } else if (res.status === 409) {
    soldOut.add(1);
  } else {
    other.add(1);
  }
  check(res, { 'accepted or sold out': (r) => r.status === 202 || r.status === 409 });
}
