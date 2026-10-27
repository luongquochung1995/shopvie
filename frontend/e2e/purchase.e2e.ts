import { expect, Page, test } from '@playwright/test';
import { fillAddressDialog, fillRegister, selectFirstAvailableVariant } from './helpers';

/**
 * Luồng mua hàng MVP (DoD Phase 2): xem sản phẩm → đăng ký → thêm giỏ → checkout COD → xem đơn → hủy đơn.
 * Mỗi test dùng tài khoản mới nên chạy được cả trên backend giả (mock) lẫn backend thật (DB có sẵn dữ liệu).
 */

async function openFirstInStockProduct(page: Page) {
  await page.goto('/');
  await page.locator('app-product-card a').first().click();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await selectFirstAvailableVariant(page);
}

test('buyer purchases with COD and cancels the order', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  // 1. Xem sản phẩm, thêm giỏ khi chưa đăng nhập → trang đăng nhập → đăng ký (giữ returnUrl) → quay lại
  await openFirstInStockProduct(page);
  const productUrl = page.url();
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page).toHaveURL(/\/auth\/login\?returnUrl=/);
  await page.getByRole('link', { name: 'Đăng ký', exact: true }).click();
  await expect(page).toHaveURL(/\/auth\/register\?returnUrl=/);
  await fillRegister(page);
  await expect(page).toHaveURL(productUrl);

  // 2. Thêm vào giỏ → badge giỏ hàng = 1
  await selectFirstAvailableVariant(page);
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page.getByText('Sản phẩm đã được thêm vào giỏ hàng')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Giỏ hàng: 1 sản phẩm' })).toBeVisible();

  // 3. Giỏ hàng → Mua hàng
  await page.getByRole('link', { name: 'Giỏ hàng: 1 sản phẩm' }).click();
  await expect(page).toHaveURL(/\/cart$/);
  await expect(page.locator('app-quantity-stepper')).toHaveCount(1);
  await page.getByRole('button', { name: 'Mua hàng' }).click();

  // 4. Checkout: chưa có địa chỉ → thêm địa chỉ 2 cấp (tỉnh → phường)
  await expect(page).toHaveURL(/\/checkout$/);
  await page.getByRole('button', { name: 'Thêm địa chỉ mới' }).click();
  await fillAddressDialog(page);
  await expect(page.getByText('Thanh toán khi nhận hàng', { exact: true })).toBeVisible();

  // 5. Đặt hàng COD → trang thành công, giỏ hàng trống
  await page.getByRole('button', { name: 'Đặt hàng' }).click();
  await expect(page.getByRole('heading', { name: 'Đặt hàng thành công!' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Giỏ hàng', exact: true })).toBeVisible();

  // 6. Đơn mua: đơn ở trạng thái Chờ xác nhận → hủy → Đã hủy
  await page.getByRole('link', { name: 'Xem đơn mua' }).click();
  await expect(page).toHaveURL(/\/account\/orders$/);
  const order = page.locator('article').first();
  await expect(order.getByText('Chờ xác nhận', { exact: true })).toBeVisible();
  await order.getByRole('button', { name: 'Hủy đơn hàng' }).click();
  await expect(page.getByText('Đã hủy đơn hàng')).toBeVisible();
  await expect(order.getByText('Đã hủy', { exact: true })).toBeVisible();

  // Chi tiết đơn có lịch sử trạng thái
  await order.getByRole('link', { name: 'Xem chi tiết' }).click();
  await expect(page.getByText('Mã đơn hàng:')).toBeVisible();
  await expect(page.locator('ol li')).toHaveCount(2);
});

test('cannot add more than available stock', async ({ page }) => {
  await page.goto('/auth/register');
  await fillRegister(page);
  await expect(page).toHaveURL(/\/$/);

  await openFirstInStockProduct(page);
  const stockText = await page.getByText(/sản phẩm có sẵn$/).textContent();
  const stock = Number(stockText?.match(/\d+/)?.[0]);
  const quantity = page.getByRole('spinbutton', { name: 'Số lượng' });
  await quantity.fill(String(stock));
  await quantity.blur();
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page.getByText('Sản phẩm đã được thêm vào giỏ hàng')).toBeVisible();

  // Thêm tiếp 1 cái nữa → vượt tồn kho
  await page.getByRole('spinbutton', { name: 'Số lượng' }).fill('1');
  await page.getByRole('spinbutton', { name: 'Số lượng' }).blur();
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page.getByText('bạn chỉ có thể thêm tối đa 0 sản phẩm nữa')).toBeVisible();
});
