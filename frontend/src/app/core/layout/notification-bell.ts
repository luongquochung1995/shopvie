import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { AppNotification } from '../api/api.models';
import { LanguageService } from '../i18n/language.service';
import { notificationText } from '../notifications/notification-text';
import { NotificationStore } from '../notifications/notification.store';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';

/** Chuông thông báo trên header: badge số chưa đọc, danh sách gần nhất, đánh dấu đã đọc. */
@Component({
  selector: 'app-notification-bell',
  imports: [TranslocoDirective, DateTimePipe],
  template: `
    <div *transloco="let t; prefix: 'notifications'" class="relative">
      <button type="button" class="relative p-1 hover:opacity-80" aria-haspopup="true" [attr.aria-expanded]="open()"
              [attr.aria-label]="store.unreadCount() > 0 ? t('bellWithCount', { count: store.unreadCount() }) : t('bell')"
              (click)="open.set(!open())">
        <i class="pi pi-bell text-2xl md:text-xl" aria-hidden="true"></i>
        @if (store.unreadCount() > 0) {
          <span class="absolute -right-1 -top-1 min-w-5 rounded-full border-2 border-brand-500 bg-white px-1 text-center text-xs font-medium leading-4 text-brand-500"
                aria-hidden="true">{{ store.unreadCount() > 99 ? '99+' : store.unreadCount() }}</span>
        }
      </button>
      @if (open()) {
        <div class="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-sm bg-white text-sm text-gray-800 shadow-lg" role="dialog"
             [attr.aria-label]="t('title')">
          <div class="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <span class="font-medium">{{ t('title') }}</span>
            @if (store.unreadCount() > 0) {
              <button type="button" class="text-xs text-blue-600" (click)="store.markAllRead()">{{ t('markAllRead') }}</button>
            }
          </div>
          @if (store.items().length === 0) {
            <p class="px-3 py-6 text-center text-gray-500">{{ t('empty') }}</p>
          } @else {
            <ul class="max-h-96 overflow-y-auto">
              @for (n of store.items(); track n.id) {
                <li>
                  <button type="button" class="flex w-full gap-2 px-3 py-2 text-left hover:bg-gray-50" [class.bg-brand-50]="!n.read"
                          (click)="openNotification(n)">
                    <i class="pi mt-0.5 text-brand-500" [class]="icon(n)" aria-hidden="true"></i>
                    <span class="min-w-0 flex-1">
                      <span class="block" [class.font-medium]="!n.read">{{ text(n) }}</span>
                      <span class="block text-xs text-gray-500">{{ n.createdAt | dateTime }}</span>
                    </span>
                    @if (!n.read) {
                      <span class="mt-1.5 size-2 shrink-0 rounded-full bg-brand-500"><span class="sr-only">{{ t('unread') }}</span></span>
                    }
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'open.set(false)',
  },
})
export class NotificationBell {
  protected readonly store = inject(NotificationStore);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly open = signal(false);

  protected text(n: AppNotification): string {
    this.language.lang(); // dịch lại khi đổi ngôn ngữ
    return notificationText(n, (key, params) => this.transloco.translate(key, params), this.language.intlLocale());
  }

  protected icon(n: AppNotification): string {
    if (n.type.startsWith('SHOP_')) return 'pi-shop';
    if (n.type.startsWith('PRODUCT_')) return 'pi-box';
    if (n.type === 'PAYMENT_SUCCEEDED') return 'pi-wallet';
    return 'pi-file';
  }

  protected openNotification(n: AppNotification): void {
    this.store.markRead(n);
    this.open.set(false);
    if (n.link) {
      void this.router.navigateByUrl(n.link);
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
