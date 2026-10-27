import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Subscription } from 'rxjs';
import { errorKey } from '../../../core/api/api-error';
import { ORDER_STATUSES, OrderStatus, OrderStatusCounts, SellerOrderDetail, SellerOrderSummary } from '../../../core/api/api.models';
import { SellerApi } from '../../../core/api/seller.api';
import { DateTimePipe } from '../../../shared/pipes/date-time.pipe';
import { VndPipe } from '../../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../../shared/ui/empty-state';
import { OrderItems } from '../../account/order-items';
import { SellerOrderActions } from './order-actions';

const PAGE_SIZE = 10;

/** Đơn hàng của shop theo tab trạng thái (kèm số đơn mỗi tab) và thao tác xử lý ngay trên danh sách. */
@Component({
  selector: 'app-seller-order-list',
  imports: [RouterLink, TranslocoDirective, TranslocoPipe, ButtonDirective, VndPipe, DateTimePipe, EmptyState, OrderItems, SellerOrderActions],
  template: `
    <div *transloco="let t; prefix: 'seller.orders'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <h1 class="mb-4 text-lg font-medium">{{ t('title') }}</h1>
      <nav class="-mx-4 mb-4 flex overflow-x-auto border-b border-gray-200 px-4 text-sm md:mx-0 md:px-0" [attr.aria-label]="t('tabs')">
        @for (tab of tabs; track tab) {
          <a [routerLink]="[]" [queryParams]="{ status: tab === 'ALL' ? null : tab }" class="shrink-0 border-b-2 px-3 py-3"
             [class]="currentTab() === tab ? 'border-brand-500 text-brand-500' : 'border-transparent hover:text-brand-500'"
             [attr.aria-current]="currentTab() === tab ? 'page' : null">
            {{ tab === 'ALL' ? t('all') : ('orderStatus.' + tab | transloco) }}
            @if (tab !== 'ALL' && counts()?.[tab]) {
              <span class="text-xs">({{ counts()![tab] }})</span>
            }
          </a>
        }
      </nav>

      @if (orders().length === 0 && !loading()) {
        <app-empty-state [title]="t('empty')" icon="pi-file" />
      }

      <div class="flex flex-col gap-3">
        @for (order of orders(); track order.code) {
          <article class="rounded-sm border border-gray-100">
            <header class="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 px-4 py-3 text-sm">
              <span class="flex min-w-0 items-center gap-2">
                <i class="pi pi-user text-gray-500" aria-hidden="true"></i>
                <span class="font-medium">{{ order.receiverName }}</span>
                <span class="text-xs text-gray-400">{{ order.createdAt | dateTime }}</span>
              </span>
              <span class="uppercase text-brand-500">{{ 'orderStatus.' + order.status | transloco }}</span>
            </header>
            <div class="px-4"><app-order-items [items]="order.items" /></div>
            @if (order.note) {
              <p class="px-4 pb-2 text-sm text-gray-600">{{ 'checkout.note' | transloco }} {{ order.note }}</p>
            }
            <footer class="flex flex-col gap-3 border-t border-gray-100 bg-brand-50/30 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
              <span>
                {{ t('total') }} <b class="text-lg font-medium text-brand-500">{{ order.total | vnd }}</b>
                <span class="ml-1 text-xs text-gray-500">· {{ 'checkout.methods.' + order.paymentMethod | transloco }}</span>
              </span>
              <div class="flex flex-wrap justify-end gap-2">
                <app-seller-order-actions [code]="order.code" [actions]="order.actions" (changed)="onChanged($event)" />
                <a pButton size="small" [outlined]="true" [routerLink]="['/seller-center/orders', order.code]">{{ t('viewDetail') }}</a>
              </div>
            </footer>
          </article>
        }
      </div>

      @if (loading()) {
        <p class="py-6 text-center text-gray-500">{{ 'common.loading' | transloco }}</p>
      } @else if (hasMore()) {
        <div class="mt-4 flex justify-center">
          <button pButton type="button" [outlined]="true" (click)="fetch(page() + 1)">{{ 'home.loadMore' | transloco }}</button>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class SellerOrderList {
  private readonly api = inject(SellerApi);
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
  protected readonly orders = signal<SellerOrderSummary[]>([]);
  protected readonly counts = signal<OrderStatusCounts | null>(null);
  protected readonly loading = signal(false);
  protected readonly page = signal(0);
  private readonly totalPages = signal(0);
  protected readonly hasMore = computed(() => this.page() + 1 < this.totalPages());
  private request?: Subscription;

  constructor() {
    effect(() => {
      this.currentTab();
      this.orders.set([]);
      this.fetch(0);
    });
    this.loadCounts();
  }

  /** Đơn vừa đổi trạng thái: cập nhật tại chỗ hoặc bỏ khỏi tab đang lọc; làm mới số đếm. */
  protected onChanged(updated: SellerOrderDetail): void {
    const tab = this.currentTab();
    this.orders.update((list) =>
      tab === 'ALL'
        ? list.map((o) => (o.code === updated.code ? { ...o, status: updated.status, actions: updated.actions } : o))
        : list.filter((o) => o.code !== updated.code),
    );
    this.loadCounts();
  }

  protected fetch(page: number): void {
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

  private loadCounts(): void {
    this.api.orderCounts().subscribe({ next: (c) => this.counts.set(c), error: () => undefined });
  }
}
