import {
  Cart,
  CartItem,
  CheckoutGroup,
  CheckoutLine,
  CheckoutPreview,
  OrderStatus,
  OrderSummary,
  ShopSummary,
} from '../core/api/api.models';
import { MockUser } from './mock-data';
import { MockDb, MockOrder } from './mock-db';

/**
 * Giỏ hàng, checkout, đơn mua của backend giả — cùng quy tắc với backend thật:
 * không vượt tồn kho, tách đơn theo shop, phí ship cố định, chỉ hủy được khi PENDING/CONFIRMED (hoàn kho).
 */

export type MockResult = { status: number; body: unknown };

export const ok = (data: unknown, status = 200): MockResult => ({ status, body: { success: true, data, message: null } });
export const fail = (status: number, code: string, details: { field: string; message: string }[] = []): MockResult => ({
  status,
  body: { success: false, error: { code, message: code, details } },
});

const SHIPPING_FEE = 30_000;
const MAX_CART_ITEMS = 200;
const CANCELLABLE: OrderStatus[] = ['PENDING', 'CONFIRMED'];

// ----- Cart

export function cartView(d: MockDb, userId: number): Cart {
  const groups = new Map<number, { shop: ShopSummary; items: CartItem[] }>();
  let selectedQuantity = 0;
  let selectedSubtotal = 0;
  const items = d.cart.filter((c) => c.userId === userId).sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
  for (const c of items) {
    const found = d.findVariant(c.variantId);
    if (!found) continue;
    const { product, variant } = found;
    const available = variant.stock >= c.quantity && d.isPurchasable(product);
    const lineTotal = variant.price * c.quantity;
    if (c.selected && available) {
      selectedQuantity += c.quantity;
      selectedSubtotal += lineTotal;
    }
    const group = groups.get(product.shop.id) ?? { shop: product.shop, items: [] };
    group.items.push({
      id: c.id, variantId: variant.id, productId: product.id, productName: product.name, productSlug: product.slug,
      variantName: variantName(product.variantOptions, variant.optionValues), imageUrl: variant.imageUrl ?? product.images[0] ?? null,
      unitPrice: variant.price, quantity: c.quantity, stock: variant.stock, selected: c.selected, available, lineTotal,
    });
    groups.set(product.shop.id, group);
  }
  const shops = [...groups.values()];
  return { shops, itemCount: shops.reduce((n, g) => n + g.items.length, 0), selectedQuantity, selectedSubtotal };
}

export function addToCart(d: MockDb, userId: number, variantId: number, quantity: number): MockResult {
  const found = d.findVariant(variantId);
  if (!found) return fail(404, 'VARIANT_NOT_FOUND');
  if (!Number.isInteger(quantity) || quantity < 1) return fail(400, 'VALIDATION_ERROR', [{ field: 'quantity', message: 'invalid' }]);
  const existing = d.cart.find((c) => c.userId === userId && c.variantId === variantId);
  const current = existing?.quantity ?? 0;
  if (current + quantity > found.variant.stock) {
    return fail(422, 'CART_QUANTITY_EXCEEDS_STOCK', [
      { field: 'quantity', message: String(Math.max(found.variant.stock - current, 0)) },
    ]);
  }
  if (existing) {
    existing.quantity += quantity;
    existing.selected = true;
  } else {
    if (d.cart.filter((c) => c.userId === userId).length >= MAX_CART_ITEMS) return fail(422, 'CART_LIMIT_REACHED');
    d.cart.push({ id: d.nextId(d.cart), userId, variantId, quantity, selected: true, createdAt: Date.now() });
  }
  d.save();
  return ok(cartView(d, userId), 201);
}

export function updateCartItem(d: MockDb, userId: number, itemId: number, body: Record<string, unknown>): MockResult {
  const item = d.cart.find((c) => c.id === itemId && c.userId === userId);
  if (!item) return fail(404, 'CART_ITEM_NOT_FOUND');
  if (typeof body['quantity'] === 'number' && body['quantity'] !== item.quantity) {
    const quantity = body['quantity'];
    const stock = d.findVariant(item.variantId)?.variant.stock ?? 0;
    if (quantity < 1) return fail(400, 'VALIDATION_ERROR', [{ field: 'quantity', message: 'invalid' }]);
    if (quantity > item.quantity && quantity > stock) {
      return fail(422, 'CART_QUANTITY_EXCEEDS_STOCK', [{ field: 'quantity', message: String(stock) }]);
    }
    item.quantity = quantity;
  }
  if (typeof body['selected'] === 'boolean') item.selected = body['selected'];
  d.save();
  return ok(cartView(d, userId));
}

export function selectCartItems(d: MockDb, userId: number, ids: number[], selected: boolean): MockResult {
  d.cart.filter((c) => c.userId === userId && (ids.length === 0 || ids.includes(c.id))).forEach((c) => (c.selected = selected));
  d.save();
  return ok(cartView(d, userId));
}

export function removeCartItems(d: MockDb, userId: number, ids: number[]): MockResult {
  const before = d.cart.length;
  d.cart = d.cart.filter((c) => !(c.userId === userId && ids.includes(c.id)));
  if (d.cart.length === before) return fail(404, 'CART_ITEM_NOT_FOUND');
  d.save();
  return ok(cartView(d, userId));
}

// ----- Checkout

function quote(d: MockDb, userId: number, addressId?: number): MockResult | CheckoutPreview {
  const address = d.addresses.find((a) => a.userId === userId && (addressId ? a.id === addressId : a.isDefault));
  if (addressId && !address) return fail(404, 'ADDRESS_NOT_FOUND');
  const selected = d.cart.filter((c) => c.userId === userId && c.selected);
  const groups = new Map<number, CheckoutGroup>();
  for (const c of selected) {
    const found = d.findVariant(c.variantId);
    if (!found) continue;
    const { product, variant } = found;
    const line: CheckoutLine = {
      variantId: variant.id, productId: product.id, productName: product.name, productSlug: product.slug,
      variantName: variantName(product.variantOptions, variant.optionValues), imageUrl: variant.imageUrl ?? product.images[0] ?? null,
      unitPrice: variant.price, quantity: c.quantity, stock: variant.stock, available: variant.stock >= c.quantity && d.isPurchasable(product),
      lineTotal: variant.price * c.quantity,
    };
    const g = groups.get(product.shop.id) ?? { shop: product.shop, items: [], subtotal: 0, shippingFee: SHIPPING_FEE, discount: 0, total: 0 };
    g.items.push(line);
    g.subtotal += line.lineTotal;
    g.total = g.subtotal + g.shippingFee;
    groups.set(product.shop.id, g);
  }
  if (groups.size === 0) return fail(422, 'NO_ITEMS_SELECTED');
  const list = [...groups.values()].sort((a, b) => a.shop.id - b.shop.id);
  const subtotal = list.reduce((s, g) => s + g.subtotal, 0);
  const shippingFee = list.reduce((s, g) => s + g.shippingFee, 0);
  return {
    address: address ? toSnapshot(address) : null,
    groups: list, subtotal, shippingFee, discount: 0, total: subtotal + shippingFee, paymentMethods: ['COD'],
    hasUnavailableItems: list.some((g) => g.items.some((l) => !l.available)),
  };
}

export function previewCheckout(d: MockDb, userId: number, addressId?: number): MockResult {
  const q = quote(d, userId, addressId);
  return 'status' in q ? q : ok(q);
}

export function placeOrder(d: MockDb, user: MockUser, body: Record<string, unknown>): MockResult {
  if (body['paymentMethod'] !== 'COD') return fail(422, 'PAYMENT_METHOD_NOT_SUPPORTED');
  const addressId = Number(body['addressId']);
  if (!addressId) return fail(404, 'ADDRESS_NOT_FOUND');
  const q = quote(d, user.id, addressId);
  if ('status' in q) return q;
  const unavailable = q.groups.flatMap((g) => g.items).filter((l) => !l.available);
  if (unavailable.length) return fail(409, 'OUT_OF_STOCK', unavailable.map((l) => ({ field: 'variantId', message: String(l.variantId) })));
  if (typeof body['expectedTotal'] === 'number' && body['expectedTotal'] !== q.total) return fail(409, 'PRICE_CHANGED');

  q.groups.flatMap((g) => g.items).forEach((l) => d.adjustStock(l.variantId, -l.quantity));
  const notes = (body['notes'] ?? {}) as Record<string, string>;
  const { id: _addressId, ...shippingAddress } = q.address!;
  const checkoutId = crypto.randomUUID();
  const now = new Date().toISOString();
  const placed = q.groups.map((g) => {
    const order: MockOrder = {
      id: d.nextId(d.orders), userId: user.id, code: crypto.randomUUID(), checkoutId, status: 'PENDING', shop: g.shop,
      items: g.items.map((l) => ({
        variantId: l.variantId, productId: l.productId, productName: l.productName, productSlug: l.productSlug,
        variantName: l.variantName, imageUrl: l.imageUrl, unitPrice: l.unitPrice, quantity: l.quantity, lineTotal: l.lineTotal,
      })),
      subtotal: g.subtotal, shippingFee: g.shippingFee, discount: 0, total: g.total, paymentMethod: 'COD',
      paymentStatus: 'UNPAID', cancellable: true, shippingAddress,
      note: notes[String(g.shop.id)]?.trim() || null,
      history: [{ fromStatus: null, toStatus: 'PENDING', note: null, createdAt: now }], createdAt: now, updatedAt: now,
    };
    d.orders.push(order);
    return { code: order.code, shopId: g.shop.id, total: order.total };
  });
  const bought = new Set(q.groups.flatMap((g) => g.items.map((l) => l.variantId)));
  d.cart = d.cart.filter((c) => !(c.userId === user.id && bought.has(c.variantId)));
  d.save();
  return ok({ checkoutId, orders: placed, total: q.total }, 201);
}

// ----- Orders

export function listOrders(d: MockDb, userId: number, params: URLSearchParams): MockResult {
  const status = params.get('status');
  const page = Math.max(0, Number(params.get('page') ?? 0));
  const size = Math.min(100, Math.max(1, Number(params.get('size') ?? 10)));
  const mine = d.orders
    .filter((o) => o.userId === userId && (!status || o.status === status))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
  const items: OrderSummary[] = mine.slice(page * size, page * size + size).map(toSummary);
  return ok({ items, page, size, totalItems: mine.length, totalPages: Math.ceil(mine.length / size) });
}

export function getOrder(d: MockDb, userId: number, code: string): MockResult {
  const o = d.orders.find((x) => x.userId === userId && x.code === code);
  return o ? ok(toDetail(o)) : fail(404, 'ORDER_NOT_FOUND');
}

export function cancelOrder(d: MockDb, userId: number, code: string, reason?: string): MockResult {
  const o = d.orders.find((x) => x.userId === userId && x.code === code);
  if (!o) return fail(404, 'ORDER_NOT_FOUND');
  if (!CANCELLABLE.includes(o.status)) return fail(409, 'INVALID_ORDER_STATUS_TRANSITION');
  const now = new Date().toISOString();
  o.history.push({ fromStatus: o.status, toStatus: 'CANCELLED', note: reason?.trim() || null, createdAt: now });
  o.status = 'CANCELLED';
  o.cancellable = false;
  o.updatedAt = now;
  o.items.forEach((i) => i.variantId && d.adjustStock(i.variantId, i.quantity));
  d.save();
  return ok(toDetail(o));
}

// ----- helpers

function toSummary({ id: _i, userId: _u, checkoutId: _c, shippingAddress: _a, note: _n, history: _h, updatedAt: _t, ...s }: MockOrder): OrderSummary {
  return s;
}

function toDetail({ id: _i, userId: _u, ...o }: MockOrder) {
  return o;
}

function toSnapshot(a: MockDb['addresses'][number]) {
  return { id: a.id, receiverName: a.receiverName, phone: a.phone, provinceCode: a.provinceCode, districtCode: a.districtCode, wardCode: a.wardCode, detail: a.detail };
}

function variantName(options: { name: string }[], values: Record<string, string>): string | null {
  const parts = options.map((o) => values[o.name]).filter(Boolean);
  return parts.length ? parts.join(', ') : null;
}
