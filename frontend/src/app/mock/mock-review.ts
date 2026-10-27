import { Review, SellerReview } from '../core/api/api.models';
import { MockUser } from './mock-data';
import { fail, MockResult, ok } from './mock-commerce';
import { MockDb, MockReview } from './mock-db';

/** Đánh giá của backend giả: chỉ món của đơn COMPLETED, 1 lần/món; cập nhật điểm sản phẩm; seller phản hồi 1 lần. */

const MAX_CONTENT = 1000;

export function productReviews(d: MockDb, slug: string, query: URLSearchParams): MockResult {
  const product = d.catalog.products.find((p) => p.slug === slug);
  if (!product || !d.isPurchasable(product)) return fail(404, 'PRODUCT_NOT_FOUND');
  const all = d.reviews.filter((r) => r.productId === product.id);
  const rating = Number(query.get('rating')) || null;
  const withMedia = query.get('withMedia') === 'true';
  const page = Math.max(0, Number(query.get('page') ?? 0));
  const size = Math.min(50, Math.max(1, Number(query.get('size') ?? 10)));
  const filtered = all
    .filter((r) => (!rating || r.rating === rating) && (!withMedia || r.media.length > 0))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
  const counts: Partial<Record<string, number>> = {};
  all.forEach((r) => (counts[r.rating] = (counts[r.rating] ?? 0) + 1));
  return ok({
    summary: { ratingAvg: average(all), ratingCount: all.length, counts, withMedia: all.filter((r) => r.media.length).length },
    items: filtered.slice(page * size, page * size + size).map(toView),
    page, size, totalItems: filtered.length, totalPages: Math.ceil(filtered.length / size),
  });
}

export function orderReviews(d: MockDb, userId: number, code: string): MockResult {
  const order = d.orders.find((o) => o.userId === userId && o.code === code);
  if (!order) return fail(404, 'ORDER_NOT_FOUND');
  return ok(d.reviews.filter((r) => r.orderCode === code).map(toView));
}

export function createReview(d: MockDb, user: MockUser, code: string, itemId: number, b: Record<string, unknown>): MockResult {
  const order = d.orders.find((o) => o.userId === user.id && o.code === code);
  const item = order?.items.find((i) => i.id === itemId);
  if (!order || !item || order.status !== 'COMPLETED') return fail(422, 'REVIEW_NOT_ALLOWED');
  if (d.reviews.some((r) => r.orderItemId === itemId && r.orderCode === code)) return fail(409, 'REVIEW_ALREADY_EXISTS');
  const rating = Number(b['rating']);
  const content = typeof b['content'] === 'string' ? b['content'].trim() : '';
  if (!Number.isInteger(rating) || rating < 1 || rating > 5 || content.length > MAX_CONTENT) {
    return fail(400, 'VALIDATION_ERROR', [{ field: 'rating', message: 'invalid' }]);
  }
  const review: MockReview = {
    id: d.nextId(d.reviews), orderItemId: itemId, orderCode: code, productId: item.productId, productName: item.productName,
    shopId: order.shop?.id ?? 0, userId: user.id, reviewerName: maskName(user.fullName), rating, content: content || null,
    media: ((b['media'] as string[] | undefined) ?? []).slice(0, 5), variantName: item.variantName,
    sellerReply: null, sellerRepliedAt: null, createdAt: new Date().toISOString(),
  };
  d.reviews.push(review);
  const product = d.catalog.products.find((p) => p.id === item.productId);
  if (product) {
    const mine = d.reviews.filter((r) => r.productId === product.id);
    product.ratingAvg = average(mine);
    product.ratingCount = mine.length;
  }
  d.save();
  return ok(toView(review), 201);
}

export function sellerReviews(d: MockDb, user: MockUser, query: URLSearchParams): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  const unreplied = query.get('unreplied') === 'true';
  const page = Math.max(0, Number(query.get('page') ?? 0));
  const size = Math.min(100, Math.max(1, Number(query.get('size') ?? 20)));
  const list = d.reviews
    .filter((r) => r.shopId === shop.id && (!unreplied || !r.sellerReply))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
  return ok({ items: list.slice(page * size, page * size + size).map(toSellerView), page, size, totalItems: list.length, totalPages: Math.ceil(list.length / size) });
}

export function replyReview(d: MockDb, user: MockUser, id: number, b: Record<string, unknown>): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  const review = d.reviews.find((r) => r.id === id && r.shopId === shop?.id);
  if (!review) return fail(404, 'REVIEW_NOT_FOUND');
  if (review.sellerReply) return fail(409, 'REVIEW_ALREADY_REPLIED');
  const content = typeof b['content'] === 'string' ? b['content'].trim() : '';
  if (!content || content.length > MAX_CONTENT) return fail(400, 'VALIDATION_ERROR', [{ field: 'content', message: 'invalid' }]);
  review.sellerReply = content;
  review.sellerRepliedAt = new Date().toISOString();
  d.save();
  return ok(toSellerView(review));
}

/** "Nguyễn Văn An" → "N*****n" (giống backend). */
export function maskName(fullName: string | null | undefined): string {
  const chars = [...(fullName ?? '').trim()];
  if (!chars.length) return '*****';
  return chars.length <= 2 ? `${chars[0]}*****` : `${chars[0]}*****${chars[chars.length - 1]}`;
}

function average(list: { rating: number }[]): number {
  return list.length ? Math.round((list.reduce((s, r) => s + r.rating, 0) / list.length) * 10) / 10 : 0;
}

function toView(r: MockReview): Review {
  return {
    id: r.id, orderItemId: r.orderItemId, reviewerName: r.reviewerName, rating: r.rating, content: r.content, media: r.media,
    variantName: r.variantName, sellerReply: r.sellerReply, sellerRepliedAt: r.sellerRepliedAt, createdAt: r.createdAt,
  };
}

function toSellerView(r: MockReview): SellerReview {
  return {
    id: r.id, productId: r.productId, productName: r.productName, variantName: r.variantName, reviewerName: r.reviewerName,
    rating: r.rating, content: r.content, media: r.media, sellerReply: r.sellerReply, sellerRepliedAt: r.sellerRepliedAt,
    createdAt: r.createdAt,
  };
}
