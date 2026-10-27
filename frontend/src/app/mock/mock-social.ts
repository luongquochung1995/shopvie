import { AdminOverview, AdminProduct, AdminUser, AppNotification, Banner, ChatMessage, Conversation, NotificationType } from '../core/api/api.models';
import { MockUser } from './mock-data';
import { fail, MockResult, ok } from './mock-commerce';
import { MockConversation, MockDb, MockOrder } from './mock-db';
import { mockPush } from './mock-realtime';
import { toAdminShop } from './mock-seller';

/**
 * Thông báo, chat buyer ↔ shop, quản trị (tài khoản, hậu kiểm sản phẩm, khóa shop, banner, thống kê) của backend giả —
 * cùng quy tắc với backend thật; tin realtime đi qua {@link mockPush}.
 */

const PAGE = (q: URLSearchParams, def = 20) => ({
  page: Math.max(0, Number(q.get('page') ?? 0)),
  size: Math.min(100, Math.max(1, Number(q.get('size') ?? def))),
});
const paged = <T>(list: T[], page: number, size: number) => ({
  items: list.slice(page * size, page * size + size), page, size, totalItems: list.length, totalPages: Math.ceil(list.length / size),
});

// ----- Thông báo

export function notify(d: MockDb, userId: number, type: NotificationType, params: Record<string, string | number | null | undefined>, link: string): void {
  const clean: Record<string, string | number> = {};
  Object.entries(params).forEach(([k, v]) => v !== null && v !== undefined && (clean[k] = v));
  const n = { id: d.nextId(d.notifications), userId, type, params: clean, link, read: false, createdAt: new Date().toISOString() };
  d.notifications.push(n);
  mockPush(userId, 'notifications', { notification: toNotification(n), unreadCount: unread(d, userId) });
}

/** Đơn đổi trạng thái: người mua được báo (trừ khi chính họ thao tác); người mua hủy → báo chủ shop. */
export function notifyOrderStatus(d: MockDb, order: MockOrder, by: 'buyer' | 'seller' | 'system', note?: string | null): void {
  if (by !== 'buyer') {
    notify(d, order.userId, 'ORDER_STATUS', { orderCode: order.code, status: order.status, note }, `/account/orders/${order.code}`);
  } else if (order.status === 'CANCELLED') {
    const owner = ownerOf(d, order.shop?.id);
    if (owner) notify(d, owner, 'ORDER_CANCELLED_BY_BUYER', { orderCode: order.code, note }, `/seller-center/orders/${order.code}`);
  }
}

/** Đơn mới cần xử lý (COD khi đặt, online khi đã thanh toán). */
export function notifyNewOrder(d: MockDb, order: MockOrder): void {
  const owner = ownerOf(d, order.shop?.id);
  if (owner) notify(d, owner, 'NEW_ORDER', { orderCode: order.code, total: order.total }, `/seller-center/orders/${order.code}`);
}

export function listNotifications(d: MockDb, userId: number, q: URLSearchParams): MockResult {
  const { page, size } = PAGE(q, 10);
  const mine = d.notifications.filter((n) => n.userId === userId).sort((a, b) => b.id - a.id).map(toNotification);
  return ok({ ...paged(mine, page, size), unreadCount: unread(d, userId) });
}

export function markNotificationRead(d: MockDb, userId: number, id: number): MockResult {
  const n = d.notifications.find((x) => x.id === id && x.userId === userId);
  if (!n) return fail(404, 'NOTIFICATION_NOT_FOUND');
  n.read = true;
  d.save();
  return ok(toNotification(n));
}

export function markAllNotificationsRead(d: MockDb, userId: number): MockResult {
  d.notifications.filter((n) => n.userId === userId).forEach((n) => (n.read = true));
  d.save();
  return { status: 204, body: null };
}

// ----- Chat

export function openConversation(d: MockDb, user: MockUser, shopId: number): MockResult {
  const shop = d.shops.find((s) => s.id === shopId && s.status === 'ACTIVE');
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  if (shop.ownerId === user.id) return fail(422, 'CHAT_WITH_OWN_SHOP');
  let c = d.conversations.find((x) => x.buyerId === user.id && x.shopId === shopId);
  if (!c) {
    c = { id: d.nextId(d.conversations), buyerId: user.id, shopId, lastMessage: null, lastMessageAt: null, buyerUnread: 0, shopUnread: 0, buyerLastRead: null, shopLastRead: null };
    d.conversations.push(c);
    d.save();
  }
  return ok(toConversation(d, c, 'BUYER'));
}

export function listConversations(d: MockDb, user: MockUser, q: URLSearchParams): MockResult {
  const { page, size } = PAGE(q, 30);
  const myShop = d.shops.find((s) => s.ownerId === user.id)?.id;
  const mine = d.conversations
    .filter((c) => c.buyerId === user.id || c.shopId === myShop)
    .sort((a, b) => (b.lastMessageAt ?? '').localeCompare(a.lastMessageAt ?? '') || b.id - a.id)
    .map((c) => toConversation(d, c, c.buyerId === user.id ? 'BUYER' : 'SHOP'));
  return ok(paged(mine, page, size));
}

export function messages(d: MockDb, user: MockUser, id: number, q: URLSearchParams): MockResult {
  const c = participant(d, user, id);
  if (!c) return fail(404, 'CONVERSATION_NOT_FOUND');
  const side = c.buyerId === user.id ? 'BUYER' : 'SHOP';
  const before = Number(q.get('before')) || Infinity;
  const size = Math.min(50, Math.max(1, Number(q.get('size') ?? 30)));
  const older = d.chatMessages.filter((m) => m.conversationId === id && m.id < before).sort((a, b) => a.id - b.id);
  return ok({
    items: older.slice(-size), hasMore: older.length > size,
    otherLastReadMessageId: side === 'BUYER' ? c.shopLastRead : c.buyerLastRead,
  });
}

export function sendMessage(d: MockDb, user: MockUser, id: number, body: Record<string, unknown>): MockResult {
  const c = participant(d, user, id);
  if (!c) return fail(404, 'CONVERSATION_NOT_FOUND');
  const content = typeof body['content'] === 'string' ? body['content'].trim() : '';
  if (!content || content.length > 2000) return fail(400, 'VALIDATION_ERROR', [{ field: 'content', message: 'invalid' }]);
  const side = c.buyerId === user.id ? 'BUYER' : 'SHOP';
  const m: ChatMessage = { id: d.nextId(d.chatMessages), conversationId: id, senderId: user.id, senderSide: side, content, createdAt: new Date().toISOString() };
  d.chatMessages.push(m);
  c.lastMessage = content.slice(0, 200);
  c.lastMessageAt = m.createdAt;
  if (side === 'BUYER') {
    c.shopUnread++;
    c.buyerUnread = 0;
    c.buyerLastRead = m.id;
  } else {
    c.buyerUnread++;
    c.shopUnread = 0;
    c.shopLastRead = m.id;
  }
  d.save();
  mockPush(c.buyerId, 'chat', { type: 'MESSAGE', conversationId: id, message: m, unread: c.buyerUnread, lastReadMessageId: null });
  const owner = ownerOf(d, c.shopId);
  if (owner) mockPush(owner, 'chat', { type: 'MESSAGE', conversationId: id, message: m, unread: c.shopUnread, lastReadMessageId: null });
  return ok(m, 201);
}

export function markConversationRead(d: MockDb, user: MockUser, id: number): MockResult {
  const c = participant(d, user, id);
  if (!c) return fail(404, 'CONVERSATION_NOT_FOUND');
  const last = d.chatMessages.filter((m) => m.conversationId === id).reduce((max, m) => Math.max(max, m.id), 0);
  if (last) {
    const buyer = c.buyerId === user.id;
    if (buyer) {
      c.buyerUnread = 0;
      c.buyerLastRead = Math.max(c.buyerLastRead ?? 0, last);
    } else {
      c.shopUnread = 0;
      c.shopLastRead = Math.max(c.shopLastRead ?? 0, last);
    }
    d.save();
    const other = buyer ? ownerOf(d, c.shopId) : c.buyerId;
    if (other) mockPush(other, 'chat', { type: 'READ', conversationId: id, message: null, unread: null, lastReadMessageId: last });
  }
  return { status: 204, body: null };
}

export function chatUnread(d: MockDb, user: MockUser): MockResult {
  const myShop = d.shops.find((s) => s.ownerId === user.id)?.id;
  const count = d.conversations.reduce((n, c) => n + (c.buyerId === user.id ? c.buyerUnread : c.shopId === myShop ? c.shopUnread : 0), 0);
  return ok({ count });
}

// ----- Quản trị: tài khoản

export function adminUsers(d: MockDb, q: URLSearchParams): MockResult {
  const { page, size } = PAGE(q);
  const keyword = (q.get('q') ?? '').trim().toLowerCase();
  const list = d.users
    .filter((u) => !keyword || u.email.includes(keyword) || u.fullName.toLowerCase().includes(keyword) || (u.phone ?? '').includes(keyword))
    .filter((u) => !q.get('role') || u.role === q.get('role'))
    .filter((u) => !q.get('status') || (u.status ?? 'ACTIVE') === q.get('status'))
    .sort((a, b) => b.id - a.id)
    .map(toAdminUser);
  return ok(paged(list, page, size));
}

export function lockUser(d: MockDb, id: number, lock: boolean, reason?: unknown): MockResult {
  const u = d.users.find((x) => x.id === id);
  if (!u) return fail(404, 'USER_NOT_FOUND');
  if (lock && u.role === 'ADMIN') return fail(422, 'CANNOT_LOCK_ADMIN');
  if (lock === ((u.status ?? 'ACTIVE') === 'LOCKED')) return fail(409, 'INVALID_USER_STATUS');
  if (lock && !(typeof reason === 'string' && reason.trim())) return fail(400, 'VALIDATION_ERROR', [{ field: 'reason', message: 'must not be blank' }]);
  u.status = lock ? 'LOCKED' : 'ACTIVE';
  u.lockReason = lock ? (reason as string).trim() : null;
  if (lock && d.session === u.id) d.session = null;
  d.save();
  return ok(toAdminUser(u));
}

// ----- Quản trị: sản phẩm

export function adminProducts(d: MockDb, q: URLSearchParams): MockResult {
  const { page, size } = PAGE(q);
  const keyword = (q.get('q') ?? '').trim().toLowerCase();
  const list = d.catalog.products
    .filter((p) => !q.get('status') || (p.status ?? 'ACTIVE') === q.get('status'))
    .filter((p) => !keyword || p.name.toLowerCase().includes(keyword))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
    .map(toAdminProduct);
  return ok(paged(list, page, size));
}

export function banProduct(d: MockDb, id: number, ban: boolean, reason?: unknown): MockResult {
  const p = d.catalog.products.find((x) => x.id === id);
  if (!p) return fail(404, 'PRODUCT_NOT_FOUND');
  if (ban === ((p.status ?? 'ACTIVE') === 'BANNED')) return fail(409, 'INVALID_PRODUCT_STATUS');
  if (ban && !(typeof reason === 'string' && reason.trim())) return fail(400, 'VALIDATION_ERROR', [{ field: 'reason', message: 'must not be blank' }]);
  p.status = ban ? 'BANNED' : 'ACTIVE';
  p.banReason = ban ? (reason as string).trim() : null;
  d.saveProduct(p);
  const owner = ownerOf(d, p.shop.id);
  if (owner) {
    notify(d, owner, ban ? 'PRODUCT_BANNED' : 'PRODUCT_UNBANNED', { productName: p.name, reason: p.banReason }, `/seller-center/products/${p.id}`);
  }
  d.save();
  return ok(toAdminProduct(p));
}

// ----- Quản trị: shop

export function banShop(d: MockDb, id: number, ban: boolean, reason?: unknown): MockResult {
  const s = d.shops.find((x) => x.id === id);
  if (!s) return fail(404, 'SHOP_NOT_FOUND');
  if (s.status !== (ban ? 'ACTIVE' : 'BANNED')) return fail(409, 'INVALID_SHOP_STATUS');
  if (ban && !(typeof reason === 'string' && reason.trim())) return fail(400, 'VALIDATION_ERROR', [{ field: 'reason', message: 'must not be blank' }]);
  s.status = ban ? 'BANNED' : 'ACTIVE';
  s.banReason = ban ? (reason as string).trim() : null;
  s.updatedAt = new Date().toISOString();
  notify(d, s.ownerId, ban ? 'SHOP_BANNED' : 'SHOP_UNBANNED', { shopName: s.name, reason: s.banReason }, '/seller-center/shop');
  d.save();
  return ok(toAdminShop(d, s));
}

// ----- Banner

export function liveBanners(d: MockDb): MockResult {
  const now = Date.now();
  return ok(d.banners
    .filter((b) => b.active && (!b.startsAt || Date.parse(b.startsAt) <= now) && (!b.endsAt || now < Date.parse(b.endsAt)))
    .sort((a, b) => a.position - b.position || a.id - b.id));
}

export function allBanners(d: MockDb): MockResult {
  return ok([...d.banners].sort((a, b) => a.position - b.position || a.id - b.id));
}

export function saveBanner(d: MockDb, id: number | null, body: Record<string, unknown>): MockResult {
  const title = typeof body['title'] === 'string' ? body['title'].trim() : '';
  const imageUrl = typeof body['imageUrl'] === 'string' ? body['imageUrl'].trim() : '';
  const linkUrl = typeof body['linkUrl'] === 'string' && body['linkUrl'].trim() ? body['linkUrl'].trim() : null;
  const fields = [
    ...(title ? [] : ['title']),
    ...(/^https?:\/\/\S+$/.test(imageUrl) ? [] : ['imageUrl']),
    ...(!linkUrl || /^(https?:\/\/\S+|\/\S*)$/.test(linkUrl) ? [] : ['linkUrl']),
  ];
  if (fields.length) return fail(400, 'VALIDATION_ERROR', fields.map((field) => ({ field, message: 'invalid' })));
  const data: Omit<Banner, 'id'> = {
    title, imageUrl, linkUrl, position: Number(body['position']) || 0, active: body['active'] !== false,
    startsAt: (body['startsAt'] as string | null) ?? null, endsAt: (body['endsAt'] as string | null) ?? null,
  };
  let banner: Banner;
  if (id === null) {
    banner = { id: d.nextId(d.banners), ...data };
    d.banners.push(banner);
  } else {
    const existing = d.banners.find((b) => b.id === id);
    if (!existing) return fail(404, 'BANNER_NOT_FOUND');
    banner = Object.assign(existing, data);
  }
  d.save();
  return ok(banner, id === null ? 201 : 200);
}

export function deleteBanner(d: MockDb, id: number): MockResult {
  if (!d.banners.some((b) => b.id === id)) return fail(404, 'BANNER_NOT_FOUND');
  d.banners = d.banners.filter((b) => b.id !== id);
  d.save();
  return { status: 204, body: null };
}

// ----- Thống kê

export function overview(d: MockDb, q: URLSearchParams): MockResult {
  const days = Number(q.get('days') ?? 30);
  if (!Number.isInteger(days) || days < 1 || days > 90) return fail(400, 'VALIDATION_ERROR', [{ field: 'days', message: 'invalid' }]);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const from = new Date(today.getTime() - (days - 1) * 86_400_000);
  const key = (dt: Date) => `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
  const daily = Array.from({ length: days }, (_, i) => ({ date: key(new Date(from.getTime() + i * 86_400_000)), orders: 0, gmv: 0 }));
  const byStatus: Record<string, number> = {};
  for (const o of d.orders.filter((x) => Date.parse(x.createdAt) >= from.getTime())) {
    const point = daily.find((p) => p.date === key(new Date(o.createdAt)));
    if (point) {
      point.orders++;
      if (o.status !== 'CANCELLED' && o.status !== 'RETURNED') point.gmv += o.total;
    }
    byStatus[o.status] = (byStatus[o.status] ?? 0) + 1;
  }
  const count = <T>(list: T[], f: (x: T) => string) => list.reduce<Record<string, number>>((m, x) => ((m[f(x)] = (m[f(x)] ?? 0) + 1), m), {});
  const result: AdminOverview = {
    days,
    users: {
      total: d.users.length, byRole: count(d.users, (u) => u.role), locked: d.users.filter((u) => u.status === 'LOCKED').length,
      newSince: d.users.filter((u) => Date.parse(u.createdAt) >= from.getTime()).length,
    },
    shops: count(d.shops, (s) => s.status),
    products: count(d.catalog.products, (p) => p.status ?? 'ACTIVE'),
    orders: { orders: daily.reduce((n, p) => n + p.orders, 0), gmv: daily.reduce((n, p) => n + p.gmv, 0), byStatus, daily },
  };
  return ok(result);
}

// ----- helpers

function ownerOf(d: MockDb, shopId: number | undefined): number | undefined {
  return d.shops.find((s) => s.id === shopId)?.ownerId;
}

function unread(d: MockDb, userId: number): number {
  return d.notifications.filter((n) => n.userId === userId && !n.read).length;
}

function toNotification({ userId: _u, ...n }: AppNotification & { userId: number }): AppNotification {
  return n;
}

function participant(d: MockDb, user: MockUser, id: number): MockConversation | undefined {
  const c = d.conversations.find((x) => x.id === id);
  const myShop = d.shops.find((s) => s.ownerId === user.id)?.id;
  return c && (c.buyerId === user.id || c.shopId === myShop) ? c : undefined;
}

function toConversation(d: MockDb, c: MockConversation, side: 'BUYER' | 'SHOP'): Conversation {
  const shop = d.shops.find((s) => s.id === c.shopId);
  const buyer = d.users.find((u) => u.id === c.buyerId);
  return {
    id: c.id, side, shopId: c.shopId,
    counterpart: side === 'BUYER' ? (shop?.name ?? null) : (buyer?.fullName ?? null),
    counterpartAvatar: side === 'BUYER' ? (shop?.logoUrl ?? null) : null,
    lastMessage: c.lastMessage, lastMessageAt: c.lastMessageAt, unread: side === 'BUYER' ? c.buyerUnread : c.shopUnread,
  };
}

function toAdminProduct(p: MockDb['catalog']['products'][number]): AdminProduct {
  return {
    id: p.id, name: p.name, slug: p.slug, thumbnailUrl: p.images[0] ?? null, status: p.status ?? 'ACTIVE', banReason: p.banReason ?? null,
    shop: p.shop, minPrice: p.minPrice, maxPrice: p.maxPrice, soldCount: p.soldCount, ratingAvg: p.ratingAvg,
    createdAt: p.createdAt, updatedAt: p.updatedAt ?? p.createdAt,
  };
}

function toAdminUser(u: MockUser): AdminUser {
  return {
    id: u.id, email: u.email, phone: u.phone, fullName: u.fullName, avatarUrl: u.avatarUrl, role: u.role,
    status: u.status ?? 'ACTIVE', lockReason: u.lockReason ?? null, createdAt: u.createdAt,
  };
}
