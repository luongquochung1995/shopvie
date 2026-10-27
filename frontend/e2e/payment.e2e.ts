import { expect, Page, test } from '@playwright/test';
import { fillAddressDialog, fillRegister, login, logout, SEED_PASSWORD, selectFirstAvailableVariant } from './helpers';

/**
 * Phase 4: thanh toán VNPay qua cổng giả lập (hủy → thanh toán lại → thành công), seller xác nhận tạo vận đơn,
 * người mua xem hành trình vận chuyển. Chạy trên mock hoặc backend có bật app.payment.sandbox-gateway.
 */
const SELLER_OF_SHOP: Record<string, string> = {
  'ShopVi Fashion': 'seller1@shopvi.local',
  'Tech Zone VN': 'seller2@shopvi.local',
  'Nhà Xinh & Beauty': 'seller3@shopvi.local',
};

/** Tên shop seed hiển thị trên trang chi tiết sản phẩm. */
async function shopOf(page: Page): Promise<string> {
  for (const name of Object.keys(SELLER_OF_SHOP)) {
    if ((await page.getByText(name, { exact: true }).count()) > 0) {
      return name;
    }
  }
  throw new Error('Product does not belong to a seed shop');
}

test('buyer pays with VNPay after a failed attempt and tracks the shipment', async ({ page }) => {
  test.slow();
  await page.goto('/auth/register');
  const buyerEmail = await fillRegister(page);
  await expect(page).toHaveURL(/\/$/);

  // Sản phẩm của một shop seed
  await page.goto('/');
  await page.locator('app-product-card a').first().click();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  const shopName = await shopOf(page);
  await selectFirstAvailableVariant(page);
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page.getByText('Sản phẩm đã được thêm vào giỏ hàng')).toBeVisible();
  await page.getByRole('link', { name: 'Giỏ hàng: 1 sản phẩm' }).click();
  await page.getByRole('button', { name: 'Mua hàng' }).click();
  await page.getByRole('button', { name: 'Thêm địa chỉ mới' }).click();
  await fillAddressDialog(page);

  // Chọn VNPay → sang cổng thanh toán
  await page.getByRole('radio', { name: 'VNPay' }).click();
  await expect(page.getByRole('radio', { name: 'VNPay' })).toHaveAttribute('aria-checked', 'true');
  await page.getByRole('button', { name: 'Đặt hàng & thanh toán' }).click();
  await expect(page).toHaveURL(/\/payment\/sandbox\?/);
  await expect(page.getByText('Cổng thanh toán VNPay')).toBeVisible();

  // Hủy trên cổng → kết quả chưa thành công → thanh toán lại → thành công
  await page.getByRole('button', { name: 'Hủy giao dịch' }).click();
  await expect(page.getByRole('heading', { name: 'Thanh toán chưa thành công' })).toBeVisible();
  await expect(page.getByText('Thời gian còn lại:')).toBeVisible();
  await page.getByRole('button', { name: /^Thanh toán lại/ }).click();
  await expect(page).toHaveURL(/\/payment\/sandbox\?/);
  await page.getByRole('button', { name: 'Thanh toán thành công' }).click();
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible();

  // Đơn mua: đã thanh toán, chờ shop xác nhận (không còn nút thanh toán)
  await page.getByRole('link', { name: 'Xem đơn mua' }).click();
  const order = page.locator('article').first();
  await expect(order.getByText('Chờ xác nhận', { exact: true })).toBeVisible();
  await expect(order.getByRole('button', { name: 'Thanh toán ngay' })).toHaveCount(0);
  await order.getByRole('link', { name: 'Xem chi tiết' }).click();
  await expect(page.getByText('Đã thanh toán')).toBeVisible();
  const orderUrl = page.url();
  await logout(page);

  // Seller xác nhận (tạo vận đơn) và giao cho vận chuyển
  await login(page, SELLER_OF_SHOP[shopName], SEED_PASSWORD, '/seller-center/orders');
  const sellerOrder = page.locator('article').first();
  await sellerOrder.getByRole('button', { name: 'Xác nhận đơn' }).click();
  await expect(sellerOrder.getByText('Chờ lấy hàng', { exact: true })).toBeVisible();
  await sellerOrder.getByRole('button', { name: 'Giao cho ĐVVC' }).click();
  await expect(sellerOrder.getByText('Đang giao', { exact: true })).toBeVisible();
  await logout(page);

  // Người mua xem hành trình vận chuyển
  await login(page, buyerEmail, 'secret123', new URL(orderUrl).pathname);
  await expect(page.getByRole('heading', { name: 'Thông tin vận chuyển' })).toBeVisible();
  await expect(page.getByText(/^SVX\d+$/)).toBeVisible();
  const timeline = page.getByRole('region', { name: 'Thông tin vận chuyển' }).locator('ol li');
  await expect(timeline).toHaveCount(2);
  await expect(timeline.first()).toContainText('Đang vận chuyển');
});
