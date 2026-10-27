import { HttpInterceptorFn } from '@angular/common/http';
import { inject, InjectionToken } from '@angular/core';

/**
 * Origin backend mà SSR server gọi trực tiếp trong mạng nội bộ (vd. http://backend:8080). Chỉ được cung cấp ở
 * cấu hình server (`app.config.server.ts`); trình duyệt không có token này.
 */
export const SERVER_API_ORIGIN = new InjectionToken<string>('SERVER_API_ORIGIN');

/**
 * Khi render phía server, URL API tương đối (`/api/v1/...`, bản production) được gọi thẳng tới backend thay vì
 * vòng qua domain công khai / nginx — hoặc tệ hơn, gọi ngược vào chính SSR server khi không có reverse proxy.
 */
export const serverApiInterceptor: HttpInterceptorFn = (req, next) => {
  const origin = inject(SERVER_API_ORIGIN, { optional: true });
  if (origin && req.url.startsWith('/')) {
    return next(req.clone({ url: origin.replace(/\/$/, '') + req.url }));
  }
  return next(req);
};
