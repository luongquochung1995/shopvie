// Đo tải nhanh bằng Node (không cần cài k6): gửi request với tốc độ cố định (open model) trong một khoảng thời gian,
// in độ trễ p50/p95/p99 theo nhóm. Tải trộn giống loadtest/read-apis.js.
//
//   node loadtest/node-bench.mjs --base http://localhost:8080 --rps 500 --seconds 30
//
// Lưu ý: chạy cùng máy với backend nên kết quả chỉ mang tính tham khảo (client và server tranh CPU).
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc), []));
const API = `${args.base ?? 'http://localhost:8080'}/api/v1`;
const RPS = Number(args.rps ?? 500);
const SECONDS = Number(args.seconds ?? 30);
const KEYWORDS = ['ao thun', 'quan jeans', 'giay', 'tai nghe', 'son', 'dong ho', 'balo', 'vay'];

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const getJson = async (path) => (await (await fetch(API + path)).json()).data;

const categories = await getJson('/categories');
const categoryIds = categories.flatMap((c) => [c.id, ...c.children.map((x) => x.id)]);
const slugs = (await getJson('/products?size=100&sort=soldCount,desc')).items.map((p) => p.slug);

function nextRequest() {
  const r = Math.random();
  if (r < 0.25) return ['home', `/products?size=24&sort=${Math.random() < 0.5 ? 'createdAt,desc' : 'soldCount,desc'}`];
  if (r < 0.45) return ['category', `/products?categoryId=${pick(categoryIds)}&size=30&sort=soldCount,desc`];
  if (r < 0.75) return ['detail', `/products/${pick(slugs)}`];
  if (r < 0.88) return ['search', `/search?q=${encodeURIComponent(pick(KEYWORDS))}&size=30`];
  if (r < 0.95) return ['suggest', `/search/suggest?q=${encodeURIComponent(pick(KEYWORDS).slice(0, 4))}`];
  return ['widgets', Math.random() < 0.5 ? '/banners' : '/flash-sales/current'];
}

const samples = new Map();
let errors = 0;
const inflight = [];
const started = performance.now();
const total = RPS * SECONDS;
for (let i = 0; i < total; i++) {
  // Lịch gửi cố định theo thời gian (không chờ response) — tránh "coordinated omission"
  const due = started + (i * 1000) / RPS;
  const wait = due - performance.now();
  if (wait > 0) await new Promise((r) => setTimeout(r, wait));
  const [name, path] = nextRequest();
  const t0 = performance.now();
  inflight.push(
    fetch(API + path)
      .then(async (res) => {
        await res.arrayBuffer();
        if (res.status !== 200) errors++;
      })
      .catch(() => errors++)
      .finally(() => {
        const list = samples.get(name) ?? [];
        list.push(performance.now() - t0);
        samples.set(name, list);
      }),
  );
}
await Promise.all(inflight);
const elapsed = (performance.now() - started) / 1000;

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
const row = (name, list) => {
  const s = [...list].sort((a, b) => a - b);
  return `${name.padEnd(10)} n=${String(s.length).padStart(6)}  p50=${pct(s, 50).toFixed(1).padStart(7)}ms  p95=${pct(s, 95).toFixed(1).padStart(7)}ms  p99=${pct(s, 99).toFixed(1).padStart(7)}ms`;
};
console.log(`Target ${RPS} RPS x ${SECONDS}s → achieved ${(total / elapsed).toFixed(0)} RPS, errors=${errors}`);
for (const [name, list] of samples) console.log(row(name, list));
console.log(row('ALL', [...samples.values()].flat()));
