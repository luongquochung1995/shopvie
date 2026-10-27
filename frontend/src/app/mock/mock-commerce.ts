import {
  Cart,
  CartItem,
  CheckoutGroup,
  CheckoutLine,
  CheckoutPreview,
  OrderStatus,
  OrderSummary,
  PaymentMethod,
  ShopSummary,
} from '../core/api/api.models';
import { MockUser } from './mock-data';
import { MockDb, MockOrder } from './mock-db';
import { ONLINE_METHODS, PAYMENT_TTL_MS, setStatus, startPayment, zoneFee } from './mock-payment';
import { quoteVouchers, redeemVouchers, releaseVouchers } from './mock-promotion';
import { notifyNewOrder, notifyOrderStatus } from './mock-social';

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

/** Phí ship ước tính khi chưa có địa chỉ nhận (có địa chỉ thì tính theo vùng). */
const ESTIMATED_SHIPPING_FEE = 30_000;
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

function quote(d: MockDb, userId: number, addressId?: number, voucherCodes?: unknown): MockResult | CheckoutPreview {
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
    const g = groups.get(product.shop.id) ?? { shop: product.shop, items: [], subtotal: 0, shippingFee: 0, discount: 0, total: 0, vouchers: [] };
    g.items.push(line);
    g.subtotal += line.lineTotal;
    groups.set(product.shop.id, g);
  }
  for (const g of groups.values()) {
    const origin = d.shops.find((s) => s.id === g.shop.id)?.pickupAddress?.provinceCode;
    const quantity = g.items.reduce((n, l) => n + l.quantity, 0);
    g.shippingFee = address ? zoneFee(origin, address.provinceCode, quantity) : ESTIMATED_SHIPPING_FEE;
    g.total = g.subtotal + g.shippingFee;
  }
  if (groups.size === 0) return fail(422, 'NO_ITEMS_SELECTED');
  const list = [...groups.values()].sort((a, b) => a.shop.id - b.shop.id);
  const vouchers = quoteVouchers(d, userId, voucherCodes, list);
  if ('status' in vouchers) return vouchers;
  for (const g of list) {
    g.vouchers = vouchers.byShop.get(g.shop.id) ?? [];
    g.discount = Math.min(g.vouchers.reduce((n, v) => n + v.amount, 0), g.subtotal + g.shippingFee);
    g.total = g.subtotal + g.shippingFee - g.discount;
  }
  const subtotal = list.reduce((s, g) => s + g.subtotal, 0);
  const shippingFee = list.reduce((s, g) => s + g.shippingFee, 0);
  const discount = list.reduce((s, g) => s + g.discount, 0);
  return {
    address: address ? toSnapshot(address) : null,
    groups: list, subtotal, shippingFee, discount, total: subtotal + shippingFee - discount, paymentMethods: ['COD', ...ONLINE_METHODS],
    hasUnavailableItems: list.some((g) => g.items.some((l) => !l.available)),
    voucherCodes: [...new Set(list.flatMap((g) => g.vouchers.map((v) => v.code)))],
  };
}

export function previewCheckout(d: MockDb, userId: number, addressId?: number, voucherCodes?: unknown): MockResult {
  const q = quote(d, userId, addressId, voucherCodes);
  return 'status' in q ? q : ok(q);
}

export function placeOrder(d: MockDb, user: MockUser, body: Record<string, unknown>): MockResult {
  const method = body['paymentMethod'] as PaymentMethod;
  if (method !== 'COD' && !ONLINE_METHODS.includes(method)) return fail(422, 'PAYMENT_METHOD_NOT_SUPPORTED');
  const online = method !== 'COD';
  const addressId = Number(body['addressId']);
  if (!addressId) return fail(404, 'ADDRESS_NOT_FOUND');
  const q = quote(d, user.id, addressId, body['voucherCodes']);
  if ('status' in q) return q;
  const unavailable = q.groups.flatMap((g) => g.items).filter((l) => !l.available);
  if (unavailable.length) return fail(409, 'OUT_OF_STOCK', unavailable.map((l) => ({ field: 'variantId', message: String(l.variantId) })));
  if (typeof body['expectedTotal'] === 'number' && body['expectedTotal'] !== q.total) return fail(409, 'PRICE_CHANGED');

  q.groups.flatMap((g) => g.items).forEach((l) => d.adjustStock(l.variantId, -l.quantity));
  const notes = (body['notes'] ?? {}) as Record<string, string>;
  const { id: _addressId, ...shippingAddress } = q.address!;
  const checkoutId = crypto.randomUUID();
  const now = new Date().toISOString();
  const paymentExpiresAt = online ? new Date(Date.now() + PAYMENT_TTL_MS).toISOString() : null;
  const placed = q.groups.map((g) => {
    const id = d.nextId(d.orders);
    const order: MockOrder = {
      id, userId: user.id, code: crypto.randomUUID(), checkoutId, status: 'PENDING', shop: g.shop,
      items: g.items.map((l, i) => ({
        id: id * 100 + i, variantId: l.variantId, productId: l.productId, productName: l.productName, productSlug: l.productSlug,
        variantName: l.variantName, imageUrl: l.imageUrl, unitPrice: l.unitPrice, quantity: l.quantity, lineTotal: l.lineTotal,
      })),
      subtotal: g.subtotal, shippingFee: g.shippingFee, discount: g.discount, total: g.total, paymentMethod: method,
      vouchers: g.vouchers.map(({ code, type, scope, amount }) => ({ code, type, scope, amount })),
      paymentStatus: 'UNPAID', cancellable: true, awaitingPayment: online, paymentExpiresAt, shipment: null, shippingAddress,
      note: notes[String(g.shop.id)]?.trim() || null,
      history: [{ fromStatus: null, toStatus: 'PENDING', note: null, createdAt: now }], createdAt: now, updatedAt: now,
    };
    d.orders.push(order);
    redeemVouchers(d, user.id, order);
    if (!online) notifyNewOrder(d, order);
    return { code: order.code, shopId: g.shop.id, total: order.total };
  });
  const bought = new Set(q.groups.flatMap((g) => g.items.map((l) => l.variantId)));
  d.cart = d.cart.filter((c) => !(c.userId === user.id && bought.has(c.variantId)));
  const link = online ? startPayment(d, user.id, checkoutId, method, q.total, paymentExpiresAt!) : null;
  d.save();
  return ok({ checkoutId, orders: placed, total: q.total, paymentUrl: link?.paymentUrl ?? null, paymentExpiresAt }, 201);
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
  setStatus(o, 'CANCELLED', reason?.trim() || null);
  o.items.forEach((i) => i.variantId && d.adjustStock(i.variantId, i.quantity));
  releaseVouchers(d, o.id);
  notifyOrderStatus(d, o, 'buyer', reason?.trim() || null);
  d.save();
  return ok(toDetail(o));
}

/** Người mua xác nhận đã nhận hàng: DELIVERED → COMPLETED. */
export function completeOrder(d: MockDb, userId: number, code: string): MockResult {
  const o = d.orders.find((x) => x.userId === userId && x.code === code);
  if (!o) return fail(404, 'ORDER_NOT_FOUND');
  if (o.status !== 'DELIVERED') return fail(409, 'INVALID_ORDER_STATUS_TRANSITION');
  setStatus(o, 'COMPLETED', null);
  d.save();
  return ok(toDetail(o));
}

// ----- helpers

function toSummary({ id: _i, userId: _u, shippingAddress: _a, note: _n, history: _h, updatedAt: _t, shipment: _s, ...s }: MockOrder): OrderSummary {
  return s;
}

function toDetail({ id: _i, userId: _u, ...o }: MockOrder) {
  // Đơn tạo trước Phase 5 (đã lưu localStorage) chưa có id món / voucher
  return { ...o, vouchers: o.vouchers ?? [], items: o.items.map((item, i) => ({ ...item, id: item.id ?? _i * 100 + i })) };
}

function toSnapshot(a: MockDb['addresses'][number]) {
  return { id: a.id, receiverName: a.receiverName, phone: a.phone, provinceCode: a.provinceCode, districtCode: a.districtCode, wardCode: a.wardCode, detail: a.detail };
}

function variantName(options: { name: string }[], values: Record<string, string>): string | null {
  const parts = options.map((o) => values[o.name]).filter(Boolean);
  return parts.length ? parts.join(', ') : null;
}
