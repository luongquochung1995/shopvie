import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { Subscription } from 'rxjs';
import { errorKey } from '../../../core/api/api-error';
import { OrderStatus, SellerDashboard } from '../../../core/api/api.models';
import { SellerApi } from '../../../core/api/seller.api';
import { VndPipe } from '../../../shared/pipes/vnd.pipe';
import { RevenueChart } from './revenue-chart';

type RangeKey = '7d' | '30d' | '12m';

/** yyyy-MM-dd theo giờ máy người dùng. */
export function isoDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Dashboard người bán: việc cần làm, doanh số theo kỳ, sản phẩm bán chạy. */
@Component({
  selector: 'app-seller-dashboard',
  imports: [RouterLink, TranslocoDirective, TranslocoPipe, VndPipe, RevenueChart],
  template: `
    <div *transloco="let t; prefix: 'seller.dashboard'" class="flex flex-col gap-4">
      <!-- Việc cần làm -->
      <section class="rounded-sm bg-white p-4 shadow-sm">
        <h1 class="mb-3 text-lg font-medium">{{ t('todo') }}</h1>
        <ul class="grid grid-cols-3 gap-2 text-center">
          @for (s of todoStatuses; track s) {
            <li>
              <a [routerLink]="['/seller-center/orders']" [queryParams]="{ status: s }"
                 class="flex flex-col rounded-sm p-2 hover:bg-brand-50">
                <span class="text-xl font-medium text-blue-600 tabular-nums">{{ data()?.statusCounts?.[s] ?? 0 }}</span>
                <span class="text-xs text-gray-600">{{ t('todoStatus.' + s) }}</span>
              </a>
            </li>
          }
        </ul>
      </section>

      <!-- Doanh số -->
      <section class="rounded-sm bg-white p-4 shadow-sm">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 class="text-lg font-medium">{{ t('sales') }}</h2>
          <div class="flex rounded-sm border border-gray-200 text-sm" role="group" [attr.aria-label]="t('range')">
            @for (r of ranges; track r) {
              <button type="button" class="px-3 py-1.5" [attr.aria-pressed]="range() === r"
                      [class]="range() === r ? 'bg-brand-500 text-white' : 'text-gray-600 hover:bg-gray-50'"
                      (click)="range.set(r)">{{ t('ranges.' + r) }}</button>
            }
          </div>
        </div>

        @if (data(); as d) {
          <dl class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
            <div class="rounded-sm bg-gray-50 p-3">
              <dt class="text-xs text-gray-500">{{ t('revenue') }}</dt>
              <dd class="text-lg font-medium text-brand-500 tabular-nums">{{ d.summary.revenue | vnd }}</dd>
            </div>
            <div class="rounded-sm bg-gray-50 p-3">
              <dt class="text-xs text-gray-500">{{ t('orders') }}</dt>
              <dd class="text-lg font-medium tabular-nums">{{ d.summary.orders }}</dd>
            </div>
            <div class="rounded-sm bg-gray-50 p-3">
              <dt class="text-xs text-gray-500">{{ t('unitsSold') }}</dt>
              <dd class="text-lg font-medium tabular-nums">{{ d.summary.unitsSold }}</dd>
            </div>
            <div class="rounded-sm bg-gray-50 p-3">
              <dt class="text-xs text-gray-500">{{ t('cancelled') }}</dt>
              <dd class="text-lg font-medium tabular-nums">{{ d.summary.cancelledOrders }}</dd>
            </div>
          </dl>
          <h3 class="mb-2 text-sm text-gray-600">{{ t(d.granularity === 'DAY' ? 'chartTitleDay' : 'chartTitleMonth') }}</h3>
          <app-revenue-chart [points]="d.series" [granularity]="d.granularity" />
          <p class="mt-2 text-xs text-gray-400">{{ t('revenueHint') }}</p>
        } @else if (errorKey(); as key) {
          <p class="py-8 text-center text-gray-500">{{ key | transloco }}</p>
        } @else {
          <p class="py-8 text-center text-gray-500">{{ t('loading') }}</p>
        }
      </section>

      <!-- Sản phẩm bán chạy -->
      <section class="rounded-sm bg-white p-4 shadow-sm">
        <h2 class="mb-3 text-lg font-medium">{{ t('topProducts') }}</h2>
        @if (data()?.topProducts?.length) {
          <ol class="divide-y divide-gray-50">
            @for (p of data()!.topProducts; track p.productId; let i = $index) {
              <li class="flex items-center gap-3 py-2 text-sm">
                <span class="w-5 shrink-0 text-center font-medium text-gray-400">{{ i + 1 }}</span>
                <span class="size-10 shrink-0 overflow-hidden bg-gray-50">
                  @if (p.imageUrl) {
                    <img [src]="p.imageUrl" alt="" loading="lazy" class="size-full object-cover" />
                  }
                </span>
                <span class="min-w-0 flex-1 truncate">{{ p.productName }}</span>
                <span class="shrink-0 text-right">
                  <span class="block tabular-nums">{{ t('sold', { count: p.quantity }) }}</span>
                  <span class="block text-xs text-gray-500 tabular-nums">{{ p.revenue | vnd }}</span>
                </span>
              </li>
            }
          </ol>
        } @else if (data()) {
          <p class="py-4 text-center text-sm text-gray-500">{{ t('noSales') }}</p>
        }
      </section>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Dashboard {
  private readonly api = inject(SellerApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly todoStatuses: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPING'];
  protected readonly ranges: RangeKey[] = ['7d', '30d', '12m'];
  protected readonly range = signal<RangeKey>('30d');
  protected readonly data = signal<SellerDashboard | null>(null);
  protected readonly errorKey = signal<string | null>(null);
  private request?: Subscription;

  constructor() {
    effect(() => this.fetch(this.range()));
  }

  private fetch(range: RangeKey): void {
    const today = new Date();
    const from = new Date(today);
    if (range === '7d') {
      from.setDate(today.getDate() - 6);
    } else if (range === '30d') {
      from.setDate(today.getDate() - 29);
    }
    this.request?.unsubscribe();
    this.errorKey.set(null);
    this.request = this.api
      .dashboard(range === '12m' ? 'MONTH' : 'DAY', range === '12m' ? undefined : isoDate(from), isoDate(today))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (d) => this.data.set(d),
        error: (err) => this.errorKey.set(errorKey(err)),
      });
  }
}
