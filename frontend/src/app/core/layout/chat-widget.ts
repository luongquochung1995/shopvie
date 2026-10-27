import { ChangeDetectionStrategy, Component, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { errorKey } from '../api/api-error';
import { ChatMessage, Conversation } from '../api/api.models';
import { AuthStore } from '../auth/auth.store';
import { ChatStore } from '../chat/chat.store';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';

const MAX_LENGTH = 2000;

/** Widget chat nổi góc phải dưới: danh sách hội thoại + khung tin nhắn (người mua ↔ shop). */
@Component({
  selector: 'app-chat-widget',
  imports: [FormsModule, TranslocoDirective, DateTimePipe],
  template: `
    @if (auth.isLoggedIn()) {
      <ng-container *transloco="let t; prefix: 'chat'">
        @if (!store.panelOpen()) {
          <button type="button" class="fixed bottom-4 right-4 z-40 flex items-center gap-2 rounded-full bg-brand-500 px-4 py-3 text-white shadow-lg hover:bg-brand-600"
                  [attr.aria-label]="store.unreadTotal() > 0 ? t('openWithCount', { count: store.unreadTotal() }) : t('open')"
                  (click)="store.toggle()">
            <i class="pi pi-comments text-lg" aria-hidden="true"></i>
            <span class="hidden text-sm font-medium sm:inline">{{ t('title') }}</span>
            @if (store.unreadTotal() > 0) {
              <span class="min-w-5 rounded-full bg-white px-1 text-center text-xs font-medium leading-5 text-brand-500" aria-hidden="true">
                {{ store.unreadTotal() > 99 ? '99+' : store.unreadTotal() }}</span>
            }
          </button>
        } @else {
          <section class="fixed inset-x-0 bottom-0 z-50 flex h-[70vh] flex-col overflow-hidden rounded-t-md bg-white shadow-2xl sm:inset-x-auto sm:bottom-4 sm:right-4 sm:h-[30rem] sm:w-[40rem] sm:rounded-md"
                   role="dialog" [attr.aria-label]="t('title')">
            <header class="flex items-center justify-between border-b border-gray-100 px-3 py-2">
              <h2 class="font-medium text-brand-500">{{ t('title') }}</h2>
              <button type="button" class="p-1 text-gray-500 hover:text-gray-800" [attr.aria-label]="t('close')" (click)="store.close()">
                <i class="pi pi-times" aria-hidden="true"></i>
              </button>
            </header>
            <div class="flex min-h-0 flex-1">
              <!-- Danh sách hội thoại (mobile: ẩn khi đang mở một hội thoại) -->
              <ul class="w-full overflow-y-auto border-r border-gray-100 sm:block sm:w-56 sm:shrink-0" [class.hidden]="store.activeId() !== null"
                  [attr.aria-label]="t('conversations')">
                @if (store.conversations().length === 0) {
                  <li class="p-4 text-center text-sm text-gray-500">{{ t('empty') }}</li>
                }
                @for (c of store.conversations(); track c.id) {
                  <li>
                    <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-gray-50"
                            [class.bg-brand-50]="c.id === store.activeId()" [attr.aria-current]="c.id === store.activeId() || null"
                            (click)="store.select(c.id)">
                      <span class="flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-gray-100 text-gray-500">
                        @if (c.counterpartAvatar) {
                          <img [src]="c.counterpartAvatar" alt="" class="size-full object-cover" />
                        } @else {
                          <i class="pi" [class]="c.side === 'BUYER' ? 'pi-shop' : 'pi-user'" aria-hidden="true"></i>
                        }
                      </span>
                      <span class="min-w-0 flex-1">
                        <span class="flex items-center gap-1">
                          <span class="truncate font-medium">{{ c.counterpart ?? '—' }}</span>
                          @if (c.side === 'SHOP') {
                            <span class="shrink-0 rounded-sm bg-gray-100 px-1 text-[10px] text-gray-600">{{ t('customer') }}</span>
                          }
                        </span>
                        <span class="block truncate text-xs text-gray-500">{{ c.lastMessage ?? t('noMessages') }}</span>
                      </span>
                      @if (c.unread > 0) {
                        <span class="min-w-5 rounded-full bg-brand-500 px-1 text-center text-xs leading-5 text-white">{{ c.unread }}</span>
                      }
                    </button>
                  </li>
                }
              </ul>

              <!-- Khung tin nhắn -->
              <div class="min-w-0 flex-1 flex-col sm:flex" [class.flex]="store.activeId() !== null" [class.hidden]="store.activeId() === null">
                @if (store.active(); as c) {
                  <div class="flex items-center gap-2 border-b border-gray-100 px-3 py-2 text-sm">
                    <button type="button" class="p-1 sm:hidden" [attr.aria-label]="t('back')" (click)="store.select(null)">
                      <i class="pi pi-angle-left" aria-hidden="true"></i>
                    </button>
                    <span class="truncate font-medium">{{ c.counterpart }}</span>
                  </div>
                  <div #scroller class="flex-1 overflow-y-auto bg-gray-50 px-3 py-2" aria-live="polite">
                    @if (store.hasMore()) {
                      <button type="button" class="mb-2 w-full text-center text-xs text-blue-600" (click)="store.loadOlder()">{{ t('older') }}</button>
                    }
                    @for (m of store.messages(); track m.id) {
                      <div class="mb-2 flex" [class.justify-end]="isMine(m, c)" [attr.data-message]="m.id">
                        <div class="max-w-[75%] rounded-lg px-3 py-1.5 text-sm"
                             [class]="isMine(m, c) ? 'bg-brand-500 text-white' : 'bg-white text-gray-800 shadow-sm'">
                          <p class="whitespace-pre-line break-words">{{ m.content }}</p>
                          <p class="mt-0.5 text-right text-[10px]" [class]="isMine(m, c) ? 'text-white/80' : 'text-gray-400'">
                            {{ m.createdAt | dateTime }}
                            @if (isMine(m, c) && m.id === lastSeenMine()) {
                              · {{ t('seen') }}
                            }
                          </p>
                        </div>
                      </div>
                    }
                    @if (!store.loadingMessages() && store.messages().length === 0) {
                      <p class="py-6 text-center text-sm text-gray-500">{{ t('startHint') }}</p>
                    }
                  </div>
                  <form class="flex gap-2 border-t border-gray-100 p-2" (ngSubmit)="send()">
                    <input type="text" name="message" autocomplete="off" [maxlength]="maxLength" class="min-w-0 flex-1 rounded-sm border border-gray-300 px-2 py-1.5 text-sm"
                           [placeholder]="t('placeholder')" [attr.aria-label]="t('message')"
                           [ngModel]="draft()" (ngModelChange)="draft.set($event)" />
                    <button type="submit" class="rounded-sm bg-brand-500 px-3 text-white disabled:opacity-50" [disabled]="sending() || !draft().trim()"
                            [attr.aria-label]="t('send')"><i class="pi pi-send" aria-hidden="true"></i></button>
                  </form>
                } @else {
                  <p class="m-auto p-6 text-center text-sm text-gray-500">{{ t('pick') }}</p>
                }
              </div>
            </div>
          </section>
        }
      </ng-container>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatWidget {
  protected readonly store = inject(ChatStore);
  protected readonly auth = inject(AuthStore);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly maxLength = MAX_LENGTH;
  protected readonly draft = signal('');
  protected readonly sending = signal(false);

  constructor() {
    // Cuộn xuống tin mới nhất khi có tin mới
    effect(() => {
      const count = this.store.messages().length;
      const el = this.scroller()?.nativeElement;
      if (el && count) {
        queueMicrotask(() => (el.scrollTop = el.scrollHeight));
      }
    });
  }

  protected isMine(m: ChatMessage, c: Conversation): boolean {
    return m.senderSide === c.side;
  }

  /** Tin cuối cùng của tôi mà bên kia đã xem (hiện "Đã xem" dưới tin đó). */
  protected lastSeenMine(): number | null {
    const seen = this.store.otherLastRead();
    const side = this.store.mySide();
    if (seen === null || side === null) {
      return null;
    }
    const mine = this.store.messages().filter((m) => m.senderSide === side && m.id <= seen);
    return mine.length ? mine[mine.length - 1].id : null;
  }

  protected send(): void {
    const content = this.draft().trim();
    if (!content) {
      return;
    }
    this.sending.set(true);
    this.store.send(content).subscribe({
      next: () => {
        this.sending.set(false);
        this.draft.set('');
      },
      error: (err) => {
        this.sending.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
