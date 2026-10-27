import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Address,
  ApiResponse,
  AuthResponse,
  CheckoutPreview,
  FlashSale,
  FlashSaleReservation,
  OrderDetail,
  Page,
  PlaceOrderResult,
  ProductReviews,
  SearchHit,
  Suggestion,
} from '../core/api/api.models';
import { ApiError } from '../core/api/api-error';
import { errorInterceptor } from '../core/api/error.interceptor';
import { authInterceptor } from '../core/auth/auth.interceptor';
import { AuthStore } from '../core/auth/auth.store';
import { mockBackendInterceptor, mockDb, resetMockDb } from './mock-backend.interceptor';
import { MOCK_PASSWORD } from './mock-data';
import { fold } from './mock-search';
import { maskName } from './mock-review';

const API = environment.apiBaseUrl;

describe('mock backend — Phase 5', () => {
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
  const address = () =>
    post<Address>('/me/addresses', {
      receiverName: 'Nguyễn Văn An', phone: '0901234567', provinceCode: '79', wardCode: '26740', detail: '1 Lê Lợi', isDefault: true,
    });

  it('searches without diacritics and highlights matches', async () => {
    const product = mockDb().catalog.products.find((p) => fold(p.name) !== p.name.toLowerCase())!;
    const keyword = fold(product.name).split(' ').slice(0, 2).join(' ');
    const page = await get<Page<SearchHit>>(`/search?q=${encodeURIComponent(keyword)}&size=60`);
    expect(page.items.map((h) => h.id)).toContain(product.id);
    expect(page.items.find((h) => h.id === product.id)?.highlightedName).toContain('<mark>');
    const suggestions = await get<Suggestion[]>(`/search/suggest?q=${encodeURIComponent(keyword)}`);
    expect(suggestions.length).toBeGreaterThan(0);
  });

  it('applies a platform voucher at checkout and returns it when the order is cancelled', async () => {
    await login();
    const addr = await address();
    const variant = mockDb().catalog.products.flatMap((p) => p.variants).find((v) => v.price >= 100_000 && v.stock > 2)!;
    await post('/cart/items', { variantId: variant.id, quantity: 1 });

    const preview = await post<CheckoutPreview>('/checkout/preview', { addressId: addr.id, voucherCodes: ['shopvie20k'] });
    expect(preview.discount).toBe(20_000);
    expect(preview.voucherCodes).toEqual(['SHOPVIE20K']);

    const placed = await post<PlaceOrderResult>('/checkout', {
      addressId: addr.id, paymentMethod: 'COD', expectedTotal: preview.total, voucherCodes: ['SHOPVIE20K'],
    });
    const order = await get<OrderDetail>(`/orders/${placed.orders[0].code}`);
    expect(order.discount).toBe(20_000);
    expect(order.vouchers[0].code).toBe('SHOPVIE20K');

    // Mỗi người 1 lần → lần sau báo lý do
    await post('/cart/items', { variantId: variant.id, quantity: 1 });
    const reused = await error(post('/checkout/preview', { voucherCodes: ['SHOPVIE20K'] }));
    expect(reused?.code).toBe('VOUCHER_NOT_APPLICABLE');
    expect(reused?.details[0].message).toBe('USER_LIMIT');

    await post(`/orders/${order.code}/cancel`, {});
    expect((await post<CheckoutPreview>('/checkout/preview', { voucherCodes: ['SHOPVIE20K'] })).discount).toBe(20_000);
  });

  it('reviews a completed order item once and updates the product rating', async () => {
    await login();
    const addr = await address();
    const product = mockDb().catalog.products.find((p) => p.variants.some((v) => v.stock > 0))!;
    const variant = product.variants.find((v) => v.stock > 0)!;
    await post('/cart/items', { variantId: variant.id, quantity: 1 });
    const placed = await post<PlaceOrderResult>('/checkout', { addressId: addr.id, paymentMethod: 'COD' });
    const code = placed.orders[0].code;
    const order = await get<OrderDetail>(`/orders/${code}`);

    expect((await error(post(`/orders/${code}/items/${order.items[0].id}/review`, { rating: 5 })))?.code).toBe('REVIEW_NOT_ALLOWED');
    mockDb().orders.find((o) => o.code === code)!.status = 'DELIVERED';
    expect((await post<OrderDetail>(`/orders/${code}/complete`, null)).status).toBe('COMPLETED');

    await post(`/orders/${code}/items/${order.items[0].id}/review`, { rating: 4, content: 'Đẹp' });
    expect((await error(post(`/orders/${code}/items/${order.items[0].id}/review`, { rating: 1 })))?.code).toBe('REVIEW_ALREADY_EXISTS');
    const reviews = await get<ProductReviews>(`/products/${product.slug}/reviews`);
    expect(reviews.summary.ratingCount).toBe(1);
    expect(reviews.items[0].reviewerName).toBe(maskName('Người Mua Demo'));
  });

  it('sells a flash sale slot once per user and creates a COD order at the sale price', async () => {
    await login();
    await address();
    const [sale] = await get<FlashSale[]>('/flash-sales/current');
    expect(sale.running).toBe(true);
    const item = sale.items[0];

    const r = await post<FlashSaleReservation>(`/flash-sales/items/${item.id}/purchase`, { quantity: 1 });
    expect(r.status).toBe('PENDING');
    expect((await error(post(`/flash-sales/items/${item.id}/purchase`, { quantity: 1 })))?.code).toBe('FLASH_SALE_LIMIT_REACHED');

    mockDb().reservations[0].readyAt = 0; // bỏ qua độ trễ giả lập
    const done = await get<FlashSaleReservation>(`/flash-sales/reservations/${r.reservationId}`);
    expect(done.status).toBe('CONFIRMED');
    const order = await get<OrderDetail>(`/orders/${done.orderCode}`);
    expect(order.items[0].unitPrice).toBe(item.salePrice);
    expect((await get<FlashSale[]>('/flash-sales/current'))[0].items[0].remaining).toBe(item.remaining - 1);
  });
});
