import { HttpInterceptorFn } from '@angular/common/http';
import { StompRealtimeService } from '../app/core/realtime/stomp-realtime.service';

export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  /** Interceptor bổ sung theo môi trường (cấu hình `mock` dùng để giả lập backend). */
  interceptors: [] as HttpInterceptorFn[],
  /** Endpoint STOMP over SockJS (thông báo, chat realtime). */
  wsUrl: '/ws',
  /** Cài đặt kênh realtime. */
  realtime: StompRealtimeService,
};
