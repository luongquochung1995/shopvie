/** Kiểu dữ liệu khớp với API backend (/api/v1). Tiền tệ: số nguyên VND. */

export interface ApiResponse<T> {
  success: true;
  data: T;
  message: string | null;
}

export interface ApiErrorDetail {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  success: false;
  error: { code: string; message: string; details: ApiErrorDetail[] };
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN';

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface Profile {
  id: number;
  email: string;
  phone: string | null;
  fullName: string;
  avatarUrl: string | null;
  role: UserRole;
  createdAt: string;
}

export interface Address {
  id: number;
  receiverName: string;
  phone: string;
  provinceCode: string;
  districtCode: string | null;
  wardCode: string;
  detail: string;
  isDefault: boolean;
}

export type AddressInput = Omit<Address, 'id' | 'districtCode'>;

export interface CategoryNode {
  id: number;
  parentId: number | null;
  name: string;
  slug: string;
  level: number;
  position: number;
  iconUrl: string | null;
  children: CategoryNode[];
}

export interface ProductCard {
  id: number;
  name: string;
  slug: string;
  thumbnailUrl: string | null;
  minPrice: number;
  maxPrice: number;
  soldCount: number;
  ratingAvg: number;
  ratingCount: number;
  createdAt: string;
}

export interface VariantOption {
  name: string;
  values: string[];
}

export interface Variant {
  id: number;
  sku: string;
  optionValues: Record<string, string>;
  price: number;
  stock: number;
  imageUrl: string | null;
}

export interface ShopSummary {
  id: number;
  name: string;
  slug: string;
  logoUrl: string | null;
  ratingAvg: number;
}

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  attributes: Record<string, unknown>;
  minPrice: number;
  maxPrice: number;
  totalStock: number;
  soldCount: number;
  ratingAvg: number;
  ratingCount: number;
  breadcrumb: { id: number; name: string; slug: string }[];
  images: string[];
  variantOptions: VariantOption[];
  variants: Variant[];
  shop: ShopSummary;
}

/** createdAt = mới nhất, soldCount = bán chạy, minPrice = giá. */
export type ProductSort = 'createdAt,desc' | 'soldCount,desc' | 'minPrice,asc' | 'minPrice,desc';

export interface ProductQuery {
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

export interface UploadedMedia {
  url: string;
  contentType: string;
  size: number;
}

// ===== Cart =====

export interface CartItem {
  id: number;
  variantId: number;
  productId: number;
  productName: string;
  productSlug: string;
  variantName: string | null;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  stock: number;
  selected: boolean;
  /** Còn bán và đủ hàng; dòng không available không được tính tiền. */
  available: boolean;
  lineTotal: number;
}

export interface CartShopGroup {
  shop: ShopSummary;
  items: CartItem[];
}

export interface Cart {
  shops: CartShopGroup[];
  itemCount: number;
  selectedQuantity: number;
  selectedSubtotal: number;
}

// ===== Checkout =====

export interface AddressSnapshot {
  id: number;
  receiverName: string;
  phone: string;
  provinceCode: string;
  districtCode: string | null;
  wardCode: string;
  detail: string;
}

export interface CheckoutLine {
  variantId: number;
  productId: number;
  productName: string;
  productSlug: string;
  variantName: string | null;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  stock: number;
  available: boolean;
  lineTotal: number;
}

export interface CheckoutGroup {
  shop: ShopSummary;
  items: CheckoutLine[];
  subtotal: number;
  shippingFee: number;
  discount: number;
  total: number;
  vouchers: AppliedVoucher[];
}

export interface CheckoutPreview {
  address: AddressSnapshot | null;
  groups: CheckoutGroup[];
  subtotal: number;
  shippingFee: number;
  discount: number;
  total: number;
  paymentMethods: string[];
  hasUnavailableItems: boolean;
  /** Mã voucher đã áp được (chuẩn hóa chữ hoa). */
  voucherCodes: string[];
}

export interface PlaceOrderRequest {
  addressId: number;
  paymentMethod: string;
  /** shopId → lời nhắn cho shop */
  notes: Record<number, string>;
  /** Tổng tiền đã xem ở bước preview; khác thực tế → 409 PRICE_CHANGED */
  expectedTotal: number;
  voucherCodes?: string[];
}

export interface PlaceOrderResult {
  checkoutId: string;
  orders: { code: string; shopId: number; total: number }[];
  total: number;
  /** URL cổng thanh toán (null với COD) — chuyển người mua tới đây. */
  paymentUrl: string | null;
  paymentExpiresAt: string | null;
}

export type PaymentMethod = 'COD' | 'VNPAY' | 'MOMO';

export type CheckoutPaymentStatus = 'NOT_REQUIRED' | 'PENDING' | 'FAILED' | 'PAID' | 'CANCELLED';

export interface CheckoutPayment {
  checkoutId: string;
  paymentMethod: PaymentMethod;
  status: CheckoutPaymentStatus;
  total: number;
  /** Hạn thanh toán (khi PENDING/FAILED). */
  expiresAt: string | null;
  orderCodes: string[];
}

export interface PaymentLink {
  paymentUrl: string;
  expiresAt: string;
}

// ===== Shipping =====

export type ShipmentStatus = 'CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'RETURNED' | 'CANCELLED';

export interface ShipmentEvent {
  status: ShipmentStatus;
  description: string | null;
  occurredAt: string;
}

export interface Shipment {
  /** GHN | INTERNAL */
  provider: string;
  trackingCode: string;
  status: ShipmentStatus;
  fee: number | null;
  expectedDeliveryAt: string | null;
  /** Mới nhất trước. */
  events: ShipmentEvent[];
}

// ===== Order =====

export const ORDER_STATUSES = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'RETURNED'] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

export interface OrderItem {
  id: number;
  variantId: number | null;
  productId: number;
  productName: string;
  productSlug: string;
  variantName: string | null;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface OrderSummary {
  code: string;
  checkoutId: string;
  status: OrderStatus;
  shop: ShopSummary | null;
  items: OrderItem[];
  subtotal: number;
  shippingFee: number;
  discount: number;
  total: number;
  paymentMethod: string;
  paymentStatus: string;
  cancellable: boolean;
  /** Đơn online chưa thanh toán (thanh toán lại được tới paymentExpiresAt). */
  awaitingPayment: boolean;
  paymentExpiresAt: string | null;
  createdAt: string;
}

export interface ShippingAddress {
  receiverName: string;
  phone: string;
  provinceCode: string;
  districtCode: string | null;
  wardCode: string;
  detail: string;
}

export interface OrderHistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  note: string | null;
  createdAt: string;
}

export interface OrderDetail extends OrderSummary {
  shippingAddress: ShippingAddress;
  note: string | null;
  history: OrderHistoryEntry[];
  /** Vận đơn (null khi shop chưa xác nhận). */
  shipment: Shipment | null;
  vouchers: OrderVoucher[];
  updatedAt: string;
}

// ===== Seller Center =====

export const SHOP_STATUSES = ['PENDING', 'ACTIVE', 'REJECTED', 'BANNED'] as const;
export type ShopStatus = (typeof SHOP_STATUSES)[number];

/** Shop của seller đang đăng nhập. */
export interface MyShop {
  id: number;
  name: string;
  slug: string;
  logoUrl: string | null;
  description: string | null;
  ratingAvg: number;
  status: ShopStatus;
  /** Lý do admin từ chối (khi REJECTED). */
  rejectReason: string | null;
  pickupAddress: PickupAddress | null;
  createdAt: string;
  updatedAt: string;
}

/** Địa chỉ lấy hàng của shop. */
export interface PickupAddress {
  name: string;
  phone: string;
  provinceCode: string;
  wardCode: string;
  detail: string;
}

export interface ShopInfoInput {
  name: string;
  description: string | null;
  logoUrl: string | null;
}

/** Shop trong trang duyệt của admin. */
export interface AdminShop {
  id: number;
  name: string;
  slug: string;
  logoUrl: string | null;
  description: string | null;
  status: ShopStatus;
  rejectReason: string | null;
  /** Lý do khóa (khi BANNED). */
  banReason: string | null;
  owner: { id: number; email: string; fullName: string } | null;
  createdAt: string;
  updatedAt: string;
}

export const PRODUCT_STATUSES = ['DRAFT', 'PENDING', 'ACTIVE', 'HIDDEN', 'BANNED'] as const;
export type ProductStatus = (typeof PRODUCT_STATUSES)[number];

export interface SellerProductRow {
  id: number;
  name: string;
  slug: string;
  thumbnailUrl: string | null;
  status: ProductStatus;
  /** Lý do admin khóa (khi BANNED). */
  banReason: string | null;
  minPrice: number;
  maxPrice: number;
  totalStock: number;
  variantCount: number;
  soldCount: number;
  ratingAvg: number;
  createdAt: string;
  updatedAt: string;
}

export interface SellerProductDetail {
  id: number;
  name: string;
  slug: string;
  status: ProductStatus;
  banReason: string | null;
  categoryId: number;
  categoryPath: { id: number; name: string; slug: string }[];
  description: string | null;
  attributes: Record<string, string>;
  images: string[];
  variantOptions: VariantOption[];
  variants: Variant[];
  minPrice: number;
  maxPrice: number;
  totalStock: number;
  soldCount: number;
  ratingAvg: number;
  ratingCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface VariantInput {
  /** SKU đang có (sửa) hoặc null (thêm mới). */
  id: number | null;
  optionValues: Record<string, string>;
  price: number;
  stock: number;
  /** Bỏ trống → hệ thống tự sinh. */
  sku: string | null;
  imageUrl: string | null;
}

export interface ProductInput {
  categoryId: number;
  name: string;
  description: string | null;
  attributes: Record<string, string>;
  images: string[];
  variantOptions: VariantOption[];
  variants: VariantInput[];
  /** Chỉ dùng khi tạo mới: true = đăng bán ngay, false = lưu ẩn. */
  visible?: boolean;
}

export interface SellerProductQuery {
  status?: ProductStatus | null;
  q?: string | null;
  page?: number;
  size?: number;
}

export const SELLER_ACTIONS = ['CONFIRM', 'SHIP', 'DELIVER', 'CANCEL'] as const;
export type SellerAction = (typeof SELLER_ACTIONS)[number];

export interface SellerOrderSummary {
  code: string;
  status: OrderStatus;
  items: OrderItem[];
  subtotal: number;
  shippingFee: number;
  discount: number;
  total: number;
  paymentMethod: string;
  paymentStatus: string;
  awaitingPayment: boolean;
  receiverName: string;
  note: string | null;
  /** Thao tác seller làm được ở trạng thái hiện tại. */
  actions: SellerAction[];
  createdAt: string;
}

export interface SellerOrderDetail extends Omit<SellerOrderSummary, 'receiverName'> {
  shippingAddress: ShippingAddress;
  history: OrderHistoryEntry[];
  shipment: Shipment | null;
  updatedAt: string;
}

export type OrderStatusCounts = Record<OrderStatus, number>;

export type DashboardGranularity = 'DAY' | 'MONTH';

export interface DashboardPoint {
  /** Ngày đầu kỳ (yyyy-MM-dd). */
  period: string;
  revenue: number;
  orders: number;
}

export interface TopProduct {
  productId: number;
  productName: string;
  productSlug: string;
  imageUrl: string | null;
  quantity: number;
  revenue: number;
}

export interface SellerDashboard {
  from: string;
  to: string;
  granularity: DashboardGranularity;
  timezone: string;
  summary: { revenue: number; orders: number; cancelledOrders: number; unitsSold: number };
  series: DashboardPoint[];
  /** Số đơn theo trạng thái (toàn thời gian). */
  statusCounts: OrderStatusCounts;
  topProducts: TopProduct[];
}

// ===== Search (Phase 5) =====

export type SearchSort = 'RELEVANCE' | 'BEST_SELLING' | 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC';

export interface SearchQuery {
  q: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  sort?: SearchSort;
  page?: number;
  size?: number;
}

export interface SearchHit {
  id: number;
  name: string;
  /** Tên với phần khớp bọc trong <mark> (đã escape HTML); null nếu không khớp tên. */
  highlightedName: string | null;
  slug: string;
  thumbnailUrl: string | null;
  minPrice: number;
  maxPrice: number;
  soldCount: number;
  ratingAvg: number;
  ratingCount: number;
  shopName: string | null;
}

export interface Suggestion {
  name: string;
  slug: string;
}

// ===== Voucher (Phase 5) =====

export type VoucherType = 'FIXED' | 'PERCENT' | 'FREESHIP';
export type VoucherStatus = 'ACTIVE' | 'DISABLED';

export interface Voucher {
  id: number;
  /** null = voucher sàn */
  shopId: number | null;
  code: string;
  name: string;
  type: VoucherType;
  value: number;
  minOrderValue: number;
  maxDiscount: number | null;
  usageLimit: number;
  usedCount: number;
  perUserLimit: number;
  startAt: string;
  endAt: string;
  status: VoucherStatus;
}

export interface VoucherInput {
  code: string;
  name: string;
  type: VoucherType;
  value: number;
  minOrderValue: number;
  maxDiscount: number | null;
  usageLimit: number;
  perUserLimit: number;
  startAt: string;
  endAt: string;
}

/** Voucher đã áp vào một shop khi thanh toán (voucher sàn được phân bổ cho từng shop). */
export interface AppliedVoucher {
  voucherId: number;
  code: string;
  type: VoucherType;
  scope: 'SHOP' | 'PLATFORM';
  amount: number;
}

export interface OrderVoucher {
  code: string;
  type: VoucherType;
  scope: 'SHOP' | 'PLATFORM';
  amount: number;
}

// ===== Review (Phase 5) =====

export interface Review {
  id: number;
  orderItemId: number;
  /** Tên đã che bớt, vd. "N*****n". */
  reviewerName: string;
  rating: number;
  content: string | null;
  media: string[];
  variantName: string | null;
  sellerReply: string | null;
  sellerRepliedAt: string | null;
  createdAt: string;
}

export interface ReviewSummary {
  ratingAvg: number;
  ratingCount: number;
  /** Số đánh giá theo số sao ("1".."5"). */
  counts: Partial<Record<string, number>>;
  withMedia: number;
}

export interface ProductReviews extends Page<Review> {
  summary: ReviewSummary;
}

export interface ReviewInput {
  rating: number;
  content: string | null;
  media: string[];
}

export interface SellerReview {
  id: number;
  productId: number;
  productName: string;
  variantName: string | null;
  reviewerName: string;
  rating: number;
  content: string | null;
  media: string[];
  sellerReply: string | null;
  sellerRepliedAt: string | null;
  createdAt: string;
}

// ===== Flash sale (Phase 5) =====

export interface FlashSaleItem {
  id: number;
  variantId: number;
  productId: number;
  productName: string | null;
  productSlug: string | null;
  variantName: string | null;
  imageUrl: string | null;
  originalPrice: number;
  salePrice: number;
  quota: number;
  remaining: number;
  perUserLimit: number;
}

export interface FlashSale {
  id: number;
  name: string;
  startAt: string;
  endAt: string;
  status: 'ACTIVE' | 'CANCELLED';
  running: boolean;
  items: FlashSaleItem[];
}

export interface FlashSaleInput {
  name: string;
  startAt: string;
  endAt: string;
  items: { variantId: number; salePrice: number; quota: number; perUserLimit: number }[];
}

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'FAILED';

export interface FlashSaleReservation {
  reservationId: string;
  itemId: number;
  status: ReservationStatus;
  orderCode: string | null;
  reason: string | null;
}

// ===== Thông báo & chat (Phase 6) =====

export type NotificationType =
  | 'ORDER_STATUS'
  | 'ORDER_CANCELLED_BY_BUYER'
  | 'NEW_ORDER'
  | 'PAYMENT_SUCCEEDED'
  | 'SHOP_APPROVED'
  | 'SHOP_REJECTED'
  | 'SHOP_BANNED'
  | 'SHOP_UNBANNED'
  | 'PRODUCT_BANNED'
  | 'PRODUCT_UNBANNED';

export interface AppNotification {
  id: number;
  type: NotificationType;
  /** Tham số hiển thị: orderCode, status, note, total, shopName, productName, reason... */
  params: Record<string, string | number>;
  /** Đường dẫn FE khi bấm vào. */
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage extends Page<AppNotification> {
  unreadCount: number;
}

/** Tin realtime trên /user/queue/notifications. */
export interface NotificationPush {
  notification: AppNotification;
  unreadCount: number;
}

export type ChatSide = 'BUYER' | 'SHOP';

export interface Conversation {
  id: number;
  /** Phía của tôi trong hội thoại. */
  side: ChatSide;
  shopId: number;
  /** Tên bên kia: shop (khi tôi là người mua) hoặc khách (khi tôi là shop). */
  counterpart: string | null;
  counterpartAvatar: string | null;
  lastMessage: string | null;
  lastMessageAt: string | null;
  unread: number;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderId: number;
  senderSide: ChatSide;
  content: string;
  createdAt: string;
}

export interface MessagePage {
  /** Cũ → mới. */
  items: ChatMessage[];
  hasMore: boolean;
  /** Tin cuối bên kia đã xem. */
  otherLastReadMessageId: number | null;
}

/** Tin realtime trên /user/queue/chat. */
export interface ChatPush {
  type: 'MESSAGE' | 'READ';
  conversationId: number;
  message: ChatMessage | null;
  /** MESSAGE: số chưa đọc của tôi trong hội thoại. */
  unread: number | null;
  /** READ: bên kia đã xem tới tin này. */
  lastReadMessageId: number | null;
}

// ===== Quản trị (Phase 6) =====

export type UserStatus = 'ACTIVE' | 'LOCKED';

export interface AdminUser {
  id: number;
  email: string;
  phone: string | null;
  fullName: string;
  avatarUrl: string | null;
  role: UserRole;
  status: UserStatus;
  lockReason: string | null;
  createdAt: string;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  thumbnailUrl: string | null;
  status: ProductStatus;
  banReason: string | null;
  shop: ShopSummary | null;
  minPrice: number;
  maxPrice: number;
  soldCount: number;
  ratingAvg: number;
  createdAt: string;
  updatedAt: string;
}

export interface Banner {
  id: number;
  title: string;
  imageUrl: string;
  linkUrl: string | null;
  position: number;
  active: boolean;
  startsAt: string | null;
  endsAt: string | null;
}

export type BannerInput = Omit<Banner, 'id'>;

export interface AdminOverview {
  days: number;
  users: { total: number; byRole: Partial<Record<string, number>>; locked: number; newSince: number };
  shops: Partial<Record<string, number>>;
  products: Partial<Record<string, number>>;
  orders: {
    orders: number;
    gmv: number;
    byStatus: Partial<Record<string, number>>;
    daily: { date: string; orders: number; gmv: number }[];
  };
}
