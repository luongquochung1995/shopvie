import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Address,
  AdminOverview,
  AdminUser,
  ApiResponse,
  AuthResponse,
  ChatMessage,
  ChatPush,
  Conversation,
  MessagePage,
  NotificationPage,
  NotificationPush,
  Page,
  PlaceOrderResult,
} from '../core/api/api.models';
import { ApiError } from '../core/api/api-error';
import { errorInterceptor } from '../core/api/error.interceptor';
import { authInterceptor } from '../core/auth/auth.interceptor';
import { AuthStore } from '../core/auth/auth.store';
import { RealtimeService } from '../core/realtime/realtime.service';
import { mockBackendInterceptor, mockDb, resetMockDb } from './mock-backend.interceptor';
import { MOCK_PASSWORD } from './mock-data';
import { MockRealtimeService } from './mock-realtime';

const API = environment.apiBaseUrl;

describe('mock backend — Phase 6', () => {
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
        { provide: RealtimeService, useClass: MockRealtimeService },
      ],
    });
    http = TestBed.inject(HttpClient);
  });

  const login = async (email: string) =>
    TestBed.inject(AuthStore).setSession(await post<AuthResponse>('/auth/login', { email, password: MOCK_PASSWORD }));

  /** Người mua đặt đơn COD một sản phẩm của shop 1 (seller1); trả mã đơn. */
  async function buyerOrder(): Promise<string> {
    await login('buyer@shopvi.local');
    const addr = await post<Address>('/me/addresses', {
      receiverName: 'A', phone: '0901234567', provinceCode: '79', wardCode: '26740', detail: '1 Lê Lợi', isDefault: true,
    });
    const variant = mockDb().catalog.products.find((p) => p.shop.id === mockDb().shops[0].id)!.variants.find((v) => v.stock > 0)!;
    await post('/cart/items', { variantId: variant.id, quantity: 1 });
    return (await post<PlaceOrderResult>('/checkout', { addressId: addr.id, paymentMethod: 'COD' })).orders[0].code;
  }

  it('notifies the seller of a new order and the buyer when the seller confirms it', async () => {
    const code = await buyerOrder();
    const sellerEmail = mockDb().users.find((u) => u.id === mockDb().shops[0].ownerId)!.email;

    await login(sellerEmail);
    const sellerList = await get<NotificationPage>('/notifications');
    expect(sellerList.items[0].type).toBe('NEW_ORDER');
    expect(sellerList.unreadCount).toBe(1);

    await post(`/seller/orders/${code}/confirm`, {});
    await post(`/seller/orders/${code}/ship`, { note: 'GHN' });
    await login('buyer@shopvi.local');
    const buyerList = await get<NotificationPage>('/notifications');
    expect(buyerList.items.map((n) => n.params['status'])).toEqual(['SHIPPING', 'CONFIRMED']);
    await post('/notifications/read-all', null);
    expect((await get<NotificationPage>('/notifications')).unreadCount).toBe(0);
  });

  it('chats both ways with unread counts and read receipts', async () => {
    const realtime = TestBed.inject(RealtimeService);
    await login('buyer@shopvi.local');
    const shopId = mockDb().shops[0].id;
    const conversation = await post<Conversation>('/chat/conversations', { shopId });
    expect(conversation.side).toBe('BUYER');
    const buyerPush = firstValueFrom(realtime.chat$);
    await post<ChatMessage>(`/chat/conversations/${conversation.id}/messages`, { content: 'Còn hàng không shop?' });
    expect(((await buyerPush) as ChatPush).message?.content).toBe('Còn hàng không shop?');

    const sellerEmail = mockDb().users.find((u) => u.id === mockDb().shops[0].ownerId)!.email;
    await login(sellerEmail);
    expect((await error(post('/chat/conversations', { shopId })))?.code).toBe('CHAT_WITH_OWN_SHOP');
    const [sellerView] = (await get<Page<Conversation>>('/chat/conversations')).items;
    expect(sellerView.side).toBe('SHOP');
    expect(sellerView.unread).toBe(1);
    const reply = await post<ChatMessage>(`/chat/conversations/${conversation.id}/messages`, { content: 'Còn ạ' });

    await login('buyer@shopvi.local');
    await post(`/chat/conversations/${conversation.id}/read`, null);
    await login(sellerEmail);
    const page = await get<MessagePage>(`/chat/conversations/${conversation.id}/messages`);
    expect(page.items.map((m) => m.content)).toEqual(['Còn hàng không shop?', 'Còn ạ']);
    expect(page.otherLastReadMessageId).toBe(reply.id);
  });

  it('lets admins lock accounts, ban products and see an overview', async () => {
    await login('admin@shopvi.local');
    const buyer = (await get<Page<AdminUser>>('/admin/users?q=buyer@')).items[0];
    expect((await post<AdminUser>(`/admin/users/${buyer.id}/lock`, { reason: 'Spam' })).status).toBe('LOCKED');
    expect((await error(post('/auth/login', { email: 'buyer@shopvi.local', password: MOCK_PASSWORD })))?.code).toBe('ACCOUNT_LOCKED');
    await login('admin@shopvi.local');
    expect((await error(post('/admin/users/1/lock', { reason: 'x' })))?.code).toBe('CANNOT_LOCK_ADMIN');

    const product = mockDb().catalog.products[0];
    await post(`/admin/products/${product.id}/ban`, { reason: 'Hàng giả' });
    expect((await error(get(`/products/${product.slug}`)))?.status).toBe(404);
    await post(`/admin/products/${product.id}/unban`, null);
    expect((await get<{ id: number }>(`/products/${product.slug}`)).id).toBe(product.id);

    const stats = await get<AdminOverview>('/admin/stats?days=7');
    expect(stats.orders.daily).toHaveLength(7);
    expect(stats.users.locked).toBe(1);
  });

  it('pushes notifications only to the logged-in user', async () => {
    const realtime = TestBed.inject(RealtimeService);
    await login('admin@shopvi.local');
    const received: NotificationPush[] = [];
    const sub = realtime.notifications$.subscribe((p) => received.push(p));
    const shop = mockDb().shops[0];
    await post(`/admin/shops/${shop.id}/ban`, { reason: 'Vi phạm' });
    await new Promise((r) => setTimeout(r, 10));
    expect(received).toHaveLength(0); // thông báo gửi chủ shop, không phải admin
    await post(`/admin/shops/${shop.id}/unban`, null);
    sub.unsubscribe();
    const owner = mockDb().notifications.filter((n) => n.userId === shop.ownerId).map((n) => n.type);
    expect(owner).toEqual(['SHOP_BANNED', 'SHOP_UNBANNED']);
  });
});
