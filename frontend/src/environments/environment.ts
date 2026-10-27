import { HttpInterceptorFn } from '@angular/common/http';

export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  /** Interceptor bổ sung theo môi trường (cấu hình `mock` dùng để giả lập backend). */
  interceptors: [] as HttpInterceptorFn[],
};
