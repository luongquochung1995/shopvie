import { HttpInterceptorFn } from '@angular/common/http';
import { mockBackendInterceptor } from '../app/mock/mock-backend.interceptor';

const apiBaseUrl = '/api/v1';

/**
 * Chạy frontend không cần backend (`npm run start:mock`): mọi request tới API được trả về bởi
 * backend giả với dữ liệu test (src/app/mock). Tài khoản: buyer@shopvi.local / Password@123.
 */
export const environment = {
  production: false,
  apiBaseUrl,
  interceptors: [mockBackendInterceptor(apiBaseUrl)] as HttpInterceptorFn[],
};
