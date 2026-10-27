import { AppliedVoucher, CheckoutGroup, FlashSale, Voucher, VoucherType } from '../core/api/api.models';
import { MockUser } from './mock-data';
import { fail, MockResult, ok } from './mock-commerce';
import { MockDb, MockFlashSale, MockOrder } from './mock-db';
import { notifyNewOrder } from './mock-social';

/**
 * Voucher và flash sale của backend giả — cùng quy tắc với backend thật: voucher shop tính trước trên tiền hàng của shop,
 * voucher sàn tính trên phần còn lại và chia cho các shop theo tỷ lệ; flash sale giữ suất nguyên tử, đơn COD giá sale.
 */

const CODE = /^[A-Za-z0-9]{4,20}$/;
/** Đơn flash sale "được tạo" sau khoảng này (giả lập consumer Kafka). */
const FLASH_ORDER_DELAY_MS = 1200;

// ----- Seed

/** Voucher mẫu: 2 voucher sàn + 1 voucher của shop đầu tiên, hiệu lực 30 ngày. */
export function ensureVouchers(d: MockDb): void {
  if (d.vouchers.length) return;
  const start = new Date(Date.now() - 24 * 3600_000).toISOString();
  const end = new Date(Date.now() + 30 * 24 * 3600_000).toISOString();
  const base = { usedCount: 0, perUserLimit: 1, startAt: start, endAt: end, status: 'ACTIVE' as const, maxDiscount: null };
  d.vouchers.push(
    { ...base, id: 1, shopId: null, code: 'SHOPVIE20K', name: 'Giảm 20K cho đơn từ 100K', type: 'FIXED', value: 20_000, minOrderValue: 100_000, usageLimit: 1000 },
    { ...base, id: 2, shopId: null, code: 'FREESHIP30', name: 'Miễn phí vận chuyển tới 30K', type: 'FREESHIP', value: 30_000, minOrderValue: 0, usageLimit: 1000 },
    { ...base, id: 3, shopId: d.shops[0]?.id ?? 1, code: 'SHOP10', name: 'Giảm 10% tối đa 50K', type: 'PERCENT', value: 10, minOrderValue: 0, usageLimit: 500, maxDiscount: 50_000 },
  );
  d.save();
}

/** Luôn có 1 phiên flash sale đang chạy (4 SKU còn hàng đầu tiên, giá 50%) để dùng thử. */
export function ensureFlashSale(d: MockDb): void {
  const now = Date.now();
  if (d.flashSales.some((s) => s.status === 'ACTIVE' && Date.parse(s.endAt) > now)) return;
  const variants = d.catalog.products
    .filter((p) => d.isPurchasable(p))
    .flatMap((p) => p.variants.filter((v) => v.stock >= 20).slice(0, 1).map((v) => ({ p, v })))
    .slice(0, 4);
  d.flashSales.push({
    id: d.nextId(d.flashSales), name: 'Flash Sale giờ vàng', status: 'ACTIVE',
    startAt: new Date(now - 10 * 60_000).toISOString(), endAt: new Date(now + 2 * 3600_000).toISOString(),
    items: variants.map(({ p, v }, i) => ({
      id: d.flashSales.flatMap((s) => s.items).reduce((m, x) => Math.max(m, x.id), 0) + i + 1,
      variantId: v.id, productId: p.id, productName: p.name, productSlug: p.slug,
      variantName: Object.values(v.optionValues).join(', ') || null, imageUrl: v.imageUrl ?? p.images[0] ?? null,
      originalPrice: v.price, salePrice: Math.round(v.price / 2 / 1000) * 1000 || 1000, quota: 10, sold: 0, perUserLimit: 1,
    })),
  });
  d.save();
}

// ----- Voucher khi thanh toán

type Quote = { byShop: Map<number, AppliedVoucher[]> } | MockResult;

/** Kiểm tra + tính giảm giá; lỗi trả về giống backend (VOUCHER_NOT_FOUND / VOUCHER_NOT_APPLICABLE kèm lý do). */
export function quoteVouchers(d: MockDb, userId: number, rawCodes: unknown, groups: CheckoutGroup[]): Quote {
  const codes = [...new Set(((rawCodes as string[] | undefined) ?? []).map((c) => String(c).trim().toUpperCase()).filter(Boolean))];
  const byShop = new Map<number, AppliedVoucher[]>(groups.map((g) => [g.shop.id, []]));
  if (!codes.length) return { byShop };
  ensureVouchers(d);
  const vouchers = codes.map((c) => d.vouchers.find((v) => v.code === c));
  const missing = codes.filter((_, i) => !vouchers[i]);
  if (missing.length) return fail(404, 'VOUCHER_NOT_FOUND', missing.map((c) => ({ field: c, message: 'NOT_FOUND' })));

  const now = Date.now();
  let platform: Voucher | null = null;
  const shopVouchers = new Map<number, Voucher>();
  for (const v of vouchers as Voucher[]) {
    const used = d.voucherUsages.filter((u) => u.voucherId === v.id && u.userId === userId).length;
    const group = v.shopId === null ? null : groups.find((g) => g.shop.id === v.shopId);
    const merchandise = v.shopId === null ? groups.reduce((s, g) => s + g.subtotal, 0) : (group?.subtotal ?? 0);
    const reason =
      v.status === 'DISABLED' ? 'DISABLED'
      : now < Date.parse(v.startAt) ? 'NOT_STARTED'
      : now >= Date.parse(v.endAt) ? 'EXPIRED'
      : v.usedCount >= v.usageLimit ? 'EXHAUSTED'
      : used >= v.perUserLimit ? 'USER_LIMIT'
      : v.shopId !== null && !group ? 'SHOP_NOT_IN_CART'
      : (v.shopId === null ? platform : shopVouchers.get(v.shopId)) ? 'DUPLICATE'
      : merchandise < v.minOrderValue ? 'MIN_ORDER'
      : null;
    if (reason) return fail(422, 'VOUCHER_NOT_APPLICABLE', [{ field: v.code, message: reason }]);
    if (v.shopId === null) platform = v;
    else shopVouchers.set(v.shopId, v);
  }

  const remaining = new Map<number, [number, number]>();
  for (const g of groups) {
    let merchandise = g.subtotal;
    let shipping = g.shippingFee;
    const v = shopVouchers.get(g.shop.id);
    if (v) {
      const amount = discountFor(v, merchandise, shipping);
      byShop.get(g.shop.id)!.push(applied(v, 'SHOP', amount));
      if (v.type === 'FREESHIP') shipping -= amount;
      else merchandise -= amount;
    }
    remaining.set(g.shop.id, [merchandise, shipping]);
  }
  const p = platform as Voucher | null;
  if (p) {
    const part = p.type === 'FREESHIP' ? 1 : 0;
    const base = [...remaining.values()].reduce((s, r) => s + r[part], 0);
    const total = part ? discountFor(p, 0, base) : discountFor(p, base, 0);
    const eligible = [...remaining.entries()].filter(([, r]) => r[part] > 0);
    let allocated = 0;
    eligible.forEach(([shopId, r], i) => {
      const share = Math.min(r[part], i === eligible.length - 1 ? total - allocated : Math.floor((total * r[part]) / base));
      allocated += share;
      if (share > 0) byShop.get(shopId)!.push(applied(p, 'PLATFORM', share));
    });
  }
  return { byShop };
}

/** Ghi lượt dùng khi đặt hàng (sau khi đơn đã được tạo). */
export function redeemVouchers(d: MockDb, userId: number, order: MockOrder): void {
  for (const v of order.vouchers ?? []) {
    const voucher = d.vouchers.find((x) => x.code === v.code);
    if (!voucher) continue;
    voucher.usedCount++;
    d.voucherUsages.push({ voucherId: voucher.id, userId, orderId: order.id, amount: v.amount });
  }
}

/** Đơn bị hủy: hoàn lượt dùng voucher. */
export function releaseVouchers(d: MockDb, orderId: number): void {
  const usages = d.voucherUsages.filter((u) => u.orderId === orderId);
  usages.forEach((u) => {
    const v = d.vouchers.find((x) => x.id === u.voucherId);
    if (v) v.usedCount = Math.max(0, v.usedCount - 1);
  });
  d.voucherUsages = d.voucherUsages.filter((u) => u.orderId !== orderId);
}

export function availableVouchers(d: MockDb, userId: number, shopIds: number[]): MockResult {
  ensureVouchers(d);
  const now = Date.now();
  const list = d.vouchers.filter(
    (v) =>
      v.status === 'ACTIVE' && Date.parse(v.startAt) <= now && now < Date.parse(v.endAt) && v.usedCount < v.usageLimit
      && (v.shopId === null || shopIds.includes(v.shopId))
      && d.voucherUsages.filter((u) => u.voucherId === v.id && u.userId === userId).length < v.perUserLimit,
  );
  return ok(list);
}

// ----- Quản lý voucher (seller: voucher shop, admin: voucher sàn)

export function listVouchers(d: MockDb, shopId: number | null, query: URLSearchParams): MockResult {
  ensureVouchers(d);
  const page = Math.max(0, Number(query.get('page') ?? 0));
  const size = Math.min(100, Math.max(1, Number(query.get('size') ?? 20)));
  const mine = d.vouchers.filter((v) => v.shopId === shopId).sort((a, b) => b.id - a.id);
  return ok({ items: mine.slice(page * size, page * size + size), page, size, totalItems: mine.length, totalPages: Math.ceil(mine.length / size) });
}

export function createVoucher(d: MockDb, shopId: number | null, b: Record<string, unknown>): MockResult {
  ensureVouchers(d);
  const code = String(b['code'] ?? '').trim().toUpperCase();
  const type = b['type'] as VoucherType;
  const value = Number(b['value']);
  const fields = [
    ...(CODE.test(code) ? [] : ['code']),
    ...(String(b['name'] ?? '').trim() ? [] : ['name']),
    ...(['FIXED', 'PERCENT', 'FREESHIP'].includes(type) ? [] : ['type']),
    ...(value > 0 && (type !== 'PERCENT' || value <= 100) ? [] : ['value']),
    ...(Date.parse(String(b['endAt'])) > Date.parse(String(b['startAt'])) ? [] : ['endAt']),
  ];
  if (fields.length) return fail(400, 'VALIDATION_ERROR', fields.map((field) => ({ field, message: 'invalid' })));
  if (d.vouchers.some((v) => v.code === code)) return fail(409, 'VOUCHER_CODE_EXISTS');
  const voucher: Voucher = {
    id: d.nextId(d.vouchers), shopId, code, name: String(b['name']).trim(), type, value,
    minOrderValue: Number(b['minOrderValue']) || 0, maxDiscount: type === 'PERCENT' && b['maxDiscount'] ? Number(b['maxDiscount']) : null,
    usageLimit: Number(b['usageLimit']) || 1, usedCount: 0, perUserLimit: Number(b['perUserLimit']) || 1,
    startAt: new Date(String(b['startAt'])).toISOString(), endAt: new Date(String(b['endAt'])).toISOString(), status: 'ACTIVE',
  };
  d.vouchers.push(voucher);
  d.save();
  return ok(voucher, 201);
}

export function disableVoucher(d: MockDb, shopId: number | null, id: number): MockResult {
  const v = d.vouchers.find((x) => x.id === id && x.shopId === shopId);
  if (!v) return fail(404, 'VOUCHER_NOT_FOUND');
  v.status = 'DISABLED';
  d.save();
  return ok(v);
}

// ----- Flash sale

export function currentFlashSales(d: MockDb): MockResult {
  ensureFlashSale(d);
  const now = Date.now();
  const list = d.flashSales
    .filter((s) => s.status === 'ACTIVE' && Date.parse(s.endAt) > now)
    .sort((a, b) => a.startAt.localeCompare(b.startAt))
    .slice(0, 3)
    .map(toView);
  return ok(list);
}

export function purchaseFlashSale(d: MockDb, user: MockUser, itemId: number, b: Record<string, unknown>): MockResult {
  const quantity = Number(b['quantity'] ?? 1);
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10) return fail(400, 'VALIDATION_ERROR', [{ field: 'quantity', message: 'invalid' }]);
  const address = d.addresses.find((a) => a.userId === user.id && (b['addressId'] ? a.id === Number(b['addressId']) : a.isDefault));
  if (!address) return fail(404, 'ADDRESS_NOT_FOUND');
  const sale = d.flashSales.find((s) => s.items.some((i) => i.id === itemId));
  const item = sale?.items.find((i) => i.id === itemId);
  if (!sale || !item) return fail(404, 'FLASH_SALE_NOT_FOUND');
  if (!isRunning(sale)) return fail(422, 'FLASH_SALE_NOT_ACTIVE');
  const bought = d.reservations
    .filter((r) => r.itemId === itemId && r.userId === user.id && r.status !== 'FAILED')
    .reduce((n, r) => n + r.quantity, 0);
  if (bought + quantity > item.perUserLimit) return fail(422, 'FLASH_SALE_LIMIT_REACHED');
  if (item.quota - item.sold < quantity) return fail(409, 'FLASH_SALE_SOLD_OUT');

  // Giữ suất, rồi "tạo đơn" như consumer: trừ kho thật, đơn COD giá sale
  item.sold += quantity;
  const id = crypto.randomUUID();
  const found = d.findVariant(item.variantId);
  let orderCode: string | null = null;
  let reason: string | null = null;
  if (!found || !d.isPurchasable(found.product)) {
    reason = 'PRODUCT_UNAVAILABLE';
  } else if (!d.adjustStock(item.variantId, -quantity)) {
    reason = 'OUT_OF_STOCK';
  } else {
    orderCode = createFlashOrder(d, user, id, item, quantity, address);
  }
  if (reason) item.sold -= quantity;
  d.reservations.push({
    id, userId: user.id, itemId, quantity, status: reason ? 'FAILED' : 'CONFIRMED', orderCode, reason,
    readyAt: Date.now() + FLASH_ORDER_DELAY_MS,
  });
  d.save();
  return ok({ reservationId: id, itemId, status: 'PENDING', orderCode: null, reason: null }, 202);
}

export function reservation(d: MockDb, userId: number, id: string): MockResult {
  const r = d.reservations.find((x) => x.id === id && x.userId === userId);
  if (!r) return fail(404, 'FLASH_SALE_RESERVATION_NOT_FOUND');
  const ready = Date.now() >= r.readyAt;
  return ok({
    reservationId: r.id, itemId: r.itemId, status: ready ? r.status : 'PENDING',
    orderCode: ready ? r.orderCode : null, reason: ready ? r.reason : null,
  });
}

export function adminFlashSales(d: MockDb, query: URLSearchParams): MockResult {
  const page = Math.max(0, Number(query.get('page') ?? 0));
  const size = Math.min(100, Math.max(1, Number(query.get('size') ?? 20)));
  const list = [...d.flashSales].sort((a, b) => b.startAt.localeCompare(a.startAt));
  return ok({ items: list.slice(page * size, page * size + size).map(toView), page, size, totalItems: list.length, totalPages: Math.ceil(list.length / size) });
}

export function createFlashSale(d: MockDb, b: Record<string, unknown>): MockResult {
  const items = (b['items'] as { variantId: number; salePrice: number; quota: number; perUserLimit: number }[] | undefined) ?? [];
  const errors: { field: string; message: string }[] = [];
  if (!String(b['name'] ?? '').trim()) errors.push({ field: 'name', message: 'must not be blank' });
  if (!(Date.parse(String(b['endAt'])) > Date.parse(String(b['startAt'])))) errors.push({ field: 'endAt', message: 'must be after startAt' });
  if (!items.length) errors.push({ field: 'items', message: 'must not be empty' });
  const resolved = items.map((it, i) => {
    const found = d.findVariant(Number(it.variantId));
    if (!found || !d.isPurchasable(found.product)) errors.push({ field: `items[${i}].variantId`, message: 'not found or not purchasable' });
    else if (Number(it.salePrice) >= found.variant.price) errors.push({ field: `items[${i}].salePrice`, message: `must be lower than ${found.variant.price}` });
    return found;
  });
  if (errors.length) return fail(400, 'VALIDATION_ERROR', errors);
  let nextItemId = d.flashSales.flatMap((s) => s.items).reduce((m, x) => Math.max(m, x.id), 0);
  const sale: MockFlashSale = {
    id: d.nextId(d.flashSales), name: String(b['name']).trim(), status: 'ACTIVE',
    startAt: new Date(String(b['startAt'])).toISOString(), endAt: new Date(String(b['endAt'])).toISOString(),
    items: items.map((it, i) => {
      const { product: p, variant: v } = resolved[i]!;
      return {
        id: ++nextItemId, variantId: v.id, productId: p.id, productName: p.name, productSlug: p.slug,
        variantName: Object.values(v.optionValues).join(', ') || null, imageUrl: v.imageUrl ?? p.images[0] ?? null,
        originalPrice: v.price, salePrice: Number(it.salePrice), quota: Number(it.quota), sold: 0, perUserLimit: Number(it.perUserLimit),
      };
    }),
  };
  d.flashSales.push(sale);
  d.save();
  return ok(toView(sale), 201);
}

export function cancelFlashSale(d: MockDb, id: number): MockResult {
  const sale = d.flashSales.find((s) => s.id === id);
  if (!sale) return fail(404, 'FLASH_SALE_NOT_FOUND');
  sale.status = 'CANCELLED';
  d.save();
  return ok(toView(sale));
}

// ----- helpers

function discountFor(v: Voucher, merchandise: number, shipping: number): number {
  const raw = v.type === 'FIXED' ? v.value
    : v.type === 'PERCENT' ? Math.min(Math.floor((merchandise * v.value) / 100), v.maxDiscount ?? Number.MAX_SAFE_INTEGER)
    : Math.min(v.value, shipping);
  return Math.min(raw, v.type === 'FREESHIP' ? shipping : merchandise);
}

function applied(v: Voucher, scope: 'SHOP' | 'PLATFORM', amount: number): AppliedVoucher {
  return { voucherId: v.id, code: v.code, type: v.type, scope, amount };
}

function isRunning(s: MockFlashSale): boolean {
  const now = Date.now();
  return s.status === 'ACTIVE' && Date.parse(s.startAt) <= now && now < Date.parse(s.endAt);
}

function toView(s: MockFlashSale): FlashSale {
  return { ...s, running: isRunning(s), items: s.items.map(({ sold, ...i }) => ({ ...i, remaining: i.quota - sold })) };
}

function createFlashOrder(
  d: MockDb,
  user: MockUser,
  checkoutId: string,
  item: MockFlashSale['items'][number],
  quantity: number,
  address: MockDb['addresses'][number],
): string {
  const found = d.findVariant(item.variantId)!;
  const shop = d.shops.find((s) => s.id === found.product.shop.id)!;
  const now = new Date().toISOString();
  const subtotal = item.salePrice * quantity;
  const shippingFee = 30_000;
  const id = d.nextId(d.orders);
  const order: MockOrder = {
    id, userId: user.id, code: crypto.randomUUID(), checkoutId, status: 'PENDING', shop: d.shopSummary(shop),
    items: [{
      id: id * 100, variantId: item.variantId, productId: item.productId, productName: item.productName ?? found.product.name,
      productSlug: item.productSlug ?? found.product.slug, variantName: item.variantName, imageUrl: item.imageUrl,
      unitPrice: item.salePrice, quantity, lineTotal: subtotal,
    }],
    subtotal, shippingFee, discount: 0, total: subtotal + shippingFee, paymentMethod: 'COD', paymentStatus: 'UNPAID',
    cancellable: true, awaitingPayment: false, paymentExpiresAt: null, shipment: null, vouchers: [],
    shippingAddress: {
      receiverName: address.receiverName, phone: address.phone, provinceCode: address.provinceCode,
      districtCode: address.districtCode, wardCode: address.wardCode, detail: address.detail,
    },
    note: 'Flash sale', history: [{ fromStatus: null, toStatus: 'PENDING', note: null, createdAt: now }], createdAt: now, updatedAt: now,
  };
  d.orders.push(order);
  notifyNewOrder(d, order);
  return order.code;
}
