import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { defer, delay, from, mergeMap, Observable, of, throwError } from 'rxjs';
import { Address, AuthResponse, CategoryNode, Profile, ProductCard, SellerAction } from '../core/api/api.models';
import { MockUser } from './mock-data';
import * as commerce from './mock-commerce';
import { MockAddress, MockDb } from './mock-db';
import * as payment from './mock-payment';
import * as promotion from './mock-promotion';
import * as reviews from './mock-review';
import * as searchMock from './mock-search';
import * as social from './mock-social';
import * as seller from './mock-seller';

/**
 * Backend giả chạy trong frontend (cấu hình `mock`): chặn request tới API và trả về dữ liệu test
 * đúng format của backend thật (ApiResponse / lỗi chuẩn / phân trang). Không gửi request ra mạng.
 */

type MockResult = { status: number; body: unknown };

const ACCESS_TTL_MS = 15 * 60 * 1000;
const MAX_ADDRESSES = 20;
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
const SORTABLE = new Set(['createdAt', 'soldCount', 'minPrice']);
const VN_PHONE = /^(0|\+84)\d{9,10}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const ok = (data: unknown, status = 200): MockResult => ({ status, body: { success: true, data, message: null } });
const noContent = (): MockResult => ({ status: 204, body: null });
const fail = (status: number, code: string, message = code, details: { field: string; message: string }[] = []): MockResult => ({
  status,
  body: { success: false, error: { code, message, details } },
});
const invalid = (...fields: string[]) =>
  fail(400, 'VALIDATION_ERROR', 'Validation failed', fields.map((field) => ({ field, message: 'invalid' })));

let db: MockDb | undefined;
/** Một DB dùng chung trong phiên (trình duyệt) / tiến trình (SSR). */
export function mockDb(): MockDb {
  return (db ??= new MockDb());
}

/** Chỉ dùng trong test: tạo lại DB từ đầu. */
export function resetMockDb(): void {
  MockDb.reset();
  db = undefined;
}

export function mockBackendInterceptor(apiBaseUrl: string, latencyMs = 250): HttpInterceptorFn {
  return (req, next) => {
    if (!req.url.startsWith(apiBaseUrl)) {
      return next(req);
    }
    const path = req.url.slice(apiBaseUrl.length).split('?')[0];
    const isBrowser = typeof window !== 'undefined';
    return defer(() => from(Promise.resolve(route(req, path)))).pipe(
      delay(isBrowser ? latencyMs : 0),
      mergeMap((res): Observable<HttpResponse<unknown>> => {
        if (res.status >= 400) {
          return throwError(() => new HttpErrorResponse({ status: res.status, error: res.body, url: req.url }));
        }
        // Bản sao như JSON qua mạng: client sửa response không được làm thay đổi "DB" giả
        return of(new HttpResponse({ status: res.status, body: structuredClone(res.body), url: req.url }));
      }),
    );
  };
}

function route(req: HttpRequest<unknown>, path: string): MockResult | Promise<MockResult> {
  const d = mockDb();
  payment.expireUnpaid(d);
  const m = req.method;
  const body = (req.body ?? {}) as Record<string, unknown>;
  let match: RegExpMatchArray | null;

  // ----- Auth
  if (m === 'POST' && path === '/auth/login') return login(d, body);
  if (m === 'POST' && path === '/auth/register') return register(d, body);
  if (m === 'POST' && path === '/auth/refresh') {
    const user = d.users.find((u) => u.id === d.session);
    return user ? ok(authResponse(user)) : fail(401, 'INVALID_REFRESH_TOKEN');
  }
  if (m === 'POST' && path === '/auth/logout') {
    d.session = null;
    d.save();
    return noContent();
  }

  // ----- Catalog (public)
  if (m === 'GET' && path === '/categories') return ok(d.catalog.categories);
  if (m === 'GET' && path === '/products') return listProducts(d, req);
  if (m === 'GET' && (match = path.match(/^\/products\/([^/]+)$/))) {
    const p = d.catalog.products.find((x) => x.slug === decodeURIComponent(match![1]));
    // Sản phẩm ẩn / của shop chưa hoạt động không hiển thị
    if (!p || !d.isPurchasable(p)) return fail(404, 'PRODUCT_NOT_FOUND');
    const { categoryId: _c, createdAt: _t, status: _s, updatedAt: _u, ...detail } = p;
    return ok(detail);
  }

  // ----- Tìm kiếm / đánh giá / flash sale (public)
  const publicQuery = new URLSearchParams(req.urlWithParams.split('?')[1] ?? '');
  if (m === 'GET' && path === '/search') return searchMock.search(d, publicQuery);
  if (m === 'GET' && path === '/search/suggest') return searchMock.suggest(d, publicQuery);
  if (m === 'GET' && (match = path.match(/^\/products\/([^/]+)\/reviews$/))) {
    return reviews.productReviews(d, decodeURIComponent(match[1]), publicQuery);
  }
  if (m === 'GET' && path === '/flash-sales/current') return promotion.currentFlashSales(d);
  if (m === 'GET' && path === '/banners') return social.liveBanners(d);

  // ----- Cần đăng nhập
  const auth = authenticate(d, req);
  if (!auth) return fail(401, 'UNAUTHORIZED');
  const user = auth.user;
  const query = new URLSearchParams(req.urlWithParams.split('?')[1] ?? '');

  // ----- Seller Center / quản trị (phân quyền theo role trong token, như backend thật)
  if (m === 'POST' && path === '/seller/shop/register') return seller.registerShop(d, user, body);
  if (path.startsWith('/seller/')) {
    return auth.role === 'SELLER' ? sellerRoute(d, user, m, path, body, query) : fail(403, 'FORBIDDEN');
  }
  if (path.startsWith('/admin/')) {
    return auth.role === 'ADMIN' ? adminRoute(d, m, path, body, query) : fail(403, 'FORBIDDEN');
  }

  if (m === 'GET' && path === '/me') return ok(profile(user));
  if (m === 'PUT' && path === '/me') {
    const phone = blankToNull(body['phone']);
    if (!str(body['fullName']) || (phone && !VN_PHONE.test(phone))) return invalid('fullName', 'phone');
    if (phone && d.users.some((u) => u.phone === phone && u.id !== user.id)) return fail(409, 'PHONE_ALREADY_EXISTS');
    Object.assign(user, { fullName: str(body['fullName']), phone, avatarUrl: blankToNull(body['avatarUrl']) });
    d.save();
    return ok(profile(user));
  }

  if (path === '/me/addresses' && m === 'GET') return ok(addressesOf(d, user.id));
  if (path === '/me/addresses' && m === 'POST') {
    const error = validateAddress(body);
    if (error) return error;
    const mine = d.addresses.filter((a) => a.userId === user.id);
    if (mine.length >= MAX_ADDRESSES) return fail(422, 'ADDRESS_LIMIT_REACHED');
    const isDefault = body['isDefault'] === true || mine.length === 0;
    if (isDefault) mine.forEach((a) => (a.isDefault = false));
    const address: MockAddress = { ...addressData(body), id: d.nextId(d.addresses), userId: user.id, isDefault, createdAt: Date.now() };
    d.addresses.push(address);
    d.save();
    return ok(toAddress(address), 201);
  }
  if ((match = path.match(/^\/me\/addresses\/(\d+)(\/default)?$/))) {
    const address = d.addresses.find((a) => a.id === Number(match![1]) && a.userId === user.id);
    if (!address) return fail(404, 'ADDRESS_NOT_FOUND');
    const setDefault = () => {
      d.addresses.filter((a) => a.userId === user.id).forEach((a) => (a.isDefault = false));
      address.isDefault = true;
    };
    if (match[2] && m === 'PATCH') {
      setDefault();
    } else if (!match[2] && m === 'PUT') {
      const error = validateAddress(body);
      if (error) return error;
      Object.assign(address, addressData(body));
      if (body['isDefault'] === true) setDefault();
    } else if (!match[2] && m === 'DELETE') {
      d.addresses = d.addresses.filter((a) => a !== address);
      if (address.isDefault) {
        const next = d.addresses.filter((a) => a.userId === user.id).sort((a, b) => b.createdAt - a.createdAt)[0];
        if (next) next.isDefault = true;
      }
      d.save();
      return noContent();
    } else {
      return fail(405, 'METHOD_NOT_ALLOWED');
    }
    d.save();
    return ok(toAddress(address));
  }

  if (m === 'POST' && path === '/media/upload') return upload(req);

  // ----- Giỏ hàng / checkout / đơn mua
  if (path === '/cart' && m === 'GET') return commerce.ok(commerce.cartView(d, user.id));
  if (path === '/cart/count' && m === 'GET') return commerce.ok({ count: commerce.cartView(d, user.id).itemCount });
  if (path === '/cart/items' && m === 'POST') return commerce.addToCart(d, user.id, Number(body['variantId']), Number(body['quantity']));
  if (path === '/cart/items' && m === 'DELETE') {
    return commerce.removeCartItems(d, user.id, (query.get('ids') ?? '').split(',').filter(Boolean).map(Number));
  }
  if (path === '/cart/selection' && m === 'PATCH') {
    return commerce.selectCartItems(d, user.id, ((body['itemIds'] as number[]) ?? []).map(Number), body['selected'] === true);
  }
  if ((match = path.match(/^\/cart\/items\/(\d+)$/))) {
    if (m === 'PATCH') return commerce.updateCartItem(d, user.id, Number(match[1]), body);
    if (m === 'DELETE') return commerce.removeCartItems(d, user.id, [Number(match[1])]);
  }
  if (path === '/checkout/preview' && m === 'POST') {
    return commerce.previewCheckout(d, user.id, Number(body['addressId']) || undefined, body['voucherCodes']);
  }
  if (path === '/checkout' && m === 'POST') return commerce.placeOrder(d, user, body);
  if ((match = path.match(/^\/checkout\/([^/]+)\/payment$/))) {
    if (m === 'GET') return payment.checkoutPayment(d, user.id, decodeURIComponent(match[1]));
    if (m === 'POST') return payment.retryPayment(d, user.id, decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/payments\/sandbox\/([^/]+)$/)) && m === 'POST') {
    return payment.completeSandbox(d, user.id, decodeURIComponent(match[1]), body['success']);
  }
  if (path === '/orders' && m === 'GET') return commerce.listOrders(d, user.id, query);
  if ((match = path.match(/^\/orders\/([^/]+)\/complete$/)) && m === 'POST') return commerce.completeOrder(d, user.id, decodeURIComponent(match[1]));
  if ((match = path.match(/^\/orders\/([^/]+)\/reviews$/)) && m === 'GET') return reviews.orderReviews(d, user.id, decodeURIComponent(match[1]));
  if ((match = path.match(/^\/orders\/([^/]+)\/items\/(\d+)\/review$/)) && m === 'POST') {
    return reviews.createReview(d, user, decodeURIComponent(match[1]), Number(match[2]), body);
  }
  // ----- Thông báo / chat
  if (path.startsWith('/chat/') || path.startsWith('/notifications')) d.syncShared();
  if (path === '/notifications' && m === 'GET') return social.listNotifications(d, user.id, query);
  if (path === '/notifications/read-all' && m === 'POST') return social.markAllNotificationsRead(d, user.id);
  if ((match = path.match(/^\/notifications\/(\d+)\/read$/)) && m === 'POST') return social.markNotificationRead(d, user.id, Number(match[1]));
  if (path === '/chat/conversations' && m === 'POST') return social.openConversation(d, user, Number(body['shopId']));
  if (path === '/chat/conversations' && m === 'GET') return social.listConversations(d, user, query);
  if (path === '/chat/unread-count' && m === 'GET') return social.chatUnread(d, user);
  if ((match = path.match(/^\/chat\/conversations\/(\d+)\/messages$/))) {
    if (m === 'GET') return social.messages(d, user, Number(match[1]), query);
    if (m === 'POST') return social.sendMessage(d, user, Number(match[1]), body);
  }
  if ((match = path.match(/^\/chat\/conversations\/(\d+)\/read$/)) && m === 'POST') return social.markConversationRead(d, user, Number(match[1]));

  if (path === '/vouchers/available' && m === 'GET') {
    return promotion.availableVouchers(d, user.id, (query.get('shopIds') ?? '').split(',').filter(Boolean).map(Number));
  }
  if ((match = path.match(/^\/flash-sales\/items\/(\d+)\/purchase$/)) && m === 'POST') {
    return promotion.purchaseFlashSale(d, user, Number(match[1]), body);
  }
  if ((match = path.match(/^\/flash-sales\/reservations\/([^/]+)$/)) && m === 'GET') {
    return promotion.reservation(d, user.id, decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/orders\/([^/]+)\/cancel$/)) && m === 'POST') {
    return commerce.cancelOrder(d, user.id, decodeURIComponent(match[1]), body['reason'] as string | undefined);
  }
  if ((match = path.match(/^\/orders\/([^/]+)$/)) && m === 'GET') return commerce.getOrder(d, user.id, decodeURIComponent(match[1]));

  return fail(404, 'NOT_FOUND');
}

// ----- Seller Center / admin

function sellerRoute(
  d: MockDb,
  user: MockUser,
  m: string,
  path: string,
  body: Record<string, unknown>,
  query: URLSearchParams,
): MockResult {
  let match: RegExpMatchArray | null;
  if (path === '/seller/shop' && m === 'GET') return seller.myShop(d, user);
  if (path === '/seller/shop' && m === 'PUT') return seller.updateShop(d, user, body);
  if (path === '/seller/shop/pickup-address' && m === 'PUT') return seller.updatePickupAddress(d, user, body);
  if (path === '/seller/products' && m === 'GET') return seller.listProducts(d, user, query);
  if (path === '/seller/products' && m === 'POST') return seller.saveProduct(d, user, null, body);
  if ((match = path.match(/^\/seller\/products\/(\d+)\/visibility$/)) && m === 'PATCH') {
    return seller.setVisibility(d, user, Number(match[1]), body['visible']);
  }
  if ((match = path.match(/^\/seller\/products\/(\d+)$/))) {
    if (m === 'GET') return seller.getProduct(d, user, Number(match[1]));
    if (m === 'PUT') return seller.saveProduct(d, user, Number(match[1]), body);
    if (m === 'DELETE') return seller.deleteProduct(d, user, Number(match[1]));
  }
  if (path === '/seller/orders' && m === 'GET') return seller.listOrders(d, user, query);
  if (path === '/seller/orders/counts' && m === 'GET') return seller.orderCounts(d, user);
  if ((match = path.match(/^\/seller\/orders\/([^/]+)\/(confirm|ship|deliver|cancel)$/)) && m === 'POST') {
    return seller.performAction(d, user, decodeURIComponent(match[1]), match[2].toUpperCase() as SellerAction, body);
  }
  if ((match = path.match(/^\/seller\/orders\/([^/]+)$/)) && m === 'GET') {
    return seller.getOrder(d, user, decodeURIComponent(match[1]));
  }
  if (path === '/seller/dashboard' && m === 'GET') return seller.dashboard(d, user, query);
  const shopId = d.shops.find((s) => s.ownerId === user.id)?.id;
  if (path.startsWith('/seller/vouchers') && !shopId) return fail(404, 'SHOP_NOT_FOUND');
  if (path === '/seller/vouchers' && m === 'GET') return promotion.listVouchers(d, shopId!, query);
  if (path === '/seller/vouchers' && m === 'POST') return promotion.createVoucher(d, shopId!, body);
  if ((match = path.match(/^\/seller\/vouchers\/(\d+)\/disable$/)) && m === 'POST') return promotion.disableVoucher(d, shopId!, Number(match[1]));
  if (path === '/seller/reviews' && m === 'GET') return reviews.sellerReviews(d, user, query);
  if ((match = path.match(/^\/seller\/reviews\/(\d+)\/reply$/)) && m === 'POST') return reviews.replyReview(d, user, Number(match[1]), body);
  return fail(404, 'NOT_FOUND');
}

function adminRoute(d: MockDb, m: string, path: string, body: Record<string, unknown>, query: URLSearchParams): MockResult {
  if (path === '/admin/shops' && m === 'GET') return seller.adminListShops(d, query);
  let match = path.match(/^\/admin\/shops\/(\d+)\/(approve|reject)$/);
  if (match && m === 'POST') return seller.adminReviewShop(d, Number(match[1]), match[2] === 'approve', body['reason']);
  if ((match = path.match(/^\/admin\/shops\/(\d+)\/(ban|unban)$/)) && m === 'POST') {
    return social.banShop(d, Number(match[1]), match[2] === 'ban', body['reason']);
  }
  if (path === '/admin/users' && m === 'GET') return social.adminUsers(d, query);
  if ((match = path.match(/^\/admin\/users\/(\d+)\/(lock|unlock)$/)) && m === 'POST') {
    return social.lockUser(d, Number(match[1]), match[2] === 'lock', body['reason']);
  }
  if (path === '/admin/products' && m === 'GET') return social.adminProducts(d, query);
  if ((match = path.match(/^\/admin\/products\/(\d+)\/(ban|unban)$/)) && m === 'POST') {
    return social.banProduct(d, Number(match[1]), match[2] === 'ban', body['reason']);
  }
  if (path === '/admin/banners' && m === 'GET') return social.allBanners(d);
  if (path === '/admin/banners' && m === 'POST') return social.saveBanner(d, null, body);
  if ((match = path.match(/^\/admin\/banners\/(\d+)$/))) {
    if (m === 'PUT') return social.saveBanner(d, Number(match[1]), body);
    if (m === 'DELETE') return social.deleteBanner(d, Number(match[1]));
  }
  if (path === '/admin/categories' && m === 'POST') return social.saveCategory(d, null, body);
  if ((match = path.match(/^\/admin\/categories\/(\d+)$/))) {
    if (m === 'PUT') return social.saveCategory(d, Number(match[1]), body);
    if (m === 'DELETE') return social.deleteCategory(d, Number(match[1]));
  }
  if (path === '/admin/stats' && m === 'GET') return social.overview(d, query);
  if (path === '/admin/vouchers' && m === 'GET') return promotion.listVouchers(d, null, query);
  if (path === '/admin/vouchers' && m === 'POST') return promotion.createVoucher(d, null, body);
  if ((match = path.match(/^\/admin\/vouchers\/(\d+)\/disable$/)) && m === 'POST') return promotion.disableVoucher(d, null, Number(match[1]));
  if (path === '/admin/flash-sales' && m === 'GET') return promotion.adminFlashSales(d, query);
  if (path === '/admin/flash-sales' && m === 'POST') return promotion.createFlashSale(d, body);
  if ((match = path.match(/^\/admin\/flash-sales\/(\d+)\/cancel$/)) && m === 'POST') return promotion.cancelFlashSale(d, Number(match[1]));
  return fail(404, 'NOT_FOUND');
}

// ----- Auth helpers

function login(d: MockDb, body: Record<string, unknown>): MockResult {
  const user = d.users.find((u) => u.email === str(body['email']).toLowerCase());
  if (!user || user.password !== body['password']) return fail(401, 'INVALID_CREDENTIALS');
  if (user.status === 'LOCKED') return fail(403, 'ACCOUNT_LOCKED');
  d.session = user.id;
  d.save();
  return ok(authResponse(user));
}

function register(d: MockDb, body: Record<string, unknown>): MockResult {
  const email = str(body['email']).toLowerCase();
  const password = typeof body['password'] === 'string' ? body['password'] : '';
  const phone = blankToNull(body['phone']);
  const fields = [
    ...(EMAIL.test(email) ? [] : ['email']),
    ...(password.length >= 8 && password.length <= 72 ? [] : ['password']),
    ...(str(body['fullName']) ? [] : ['fullName']),
    ...(phone && !VN_PHONE.test(phone) ? ['phone'] : []),
  ];
  if (fields.length) return invalid(...fields);
  if (d.users.some((u) => u.email === email)) return fail(409, 'EMAIL_ALREADY_EXISTS');
  if (phone && d.users.some((u) => u.phone === phone)) return fail(409, 'PHONE_ALREADY_EXISTS');
  const user: MockUser = {
    id: d.nextId(d.users), email, password, fullName: str(body['fullName']), phone, avatarUrl: null,
    role: 'BUYER', createdAt: new Date().toISOString(),
  };
  d.users.push(user);
  d.session = user.id;
  d.save();
  return ok(authResponse(user), 201);
}

function authResponse(user: MockUser): AuthResponse {
  return {
    accessToken: `mock-token.${user.id}.${Date.now() + ACCESS_TTL_MS}.${user.role}`,
    tokenType: 'Bearer',
    expiresIn: ACCESS_TTL_MS / 1000,
    user: { id: user.id, email: user.email, fullName: user.fullName, role: user.role },
  };
}

/**
 * Token giả dạng "mock-token.<userId>.<hết hạn ms>.<role>"; hết hạn → 401 để interceptor tự refresh.
 * Role nằm trong token như JWT thật: đổi role (mở shop) chỉ có hiệu lực sau khi làm mới token.
 */
function authenticate(d: MockDb, req: HttpRequest<unknown>): { user: MockUser; role: string } | undefined {
  const [, id, exp, role] = (req.headers.get('Authorization') ?? '').replace('Bearer ', '').split('.');
  if (!id || Number(exp) < Date.now()) return undefined;
  const user = d.users.find((u) => u.id === Number(id));
  return user ? { user, role: role ?? user.role } : undefined;
}

function profile(u: MockUser): Profile {
  return { id: u.id, email: u.email, phone: u.phone, fullName: u.fullName, avatarUrl: u.avatarUrl, role: u.role, createdAt: u.createdAt };
}

// ----- Catalog helpers

function listProducts(d: MockDb, req: HttpRequest<unknown>): MockResult {
  // Đọc cả query viết sẵn trong URL lẫn HttpParams
  const p = new URLSearchParams(req.urlWithParams.split('?')[1] ?? '');
  const num = (k: string) => (p.has(k) && p.get(k) !== '' ? Number(p.get(k)) : undefined);
  const categoryId = num('categoryId');
  const minPrice = num('minPrice');
  const maxPrice = num('maxPrice');
  const page = Math.max(0, num('page') ?? 0);
  const size = Math.min(100, Math.max(1, num('size') ?? 20));
  const [sortField, sortDir] = (p.get('sort') ?? 'createdAt,desc').split(',');
  if (!SORTABLE.has(sortField)) return fail(400, 'BAD_REQUEST', `Unsupported sort property: ${sortField}`);
  if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) return invalid('minPrice');

  let items = d.catalog.products.filter((x) => d.isPurchasable(x));
  if (categoryId !== undefined) {
    const ids = descendantIds(d.catalog.categories, categoryId);
    if (!ids) return fail(404, 'CATEGORY_NOT_FOUND');
    items = items.filter((x) => ids.includes(x.categoryId));
  }
  if (minPrice !== undefined) items = items.filter((x) => x.minPrice >= minPrice);
  if (maxPrice !== undefined) items = items.filter((x) => x.minPrice <= maxPrice);

  const dir = sortDir === 'asc' ? 1 : -1;
  const value = (x: (typeof items)[number]) => (sortField === 'createdAt' ? Date.parse(x.createdAt) : (x[sortField as 'soldCount' | 'minPrice']));
  items = [...items].sort((a, b) => (value(a) - value(b)) * dir || b.id - a.id);

  const cards: ProductCard[] = items.slice(page * size, page * size + size).map((x) => ({
    id: x.id, name: x.name, slug: x.slug, thumbnailUrl: x.images[0] ?? null, minPrice: x.minPrice, maxPrice: x.maxPrice,
    soldCount: x.soldCount, ratingAvg: x.ratingAvg, ratingCount: x.ratingCount, createdAt: x.createdAt,
  }));
  return ok({ items: cards, page, size, totalItems: items.length, totalPages: Math.ceil(items.length / size) });
}

function descendantIds(nodes: CategoryNode[], id: number): number[] | null {
  for (const n of nodes) {
    if (n.id === id) {
      const all: number[] = [];
      const walk = (x: CategoryNode) => (all.push(x.id), x.children.forEach(walk));
      walk(n);
      return all;
    }
    const found = descendantIds(n.children, id);
    if (found) return found;
  }
  return null;
}

// ----- Address helpers

function validateAddress(b: Record<string, unknown>): MockResult | null {
  const fields = ['receiverName', 'provinceCode', 'wardCode', 'detail'].filter((f) => !str(b[f]));
  if (!VN_PHONE.test(str(b['phone']))) fields.push('phone');
  return fields.length ? invalid(...fields) : null;
}

function addressData(b: Record<string, unknown>) {
  return {
    receiverName: str(b['receiverName']), phone: str(b['phone']), provinceCode: str(b['provinceCode']),
    districtCode: null, wardCode: str(b['wardCode']), detail: str(b['detail']),
  };
}

function addressesOf(d: MockDb, userId: number): Address[] {
  return d.addresses
    .filter((a) => a.userId === userId)
    .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || b.createdAt - a.createdAt || b.id - a.id)
    .map(toAddress);
}

function toAddress({ userId: _u, createdAt: _c, ...a }: MockAddress): Address {
  return a;
}

// ----- Media

async function upload(req: HttpRequest<unknown>): Promise<MockResult> {
  const file = req.body instanceof FormData ? req.body.get('file') : null;
  if (!(file instanceof Blob) || file.size === 0) return fail(400, 'BAD_REQUEST', 'File is required');
  if (file.size > MAX_UPLOAD_BYTES) return fail(413, 'FILE_TOO_LARGE');
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return fail(415, 'UNSUPPORTED_MEDIA_TYPE');
  // Ảnh lưu dạng data URL để còn hiển thị sau khi tải lại trang
  const url = await new Promise<string>((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.readAsDataURL(file);
  });
  return ok({ url, contentType: file.type, size: file.size }, 201);
}

// ----- utils

function str(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function blankToNull(value: unknown): string | null {
  return str(value) || null;
}
