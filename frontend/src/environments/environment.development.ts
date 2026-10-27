import { HttpInterceptorFn } from '@angular/common/http';
import { StompRealtimeService } from '../app/core/realtime/stomp-realtime.service';

export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  interceptors: [] as HttpInterceptorFn[],
  /** Endpoint STOMP over SockJS (thông báo, chat realtime). */
  wsUrl: 'http://localhost:8080/ws',
  /** Cài đặt kênh realtime. */
  realtime: StompRealtimeService,
};
