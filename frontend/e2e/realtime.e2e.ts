import { expect, Page, test } from '@playwright/test';
import { fillAddressDialog, fillRegister, login, logout, SEED_PASSWORD, selectFirstAvailableVariant } from './helpers';

/**
 * DoD Phase 6: seller đổi trạng thái đơn → người mua nhận thông báo realtime; chat 2 chiều có "Đã xem".
 * Người mua và seller ở 2 tab cùng trình duyệt (backend giả đẩy tin giữa các tab qua BroadcastChannel; với backend thật
 * là STOMP over SockJS).
 */
const SELLER_OF_SHOP: Record<string, string> = {
  'ShopVi Fashion': 'seller1@shopvi.local',
  'Tech Zone VN': 'seller2@shopvi.local',
  'Nhà Xinh & Beauty': 'seller3@shopvi.local',
};

async function shopOf(page: Page): Promise<string> {
  for (const name of Object.keys(SELLER_OF_SHOP)) {
    if ((await page.getByText(name, { exact: true }).count()) > 0) {
      return name;
    }
  }
  throw new Error('Product does not belong to a seed shop');
}

test('buyer is notified in realtime when the seller confirms, and both sides chat', async ({ page: buyer, context, isMobile }) => {
  test.slow();
  // Người mua: đăng ký, mua COD một sản phẩm của shop seed
  await buyer.goto('/auth/register');
  await fillRegister(buyer);
  await expect(buyer).toHaveURL(/\/$/);
  await buyer.locator('app-product-grid app-product-card a').first().click();
  await expect(buyer.getByRole('heading', { level: 1 })).toBeVisible();
  const productUrl = buyer.url();
  const shopName = await shopOf(buyer);
  await selectFirstAvailableVariant(buyer);
  await buyer.getByRole('button', { name: 'Thêm Vào Giỏ Hàng' }).click();
  await expect(buyer.getByText('Sản phẩm đã được thêm vào giỏ hàng')).toBeVisible();
  await buyer.goto('/cart');
  await buyer.getByRole('button', { name: 'Mua hàng' }).click();
  await buyer.getByRole('button', { name: 'Thêm địa chỉ mới' }).click();
  await fillAddressDialog(buyer);
  await buyer.getByRole('button', { name: 'Đặt hàng', exact: true }).click();
  await expect(buyer).toHaveURL(/\/checkout\/success/);
  await expect(buyer.getByRole('button', { name: 'Thông báo', exact: true })).toBeVisible();

  // Seller ở tab thứ hai (tab này đang khôi phục phiên người mua → đăng xuất rồi đăng nhập seller)
  const seller = await context.newPage();
  await seller.goto('/');
  await expect(seller.getByRole('button', { name: /^Tài khoản:/ })).toBeVisible();
  await logout(seller);
  await login(seller, SELLER_OF_SHOP[shopName], SEED_PASSWORD, '/seller-center/orders');
  const order = seller.locator('article').first();
  await order.getByRole('button', { name: 'Xác nhận đơn' }).click();
  await expect(order.getByText('Chờ lấy hàng', { exact: true })).toBeVisible();

  // Người mua nhận thông báo ngay (không tải lại trang)
  await expect(buyer.getByText('Thông báo mới')).toBeVisible();
  const bell = buyer.getByRole('button', { name: 'Thông báo: 1 chưa đọc' });
  await expect(bell).toBeVisible();
  await bell.click();
  await buyer.getByRole('dialog', { name: 'Thông báo' }).getByRole('button', { name: /Chờ lấy hàng/ }).click();
  await expect(buyer).toHaveURL(/\/account\/orders\/[0-9a-f-]+$/);
  await expect(buyer.getByRole('button', { name: 'Thông báo', exact: true })).toBeVisible();

  // Chat: người mua hỏi shop từ trang sản phẩm
  await buyer.goto(productUrl);
  await buyer.getByRole('button', { name: 'Chat ngay' }).click();
  const buyerChat = buyer.getByRole('dialog', { name: 'Chat' });
  await buyerChat.getByRole('textbox', { name: 'Tin nhắn' }).fill('Shop ơi còn hàng không?');
  await buyerChat.getByRole('button', { name: 'Gửi' }).click();
  await expect(buyerChat.locator('[data-message]').getByText('Shop ơi còn hàng không?')).toBeVisible();

  // Seller thấy tin mới trên nút chat, mở và trả lời
  await seller.getByRole('button', { name: 'Mở chat: 1 tin chưa đọc' }).click();
  const sellerChat = seller.getByRole('dialog', { name: 'Chat' });
  await sellerChat.getByRole('button', { name: /Shop ơi còn hàng không\?/ }).click();
  await expect(sellerChat.locator('[data-message]').getByText('Shop ơi còn hàng không?')).toBeVisible();
  await sellerChat.getByRole('textbox', { name: 'Tin nhắn' }).fill('Còn ạ, bạn đặt nhé!');
  await sellerChat.getByRole('button', { name: 'Gửi' }).click();

  // Người mua đang mở hội thoại → nhận tin ngay và đánh dấu đã xem → seller thấy "Đã xem"
  await expect(buyerChat.locator('[data-message]').getByText('Còn ạ, bạn đặt nhé!')).toBeVisible();
  await expect(sellerChat.getByText(/Đã xem/)).toBeVisible();
  if (!isMobile) {
    await expect(sellerChat.getByText('Khách')).toBeVisible();
  }
});
