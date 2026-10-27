import { HttpInterceptorFn } from '@angular/common/http';
import { MockRealtimeService } from '../app/mock/mock-realtime';
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
  /** Endpoint STOMP over SockJS (thông báo, chat realtime). */
  wsUrl: '/ws',
  /** Cài đặt kênh realtime. */
  realtime: MockRealtimeService,
};
