import { expect, Locator, Page } from '@playwright/test';

/** Mật khẩu tài khoản seed (backend dev seed và backend giả dùng chung). */
export const SEED_PASSWORD = 'Password@123';

export const uniqueEmail = (prefix: string) => `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;

/** Điền form đăng ký đang mở và gửi; trả về email đã dùng. */
export async function fillRegister(page: Page, fullName = 'Người Mua E2E', email = uniqueEmail('e2e')): Promise<string> {
  await page.locator('input[formcontrolname="fullName"]').fill(fullName);
  await page.locator('input[formcontrolname="email"]').fill(email);
  await page.locator('input[formcontrolname="password"]').fill('secret123');
  await page.getByRole('button', { name: 'ĐĂNG KÝ' }).click();
  return email;
}

export async function login(page: Page, email: string, password: string, returnUrl = '/') {
  await page.goto(`/auth/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole('button', { name: 'ĐĂNG NHẬP' }).click();
  await expect(page).toHaveURL(new RegExp(returnUrl.replace(/[/?]/g, '\\$&') + '$'));
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: /^Tài khoản:/ }).click();
  await page.getByRole('menuitem', { name: 'Đăng xuất' }).click();
  await expect(page.getByRole('button', { name: /^Tài khoản:/ })).toHaveCount(0);
}

/** Chọn giá trị còn hàng đầu tiên của mỗi nhóm phân loại. */
export async function selectFirstAvailableVariant(page: Page) {
  const groups = page.locator('dd[role="group"]');
  // Chờ sản phẩm tải xong (backend giả có độ trễ) trước khi đếm nhóm phân loại
  await expect(groups.first()).toBeVisible();
  const count = await groups.count();
  for (let i = 0; i < count; i++) {
    const option = groups.nth(i).locator('button:not([disabled])').first();
    await option.click();
    await expect(option).toHaveAttribute('aria-pressed', 'true');
  }
}

/** Chọn một mục trong PrimeNG Select có ô lọc: mở, gõ lọc (đúng ô lọc của select này), bấm option. */
export async function pickFromSelect(page: Page, combobox: Locator, filter: string, option: string) {
  await combobox.click();
  const listId = await combobox.getAttribute('aria-controls');
  await page.locator(`input.p-select-filter[aria-owns="${listId}"]`).fill(filter);
  await page.locator(`[id="${listId}"]`).getByRole('option', { name: option, exact: true }).click();
  await expect(combobox).toHaveAccessibleName(option);
}

/** Thêm địa chỉ mới trong hộp thoại đang mở (địa giới 2 cấp: tỉnh → phường). */
export async function fillAddressDialog(page: Page) {
  const dialog = page.getByRole('dialog');
  await dialog.locator('input[formcontrolname="receiverName"]').fill('Nguyễn Văn E2E');
  await dialog.locator('input[formcontrolname="phone"]').fill('0912345678');
  await pickFromSelect(page, dialog.getByRole('combobox').nth(0), 'Hồ Chí Minh', 'Thành phố Hồ Chí Minh');
  await pickFromSelect(page, dialog.getByRole('combobox').nth(1), 'Bến Thành', 'Phường Bến Thành');
  await dialog.locator('input[formcontrolname="detail"]').fill('45 Lê Thánh Tôn');
  await dialog.getByRole('button', { name: 'Hoàn thành' }).click();
  await expect(page.getByText('45 Lê Thánh Tôn, Phường Bến Thành, Thành phố Hồ Chí Minh')).toBeVisible();
}
