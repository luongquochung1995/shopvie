import { Observable } from 'rxjs';
import { ChatPush, NotificationPush } from '../api/api.models';

/**
 * Kênh realtime của người dùng đang đăng nhập: thông báo mới và tin chat. Bản thật dùng STOMP over SockJS
 * ({@link StompRealtimeService}); cấu hình mock dùng kênh giả trong trình duyệt. Chọn theo `environment.realtime`.
 */
export abstract class RealtimeService {
  /** Thông báo mới (kèm số chưa đọc). */
  abstract readonly notifications$: Observable<NotificationPush>;
  /** Tin chat mới / bên kia đã xem. */
  abstract readonly chat$: Observable<ChatPush>;
  /** Đang kết nối được tới server. */
  abstract readonly connected$: Observable<boolean>;
}
