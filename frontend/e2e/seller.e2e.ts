import { expect, test } from '@playwright/test';
import { fillAddressDialog, fillRegister, login, logout, pickFromSelect, SEED_PASSWORD, selectFirstAvailableVariant, uniqueEmail } from './helpers';

/**
 * Seller Center (DoD Phase 3): người mua mở shop → admin duyệt → seller đăng sản phẩm có phân loại → sản phẩm hiển thị
 * cho người mua → người mua đặt hàng → seller xử lý đơn tới "Đã giao" → dashboard có doanh số.
 * Dùng tài khoản/shop mới mỗi lần chạy nên chạy được trên backend giả lẫn backend thật.
 */
test('seller opens a shop, lists a product with variants and fulfils an order', async ({ page }) => {
  test.slow();
  const stamp = Date.now();
  const shopName = `Shop E2E ${stamp}`;
  const productName = `Áo thun E2E ${stamp}`;

  // 1. Đăng ký tài khoản → mở shop → chờ duyệt
  await page.goto('/auth/register');
  const sellerEmail = await fillRegister(page, 'Người Bán E2E', uniqueEmail('e2e-seller'));
  await expect(page).toHaveURL(/\/$/);
  await page.getByRole('button', { name: /^Tài khoản:/ }).click();
  await page.getByRole('menuitem', { name: 'Mở shop bán hàng' }).click();
  await expect(page).toHaveURL(/\/seller-center\/register$/);
  await page.locator('input[formcontrolname="name"]').fill(shopName);
  await page.locator('textarea[formcontrolname="description"]').fill('Shop thời trang E2E');
  await page.getByRole('button', { name: 'Đăng ký mở shop' }).click();
  await expect(page).toHaveURL(/\/seller-center$/);
  await expect(page.getByText(/Shop của bạn đang chờ quản trị viên duyệt/)).toBeVisible();
  await logout(page);

  // 2. Admin duyệt shop
  await login(page, 'admin@shopvi.local', SEED_PASSWORD, '/admin/shops');
  const shopRow = page.locator('li').filter({ hasText: shopName });
  await shopRow.getByRole('button', { name: 'Duyệt' }).click();
  await expect(page.getByText(`Đã duyệt shop ${shopName}`)).toBeVisible();
  await expect(shopRow).toHaveCount(0);
  await logout(page);

  // 3. Seller đăng sản phẩm: 2 màu × 2 size = 4 SKU, áp giá/kho hàng loạt
  await login(page, sellerEmail, 'secret123', '/seller-center/products/new');
  await expect(page.getByText(/chờ quản trị viên duyệt/)).toHaveCount(0);
  const imageUrl = page.getByRole('textbox', { name: 'Hoặc dán URL ảnh (https://...)' });
  await imageUrl.fill('https://picsum.photos/seed/shopvi-e2e/800/800');
  await imageUrl.press('Enter');
  await expect(page.getByRole('img', { name: 'Ảnh 1' })).toBeVisible();
  await page.getByPlaceholder(/Tên sản phẩm \+ thương hiệu/).fill(productName);
  await pickFromSelect(page, page.locator('#category'), 'Áo Thun', 'Áo Thun Nam');
  await page.getByRole('switch').click();
  await page.getByRole('textbox', { name: 'Tên nhóm phân loại 1' }).fill('Màu sắc');
  await page.getByRole('textbox', { name: 'Thêm giá trị cho nhóm phân loại 1' }).fill('Đỏ, Xanh');
  await page.getByRole('textbox', { name: 'Thêm giá trị cho nhóm phân loại 1' }).press('Enter');
  await page.getByRole('button', { name: 'Thêm nhóm phân loại' }).click();
  await page.getByRole('textbox', { name: 'Tên nhóm phân loại 2' }).fill('Size');
  await page.getByRole('textbox', { name: 'Thêm giá trị cho nhóm phân loại 2' }).fill('M, L');
  await page.getByRole('textbox', { name: 'Thêm giá trị cho nhóm phân loại 2' }).press('Enter');
  await expect(page.getByRole('textbox', { name: /^Giá của / })).toHaveCount(4);
  await page.getByRole('textbox', { name: 'Giá', exact: true }).fill('150000');
  await page.getByRole('textbox', { name: 'Kho hàng', exact: true }).fill('10');
  await page.getByRole('button', { name: 'Áp dụng cho tất cả phân loại' }).click();
  // Chờ bảng hiển thị giá vừa áp dụng (render trễ một nhịp) rồi mới sửa riêng 1 SKU
  await expect(page.getByRole('textbox', { name: 'Giá của Xanh / L' })).toHaveValue('150000');
  await page.getByRole('textbox', { name: 'Giá của Xanh / L' }).fill('170000');
  await page.getByRole('button', { name: 'Lưu & Hiển thị' }).click();
  await expect(page.getByText('Đã đăng sản phẩm')).toBeVisible();
  await expect(page).toHaveURL(/\/seller-center\/products$/);
  const row = page.locator('li').filter({ hasText: productName });
  await expect(row.getByText('Đang bán')).toBeVisible();
  await expect(row.getByText('4 phân loại')).toBeVisible();

  // Mở form sửa → lấy link sản phẩm trên sàn
  await row.getByRole('link', { name: productName }).click();
  const productHref = await page.getByRole('link', { name: /Xem trên sàn/ }).getAttribute('href');
  expect(productHref).toMatch(/^\/p\//);
  await logout(page);

  // 4. Người mua mới đặt sản phẩm vừa đăng
  await page.goto('/auth/register');
  await fillRegister(page);
  await expect(page).toHaveURL(/\/$/);
  await page.goto(productHref!);
  await expect(page.getByRole('heading', { level: 1, name: productName })).toBeVisible();
  await expect(page.getByText(shopName).first()).toBeVisible();
  await selectFirstAvailableVariant(page);
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page.getByText('Sản phẩm đã được thêm vào giỏ hàng')).toBeVisible();
  await page.getByRole('link', { name: 'Giỏ hàng: 1 sản phẩm' }).click();
  await page.getByRole('button', { name: 'Mua hàng' }).click();
  await page.getByRole('button', { name: 'Thêm địa chỉ mới' }).click();
  await fillAddressDialog(page);
  await page.getByRole('button', { name: 'Đặt hàng' }).click();
  await expect(page.getByRole('heading', { name: 'Đặt hàng thành công!' })).toBeVisible();
  await logout(page);

  // 5. Seller xử lý đơn: xác nhận → giao ĐVVC → đã giao
  await login(page, sellerEmail, 'secret123', '/seller-center/orders');
  const order = page.locator('article').first();
  await expect(order.getByText('Nguyễn Văn E2E')).toBeVisible();
  await expect(order.getByText('Chờ xác nhận', { exact: true })).toBeVisible();
  await order.getByRole('button', { name: 'Xác nhận đơn' }).click();
  await expect(order.getByText('Chờ lấy hàng', { exact: true })).toBeVisible();
  await order.getByRole('button', { name: 'Giao cho ĐVVC' }).click();
  await expect(order.getByText('Đang giao', { exact: true })).toBeVisible();
  await order.getByRole('button', { name: 'Đã giao hàng' }).click();
  await expect(order.getByText('Đã giao', { exact: true })).toBeVisible();
  await expect(order.getByRole('button', { name: 'Hủy đơn' })).toHaveCount(0);

  // Chi tiết đơn: lịch sử 4 bước, hành trình vận chuyển 3 bước, COD đã thanh toán
  await order.getByRole('link', { name: 'Chi tiết' }).click();
  await expect(page.getByRole('region', { name: 'Trạng thái đơn hàng' }).locator('ol li')).toHaveCount(4);
  await expect(page.getByRole('region', { name: 'Thông tin vận chuyển' }).locator('ol li')).toHaveCount(3);
  await expect(page.getByText('Đã thanh toán')).toBeVisible();

  // 6. Dashboard: doanh số = giá sản phẩm (không gồm phí ship), top sản phẩm là sản phẩm vừa bán
  await page.goto('/seller-center');
  await expect(page.locator('dl dd').first()).toHaveText('₫150.000');
  await expect(page.locator('ol').filter({ hasText: productName })).toBeVisible();
});

test('admin rejects a shop and the seller resubmits it', async ({ page }) => {
  const shopName = `Shop Từ Chối ${Date.now()}`;
  await page.goto('/auth/register');
  const email = await fillRegister(page, 'Người Bán Bị Từ Chối', uniqueEmail('e2e-rejected'));
  await expect(page).toHaveURL(/\/$/);
  await page.goto('/seller-center');
  await expect(page).toHaveURL(/\/seller-center\/register$/);
  await page.locator('input[formcontrolname="name"]').fill(shopName);
  await page.getByRole('button', { name: 'Đăng ký mở shop' }).click();
  await expect(page).toHaveURL(/\/seller-center$/);
  await logout(page);

  await login(page, 'admin@shopvi.local', SEED_PASSWORD, '/admin/shops');
  await page.locator('li').filter({ hasText: shopName }).getByRole('button', { name: 'Từ chối' }).click();
  await page.getByRole('dialog').getByRole('textbox').fill('Tên shop chưa phù hợp');
  await page.getByRole('dialog').getByRole('button', { name: 'Từ chối' }).click();
  await expect(page.getByText(`Đã từ chối shop ${shopName}`)).toBeVisible();
  await logout(page);

  await login(page, email, 'secret123', '/seller-center');
  await expect(page.getByText('Lý do: Tên shop chưa phù hợp')).toBeVisible();
  await page.getByRole('link', { name: 'Sửa thông tin và gửi duyệt lại' }).click();
  await page.locator('input[formcontrolname="name"]').fill(`${shopName} (sửa)`);
  await page.getByRole('button', { name: 'Gửi duyệt lại' }).click();
  await expect(page.getByText('Đã gửi duyệt lại shop')).toBeVisible();
  await expect(page.getByText(/đang chờ quản trị viên duyệt/)).toBeVisible();
});
