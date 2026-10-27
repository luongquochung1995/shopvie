import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { AdminApi } from '../../core/api/admin.api';
import { AdminOverview, DashboardPoint } from '../../core/api/api.models';
import { CompactCountPipe, VndPipe } from '../../shared/pipes/vnd.pipe';
import { RevenueChart } from '../seller-center/dashboard/revenue-chart';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ok'; data: AdminOverview };

/** Tổng quan toàn sàn: người dùng, shop, sản phẩm, đơn hàng + GMV theo ngày. */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, TranslocoDirective, TranslocoPipe, VndPipe, CompactCountPipe, RevenueChart],
  template: `
    <div *transloco="let t; prefix: 'admin.dashboard'" class="flex flex-col gap-3">
      <div class="flex flex-wrap items-center justify-between gap-2 rounded-sm bg-white p-4 shadow-sm">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        <div class="flex gap-1 text-sm" role="group" [attr.aria-label]="t('period')">
          @for (d of [7, 30, 90]; track d) {
            <button type="button" class="rounded-sm border px-3 py-1" [attr.aria-pressed]="days() === d"
                    [class]="days() === d ? 'border-brand-500 text-brand-500' : 'border-gray-200'" (click)="days.set(d)">
              {{ t('lastDays', { count: d }) }}</button>
          }
        </div>
      </div>
      @switch (state().status) {
        @case ('error') {
          <p class="rounded-sm bg-white p-6 text-center text-gray-500 shadow-sm">{{ t('loadError') }}</p>
        }
        @case ('ok') {
          @if (data(); as d) {
            <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
              <div class="rounded-sm bg-white p-4 shadow-sm">
                <p class="text-sm text-gray-500">{{ t('gmv') }}</p>
                <p class="text-xl font-medium text-brand-500">{{ d.orders.gmv | vnd }}</p>
              </div>
              <div class="rounded-sm bg-white p-4 shadow-sm">
                <p class="text-sm text-gray-500">{{ t('orders') }}</p>
                <p class="text-xl font-medium">{{ d.orders.orders | compactCount }}</p>
              </div>
              <div class="rounded-sm bg-white p-4 shadow-sm">
                <p class="text-sm text-gray-500">{{ t('newUsers') }}</p>
                <p class="text-xl font-medium">{{ d.users.newSince | compactCount }}</p>
                <p class="text-xs text-gray-500">{{ t('totalUsers', { count: d.users.total }) }}</p>
              </div>
              <div class="rounded-sm bg-white p-4 shadow-sm">
                <p class="text-sm text-gray-500">{{ t('activeShops') }}</p>
                <p class="text-xl font-medium">{{ d.shops['ACTIVE'] ?? 0 }}</p>
                @if (d.shops['PENDING']) {
                  <a routerLink="/admin/shops" [queryParams]="{ status: 'PENDING' }" class="text-xs text-blue-600">
                    {{ t('pendingShops', { count: d.shops['PENDING'] }) }}</a>
                }
              </div>
            </div>
            <section class="rounded-sm bg-white p-4 shadow-sm">
              <h2 class="mb-2 font-medium">{{ t('chart') }}</h2>
              <app-revenue-chart [points]="points()" granularity="DAY" />
            </section>
            <div class="grid gap-3 md:grid-cols-3">
              <section class="rounded-sm bg-white p-4 shadow-sm">
                <h2 class="mb-2 font-medium">{{ t('ordersByStatus') }}</h2>
                <dl class="grid grid-cols-[1fr_auto] gap-y-1 text-sm">
                  @for (s of orderStatuses; track s) {
                    <dt class="text-gray-600">{{ 'orderStatus.' + s | transloco }}</dt>
                    <dd class="text-right">{{ d.orders.byStatus[s] ?? 0 }}</dd>
                  }
                </dl>
              </section>
              <section class="rounded-sm bg-white p-4 shadow-sm">
                <h2 class="mb-2 font-medium">{{ t('usersByRole') }}</h2>
                <dl class="grid grid-cols-[1fr_auto] gap-y-1 text-sm">
                  @for (r of ['BUYER', 'SELLER', 'ADMIN']; track r) {
                    <dt class="text-gray-600">{{ 'admin.users.role.' + r | transloco }}</dt>
                    <dd class="text-right">{{ d.users.byRole[r] ?? 0 }}</dd>
                  }
                  <dt class="text-gray-600">{{ t('lockedUsers') }}</dt>
                  <dd class="text-right">{{ d.users.locked }}</dd>
                </dl>
              </section>
              <section class="rounded-sm bg-white p-4 shadow-sm">
                <h2 class="mb-2 font-medium">{{ t('productsByStatus') }}</h2>
                <dl class="grid grid-cols-[1fr_auto] gap-y-1 text-sm">
                  @for (s of ['ACTIVE', 'HIDDEN', 'BANNED']; track s) {
                    <dt class="text-gray-600">{{ 'productStatus.' + s | transloco }}</dt>
                    <dd class="text-right">{{ d.products[s] ?? 0 }}</dd>
                  }
                </dl>
              </section>
            </div>
          }
        }
        @default {
          <p class="rounded-sm bg-white p-6 text-center text-gray-500 shadow-sm">{{ 'common.loading' | transloco }}</p>
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class AdminDashboard {
  private readonly api = inject(AdminApi);

  protected readonly orderStatuses = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED'];
  protected readonly days = signal(30);
  protected readonly state = toSignal(
    toObservable(this.days).pipe(
      switchMap((days) =>
        this.api.overview(days).pipe(
          map((data): State => ({ status: 'ok', data })),
          catchError(() => of<State>({ status: 'error' })),
          startWith<State>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as State },
  );
  protected readonly data = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });
  protected readonly points = computed<DashboardPoint[]>(() =>
    (this.data()?.orders.daily ?? []).map((p) => ({ period: p.date, revenue: p.gmv, orders: p.orders })),
  );
}
