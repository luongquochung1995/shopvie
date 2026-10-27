import { SearchHit, Suggestion } from '../core/api/api.models';
import { fail, MockResult, ok } from './mock-commerce';
import { MockDb } from './mock-db';

/** Tìm kiếm của backend giả: không phân biệt dấu/hoa thường (như analyzer vi_folded), mọi từ khóa phải khớp. */

export function fold(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
}

export function search(d: MockDb, query: URLSearchParams): MockResult {
  const q = (query.get('q') ?? '').trim();
  const tokens = fold(q).split(/\s+/).filter(Boolean);
  const num = (k: string) => (query.get(k) ? Number(query.get(k)) : undefined);
  const minPrice = num('minPrice');
  const maxPrice = num('maxPrice');
  if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
    return fail(400, 'VALIDATION_ERROR', [{ field: 'minPrice', message: 'must be less than or equal to maxPrice' }]);
  }
  const page = Math.max(0, num('page') ?? 0);
  const size = Math.min(60, Math.max(1, num('size') ?? 20));
  const sort = query.get('sort') ?? 'RELEVANCE';

  let items = d.catalog.products
    .filter((p) => d.isPurchasable(p))
    .map((p) => ({ p, folded: fold(p.name) }))
    .filter(({ folded }) => tokens.every((t) => folded.includes(t)));
  if (minPrice !== undefined) items = items.filter(({ p }) => p.maxPrice >= minPrice);
  if (maxPrice !== undefined) items = items.filter(({ p }) => p.minPrice <= maxPrice);
  const score = (folded: string) => (folded.startsWith(tokens.join(' ')) ? 2 : folded.includes(tokens.join(' ')) ? 1 : 0);
  items.sort((a, b) => {
    switch (sort) {
      case 'BEST_SELLING': return b.p.soldCount - a.p.soldCount;
      case 'NEWEST': return b.p.createdAt.localeCompare(a.p.createdAt);
      case 'PRICE_ASC': return a.p.minPrice - b.p.minPrice;
      case 'PRICE_DESC': return b.p.minPrice - a.p.minPrice;
      default: return score(b.folded) - score(a.folded) || b.p.soldCount - a.p.soldCount;
    }
  });
  const hits: SearchHit[] = items.slice(page * size, page * size + size).map(({ p }) => ({
    id: p.id, name: p.name, highlightedName: highlight(p.name, tokens), slug: p.slug, thumbnailUrl: p.images[0] ?? null,
    minPrice: p.minPrice, maxPrice: p.maxPrice, soldCount: p.soldCount, ratingAvg: p.ratingAvg, ratingCount: p.ratingCount,
    shopName: p.shop.name,
  }));
  return ok({ items: hits, page, size, totalItems: items.length, totalPages: Math.ceil(items.length / size) });
}

export function suggest(d: MockDb, query: URLSearchParams): MockResult {
  const tokens = fold((query.get('q') ?? '').trim()).split(/\s+/).filter(Boolean);
  if (!tokens.join('').length || tokens.join(' ').length < 2) return ok([]);
  const list: Suggestion[] = d.catalog.products
    .filter((p) => d.isPurchasable(p))
    .filter((p) => {
      const words = fold(p.name).split(/\s+/);
      return tokens.every((t, i) => (i === tokens.length - 1 ? words.some((w) => w.startsWith(t)) : words.includes(t)));
    })
    .sort((a, b) => b.soldCount - a.soldCount)
    .slice(0, 8)
    .map((p) => ({ name: p.name, slug: p.slug }));
  return ok(list);
}

/** Bọc phần khớp trong <mark> (escape HTML trước), null nếu tên không khớp. */
function highlight(name: string, tokens: string[]): string | null {
  const folded = fold(name);
  const marks = new Array<boolean>(name.length).fill(false);
  let any = false;
  for (const t of tokens) {
    let i = folded.indexOf(t);
    while (i >= 0) {
      any = true;
      for (let k = i; k < i + t.length; k++) marks[k] = true;
      i = folded.indexOf(t, i + t.length);
    }
  }
  if (!any) return null;
  const esc = (c: string) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c;
  let out = '';
  for (let i = 0; i < name.length; i++) {
    if (marks[i] && (i === 0 || !marks[i - 1])) out += '<mark>';
    out += esc(name[i]);
    if (marks[i] && (i === name.length - 1 || !marks[i + 1])) out += '</mark>';
  }
  return out;
}
