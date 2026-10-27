import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Subscription } from 'rxjs';
import { errorKey } from '../../core/api/api-error';
import { ORDER_STATUSES, OrderStatus, OrderSummary } from '../../core/api/api.models';
import { OrderApi } from '../../core/api/order.api';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { OrderItems } from './order-items';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-orders',
  imports: [RouterLink, TranslocoDirective, ButtonDirective, VndPipe, EmptyState, OrderItems],
  template: `
    <ng-container *transloco="let t">
      <h1 class="mb-4 text-lg font-medium">{{ t('orders.title') }}</h1>
      <nav class="-mx-4 mb-4 flex overflow-x-auto border-b border-gray-200 px-4 text-sm md:mx-0 md:px-0" [attr.aria-label]="t('orders.tabs')">
        @for (tab of tabs; track tab) {
          <a [routerLink]="[]" [queryParams]="{ status: tab === 'ALL' ? null : tab }"
             class="shrink-0 border-b-2 px-4 py-3"
             [class]="currentTab() === tab ? 'border-brand-500 text-brand-500' : 'border-transparent hover:text-brand-500'"
             [attr.aria-current]="currentTab() === tab ? 'page' : null">
            {{ tab === 'ALL' ? t('orders.all') : t('orderStatus.' + tab) }}
          </a>
        }
      </nav>

      @if (orders().length === 0 && !loading()) {
        <app-empty-state [title]="t('orders.empty')" icon="pi-file">
          <a pButton routerLink="/" class="mt-2">{{ t('cart.shopNow') }}</a>
        </app-empty-state>
      }

      <div class="flex flex-col gap-3">
        @for (order of orders(); track order.code) {
          <article class="rounded-sm border border-gray-100">
            <header class="flex items-center justify-between gap-2 border-b border-gray-100 px-4 py-3 text-sm">
              <span class="flex items-center gap-2 font-medium"><i class="pi pi-shop text-gray-500" aria-hidden="true"></i>{{ order.shop?.name }}</span>
              <span class="uppercase text-brand-500">{{ t('orderStatus.' + order.status) }}</span>
            </header>
            <div class="px-4">
              <app-order-items [items]="order.items" />
            </div>
            <footer class="flex flex-col gap-3 border-t border-gray-100 bg-brand-50/30 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
              <span>{{ t('orders.total') }} <b class="text-lg font-medium text-brand-500">{{ order.total | vnd }}</b></span>
              <div class="flex gap-2">
                @if (order.cancellable) {
                  <button pButton type="button" size="small" [outlined]="true" severity="secondary"
                          [disabled]="cancelling() === order.code" (click)="cancel(order)">{{ t('orders.cancel') }}</button>
                }
                <a pButton size="small" [routerLink]="['/account/orders', order.code]">{{ t('orders.viewDetail') }}</a>
              </div>
            </footer>
          </article>
        }
      </div>

      @if (loading()) {
        <p class="py-6 text-center text-gray-500">{{ t('common.loading') }}</p>
      } @else if (hasMore()) {
        <div class="mt-4 flex justify-center">
          <button pButton type="button" [outlined]="true" (click)="loadMore()">{{ t('home.loadMore') }}</button>
        </div>
      }
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Orders {
  private readonly api = inject(OrderApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  /** Query param ?status=... */
  readonly status = input<string>();

  protected readonly tabs = ['ALL', ...ORDER_STATUSES] as const;
  protected readonly currentTab = computed(() => {
    const s = this.status();
    return (ORDER_STATUSES as readonly string[]).includes(s ?? '') ? (s as OrderStatus) : 'ALL';
  });
  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly cancelling = signal<string | null>(null);
  private readonly page = signal(0);
  private readonly totalPages = signal(0);
  protected readonly hasMore = computed(() => this.page() + 1 < this.totalPages());
  private request?: Subscription;

  constructor() {
    // Đổi tab → tải lại từ trang đầu
    effect(() => {
      this.currentTab();
      this.orders.set([]);
      this.fetch(0);
    });
  }

  protected loadMore(): void {
    this.fetch(this.page() + 1);
  }

  protected cancel(order: OrderSummary): void {
    if (!confirm(this.transloco.translate('orders.confirmCancel'))) {
      return;
    }
    this.cancelling.set(order.code);
    this.api.cancel(order.code).subscribe({
      next: (updated) => {
        this.cancelling.set(null);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('orders.cancelled') });
        this.orders.update((list) =>
          this.currentTab() === 'ALL' || this.currentTab() === updated.status
            ? list.map((o) => (o.code === updated.code ? { ...o, status: updated.status, cancellable: updated.cancellable } : o))
            : list.filter((o) => o.code !== updated.code),
        );
      },
      error: (err) => {
        this.cancelling.set(null);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }

  private fetch(page: number): void {
    const tab = this.currentTab();
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.api
      .orders(tab === 'ALL' ? null : tab, page, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.orders.update((list) => (page === 0 ? res.items : [...list, ...res.items]));
          this.page.set(res.page);
          this.totalPages.set(res.totalPages);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        },
      });
  }
}
