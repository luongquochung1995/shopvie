import { CheckoutPaymentStatus, PaymentMethod, ShipmentStatus } from '../core/api/api.models';
import { fail, MockResult, ok } from './mock-commerce';
import { MockDb, MockOrder } from './mock-db';
import { notify, notifyNewOrder, notifyOrderStatus } from './mock-social';

/**
 * Thanh toán online (cổng giả lập) và vận chuyển của backend giả — cùng quy tắc với backend thật (P4):
 * đơn online chờ thanh toán 15 phút rồi tự hủy + hoàn kho, thanh toán lại được trong hạn, phí ship theo vùng,
 * vận đơn tạo khi seller xác nhận.
 */

export const PAYMENT_TTL_MS = 15 * 60 * 1000;
export const ONLINE_METHODS: PaymentMethod[] = ['VNPAY', 'MOMO'];
const RATES = { sameProvince: 22_000, sameRegion: 30_000, otherRegion: 38_000, extraPerHalfKg: 5_000 };
const ITEM_WEIGHT_GRAMS = 500;

/** Vùng theo mã tỉnh: Bắc (< 38), Trung & Tây Nguyên (38–74), Nam (≥ 75). */
function region(provinceCode: string | undefined): number {
  const code = Number(provinceCode);
  return Number.isNaN(code) || !provinceCode ? -1 : code < 38 ? 0 : code < 75 ? 1 : 2;
}

/** Phí ship nội bộ như backend: theo vùng cho kiện ≤ 1kg, +5.000đ mỗi 500g tiếp theo. */
export function zoneFee(fromProvince: string | undefined, toProvince: string, quantity: number): number {
  const weight = Math.max(1, quantity) * ITEM_WEIGHT_GRAMS;
  const base =
    fromProvince === toProvince ? RATES.sameProvince
      : region(fromProvince) >= 0 && region(fromProvince) === region(toProvince) ? RATES.sameRegion
        : RATES.otherRegion;
  return base + (weight <= 1000 ? 0 : Math.ceil((weight - 1000) / 500)) * RATES.extraPerHalfKg;
}

export function startPayment(d: MockDb, userId: number, checkoutId: string, method: PaymentMethod, amount: number, expiresAt: string) {
  const ref = crypto.randomUUID().replaceAll('-', '');
  d.payments.push({ ref, checkoutId, userId, method, amount, status: 'PENDING', expiresAt });
  return { paymentUrl: `/payment/sandbox?ref=${ref}&method=${method}&amount=${amount}`, expiresAt };
}

/** Hủy đơn online quá hạn thanh toán (backend thật chạy job định kỳ; mock kiểm tra mỗi request). */
export function expireUnpaid(d: MockDb): void {
  const now = Date.now();
  let changed = false;
  for (const o of d.orders) {
    if (o.awaitingPayment && o.paymentExpiresAt && Date.parse(o.paymentExpiresAt) <= now) {
      setStatus(o, 'CANCELLED', 'Quá hạn thanh toán');
      notifyOrderStatus(d, o, 'system', 'Quá hạn thanh toán');
      o.items.forEach((i) => i.variantId && d.adjustStock(i.variantId, i.quantity));
      d.payments.filter((p) => p.checkoutId === o.checkoutId && p.status === 'PENDING').forEach((p) => (p.status = 'EXPIRED'));
      changed = true;
    }
  }
  if (changed) d.save();
}

/** Cổng giả lập: ghi nhận kết quả và trả URL trang kết quả (backend thật trả return URL đã ký). */
export function completeSandbox(d: MockDb, userId: number, ref: string, success: unknown): MockResult {
  const payment = d.payments.find((p) => p.ref === ref && p.userId === userId);
  if (!payment) return fail(404, 'PAYMENT_NOT_FOUND');
  if (typeof success !== 'boolean') return fail(400, 'VALIDATION_ERROR', [{ field: 'success', message: 'invalid' }]);
  if (success) {
    payment.status = 'SUCCESS';
    d.orders.filter((o) => o.checkoutId === payment.checkoutId).forEach((o) => {
      if (o.paymentStatus === 'UNPAID') {
        o.paymentStatus = 'PAID';
        notify(d, o.userId, 'PAYMENT_SUCCEEDED', { orderCode: o.code, total: o.total }, `/account/orders/${o.code}`);
        notifyNewOrder(d, o);
      }
      o.awaitingPayment = false;
      o.paymentExpiresAt = null;
    });
  } else if (payment.status === 'PENDING') {
    payment.status = 'FAILED';
  }
  d.save();
  return ok({ redirectUrl: `/payment/result?status=${success ? 'success' : 'failed'}&checkoutId=${payment.checkoutId}` });
}

export function checkoutPayment(d: MockDb, userId: number, checkoutId: string): MockResult {
  const orders = d.orders.filter((o) => o.checkoutId === checkoutId && o.userId === userId);
  if (!orders.length) return fail(404, 'ORDER_NOT_FOUND');
  const method = orders[0].paymentMethod as PaymentMethod;
  const latest = d.payments.filter((p) => p.checkoutId === checkoutId).at(-1);
  let status: CheckoutPaymentStatus;
  if (method === 'COD') status = 'NOT_REQUIRED';
  else if (orders.some((o) => o.awaitingPayment)) status = latest?.status === 'FAILED' ? 'FAILED' : 'PENDING';
  else if (orders.some((o) => o.paymentStatus !== 'UNPAID')) status = 'PAID';
  else status = 'CANCELLED';
  return ok({
    checkoutId, paymentMethod: method, status, total: orders.reduce((s, o) => s + o.total, 0),
    expiresAt: status === 'PENDING' || status === 'FAILED' ? orders[0].paymentExpiresAt : null,
    orderCodes: orders.map((o) => o.code),
  });
}

export function retryPayment(d: MockDb, userId: number, checkoutId: string): MockResult {
  const orders = d.orders.filter((o) => o.checkoutId === checkoutId && o.userId === userId);
  if (!orders.length) return fail(404, 'ORDER_NOT_FOUND');
  const awaiting = orders.filter((o) => o.awaitingPayment);
  if (!awaiting.length) return fail(409, 'PAYMENT_NOT_REQUIRED');
  const expiresAt = awaiting[0].paymentExpiresAt!;
  if (Date.parse(expiresAt) <= Date.now()) return fail(422, 'PAYMENT_EXPIRED');
  const link = startPayment(d, userId, checkoutId, awaiting[0].paymentMethod as PaymentMethod,
    awaiting.reduce((s, o) => s + o.total, 0), expiresAt);
  d.save();
  return ok(link);
}

// ----- Vận chuyển

export function createShipment(d: MockDb, order: MockOrder): void {
  const shop = d.shops.find((s) => s.id === order.shop?.id);
  const from = shop?.pickupAddress?.provinceCode;
  const to = order.shippingAddress.provinceCode;
  const days = from === to ? 1 : region(from) === region(to) ? 2 : 4;
  const now = new Date();
  order.shipment = {
    provider: 'INTERNAL',
    trackingCode: `SVX${Math.floor(100_000_000 + Math.random() * 899_999_999)}`,
    status: 'CREATED',
    fee: zoneFee(from, to, order.items.reduce((n, i) => n + i.quantity, 0)),
    expectedDeliveryAt: new Date(now.getTime() + days * 86_400_000).toISOString(),
    events: [{ status: 'CREATED', description: 'Đã tạo vận đơn, chờ lấy hàng', occurredAt: now.toISOString() }],
  };
}

const SHIPMENT_TEXT: Record<ShipmentStatus, string> = {
  CREATED: 'Đã tạo vận đơn, chờ lấy hàng',
  PICKED_UP: 'Đơn vị vận chuyển đã lấy hàng',
  IN_TRANSIT: 'Đơn hàng đang được vận chuyển',
  DELIVERED: 'Giao hàng thành công',
  FAILED: 'Giao hàng không thành công',
  RETURNED: 'Đơn hàng đang được hoàn về người bán',
  CANCELLED: 'Đã hủy vận đơn',
};

/** Thêm bước hành trình (mới nhất trước); vận đơn đã kết thúc thì bỏ qua. */
export function recordShipment(order: MockOrder, status: ShipmentStatus, note?: string | null): void {
  const s = order.shipment;
  if (!s || ['DELIVERED', 'RETURNED', 'CANCELLED'].includes(s.status)) return;
  s.status = status;
  s.events.unshift({ status, description: note || SHIPMENT_TEXT[status], occurredAt: new Date().toISOString() });
}

export function setStatus(o: MockOrder, status: MockOrder['status'], note: string | null): void {
  const now = new Date().toISOString();
  o.history.push({ fromStatus: o.status, toStatus: status, note, createdAt: now });
  o.status = status;
  o.cancellable = status === 'PENDING' || status === 'CONFIRMED';
  o.updatedAt = now;
  if (status === 'CANCELLED') {
    if (o.paymentMethod !== 'COD' && o.paymentStatus === 'PAID') o.paymentStatus = 'REFUNDED';
    o.awaitingPayment = false;
    o.paymentExpiresAt = null;
    recordShipment(o, 'CANCELLED', note);
  }
  if (status === 'DELIVERED' && o.paymentMethod === 'COD') o.paymentStatus = 'PAID';
}
