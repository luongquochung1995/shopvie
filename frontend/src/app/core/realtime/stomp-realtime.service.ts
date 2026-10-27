import { isPlatformBrowser } from '@angular/common';
import { DestroyRef, effect, inject, Injectable, PLATFORM_ID } from '@angular/core';
import type { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, firstValueFrom, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatPush, NotificationPush } from '../api/api.models';
import { AuthStore } from '../auth/auth.store';
import { RealtimeService } from './realtime.service';

const RECONNECT_MS = 5000;
/** Làm mới access token trước khi kết nối nếu nó hết hạn trong khoảng này. */
const REFRESH_MARGIN_MS = 30_000;

/**
 * STOMP over SockJS tới `/ws`: kết nối khi đăng nhập (JWT trong header CONNECT, lấy token mới nhất mỗi lần kết nối
 * lại), ngắt khi đăng xuất. Chỉ chạy trên trình duyệt; thư viện STOMP/SockJS nạp động khi cần (không vào bundle đầu).
 */
@Injectable()
export class StompRealtimeService extends RealtimeService {
  private readonly auth = inject(AuthStore);
  private readonly notificationsSubject = new Subject<NotificationPush>();
  private readonly chatSubject = new Subject<ChatPush>();
  private readonly connectedSubject = new BehaviorSubject(false);
  private client: Client | null = null;
  /** Tăng mỗi lần đổi user: kết nối đang nạp thư viện dở cho user cũ thì bỏ. */
  private generation = 0;

  readonly notifications$ = this.notificationsSubject.asObservable();
  readonly chat$ = this.chatSubject.asObservable();
  readonly connected$ = this.connectedSubject.asObservable();

  constructor() {
    super();
    if (!isPlatformBrowser(inject(PLATFORM_ID))) {
      return;
    }
    // Theo user (không theo token): đổi token khi refresh không cần kết nối lại
    effect(() => {
      const userId = this.auth.user()?.id;
      this.disconnect();
      if (userId) {
        void this.connect();
      }
    });
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  private async connect(): Promise<void> {
    const generation = this.generation;
    // sockjs-client (CommonJS) tham chiếu biến `global` của Node — trình duyệt không có
    (globalThis as { global?: unknown }).global ??= globalThis;
    const [{ Client }, { default: SockJS }] = await Promise.all([import('@stomp/stompjs'), import('sockjs-client')]);
    if (generation !== this.generation) {
      return;
    }
    const client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      reconnectDelay: RECONNECT_MS,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      beforeConnect: async (c) => {
        c.connectHeaders = { Authorization: `Bearer ${await this.freshToken()}` };
      },
      onConnect: () => {
        this.connectedSubject.next(true);
        client.subscribe('/user/queue/notifications', (m) => this.notificationsSubject.next(parse(m)));
        client.subscribe('/user/queue/chat', (m) => this.chatSubject.next(parse(m)));
      },
      onWebSocketClose: () => this.connectedSubject.next(false),
      onStompError: () => this.connectedSubject.next(false),
    });
    client.activate();
    this.client = client;
  }

  /** Token còn hạn; sắp hết hạn thì làm mới (kết nối lại sau khi mất mạng lâu). */
  private async freshToken(): Promise<string> {
    const token = this.auth.accessToken() ?? '';
    if (expiresAt(token) - Date.now() > REFRESH_MARGIN_MS) {
      return token;
    }
    try {
      return await firstValueFrom(this.auth.refresh());
    } catch {
      return token;
    }
  }

  private disconnect(): void {
    this.generation++;
    if (this.client) {
      void this.client.deactivate();
      this.client = null;
      this.connectedSubject.next(false);
    }
  }
}

function parse<T>(m: IMessage): T {
  return JSON.parse(m.body) as T;
}

/** Thời điểm hết hạn (ms) đọc từ claim `exp` của JWT; không đọc được → 0 (coi như cần làm mới). */
function expiresAt(token: string): number {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number };
    return (payload.exp ?? 0) * 1000;
  } catch {
    return 0;
  }
}
