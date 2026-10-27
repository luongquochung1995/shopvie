import {
  AdminShop,
  CategoryNode,
  MyShop,
  OrderStatus,
  ProductStatus,
  SellerAction,
  SellerOrderDetail,
  SellerOrderSummary,
  SellerProductDetail,
  SellerProductRow,
  ShopStatus,
  Variant,
  VariantOption,
} from '../core/api/api.models';
import { fail, MockResult, ok } from './mock-commerce';
import { MockProduct, MockUser, slugify } from './mock-data';
import { MockDb, MockOrder, MockShop } from './mock-db';

/**
 * Seller Center và duyệt shop của backend giả — cùng quy tắc với backend thật (P3): mở shop → PENDING, chỉ shop
 * ACTIVE mới đăng bán/xử lý đơn; mỗi tổ hợp phân loại đúng 1 SKU; seller chỉ thấy sản phẩm/đơn của shop mình.
 */

const MAX_IMAGES = 9;
const MAX_VARIANTS = 200;
const HTTP_URL = /^https?:\/\/\S+$|^data:image\//;
const NEXT: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['DELIVERED'],
  DELIVERED: ['COMPLETED', 'RETURNED'],
  COMPLETED: [],
  CANCELLED: [],
  RETURNED: [],
};
const ACTION_TARGET: Record<SellerAction, OrderStatus> = {
  CONFIRM: 'CONFIRMED',
  SHIP: 'SHIPPING',
  DELIVER: 'DELIVERED',
  CANCEL: 'CANCELLED',
};
const invalid = (field: string) => fail(400, 'VALIDATION_ERROR', [{ field, message: 'invalid' }]);
const now = () => new Date().toISOString();

// ----- Shop

export function registerShop(d: MockDb, user: MockUser, body: Record<string, unknown>): MockResult {
  if (user.role === 'ADMIN') return fail(403, 'FORBIDDEN');
  if (d.shops.some((s) => s.ownerId === user.id)) return fail(409, 'SHOP_ALREADY_EXISTS');
  const error = validateShopInfo(body);
  if (error) return error;
  const created = now();
  const shop: MockShop = {
    id: d.nextId(d.shops), ownerId: user.id, ...shopInfo(body), slug: uniqueShopSlug(d, str(body['name']), -1),
    ratingAvg: 0, status: 'PENDING', rejectReason: null, createdAt: created, updatedAt: created,
  };
  d.shops.push(shop);
  user.role = 'SELLER';
  d.save();
  return ok(toMyShop(shop), 201);
}

export function myShop(d: MockDb, user: MockUser): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  return shop ? ok(toMyShop(shop)) : fail(404, 'SHOP_NOT_FOUND');
}

export function updateShop(d: MockDb, user: MockUser, body: Record<string, unknown>): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  if (shop.status === 'BANNED') return fail(409, 'INVALID_SHOP_STATUS');
  const error = validateShopInfo(body);
  if (error) return error;
  Object.assign(shop, shopInfo(body), { updatedAt: now() });
  if (shop.status === 'PENDING' || shop.status === 'REJECTED') shop.slug = uniqueShopSlug(d, shop.name, shop.id);
  if (shop.status === 'REJECTED') Object.assign(shop, { status: 'PENDING', rejectReason: null });
  d.save();
  return ok(toMyShop(shop));
}

export function adminListShops(d: MockDb, params: URLSearchParams): MockResult {
  const status = params.get('status') as ShopStatus | null;
  const list = d.shops
    .filter((s) => !status || s.status === status)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id);
  return ok(paginate(list.map((s) => toAdminShop(d, s)), params, 20));
}

export function adminReviewShop(d: MockDb, shopId: number, approve: boolean, reason?: unknown): MockResult {
  const shop = d.shops.find((s) => s.id === shopId);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  if (!approve && !str(reason)) return invalid('reason');
  if (shop.status !== 'PENDING') return fail(409, 'INVALID_SHOP_STATUS');
  Object.assign(shop, approve ? { status: 'ACTIVE', rejectReason: null } : { status: 'REJECTED', rejectReason: str(reason) });
  shop.updatedAt = now();
  d.save();
  return ok(toAdminShop(d, shop));
}

// ----- Sản phẩm

export function listProducts(d: MockDb, user: MockUser, params: URLSearchParams): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  const status = params.get('status');
  const q = (params.get('q') ?? '').trim().toLowerCase();
  const rows: SellerProductRow[] = d.catalog.products
    .filter((p) => p.shop.id === shop.id && (!status || statusOf(p) === status) && (!q || p.name.toLowerCase().includes(q)))
    .sort((a, b) => (b.updatedAt ?? b.createdAt).localeCompare(a.updatedAt ?? a.createdAt) || b.id - a.id)
    .map((p) => ({
      id: p.id, name: p.name, slug: p.slug, thumbnailUrl: p.images[0] ?? null, status: statusOf(p), minPrice: p.minPrice,
      maxPrice: p.maxPrice, totalStock: p.variants.reduce((s, v) => s + v.stock, 0), variantCount: p.variants.length,
      soldCount: p.soldCount, ratingAvg: p.ratingAvg, createdAt: p.createdAt, updatedAt: p.updatedAt ?? p.createdAt,
    }));
  return ok(paginate(rows, params, 20));
}

export function getProduct(d: MockDb, user: MockUser, productId: number): MockResult {
  const found = ownedProduct(d, user, productId);
  return 'status' in found ? found : ok(toSellerDetail(found.product));
}

export function saveProduct(d: MockDb, user: MockUser, productId: number | null, body: Record<string, unknown>): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  if (shop.status !== 'ACTIVE') return fail(422, 'SHOP_NOT_ACTIVE');
  let existing: MockProduct | undefined;
  if (productId !== null) {
    const found = ownedProduct(d, user, productId);
    if ('status' in found) return found;
    existing = found.product;
  }

  // Kiểm tra đầu vào như backend thật
  const name = str(body['name']);
  if (name.length < 3 || name.length > 255) return invalid('name');
  const categoryId = Number(body['categoryId']);
  const category = findCategory(d.catalog.categories, categoryId);
  if (!category) return fail(404, 'CATEGORY_NOT_FOUND');
  if (category.node.children.length > 0) return fail(422, 'CATEGORY_NOT_LEAF');
  const images = [...new Set(((body['images'] as string[]) ?? []).map((i) => String(i).trim()).filter(Boolean))];
  if (images.length === 0 || images.length > MAX_IMAGES || images.some((i) => !HTTP_URL.test(i))) return invalid('images');
  const options = ((body['variantOptions'] as VariantOption[]) ?? []).map((o) => ({ name: str(o.name), values: o.values.map(str) }));
  const inputs = (body['variants'] as Record<string, unknown>[]) ?? [];
  const expected = options.reduce((n, o) => n * o.values.length, 1);
  if (options.length > 2 || expected > MAX_VARIANTS || inputs.length !== expected) return invalid('variants');
  const combos = new Set<string>();
  for (const [i, v] of inputs.entries()) {
    const values = (v['optionValues'] ?? {}) as Record<string, string>;
    const key = options.map((o) => values[o.name]).join('\u001f');
    if (options.some((o) => !o.values.includes(values[o.name])) || Object.keys(values).length !== options.length || combos.has(key)) {
      return invalid(`variants[${i}].optionValues`);
    }
    combos.add(key);
    const price = Number(v['price']);
    const stock = Number(v['stock']);
    if (!Number.isInteger(price) || price < 1000 || price > 999_999_999) return invalid(`variants[${i}].price`);
    if (!Number.isInteger(stock) || stock < 0 || stock > 999_999) return invalid(`variants[${i}].stock`);
    const sku = str(v['sku']);
    const owner = sku ? d.catalog.products.find((p) => p.variants.some((x) => x.sku === sku)) : undefined;
    if (owner && owner.id !== existing?.id) return fail(409, 'SKU_ALREADY_EXISTS', [{ field: `variants[${i}].sku`, message: sku }]);
  }

  const id = existing?.id ?? d.nextProductId();
  let nextVariantId = d.nextVariantId();
  const variants: Variant[] = inputs.map((v) => {
    const old = existing?.variants.find((x) => x.id === Number(v['id']));
    return {
      id: old?.id ?? nextVariantId++,
      sku: str(v['sku']) || `S${shop.id}-${Math.random().toString(36).slice(2, 10).toUpperCase()}`,
      optionValues: { ...(v['optionValues'] as Record<string, string>) },
      price: Number(v['price']),
      stock: Number(v['stock']),
      imageUrl: null,
    };
  });
  const prices = variants.map((v) => v.price);
  const attributes = Object.fromEntries(
    Object.entries((body['attributes'] as Record<string, string>) ?? {}).map(([k, v]) => [k.trim(), String(v).trim()]).filter(([k, v]) => k && v),
  );
  const timestamp = now();
  const product: MockProduct = {
    id,
    name,
    slug: existing?.slug ?? `${slugify(name) || 'san-pham'}-${id}`,
    description: str(body['description']) || null,
    attributes,
    minPrice: Math.min(...prices),
    maxPrice: Math.max(...prices),
    totalStock: variants.reduce((s, v) => s + v.stock, 0),
    soldCount: existing?.soldCount ?? 0,
    ratingAvg: existing?.ratingAvg ?? 0,
    ratingCount: existing?.ratingCount ?? 0,
    breadcrumb: category.path,
    images,
    variantOptions: options,
    variants,
    shop: d.shopSummary(shop),
    categoryId,
    createdAt: existing?.createdAt ?? timestamp,
    updatedAt: timestamp,
    status: existing ? statusOf(existing) : body['visible'] === false ? 'HIDDEN' : 'ACTIVE',
  };
  // SKU bị bỏ khỏi sản phẩm cũng bị gỡ khỏi giỏ hàng
  const kept = new Set(variants.map((v) => v.id));
  d.cart = d.cart.filter((c) => !existing?.variants.some((v) => v.id === c.variantId && !kept.has(v.id)));
  d.saveProduct(product);
  return ok(toSellerDetail(product), existing ? 200 : 201);
}

export function setVisibility(d: MockDb, user: MockUser, productId: number, visible: unknown): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (shop?.status !== 'ACTIVE') return fail(422, 'SHOP_NOT_ACTIVE');
  const found = ownedProduct(d, user, productId);
  if ('status' in found) return found;
  if (typeof visible !== 'boolean') return invalid('visible');
  if (statusOf(found.product) === 'BANNED') return fail(422, 'PRODUCT_BANNED');
  const product = { ...found.product, status: (visible ? 'ACTIVE' : 'HIDDEN') as ProductStatus, updatedAt: now() };
  d.saveProduct(product);
  return ok(toSellerDetail(product));
}

export function deleteProduct(d: MockDb, user: MockUser, productId: number): MockResult {
  const found = ownedProduct(d, user, productId);
  if ('status' in found) return found;
  d.deleteProduct(productId);
  return { status: 204, body: null };
}

// ----- Đơn hàng

export function listOrders(d: MockDb, user: MockUser, params: URLSearchParams): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  const status = params.get('status');
  const list = shopOrders(d, shop.id)
    .filter((o) => !status || o.status === status)
    .map(toSellerSummary);
  return ok(paginate(list, params, 20));
}

export function orderCounts(d: MockDb, user: MockUser): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  return ok(countByStatus(shopOrders(d, shop.id)));
}

export function getOrder(d: MockDb, user: MockUser, code: string): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  const order = shop && d.orders.find((o) => o.code === code && o.shop?.id === shop.id);
  return order ? ok(toSellerOrderDetail(order)) : fail(404, 'ORDER_NOT_FOUND');
}

export function performAction(d: MockDb, user: MockUser, code: string, action: SellerAction, body: Record<string, unknown>): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  if (shop.status !== 'ACTIVE') return fail(422, 'SHOP_NOT_ACTIVE');
  const order = d.orders.find((o) => o.code === code && o.shop?.id === shop.id);
  if (!order) return fail(404, 'ORDER_NOT_FOUND');
  const note = str(action === 'CANCEL' ? body['reason'] : body['note']) || null;
  if (action === 'CANCEL' && !note) return invalid('reason');
  const target = ACTION_TARGET[action];
  if (!NEXT[order.status].includes(target)) return fail(409, 'INVALID_ORDER_STATUS_TRANSITION');
  const timestamp = now();
  order.history.push({ fromStatus: order.status, toStatus: target, note, createdAt: timestamp });
  order.status = target;
  order.cancellable = NEXT[target].includes('CANCELLED');
  order.updatedAt = timestamp;
  if (target === 'DELIVERED' && order.paymentMethod === 'COD') order.paymentStatus = 'PAID';
  if (target === 'CANCELLED') order.items.forEach((i) => i.variantId && d.adjustStock(i.variantId, i.quantity));
  d.save();
  return ok(toSellerOrderDetail(order));
}

export function dashboard(d: MockDb, user: MockUser, params: URLSearchParams): MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  const monthly = params.get('granularity') === 'MONTH';
  const today = new Date();
  const to = params.get('to') ? parseDate(params.get('to')!) : today;
  let from = params.get('from') ? parseDate(params.get('from')!) : monthly ? new Date(to.getFullYear(), to.getMonth() - 11, 1) : addDays(to, -29);
  let end = to;
  if (monthly) {
    from = new Date(from.getFullYear(), from.getMonth(), 1);
    end = new Date(to.getFullYear(), to.getMonth() + 1, 0);
  }
  if (from > end) return invalid('from');

  const keyOf = (date: Date) => isoDate(monthly ? new Date(date.getFullYear(), date.getMonth(), 1) : date);
  const series = new Map<string, { period: string; revenue: number; orders: number }>();
  for (let p = new Date(from); p <= end; p = monthly ? new Date(p.getFullYear(), p.getMonth() + 1, 1) : addDays(p, 1)) {
    series.set(isoDate(p), { period: isoDate(p), revenue: 0, orders: 0 });
  }
  const startMs = from.getTime();
  const endMs = addDays(end, 1).getTime();
  const all = shopOrders(d, shop.id);
  const inRange = all.filter((o) => Date.parse(o.createdAt) >= startMs && Date.parse(o.createdAt) < endMs);
  const counted = inRange.filter((o) => o.status !== 'CANCELLED' && o.status !== 'RETURNED');
  counted.forEach((o) => {
    const point = series.get(keyOf(new Date(o.createdAt)));
    if (point) {
      point.revenue += o.subtotal;
      point.orders += 1;
    }
  });
  const top = new Map<number, { productId: number; productName: string; productSlug: string; imageUrl: string | null; quantity: number; revenue: number }>();
  counted.flatMap((o) => o.items).forEach((i) => {
    const t = top.get(i.productId) ?? { productId: i.productId, productName: i.productName, productSlug: i.productSlug, imageUrl: i.imageUrl, quantity: 0, revenue: 0 };
    t.quantity += i.quantity;
    t.revenue += i.lineTotal;
    top.set(i.productId, t);
  });
  const points = [...series.values()];
  return ok({
    from: isoDate(from), to: isoDate(end), granularity: monthly ? 'MONTH' : 'DAY', timezone: 'Asia/Ho_Chi_Minh',
    summary: {
      revenue: points.reduce((s, p) => s + p.revenue, 0),
      orders: points.reduce((s, p) => s + p.orders, 0),
      cancelledOrders: inRange.filter((o) => o.status === 'CANCELLED').length,
      unitsSold: counted.flatMap((o) => o.items).reduce((s, i) => s + i.quantity, 0),
    },
    series: points,
    statusCounts: countByStatus(all),
    topProducts: [...top.values()].sort((a, b) => b.quantity - a.quantity || b.revenue - a.revenue).slice(0, 5),
  });
}

// ----- helpers

function ownedProduct(d: MockDb, user: MockUser, productId: number): { product: MockProduct } | MockResult {
  const shop = d.shops.find((s) => s.ownerId === user.id);
  if (!shop) return fail(404, 'SHOP_NOT_FOUND');
  const product = d.catalog.products.find((p) => p.id === productId && p.shop.id === shop.id);
  return product ? { product } : fail(404, 'PRODUCT_NOT_FOUND');
}

function statusOf(p: MockProduct): ProductStatus {
  return p.status ?? 'ACTIVE';
}

function toSellerDetail(p: MockProduct): SellerProductDetail {
  return {
    id: p.id, name: p.name, slug: p.slug, status: statusOf(p), categoryId: p.categoryId, categoryPath: p.breadcrumb,
    description: p.description, attributes: p.attributes as Record<string, string>, images: p.images,
    variantOptions: p.variantOptions, variants: p.variants, minPrice: p.minPrice, maxPrice: p.maxPrice,
    totalStock: p.variants.reduce((s, v) => s + v.stock, 0), soldCount: p.soldCount, ratingAvg: p.ratingAvg,
    ratingCount: p.ratingCount, createdAt: p.createdAt, updatedAt: p.updatedAt ?? p.createdAt,
  };
}

function shopOrders(d: MockDb, shopId: number): MockOrder[] {
  return d.orders
    .filter((o) => o.shop?.id === shopId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
}

function actionsOf(status: OrderStatus): SellerAction[] {
  return (Object.keys(ACTION_TARGET) as SellerAction[]).filter((a) => NEXT[status].includes(ACTION_TARGET[a]));
}

function toSellerSummary(o: MockOrder): SellerOrderSummary {
  return {
    code: o.code, status: o.status, items: o.items, subtotal: o.subtotal, shippingFee: o.shippingFee, discount: o.discount,
    total: o.total, paymentMethod: o.paymentMethod, paymentStatus: o.paymentStatus, receiverName: o.shippingAddress.receiverName,
    note: o.note, actions: actionsOf(o.status), createdAt: o.createdAt,
  };
}

function toSellerOrderDetail(o: MockOrder): SellerOrderDetail {
  const { receiverName: _r, ...summary } = toSellerSummary(o);
  return { ...summary, shippingAddress: o.shippingAddress, history: o.history, updatedAt: o.updatedAt };
}

function countByStatus(orders: MockOrder[]): Record<OrderStatus, number> {
  const counts = Object.fromEntries(Object.keys(NEXT).map((s) => [s, 0])) as Record<OrderStatus, number>;
  orders.forEach((o) => counts[o.status]++);
  return counts;
}

function toMyShop({ ownerId: _o, ...s }: MockShop): MyShop {
  return s;
}

function toAdminShop(d: MockDb, s: MockShop): AdminShop {
  const owner = d.users.find((u) => u.id === s.ownerId);
  const { ownerId: _o, ratingAvg: _r, ...rest } = s;
  return { ...rest, owner: owner ? { id: owner.id, email: owner.email, fullName: owner.fullName } : null };
}

function validateShopInfo(body: Record<string, unknown>): MockResult | null {
  const name = str(body['name']);
  if (name.length < 3 || name.length > 100) return invalid('name');
  const logo = str(body['logoUrl']);
  if (logo && !HTTP_URL.test(logo)) return invalid('logoUrl');
  return null;
}

function shopInfo(body: Record<string, unknown>) {
  return { name: str(body['name']), description: str(body['description']) || null, logoUrl: str(body['logoUrl']) || null };
}

function uniqueShopSlug(d: MockDb, name: string, excludeId: number): string {
  const base = slugify(name) || 'shop';
  let slug = base;
  for (let i = 2; d.shops.some((s) => s.slug === slug && s.id !== excludeId); i++) slug = `${base}-${i}`;
  return slug;
}

function findCategory(nodes: CategoryNode[], id: number, path: { id: number; name: string; slug: string }[] = []):
  { node: CategoryNode; path: { id: number; name: string; slug: string }[] } | null {
  for (const n of nodes) {
    const here = [...path, { id: n.id, name: n.name, slug: n.slug }];
    if (n.id === id) return { node: n, path: here };
    const found = findCategory(n.children, id, here);
    if (found) return found;
  }
  return null;
}

function paginate<T>(list: T[], params: URLSearchParams, defaultSize: number) {
  const page = Math.max(0, Number(params.get('page') ?? 0));
  const size = Math.min(100, Math.max(1, Number(params.get('size') ?? defaultSize)));
  return { items: list.slice(page * size, page * size + size), page, size, totalItems: list.length, totalPages: Math.ceil(list.length / size) };
}

function parseDate(value: string): Date {
  const [y, m, day] = value.split('-').map(Number);
  return new Date(y, m - 1, day);
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function str(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}
