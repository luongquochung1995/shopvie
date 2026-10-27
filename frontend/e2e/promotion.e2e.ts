import { expect, test } from '@playwright/test';
import { fillAddressDialog, fillRegister, selectFirstAvailableVariant } from './helpers';

/** Phase 5: tìm kiếm không dấu, voucher khi thanh toán, mua flash sale (chạy trên backend giả). */

test('search without diacritics finds Vietnamese product names', async ({ page }) => {
  await page.goto('/');
  const box = page.getByRole('combobox', { name: 'Từ khóa tìm kiếm' });
  // Gợi ý khi gõ (gõ lại nếu trang SSR chưa hydrate xong lúc gõ lần đầu)
  await expect(async () => {
    await box.fill('');
    await box.fill('ao thun');
    await expect(page.getByRole('option').first()).toContainText(/Áo Thun/i, { timeout: 2000 });
  }).toPass();
  await box.press('Enter');
  await expect(page).toHaveURL(/\/search\?q=ao(%20|\+)thun/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('ao thun');
  await expect(page.locator('app-product-card').first()).toContainText(/Áo Thun/i);

  await page.goto('/search?q=khongcosanphamnay');
  await expect(page.getByText('Không tìm thấy sản phẩm nào cho “khongcosanphamnay”')).toBeVisible();
});

test('buyer applies a voucher at checkout, then buys a flash sale item', async ({ page }) => {
  test.slow();
  await page.goto('/auth/register');
  await fillRegister(page);
  await expect(page).toHaveURL(/\/$/);

  // Giỏ hàng → thanh toán, thêm địa chỉ (mặc định — dùng cho flash sale sau đó)
  await page.locator('app-product-grid app-product-card a').first().click();
  await selectFirstAvailableVariant(page);
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(page.getByText('Sản phẩm đã được thêm vào giỏ hàng')).toBeVisible();
  await page.goto('/cart');
  await page.getByRole('button', { name: 'Mua hàng' }).click();
  await page.getByRole('button', { name: 'Thêm địa chỉ mới' }).click();
  await fillAddressDialog(page);

  // Mã sai → báo lỗi; mã đúng → giảm phí ship
  const voucherBox = page.getByRole('textbox', { name: 'Mã voucher' });
  await voucherBox.fill('KHONGCO1');
  await page.getByRole('button', { name: 'Áp dụng' }).click();
  await expect(page.getByRole('alert')).toHaveText('Mã voucher không tồn tại');
  await voucherBox.fill('freeship30');
  await page.getByRole('button', { name: 'Áp dụng' }).click();
  await expect(page.getByText('FREESHIP30', { exact: true })).toBeVisible();
  await expect(page.getByText('Voucher giảm giá')).toBeVisible();

  await page.getByRole('button', { name: 'Đặt hàng', exact: true }).click();
  await expect(page).toHaveURL(/\/checkout\/success/);

  // Flash sale ở trang chủ: mua → đơn COD giá sale
  await page.goto('/');
  const section = page.getByRole('region', { name: 'FLASH SALE' });
  await expect(section).toBeVisible();
  const salePrice = (await section.locator('li[data-item]').first().locator('p.text-lg').textContent())!.trim();
  await section.getByRole('button', { name: 'Mua ngay' }).first().click();
  await expect(page.getByText('Đặt hàng flash sale thành công!')).toBeVisible();
  await expect(page).toHaveURL(/\/account\/orders\/[0-9a-f-]+$/);
  await expect(page.getByText(salePrice).first()).toBeVisible();
  await expect(page.getByText('Lời nhắn: Flash sale')).toBeVisible();

  // Mỗi người 1 suất
  await page.goto('/');
  await page.getByRole('region', { name: 'FLASH SALE' }).getByRole('button', { name: 'Mua ngay' }).first().click();
  await expect(page.getByText('Bạn đã mua tối đa số lượng cho phép của sản phẩm này')).toBeVisible();
});
