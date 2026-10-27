import { isPlatformBrowser } from '@angular/common';
import { DestroyRef, effect, inject, Injectable, PLATFORM_ID, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { AppNotification } from '../api/api.models';
import { NotificationApi } from '../api/notification.api';
import { AuthStore } from '../auth/auth.store';
import { RealtimeService } from '../realtime/realtime.service';
import { notificationText } from './notification-text';

const RECENT = 10;

/**
 * Thông báo của người dùng cho chuông trên header: tải 10 thông báo gần nhất khi đăng nhập, nhận thông báo mới qua
 * kênh realtime (thêm vào đầu danh sách + toast), đánh dấu đã đọc.
 */
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly api = inject(NotificationApi);
  private readonly auth = inject(AuthStore);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  readonly items = signal<AppNotification[]>([]);
  readonly unreadCount = signal(0);

  constructor() {
    if (!isPlatformBrowser(inject(PLATFORM_ID))) {
      return;
    }
    effect(() => {
      const loggedIn = this.auth.isLoggedIn();
      untracked(() => (loggedIn ? this.load() : this.reset()));
    });
    inject(RealtimeService)
      .notifications$.pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((push) => {
        this.items.update((list) => [push.notification, ...list.filter((n) => n.id !== push.notification.id)].slice(0, RECENT));
        this.unreadCount.set(push.unreadCount);
        this.messages.add({
          severity: 'info',
          summary: this.transloco.translate('notifications.new'),
          detail: notificationText(push.notification, (key, params) => this.transloco.translate(key, params)),
          life: 6000,
        });
      });
  }

  load(): void {
    this.api.list(0, RECENT).subscribe({
      next: (page) => {
        this.items.set(page.items);
        this.unreadCount.set(page.unreadCount);
      },
      error: () => undefined,
    });
  }

  markRead(n: AppNotification): void {
    if (n.read) {
      return;
    }
    this.items.update((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
    this.unreadCount.update((c) => Math.max(0, c - 1));
    this.api.markRead(n.id).subscribe({ error: () => this.load() });
  }

  markAllRead(): void {
    this.items.update((list) => list.map((x) => ({ ...x, read: true })));
    this.unreadCount.set(0);
    this.api.markAllRead().subscribe({ error: () => this.load() });
  }

  private reset(): void {
    this.items.set([]);
    this.unreadCount.set(0);
  }
}
