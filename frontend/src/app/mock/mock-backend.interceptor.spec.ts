import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Address,
  AdminShop,
  ApiResponse,
  AuthResponse,
  Cart,
  CheckoutPayment,
  CheckoutPreview,
  MyShop,
  OrderDetail,
  Page,
  PaymentLink,
  PlaceOrderResult,
  ProductCard,
  ProductDetail,
  ProductInput,
  SellerOrderDetail,
  SellerProductDetail,
} from '../core/api/api.models';
import { MockOrder } from './mock-db';
import { ApiError } from '../core/api/api-error';
import { errorInterceptor } from '../core/api/error.interceptor';
import { authInterceptor } from '../core/auth/auth.interceptor';
import { AuthStore } from '../core/auth/auth.store';
import { MOCK_PASSWORD } from './mock-data';
import { mockBackendInterceptor, mockDb, resetMockDb } from './mock-backend.interceptor';

const API = environment.apiBaseUrl;

describe('mockBackendInterceptor', () => {
  let http: HttpClient;
  const get = <T>(url: string) => firstValueFrom(http.get<ApiResponse<T>>(API + url)).then((r) => r.data);
  const post = <T>(url: string, body: unknown) => firstValueFrom(http.post<ApiResponse<T>>(API + url, body)).then((r) => r?.data);
  const error = (p: Promise<unknown>) => p.then(() => null, (e: ApiError) => e);

  beforeEach(() => {
    resetMockDb();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor, mockBackendInterceptor(API, 0)])),
      ],
    });
    http = TestBed.inject(HttpClient);
  });

  const login = async (email = 'buyer@shopvi.local') =>
    TestBed.inject(AuthStore).setSession(await post<AuthResponse>('/auth/login', { email, password: MOCK_PASSWORD }));

  it('serves a catalog of 20 categories and 100 products', async () => {
    const roots = await get<{ children: unknown[] }[]>('/categories');
    expect(roots.length + roots.reduce((n, r) => n + r.children.length, 0)).toBe(20);
    expect((await get<Page<ProductCard>>('/products?size=1')).totalItems).toBe(100);
  });

  it('filters by root category (incl. children), price and sorts like the real API', async () => {
    const page = await get<Page<ProductCard>>('/products?categoryId=1&minPrice=200000&sort=minPrice,asc&size=100');
    const prices = page.items.map((p) => p.minPrice);
    expect(page.totalItems).toBeGreaterThan(0);
    expect(prices.every((p) => p >= 200_000)).toBe(true);
    expect(prices).toEqual([...prices].sort((a, b) => a - b));

    expect((await error(get('/products?sort=password,asc')))?.code).toBe('BAD_REQUEST');
    expect((await error(get('/products?categoryId=999')))?.code).toBe('CATEGORY_NOT_FOUND');
  });

  it('returns product detail with variants whose prices match the range', async () => {
    const [card] = (await get<Page<ProductCard>>('/products?size=1')).items;
    const detail = await get<ProductDetail>(`/products/${card.slug}`);

    expect(Math.min(...detail.variants.map((v) => v.price))).toBe(detail.minPrice);
    expect(detail.breadcrumb).toHaveLength(2);
    expect((await error(get('/products/khong-co')))?.status).toBe(404);
  });

  it('rejects wrong password and duplicate email', async () => {
    expect((await error(post('/auth/login', { email: 'buyer@shopvi.local', password: 'x' })))?.code).toBe('INVALID_CREDENTIALS');
    expect((await error(post('/auth/register', { email: 'BUYER@shopvi.local', password: 'secret123', fullName: 'X' })))?.code).toBe(
      'EMAIL_ALREADY_EXISTS',
    );
  });

  it('requires login for /me', async () => {
    expect((await error(get('/me')))?.status).toBe(401);
    await login();
    expect((await get<{ email: string }>('/me')).email).toBe('buyer@shopvi.local');
  });

  it('keeps exactly one default address and persists across reload', async () => {
    await login();
    const addr = (n: string, isDefault = false) => ({
      receiverName: n, phone: '0901234567', provinceCode: '79', wardCode: '26740', detail: n, isDefault,
    });
    const first = await post<Address>('/me/addresses', addr('1'));
    await post<Address>('/me/addresses', addr('2', true));

    let list = await get<Address[]>('/me/addresses');
    expect(list.filter((a) => a.isDefault).map((a) => a.detail)).toEqual(['2']);

    await firstValueFrom(http.delete(`${API}/me/addresses/${list[0].id}`));
    list = await get<Address[]>('/me/addresses');
    expect(list).toHaveLength(1);
    expect(list[0]).toMatchObject({ id: first.id, isDefault: true });
    expect(JSON.parse(localStorage.getItem('shopvi.mock.addresses')!)).toHaveLength(1);
  });

  it('expired access token is refreshed transparently by the auth interceptor', async () => {
    await login();
    const store = TestBed.inject(AuthStore);
    store.setSession({ ...(await post<AuthResponse>('/auth/refresh', null)), accessToken: 'mock-token.2.0' });

    expect((await get<{ id: number }>('/me')).id).toBe(2);
    expect(store.accessToken()).not.toBe('mock-token.2.0');
    expect(mockDb().session).toBe(2);
  });

  it('cart → checkout → cancel keeps stock consistent like the real backend', async () => {
    await login();
    const address = await post<Address>('/me/addresses', {
      receiverName: 'A', phone: '0901234567', provinceCode: '79', wardCode: '26740', detail: '1 Lê Lợi', isDefault: true,
    });
    const products = (await get<Page<ProductCard>>('/products?size=100')).items;
    const details = await Promise.all(products.slice(0, 20).map((p) => get<ProductDetail>(`/products/${p.slug}`)));
    // 2 SKU còn hàng của 2 shop khác nhau
    const picks = details
      .flatMap((p) => p.variants.filter((v) => v.stock >= 3).map((v) => ({ shop: p.shop.id, variant: v })))
      .filter((x, i, all) => all.findIndex((y) => y.shop === x.shop) === i)
      .slice(0, 2);
    expect(picks).toHaveLength(2);

    await post<Cart>('/cart/items', { variantId: picks[0].variant.id, quantity: 2 });
    const cart = await post<Cart>('/cart/items', { variantId: picks[1].variant.id, quantity: 1 });
    expect(cart.itemCount).toBe(2);
    expect(cart.shops).toHaveLength(2);
    const tooMany = await error(post('/cart/items', { variantId: picks[0].variant.id, quantity: picks[0].variant.stock }));
    expect(tooMany?.code).toBe('CART_QUANTITY_EXCEEDS_STOCK');

    const preview = await post<CheckoutPreview>('/checkout/preview', {});
    expect(preview.address?.id).toBe(address.id);
    expect(preview.shippingFee).toBe(60_000);
    expect((await error(post('/checkout', { addressId: address.id, paymentMethod: 'COD', expectedTotal: 1 })))?.code).toBe('PRICE_CHANGED');

    const result = await post<PlaceOrderResult>('/checkout', { addressId: address.id, paymentMethod: 'COD', notes: {}, expectedTotal: preview.total });
    expect(result.orders).toHaveLength(2);
    expect((await get<Cart>('/cart')).itemCount).toBe(0);
    const stockAfter = mockDb().findVariant(picks[0].variant.id)!.variant.stock;
    expect(stockAfter).toBe(picks[0].variant.stock - 2);

    const code = result.orders.find((o) => o.shopId === picks[0].shop)!.code;
    const cancelled = await post<OrderDetail>(`/orders/${code}/cancel`, {});
    expect(cancelled.status).toBe('CANCELLED');
    expect(cancelled.history.map((h) => h.toStatus)).toEqual(['PENDING', 'CANCELLED']);
    expect(mockDb().findVariant(picks[0].variant.id)!.variant.stock).toBe(picks[0].variant.stock);
    expect((await error(post(`/orders/${code}/cancel`, {})))?.code).toBe('INVALID_ORDER_STATUS_TRANSITION');
    expect((await get<Page<unknown>>('/orders?status=PENDING')).totalItems).toBe(1);
  });

  it('online payment: retry after failure, pay, seller ships with tracking; unpaid orders expire', async () => {
    await login();
    const hanoi = await post<Address>('/me/addresses', {
      receiverName: 'B', phone: '0901234567', provinceCode: '01', wardCode: '00004', detail: '1 Kim Mã', isDefault: true,
    });
    // Sản phẩm của shop 1 (kho TP.HCM) giao ra Hà Nội → khác miền 38.000đ
    const product = mockDb().catalog.products.find((p) => p.shop.id === 1 && p.variants.some((v) => v.stock >= 5))!;
    const variant = product.variants.find((v) => v.stock >= 5)!;
    await post<Cart>('/cart/items', { variantId: variant.id, quantity: 1 });
    const preview = await post<CheckoutPreview>('/checkout/preview', {});
    expect(preview.groups[0].shippingFee).toBe(38_000);
    expect(preview.paymentMethods).toEqual(['COD', 'VNPAY', 'MOMO']);

    const placed = await post<PlaceOrderResult>('/checkout', { addressId: hanoi.id, paymentMethod: 'VNPAY', notes: {} });
    expect(placed.paymentUrl).toMatch(/^\/payment\/sandbox\?ref=\w+&method=VNPAY&amount=\d+$/);
    const ref = new URLSearchParams(placed.paymentUrl!.split('?')[1]).get('ref')!;
    const status = () => get<CheckoutPayment>(`/checkout/${placed.checkoutId}/payment`);
    expect((await status()).status).toBe('PENDING');

    const failed = await post<{ redirectUrl: string }>(`/payments/sandbox/${ref}`, { success: false });
    expect(failed.redirectUrl).toBe(`/payment/result?status=failed&checkoutId=${placed.checkoutId}`);
    expect((await status()).status).toBe('FAILED');
    const retry = await post<PaymentLink>(`/checkout/${placed.checkoutId}/payment`, null);
    expect(retry.expiresAt).toBe(placed.paymentExpiresAt);
    await post(`/payments/sandbox/${new URLSearchParams(retry.paymentUrl.split('?')[1]).get('ref')}`, { success: true });
    expect((await status()).status).toBe('PAID');

    // Seller xác nhận → vận đơn; giao → hành trình
    const code = placed.orders[0].code;
    await login('seller1@shopvi.local');
    const confirmed = await post<SellerOrderDetail>(`/seller/orders/${code}/confirm`, {});
    expect(confirmed.shipment?.trackingCode).toMatch(/^SVX\d+$/);
    expect(confirmed.shipment?.fee).toBe(38_000);
    await post(`/seller/orders/${code}/ship`, {});
    const delivered = await post<SellerOrderDetail>(`/seller/orders/${code}/deliver`, {});
    expect(delivered.shipment?.events.map((e) => e.status)).toEqual(['DELIVERED', 'IN_TRANSIT', 'CREATED']);

    // Đơn online khác quá hạn thanh toán → tự hủy, hoàn kho
    await login();
    await post<Cart>('/cart/items', { variantId: variant.id, quantity: 2 });
    const unpaid = await post<PlaceOrderResult>('/checkout', { addressId: hanoi.id, paymentMethod: 'MOMO', notes: {} });
    const stockAfterOrder = mockDb().findVariant(variant.id)!.variant.stock;
    mockDb().orders.filter((o) => o.checkoutId === unpaid.checkoutId).forEach((o) => (o.paymentExpiresAt = new Date(0).toISOString()));
    const expired = await get<OrderDetail>(`/orders/${unpaid.orders[0].code}`);
    expect(expired.status).toBe('CANCELLED');
    expect(expired.history.at(-1)?.note).toBe('Quá hạn thanh toán');
    expect(mockDb().findVariant(variant.id)!.variant.stock).toBe(stockAfterOrder + 2);
    expect((await error(post(`/checkout/${unpaid.checkoutId}/payment`, null)))?.code).toBe('PAYMENT_NOT_REQUIRED');
  });

  it('runs the seller center flow: open shop → approval → product with variants → fulfil order', async () => {
    const auth = TestBed.inject(AuthStore);
    const put = <T>(url: string, body: unknown) => firstValueFrom(http.put<ApiResponse<T>>(API + url, body)).then((r) => r.data);
    await post<AuthResponse>('/auth/register', { email: 'new-seller@example.com', password: 'secret123', fullName: 'Seller' })
      .then((res) => auth.setSession(res));

    // Mở shop: role mới chỉ có sau khi làm mới token (như JWT thật)
    const shop = await post<MyShop>('/seller/shop/register', { name: 'Shop Mới Toanh' });
    expect(shop.status).toBe('PENDING');
    expect(shop.slug).toBe('shop-moi-toanh');
    expect((await error(get('/seller/shop')))?.code).toBe('FORBIDDEN');
    await firstValueFrom(auth.refresh());
    expect(auth.role()).toBe('SELLER');
    const leafId = (await get<{ children: { id: number }[] }[]>('/categories'))[0].children[0].id;
    const product: ProductInput = {
      categoryId: leafId, name: 'Áo thun mock', description: null, attributes: { 'Chất liệu': 'Cotton' },
      images: ['https://img.example/1.jpg'], variantOptions: [{ name: 'Màu', values: ['Đỏ', 'Xanh'] }],
      variants: [
        { id: null, optionValues: { Màu: 'Đỏ' }, price: 100_000, stock: 5, sku: null, imageUrl: null },
        { id: null, optionValues: { Màu: 'Xanh' }, price: 120_000, stock: 3, sku: null, imageUrl: null },
      ],
    };
    expect((await error(post('/seller/products', product)))?.code).toBe('SHOP_NOT_ACTIVE');

    // Admin duyệt
    const sellerSession = auth.accessToken()!;
    const sellerUser = auth.user()!;
    await login('admin@shopvi.local');
    const pending = await get<Page<AdminShop>>('/admin/shops?status=PENDING');
    expect(pending.items.map((s) => s.id)).toContain(shop.id);
    expect((await post<AdminShop>(`/admin/shops/${shop.id}/approve`, null)).status).toBe('ACTIVE');
    auth.setSession({ accessToken: sellerSession, tokenType: 'Bearer', expiresIn: 900, user: sellerUser });

    // Đăng sản phẩm: thiếu tổ hợp → lỗi; đủ → hiển thị phía người mua
    expect((await error(post('/seller/products', { ...product, variants: product.variants.slice(0, 1) })))?.code).toBe('VALIDATION_ERROR');
    const created = await post<SellerProductDetail>('/seller/products', product);
    expect(created.slug).toBe(`ao-thun-mock-${created.id}`);
    expect(created.minPrice).toBe(100_000);
    expect(created.variants.every((v) => v.sku.startsWith(`S${shop.id}-`))).toBe(true);
    expect((await get<ProductDetail>(`/products/${created.slug}`)).shop.id).toBe(shop.id);
    await firstValueFrom(http.patch(`${API}/seller/products/${created.id}/visibility`, { visible: false }));
    expect((await error(get(`/products/${created.slug}`)))?.status).toBe(404);
    const updated = await put<SellerProductDetail>(`/seller/products/${created.id}`, {
      ...product, variants: [{ ...product.variants[0], id: created.variants[0].id, stock: 9 }, product.variants[1]],
    });
    expect(updated.status).toBe('HIDDEN');
    expect(updated.variants[0]).toMatchObject({ id: created.variants[0].id, stock: 9 });

    // Đơn của shop: xác nhận → giao → đã giao (COD → đã thanh toán)
    const db = mockDb();
    db.orders.push({ ...structuredClone(db.orders[0] ?? sampleOrder()), id: 999, code: 'shop-order', status: 'PENDING', shop: db.shopSummary(db.shops.find((s) => s.id === shop.id)!) });
    expect((await get<Record<string, number>>('/seller/orders/counts'))['PENDING']).toBe(1);
    await post('/seller/orders/shop-order/confirm', {});
    await post('/seller/orders/shop-order/ship', { note: 'GHN' });
    const delivered = await post<SellerOrderDetail>('/seller/orders/shop-order/deliver', {});
    expect(delivered.status).toBe('DELIVERED');
    expect(delivered.paymentStatus).toBe('PAID');
    expect(delivered.actions).toEqual([]);
    expect((await error(post('/seller/orders/shop-order/cancel', { reason: 'x' })))?.code).toBe('INVALID_ORDER_STATUS_TRANSITION');
  });
});

function sampleOrder(): MockOrder {
  const at = new Date().toISOString();
  return {
    id: 1, userId: 2, code: 'x', checkoutId: 'c', status: 'PENDING', shop: null,
    items: [{ id: 100, variantId: null, productId: 1, productName: 'Áo', productSlug: 'ao-1', variantName: null, imageUrl: null, unitPrice: 100_000, quantity: 1, lineTotal: 100_000 }],
    subtotal: 100_000, shippingFee: 30_000, discount: 0, total: 130_000, paymentMethod: 'COD', paymentStatus: 'UNPAID', cancellable: true,
    awaitingPayment: false, paymentExpiresAt: null, shipment: null, vouchers: [],
    shippingAddress: { receiverName: 'A', phone: '0901234567', provinceCode: '79', districtCode: null, wardCode: '26740', detail: '1 Lê Lợi' },
    note: null, history: [{ fromStatus: null, toStatus: 'PENDING', note: null, createdAt: at }], createdAt: at, updatedAt: at,
  };
}
