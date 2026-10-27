import { CategoryNode, ProductDetail, ProductStatus, ShopSummary, UserRole, Variant, VariantOption } from '../core/api/api.models';

/**
 * Dữ liệu test cho chế độ mock (không cần backend), sinh cố định (seed 2026) theo cùng mẫu với
 * DevDataSeeder của backend: 20 danh mục, 3 shop, 100 sản phẩm có phân loại.
 */

export const MOCK_PASSWORD = 'Password@123';

export interface MockUser {
  id: number;
  email: string;
  password: string;
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
  role: UserRole;
  createdAt: string;
  status?: 'ACTIVE' | 'LOCKED';
  lockReason?: string | null;
}

export interface MockProduct extends ProductDetail {
  categoryId: number;
  createdAt: string;
  /** Mặc định ACTIVE (sản phẩm sinh sẵn); sản phẩm của seller có thể HIDDEN. */
  status?: ProductStatus;
  banReason?: string | null;
  updatedAt?: string;
}

/** PRNG mulberry32: cùng seed → cùng dữ liệu mỗi lần tải trang. */
function prng(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function slugify(input: string): string {
  return input
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+)|(-+$)/g, '');
}

const image = (seed: string, size: number) => `https://picsum.photos/seed/shopvi-${seed}/${size}/${size}`;

const CATEGORIES: [string, string[]][] = [
  ['Thời Trang Nam', ['Áo Thun Nam', 'Quần Jeans Nam', 'Giày Nam']],
  ['Thời Trang Nữ', ['Váy Đầm', 'Áo Kiểu Nữ', 'Túi Xách Nữ']],
  ['Điện Thoại & Phụ Kiện', ['Điện Thoại', 'Ốp Lưng', 'Tai Nghe']],
  ['Nhà Cửa & Đời Sống', ['Dụng Cụ Nhà Bếp', 'Chăn Ga Gối', 'Đèn Trang Trí']],
  ['Sắc Đẹp', ['Chăm Sóc Da', 'Trang Điểm', 'Nước Hoa']],
];

const SHOPS: { owner: string; ownerName: string; name: string }[] = [
  { owner: 'seller1@shopvi.local', ownerName: 'Chủ Shop Thời Trang', name: 'ShopVi Fashion' },
  { owner: 'seller2@shopvi.local', ownerName: 'Chủ Shop Công Nghệ', name: 'Tech Zone VN' },
  { owner: 'seller3@shopvi.local', ownerName: 'Chủ Shop Nhà & Đẹp', name: 'Nhà Xinh & Beauty' },
];

const SUFFIXES = ['Cao Cấp', 'Chính Hãng', 'Phong Cách Hàn Quốc', 'Basic', '2026', 'Bản Giới Hạn', 'Hot Trend', ''];
const ORIGINS = ['Việt Nam', 'Hàn Quốc', 'Trung Quốc', 'Nhật Bản', 'Thái Lan'];
const COLORS: VariantOption = { name: 'Màu sắc', values: ['Đen', 'Trắng', 'Xám', 'Xanh navy', 'Be'] };
const SIZES: VariantOption = { name: 'Kích cỡ', values: ['S', 'M', 'L', 'XL'] };

interface Template {
  category: string;
  shop: number;
  names: string[];
  brands: string[];
  options: VariantOption[];
  minPrice: number;
  maxPrice: number;
  priceStep: number;
  attributes: Record<string, string>;
}

const TEMPLATES: Template[] = [
  { category: 'ao-thun-nam', shop: 0, names: ['Áo Thun Nam Cổ Tròn', 'Áo Polo Nam', 'Áo Thun Oversize'], brands: ['Coolmate', 'Routine', 'Yody'], options: [COLORS, SIZES], minPrice: 99_000, maxPrice: 299_000, priceStep: 10_000, attributes: { 'Chất liệu': 'Cotton 100%' } },
  { category: 'quan-jeans-nam', shop: 0, names: ['Quần Jeans Nam Ống Đứng', 'Quần Jeans Slim Fit'], brands: ["Levi's", 'Routine', 'Owen'], options: [{ name: 'Màu sắc', values: ['Xanh nhạt', 'Xanh đậm', 'Đen'] }, { name: 'Size', values: ['29', '30', '31', '32', '33'] }], minPrice: 249_000, maxPrice: 699_000, priceStep: 0, attributes: { 'Chất liệu': 'Denim co giãn' } },
  { category: 'giay-nam', shop: 0, names: ['Giày Sneaker Nam', 'Giày Tây Nam Da', 'Giày Chạy Bộ'], brands: ["Biti's", 'Ananas', 'Nike'], options: [{ name: 'Màu sắc', values: ['Đen', 'Trắng', 'Nâu'] }, { name: 'Size', values: ['39', '40', '41', '42', '43'] }], minPrice: 350_000, maxPrice: 1_500_000, priceStep: 0, attributes: { 'Chất liệu đế': 'Cao su' } },
  { category: 'vay-dam', shop: 0, names: ['Váy Đầm Hoa Nhí', 'Đầm Suông Công Sở', 'Váy Maxi Đi Biển'], brands: ['Elise', 'Ivy Moda', 'Canifa'], options: [{ name: 'Màu sắc', values: ['Đỏ', 'Hồng', 'Trắng', 'Đen'] }, { name: 'Kích cỡ', values: ['S', 'M', 'L'] }], minPrice: 199_000, maxPrice: 899_000, priceStep: 0, attributes: { 'Chất liệu': 'Voan lụa' } },
  { category: 'ao-kieu-nu', shop: 0, names: ['Áo Sơ Mi Nữ', 'Áo Croptop', 'Áo Kiểu Tay Phồng'], brands: ['Canifa', 'Elise', 'Yody'], options: [COLORS, { name: 'Kích cỡ', values: ['S', 'M', 'L'] }], minPrice: 129_000, maxPrice: 459_000, priceStep: 0, attributes: { 'Chất liệu': 'Lụa' } },
  { category: 'tui-xach-nu', shop: 0, names: ['Túi Xách Nữ Đeo Vai', 'Túi Tote Vải', 'Túi Mini Đeo Chéo'], brands: ['Vascara', 'Juno', 'Charles & Keith'], options: [{ name: 'Màu sắc', values: ['Đen', 'Kem', 'Nâu', 'Hồng'] }], minPrice: 189_000, maxPrice: 1_290_000, priceStep: 0, attributes: { 'Chất liệu': 'Da tổng hợp' } },
  { category: 'dien-thoai', shop: 1, names: ['Điện Thoại', 'Smartphone'], brands: ['iPhone 16', 'Samsung Galaxy S25', 'Xiaomi 15', 'OPPO Reno 13'], options: [{ name: 'Màu sắc', values: ['Đen', 'Trắng', 'Xanh'] }, { name: 'Dung lượng', values: ['128GB', '256GB', '512GB'] }], minPrice: 6_990_000, maxPrice: 24_990_000, priceStep: 3_000_000, attributes: { 'Bảo hành': '12 tháng' } },
  { category: 'op-lung', shop: 1, names: ['Ốp Lưng Trong Suốt', 'Ốp Lưng Chống Sốc', 'Ốp Lưng MagSafe'], brands: ['Spigen', 'UAG', 'Baseus'], options: [{ name: 'Mẫu máy', values: ['iPhone 15', 'iPhone 16', 'iPhone 16 Pro', 'Galaxy S25'] }, { name: 'Màu sắc', values: ['Trong suốt', 'Đen', 'Xanh'] }], minPrice: 49_000, maxPrice: 390_000, priceStep: 0, attributes: { 'Chất liệu': 'TPU + PC' } },
  { category: 'tai-nghe', shop: 1, names: ['Tai Nghe Bluetooth', 'Tai Nghe Chống Ồn', 'Tai Nghe Gaming'], brands: ['Sony', 'JBL', 'Soundpeats', 'Apple AirPods'], options: [{ name: 'Màu sắc', values: ['Đen', 'Trắng', 'Xanh dương'] }], minPrice: 290_000, maxPrice: 5_990_000, priceStep: 0, attributes: { 'Kết nối': 'Bluetooth 5.3' } },
  { category: 'dung-cu-nha-bep', shop: 2, names: ['Chảo Chống Dính', 'Nồi Inox 3 Đáy', 'Bộ Dao Nhà Bếp'], brands: ['Sunhouse', 'Lock&Lock', 'Elmich'], options: [{ name: 'Kích thước', values: ['20cm', '24cm', '28cm'] }], minPrice: 149_000, maxPrice: 890_000, priceStep: 50_000, attributes: { 'Dùng cho bếp từ': 'Có' } },
  { category: 'chan-ga-goi', shop: 2, names: ['Bộ Chăn Ga Gối Cotton', 'Chăn Hè Thu', 'Bộ Ga Gối Lụa'], brands: ['Everon', 'Hanvico', 'Sông Hồng'], options: [{ name: 'Màu sắc', values: ['Xám', 'Kem', 'Xanh mint'] }, { name: 'Kích thước', values: ['1m6 x 2m', '1m8 x 2m', '2m x 2m2'] }], minPrice: 390_000, maxPrice: 2_490_000, priceStep: 100_000, attributes: { 'Chất liệu': 'Cotton Hàn Quốc' } },
  { category: 'den-trang-tri', shop: 2, names: ['Đèn Ngủ Để Bàn', 'Đèn LED Dây', 'Đèn Cây Đứng'], brands: ['Rạng Đông', 'Philips', 'Xiaomi'], options: [{ name: 'Ánh sáng', values: ['Vàng ấm', 'Trắng', 'Đổi màu RGB'] }], minPrice: 99_000, maxPrice: 1_190_000, priceStep: 20_000, attributes: { 'Công suất': '10W' } },
  { category: 'cham-soc-da', shop: 2, names: ['Sữa Rửa Mặt', 'Serum Vitamin C', 'Kem Chống Nắng'], brands: ['La Roche-Posay', 'Cocoon', 'Some By Mi', 'Anessa'], options: [{ name: 'Dung tích', values: ['50ml', '100ml', '200ml'] }], minPrice: 129_000, maxPrice: 790_000, priceStep: 80_000, attributes: { 'Loại da': 'Mọi loại da' } },
  { category: 'trang-diem', shop: 2, names: ['Son Kem Lì', 'Phấn Nước Cushion', 'Mascara Chống Trôi'], brands: ['3CE', 'Maybelline', 'Black Rouge', 'Romand'], options: [{ name: 'Màu', values: ['01 Đỏ cam', '02 Hồng đất', '03 Đỏ gạch', '04 Nude'] }], minPrice: 99_000, maxPrice: 590_000, priceStep: 0, attributes: { 'Hạn sử dụng': '36 tháng' } },
  { category: 'nuoc-hoa', shop: 2, names: ['Nước Hoa Nữ', 'Nước Hoa Nam', 'Body Mist'], brands: ['Charme', 'Dior', 'Chanel', "Victoria's Secret"], options: [{ name: 'Dung tích', values: ['30ml', '50ml', '100ml'] }], minPrice: 250_000, maxPrice: 4_500_000, priceStep: 400_000, attributes: { 'Nhóm hương': 'Hương hoa cỏ' } },
];

/** Giá kết thúc bằng 9.000 như thực tế (vd. 189.000đ). */
const roundPrice = (price: number) => (Math.floor(Math.max(1, Math.floor(price / 1000)) / 10) * 10 + 9) * 1000;

function cartesian(groups: string[][]): string[][] {
  return groups.reduce<string[][]>((acc, group) => acc.flatMap((prefix) => group.map((v) => [...prefix, v])), [[]]);
}

export function createUsers(now: Date): MockUser[] {
  const base = { password: MOCK_PASSWORD, phone: null, avatarUrl: null, createdAt: now.toISOString() };
  return [
    { ...base, id: 1, email: 'admin@shopvi.local', fullName: 'Quản trị viên', role: 'ADMIN' },
    { ...base, id: 2, email: 'buyer@shopvi.local', fullName: 'Người Mua Demo', role: 'BUYER' },
    ...SHOPS.map((s, i): MockUser => ({ ...base, id: 3 + i, email: s.owner, fullName: s.ownerName, role: 'SELLER' })),
  ];
}

export function createCatalog(now: Date): { categories: CategoryNode[]; shops: ShopSummary[]; products: MockProduct[] } {
  const r = prng(2026);
  const pick = <T>(list: T[]) => list[Math.floor(r() * list.length)];

  let categoryId = 0;
  const leafIds = new Map<string, number>();
  const leafPath = new Map<number, { id: number; name: string; slug: string }[]>();
  const categories: CategoryNode[] = CATEGORIES.map(([rootName, children], rootPos) => {
    const rootId = ++categoryId;
    const rootSlug = slugify(rootName);
    const root: CategoryNode = {
      id: rootId, parentId: null, name: rootName, slug: rootSlug, level: 0, position: rootPos,
      iconUrl: image(`cat-${rootSlug}`, 120), children: [],
    };
    root.children = children.map((name, pos) => {
      const id = ++categoryId;
      const slug = slugify(name);
      leafIds.set(slug, id);
      leafPath.set(id, [{ id: rootId, name: rootName, slug: rootSlug }, { id, name, slug }]);
      return { id, parentId: rootId, name, slug, level: 1, position: pos, iconUrl: image(`cat-${slug}`, 120), children: [] };
    });
    return root;
  });

  const shops: ShopSummary[] = SHOPS.map((s, i) => ({
    id: i + 1, name: s.name, slug: slugify(s.name), logoUrl: image(`shop-${slugify(s.name)}`, 200),
    ratingAvg: 4.5 + Math.floor(r() * 5) / 10,
  }));

  const products: MockProduct[] = [];
  let variantId = 0;
  for (let i = 0; i < 100; i++) {
    const no = i + 1;
    const t = TEMPLATES[i % TEMPLATES.length];
    const brand = pick(t.brands);
    const name = `${pick(t.names)} ${brand} ${pick(SUFFIXES)}`.trim();
    const chosen = t.options.map((o) => {
      const values = [...o.values];
      while (values.length > 2 && Math.floor(r() * 3) === 0) {
        values.splice(Math.floor(r() * values.length), 1);
      }
      return values;
    });
    const basePrice = roundPrice(t.minPrice + r() * (t.maxPrice - t.minPrice));
    const variants: Variant[] = cartesian(chosen).map((combo, idx) => {
      const optionValues: Record<string, string> = {};
      combo.forEach((v, g) => (optionValues[t.options[g].name] = v));
      const last = combo.length - 1;
      const price = roundPrice(basePrice + t.priceStep * chosen[last].indexOf(combo[last]));
      const stock = Math.floor(r() * 10) === 0 ? 0 : 5 + Math.floor(r() * 146);
      return { id: ++variantId, sku: `SV${String(no).padStart(4, '0')}-${String(idx + 1).padStart(2, '0')}`, optionValues, price, stock, imageUrl: null };
    });
    const prices = variants.map((v) => v.price);
    const ratingCount = Math.floor(r() * 8) === 0 ? 0 : 1 + Math.floor(r() * 800);
    const categoryIdOfProduct = leafIds.get(t.category)!;
    products.push({
      id: no,
      name,
      slug: `${slugify(name)}-${no}`,
      description: `${name} chính hãng ${brand}, hàng mới 100%.\n- Chất lượng kiểm định, đổi trả trong 15 ngày nếu lỗi do nhà sản xuất.\n- Giao hàng toàn quốc, kiểm tra hàng trước khi thanh toán (COD).\n- Hình ảnh sản phẩm mang tính minh họa.`,
      attributes: { 'Thương hiệu': brand, 'Xuất xứ': pick(ORIGINS), ...t.attributes },
      minPrice: Math.min(...prices),
      maxPrice: Math.max(...prices),
      totalStock: variants.reduce((s, v) => s + v.stock, 0),
      soldCount: Math.floor(r() * 5000),
      ratingAvg: ratingCount === 0 ? 0 : 3.5 + Math.floor(r() * 16) / 10,
      ratingCount,
      breadcrumb: leafPath.get(categoryIdOfProduct)!,
      images: [0, 1, 2, 3].map((k) => image(`p${no}-${k}`, 800)),
      variantOptions: t.options.map((o, g) => ({ name: o.name, values: chosen[g] })),
      variants,
      shop: shops[t.shop],
      categoryId: categoryIdOfProduct,
      createdAt: new Date(now.getTime() - (7 * i + Math.floor(r() * 6)) * 3_600_000).toISOString(),
    });
  }
  return { categories, shops, products };
}
