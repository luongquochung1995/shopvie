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
}

export interface PlaceOrderRequest {
  addressId: number;
  paymentMethod: string;
  /** shopId → lời nhắn cho shop */
  notes: Record<number, string>;
  /** Tổng tiền đã xem ở bước preview; khác thực tế → 409 PRICE_CHANGED */
  expectedTotal: number;
}

export interface PlaceOrderResult {
  checkoutId: string;
  orders: { code: string; shopId: number; total: number }[];
  total: number;
}

// ===== Order =====

export const ORDER_STATUSES = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'RETURNED'] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

export interface OrderItem {
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
  checkoutId: string;
  shippingAddress: ShippingAddress;
  note: string | null;
  history: OrderHistoryEntry[];
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
  createdAt: string;
  updatedAt: string;
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
  receiverName: string;
  note: string | null;
  /** Thao tác seller làm được ở trạng thái hiện tại. */
  actions: SellerAction[];
  createdAt: string;
}

export interface SellerOrderDetail extends Omit<SellerOrderSummary, 'receiverName'> {
  shippingAddress: ShippingAddress;
  history: OrderHistoryEntry[];
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
