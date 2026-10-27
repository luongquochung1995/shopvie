import {
  Address,
  AppNotification,
  Banner,
  ChatMessage,
  FlashSale,
  OrderDetail,
  PaymentMethod,
  PickupAddress,
  Review,
  ShopStatus,
  ShopSummary,
  Voucher,
} from '../core/api/api.models';
import { createCatalog, createUsers, MockProduct, MockUser } from './mock-data';

const KEY = {
  users: 'shopvi.mock.users',
  addresses: 'shopvi.mock.addresses',
  /** Giả lập cookie refresh token: id user đang đăng nhập. */
  session: 'shopvi.mock.session',
  cart: 'shopvi.mock.cart',
  orders: 'shopvi.mock.orders',
  /** Tồn kho/số đã bán đã thay đổi so với dữ liệu sinh ban đầu: variantId → stock, productId → sold. */
  stock: 'shopvi.mock.stock',
  sold: 'shopvi.mock.sold',
  shops: 'shopvi.mock.shops',
  /** Sản phẩm seller tạo mới / đã sửa (productId → sản phẩm) và id sản phẩm đã xóa. */
  products: 'shopvi.mock.products',
  deletedProducts: 'shopvi.mock.deletedProducts',
  payments: 'shopvi.mock.payments',
  vouchers: 'shopvi.mock.vouchers',
  voucherUsages: 'shopvi.mock.voucherUsages',
  reviews: 'shopvi.mock.reviews',
  flashSales: 'shopvi.mock.flashSales',
  reservations: 'shopvi.mock.reservations',
  notifications: 'shopvi.mock.notifications',
  conversations: 'shopvi.mock.conversations',
  chatMessages: 'shopvi.mock.chatMessages',
  banners: 'shopvi.mock.banners',
};

export interface MockAddress extends Address {
  userId: number;
  createdAt: number;
}

export interface MockCartItem {
  id: number;
  userId: number;
  variantId: number;
  quantity: number;
  selected: boolean;
  createdAt: number;
}

export interface MockOrder extends OrderDetail {
  id: number;
  userId: number;
}

export interface MockShop {
  id: number;
  ownerId: number;
  name: string;
  slug: string;
  logoUrl: string | null;
  description: string | null;
  ratingAvg: number;
  status: ShopStatus;
  rejectReason: string | null;
  banReason?: string | null;
  pickupAddress: PickupAddress | null;
  createdAt: string;
  updatedAt: string;
}

/** Một lần thanh toán online (cổng giả lập). */
export interface MockPayment {
  ref: string;
  checkoutId: string;
  userId: number;
  method: PaymentMethod;
  amount: number;
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED';
  expiresAt: string;
}

export interface MockVoucherUsage {
  voucherId: number;
  userId: number;
  orderId: number;
  amount: number;
}

export interface MockReview extends Review {
  productId: number;
  shopId: number;
  userId: number;
  orderCode: string | null;
  productName: string;
}

/** Phiên flash sale: items giữ quota + số đã bán; remaining tính khi trả về. */
export interface MockFlashSale extends Omit<FlashSale, 'running' | 'items'> {
  items: (Omit<FlashSale['items'][number], 'remaining'> & { sold: number })[];
}

export interface MockReservation {
  id: string;
  userId: number;
  itemId: number;
  quantity: number;
  status: 'PENDING' | 'CONFIRMED' | 'FAILED';
  orderCode: string | null;
  reason: string | null;
  /** Thời điểm kết quả "xử lý xong" (giả lập tạo đơn bất đồng bộ). */
  readyAt: number;
}

export interface MockNotification extends AppNotification {
  userId: number;
}

export interface MockConversation {
  id: number;
  buyerId: number;
  shopId: number;
  lastMessage: string | null;
  lastMessageAt: string | null;
  buyerUnread: number;
  shopUnread: number;
  buyerLastRead: number | null;
  shopLastRead: number | null;
}

/** localStorage an toàn: không có (SSR) hoặc bị chặn thì chỉ giữ trong bộ nhớ. */
const storage = {
  read<T>(key: string): T | null {
    try {
      const raw = globalThis.localStorage?.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  },
  write(key: string, value: unknown): void {
    try {
      if (value === null) {
        globalThis.localStorage?.removeItem(key);
      } else {
        globalThis.localStorage?.setItem(key, JSON.stringify(value));
      }
    } catch {
      // bỏ qua: dữ liệu mock chỉ sống trong bộ nhớ
    }
  },
};

/** Trạng thái của backend giả: catalog sinh cố định, phần thay đổi (user, giỏ, đơn, tồn kho, shop...) lưu localStorage. */
export class MockDb {
  readonly catalog = createCatalog(new Date());
  users: MockUser[] = storage.read<MockUser[]>(KEY.users) ?? createUsers(new Date());
  addresses: MockAddress[] = storage.read<MockAddress[]>(KEY.addresses) ?? [];
  session: number | null = storage.read<number>(KEY.session);
  cart: MockCartItem[] = storage.read<MockCartItem[]>(KEY.cart) ?? [];
  orders: MockOrder[] = storage.read<MockOrder[]>(KEY.orders) ?? [];
  shops: MockShop[] = storage.read<MockShop[]>(KEY.shops) ?? this.initialShops();
  payments: MockPayment[] = storage.read<MockPayment[]>(KEY.payments) ?? [];
  vouchers: Voucher[] = storage.read<Voucher[]>(KEY.vouchers) ?? [];
  voucherUsages: MockVoucherUsage[] = storage.read<MockVoucherUsage[]>(KEY.voucherUsages) ?? [];
  reviews: MockReview[] = storage.read<MockReview[]>(KEY.reviews) ?? [];
  flashSales: MockFlashSale[] = storage.read<MockFlashSale[]>(KEY.flashSales) ?? [];
  reservations: MockReservation[] = storage.read<MockReservation[]>(KEY.reservations) ?? [];
  notifications: MockNotification[] = storage.read<MockNotification[]>(KEY.notifications) ?? [];
  conversations: MockConversation[] = storage.read<MockConversation[]>(KEY.conversations) ?? [];
  chatMessages: ChatMessage[] = storage.read<ChatMessage[]>(KEY.chatMessages) ?? [];
  banners: Banner[] = storage.read<Banner[]>(KEY.banners) ?? [];
  private stock: Record<number, number> = storage.read(KEY.stock) ?? {};
  private sold: Record<number, number> = storage.read(KEY.sold) ?? {};
  private productOverrides: Record<number, MockProduct> = storage.read(KEY.products) ?? {};
  private deletedProducts: number[] = storage.read(KEY.deletedProducts) ?? [];

  constructor() {
    // Tab khác (vd. seller chat với buyer ở 2 tab) ghi localStorage → nạp lại dữ liệu dùng chung
    globalThis.addEventListener?.('storage', (e: StorageEvent) => {
      if (e.key === KEY.notifications) this.notifications = storage.read<MockNotification[]>(KEY.notifications) ?? [];
      if (e.key === KEY.conversations) this.conversations = storage.read<MockConversation[]>(KEY.conversations) ?? [];
      if (e.key === KEY.chatMessages) this.chatMessages = storage.read<ChatMessage[]>(KEY.chatMessages) ?? [];
      if (e.key === KEY.orders) this.orders = storage.read<MockOrder[]>(KEY.orders) ?? [];
    });
    // Áp sản phẩm seller đã tạo/sửa/xóa lên catalog vừa sinh
    const products = this.catalog.products
      .filter((p) => !this.deletedProducts.includes(p.id))
      .map((p) => this.productOverrides[p.id] ?? p);
    const generatedIds = new Set(this.catalog.products.map((p) => p.id));
    products.push(...Object.values(this.productOverrides).filter((p) => !generatedIds.has(p.id) && !this.deletedProducts.includes(p.id)));
    this.catalog.products.splice(0, this.catalog.products.length, ...products);
    // Áp tồn kho / số đã bán đã lưu
    for (const p of this.catalog.products) {
      p.variants.forEach((v) => (v.stock = this.stock[v.id] ?? v.stock));
      p.totalStock = p.variants.reduce((s, v) => s + v.stock, 0);
      p.soldCount = this.sold[p.id] ?? p.soldCount;
    }
  }

  findVariant(variantId: number): { product: MockProduct; variant: MockProduct['variants'][number] } | undefined {
    for (const product of this.catalog.products) {
      const variant = product.variants.find((v) => v.id === variantId);
      if (variant) {
        return { product, variant };
      }
    }
    return undefined;
  }

  /** Sản phẩm đang bán và shop đang hoạt động. */
  isPurchasable(product: MockProduct): boolean {
    return (product.status ?? 'ACTIVE') === 'ACTIVE' && this.shops.some((s) => s.id === product.shop.id && s.status === 'ACTIVE');
  }

  shopSummary(shop: MockShop): ShopSummary {
    return { id: shop.id, name: shop.name, slug: shop.slug, logoUrl: shop.logoUrl, ratingAvg: shop.ratingAvg };
  }

  /** Trừ (delta âm) / hoàn (delta dương) kho và cập nhật số đã bán tương ứng. */
  adjustStock(variantId: number, delta: number): boolean {
    const found = this.findVariant(variantId);
    if (!found || found.variant.stock + delta < 0) {
      return false;
    }
    found.variant.stock += delta;
    found.product.totalStock += delta;
    found.product.soldCount = Math.max(0, found.product.soldCount - delta);
    this.stock[variantId] = found.variant.stock;
    this.sold[found.product.id] = found.product.soldCount;
    return true;
  }

  /** Lưu sản phẩm seller vừa tạo/sửa (tồn kho tuyệt đối ghi đè số đã lưu trước đó). */
  saveProduct(product: MockProduct): void {
    const index = this.catalog.products.findIndex((p) => p.id === product.id);
    if (index >= 0) {
      this.catalog.products[index] = product;
    } else {
      this.catalog.products.push(product);
    }
    product.variants.forEach((v) => (this.stock[v.id] = v.stock));
    this.sold[product.id] = product.soldCount;
    this.productOverrides[product.id] = product;
    this.save();
  }

  /** Xóa mềm: gỡ khỏi catalog và khỏi giỏ hàng (giống ON DELETE của backend thật). */
  deleteProduct(productId: number): void {
    const [removed] = this.catalog.products.splice(this.catalog.products.findIndex((p) => p.id === productId), 1);
    delete this.productOverrides[productId];
    this.deletedProducts.push(productId);
    const variantIds = new Set(removed?.variants.map((v) => v.id) ?? []);
    this.cart = this.cart.filter((c) => !variantIds.has(c.variantId));
    this.save();
  }

  /** Id lớn nhất trong danh sách (kể cả sản phẩm đã xóa) + 1 để không dùng lại id cũ. */
  nextProductId(): number {
    return Math.max(0, ...this.catalog.products.map((p) => p.id), ...this.deletedProducts) + 1;
  }

  nextVariantId(): number {
    return Math.max(0, ...this.catalog.products.flatMap((p) => p.variants.map((v) => v.id)), ...Object.keys(this.stock).map(Number)) + 1;
  }

  save(): void {
    storage.write(KEY.users, this.users);
    storage.write(KEY.addresses, this.addresses);
    storage.write(KEY.session, this.session);
    storage.write(KEY.cart, this.cart);
    storage.write(KEY.orders, this.orders);
    storage.write(KEY.stock, this.stock);
    storage.write(KEY.sold, this.sold);
    storage.write(KEY.shops, this.shops);
    storage.write(KEY.products, this.productOverrides);
    storage.write(KEY.deletedProducts, this.deletedProducts);
    storage.write(KEY.payments, this.payments);
    storage.write(KEY.vouchers, this.vouchers);
    storage.write(KEY.voucherUsages, this.voucherUsages);
    storage.write(KEY.reviews, this.reviews);
    storage.write(KEY.flashSales, this.flashSales);
    storage.write(KEY.reservations, this.reservations);
    storage.write(KEY.notifications, this.notifications);
    storage.write(KEY.conversations, this.conversations);
    storage.write(KEY.chatMessages, this.chatMessages);
    storage.write(KEY.banners, this.banners);
  }

  nextId(list: { id: number }[]): number {
    return list.reduce((max, x) => Math.max(max, x.id), 0) + 1;
  }

  /**
   * Đọc lại hội thoại / tin nhắn / thông báo từ localStorage (tab khác có thể vừa ghi — vd. seller trả lời ở tab bên cạnh)
   * trước khi xử lý request chat, để không tính "đã xem" trên dữ liệu cũ.
   */
  syncShared(): void {
    this.conversations = storage.read<MockConversation[]>(KEY.conversations) ?? this.conversations;
    this.chatMessages = storage.read<ChatMessage[]>(KEY.chatMessages) ?? this.chatMessages;
    this.notifications = storage.read<MockNotification[]>(KEY.notifications) ?? this.notifications;
  }

  /** Xóa toàn bộ dữ liệu mock đã lưu (về trạng thái ban đầu). */
  static reset(): void {
    Object.values(KEY).forEach((k) => storage.write(k, null));
  }

  /** 3 shop sinh sẵn thuộc seller1..3 (user id 3..5), đang hoạt động, kho ở TP.HCM / Hà Nội / Đà Nẵng (như seed). */
  private initialShops(): MockShop[] {
    const created = new Date().toISOString();
    const pickups: [string, string, string][] = [['79', '26740', '12 Lê Lợi'], ['01', '00004', '25 Kim Mã'], ['48', '20242', '88 Bạch Đằng']];
    return this.catalog.shops.map((s, i) => ({
      ...s, ownerId: 3 + i, description: 'Shop chính hãng, giao hàng nhanh toàn quốc.', status: 'ACTIVE',
      rejectReason: null, createdAt: created, updatedAt: created,
      pickupAddress: { name: s.name, phone: '0901234567', provinceCode: pickups[i][0], wardCode: pickups[i][1], detail: pickups[i][2] },
    }));
  }
}
