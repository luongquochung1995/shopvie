import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Trang cần đăng nhập/phiên người dùng: chỉ render phía trình duyệt (SSR không có access token)
  { path: 'auth/**', renderMode: RenderMode.Client },
  { path: 'account/**', renderMode: RenderMode.Client },
  { path: 'cart', renderMode: RenderMode.Client },
  { path: 'checkout/**', renderMode: RenderMode.Client },
  { path: 'seller-center', renderMode: RenderMode.Client },
  { path: 'seller-center/**', renderMode: RenderMode.Client },
  { path: 'admin', renderMode: RenderMode.Client },
  { path: 'admin/**', renderMode: RenderMode.Client },
  // Trang public (trang chủ, danh mục, sản phẩm): SSR theo từng request cho SEO
  { path: '**', renderMode: RenderMode.Server },
];
