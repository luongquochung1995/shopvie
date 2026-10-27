import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, filter, map, Observable, Subject } from 'rxjs';
import { ChatPush, NotificationPush } from '../core/api/api.models';
import { AuthStore } from '../core/auth/auth.store';
import { RealtimeService } from '../core/realtime/realtime.service';

/**
 * Kênh realtime giả cho cấu hình mock: backend giả đẩy tin qua {@link mockPush}; tin đi tới mọi tab cùng trình duyệt
 * (BroadcastChannel) để thử chat buyer ↔ seller ở 2 tab, mỗi tab chỉ nhận tin của user đang đăng nhập ở tab đó.
 */
interface Envelope {
  userId: number;
  queue: 'notifications' | 'chat';
  payload: unknown;
}

const CHANNEL = 'shopvi-mock-realtime';
const local = new Subject<Envelope>();
let channel: BroadcastChannel | null | undefined;

function broadcast(): BroadcastChannel | null {
  if (channel === undefined) {
    channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL);
    channel?.addEventListener('message', (e: MessageEvent<Envelope>) => local.next(e.data));
  }
  return channel;
}

/** Backend giả gọi khi có thông báo / tin chat cho user. */
export function mockPush(userId: number, queue: Envelope['queue'], payload: unknown): void {
  const envelope: Envelope = { userId, queue, payload: structuredClone(payload) };
  // Đẩy bất đồng bộ như tin từ server (sau khi response HTTP đã về)
  setTimeout(() => {
    local.next(envelope);
    broadcast()?.postMessage(envelope);
  });
}

@Injectable()
export class MockRealtimeService extends RealtimeService {
  private readonly auth = inject(AuthStore);

  readonly notifications$ = this.stream<NotificationPush>('notifications');
  readonly chat$ = this.stream<ChatPush>('chat');
  readonly connected$: Observable<boolean> = new BehaviorSubject(true);

  constructor() {
    super();
    broadcast();
  }

  private stream<T>(queue: Envelope['queue']): Observable<T> {
    return local.pipe(
      filter((e) => e.queue === queue && e.userId === this.auth.user()?.id),
      map((e) => e.payload as T),
    );
  }
}
