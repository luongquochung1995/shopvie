// k6: tải API đọc (P7-5). DoD Phase 7: p95 < 200ms ở 500 RPS.
//
//   k6 run -e BASE_URL=http://localhost:8080 loadtest/read-apis.js
//
// Trộn request giống người dùng thật: trang chủ (danh sách mới nhất / bán chạy), trang danh mục, chi tiết sản phẩm,
// tìm kiếm, gợi ý tìm kiếm, banner, flash sale.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API = `${BASE_URL}/api/v1`;

export const options = {
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 500),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 200,
      maxVUs: 1000,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

const KEYWORDS = ['ao thun', 'quan jeans', 'giay', 'tai nghe', 'son', 'dong ho', 'balo', 'vay'];

export function setup() {
  const categories = http.get(`${API}/categories`).json('data') || [];
  const categoryIds = categories.flatMap((c) => [c.id, ...(c.children || []).map((x) => x.id)]);
  const slugs = (http.get(`${API}/products?size=100&sort=soldCount,desc`).json('data.items') || []).map((p) => p.slug);
  return { categoryIds, slugs };
}

export default function (data) {
  const r = Math.random();
  let res;
  if (r < 0.25) {
    res = http.get(`${API}/products?size=24&sort=${Math.random() < 0.5 ? 'createdAt,desc' : 'soldCount,desc'}`, { tags: { name: 'home' } });
  } else if (r < 0.45) {
    const id = data.categoryIds[Math.floor(Math.random() * data.categoryIds.length)];
    res = http.get(`${API}/products?categoryId=${id}&size=30&sort=soldCount,desc`, { tags: { name: 'category' } });
  } else if (r < 0.75) {
    const slug = data.slugs[Math.floor(Math.random() * data.slugs.length)];
    res = http.get(`${API}/products/${slug}`, { tags: { name: 'detail' } });
  } else if (r < 0.88) {
    const q = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    res = http.get(`${API}/search?q=${encodeURIComponent(q)}&size=30`, { tags: { name: 'search' } });
  } else if (r < 0.95) {
    res = http.get(`${API}/search/suggest?q=${encodeURIComponent(KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)].slice(0, 4))}`, { tags: { name: 'suggest' } });
  } else {
    res = http.get(`${API}/${Math.random() < 0.5 ? 'banners' : 'flash-sales/current'}`, { tags: { name: 'home-widgets' } });
  }
  check(res, { 'status 200': (x) => x.status === 200 });
}
