import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { DashboardGranularity, DashboardPoint } from '../../../core/api/api.models';
import { LanguageService } from '../../../core/i18n/language.service';
import { formatVnd } from '../../../shared/pipes/vnd.pipe';

/** Làm tròn trục lên số "đẹp": 1, 2, 2.5, 5 × 10^n. */
export function niceMax(value: number): number {
  if (value <= 0) {
    return 1;
  }
  const exp = Math.pow(10, Math.floor(Math.log10(value)));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * exp >= value) ?? 10;
  return step * exp;
}

/**
 * Biểu đồ cột doanh số theo kỳ (một series nên không cần chú giải; tiêu đề nằm ở trang cha).
 * Mỗi cột có vùng hover/focus cao hết biểu đồ, tooltip hiện doanh số và số đơn; có bảng dữ liệu thay thế.
 */
@Component({
  selector: 'app-revenue-chart',
  imports: [TranslocoDirective],
  template: `
    <div *transloco="let t; prefix: 'seller.dashboard'">
      @if (showTable()) {
        <div class="max-h-72 overflow-auto">
          <table class="w-full text-sm">
            <thead class="sticky top-0 bg-white text-left text-gray-500">
              <tr><th class="py-1 font-normal">{{ t('period') }}</th><th class="py-1 text-right font-normal">{{ t('revenue') }}</th>
                <th class="py-1 text-right font-normal">{{ t('orders') }}</th></tr>
            </thead>
            <tbody>
              @for (p of points(); track p.period) {
                <tr class="border-t border-gray-50"><td class="py-1">{{ label(p.period, true) }}</td>
                  <td class="py-1 text-right tabular-nums">{{ money(p.revenue) }}</td><td class="py-1 text-right tabular-nums">{{ p.orders }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="relative flex h-56 gap-2" (mouseleave)="active.set(null)">
          <!-- Trục y: 0, giữa, đỉnh (chữ màu xám, lưới nhạt) -->
          <div class="flex w-14 shrink-0 flex-col justify-between pb-5 text-right text-[11px] leading-none text-gray-400 tabular-nums" aria-hidden="true">
            <span>{{ compact(max()) }}</span><span>{{ compact(max() / 2) }}</span><span>0</span>
          </div>
          <div class="relative min-w-0 flex-1">
            <div class="absolute inset-x-0 top-0 bottom-5 flex flex-col justify-between" aria-hidden="true">
              <span class="border-t border-dashed border-gray-100"></span>
              <span class="border-t border-dashed border-gray-100"></span>
              <span class="border-t border-gray-200"></span>
            </div>
            <ul class="absolute inset-x-0 top-0 bottom-5 flex items-end gap-[2px]" role="list" [attr.aria-label]="t('chartLabel')">
              @for (p of points(); track p.period; let i = $index) {
                <li class="flex h-full min-w-0 flex-1 cursor-default items-end justify-center outline-none focus-visible:bg-brand-50"
                    tabindex="0" [attr.aria-label]="label(p.period, true) + ': ' + money(p.revenue) + ', ' + t('ordersCount', { count: p.orders })"
                    (mouseenter)="active.set(i)" (focus)="active.set(i)" (blur)="active.set(null)">
                  <span class="block w-full max-w-8 rounded-t-[4px] transition-colors"
                        [class]="active() === i ? 'bg-brand-600' : 'bg-brand-500'"
                        [style.height.%]="p.revenue > 0 ? Math.max(1.5, (p.revenue / max()) * 100) : 0"></span>
                </li>
              }
            </ul>
            <!-- Nhãn trục x: đầu, giữa, cuối để không chồng chữ trên mobile -->
            <div class="absolute inset-x-0 bottom-0 flex justify-between text-[11px] text-gray-400" aria-hidden="true">
              @for (p of xLabels(); track $index) {
                <span>{{ label(p.period, false) }}</span>
              }
            </div>
            @if (activePoint(); as p) {
              <div class="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-sm bg-gray-800 px-2 py-1 text-xs whitespace-nowrap text-white shadow"
                   [style.left.%]="activeLeft()" role="status">
                <p class="font-medium">{{ label(p.period, true) }}</p>
                <p>{{ t('revenue') }}: {{ money(p.revenue) }}</p>
                <p>{{ t('ordersCount', { count: p.orders }) }}</p>
              </div>
            }
          </div>
        </div>
      }
      <div class="mt-2 text-right">
        <button type="button" class="text-xs text-blue-600 hover:underline" (click)="showTable.set(!showTable())">
          {{ t(showTable() ? 'showChart' : 'showTable') }}
        </button>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RevenueChart {
  private readonly language = inject(LanguageService);

  readonly points = input.required<DashboardPoint[]>();
  readonly granularity = input.required<DashboardGranularity>();

  protected readonly Math = Math;
  protected readonly active = signal<number | null>(null);
  protected readonly showTable = signal(false);
  // Sàn 100.000đ để trục y không hiện "1 / 0,5" khi chưa có doanh số
  protected readonly max = computed(() => niceMax(Math.max(100_000, ...this.points().map((p) => p.revenue))));
  protected readonly activePoint = computed(() => {
    const i = this.active();
    return i === null ? null : (this.points()[i] ?? null);
  });
  /** Tooltip canh giữa cột, kẹp trong khung để không tràn ra ngoài. */
  protected readonly activeLeft = computed(() => {
    const i = this.active() ?? 0;
    const n = this.points().length || 1;
    return Math.min(85, Math.max(15, ((i + 0.5) / n) * 100));
  });
  protected readonly xLabels = computed(() => {
    const p = this.points();
    return p.length <= 2 ? p : [p[0], p[Math.floor((p.length - 1) / 2)], p[p.length - 1]];
  });

  protected money(value: number): string {
    return formatVnd(value, this.language.intlLocale());
  }

  /** 1.500.000 → "1,5 Tr" (vi) / "1.5M" (en). */
  protected compact(value: number): string {
    return new Intl.NumberFormat(this.language.intlLocale(), { notation: 'compact', maximumFractionDigits: 1 }).format(value);
  }

  protected label(period: string, long: boolean): string {
    const [y, m, d] = period.split('-').map(Number);
    const date = new Date(y, m - 1, d);
    const options: Intl.DateTimeFormatOptions =
      this.granularity() === 'MONTH'
        ? { month: long ? 'long' : 'numeric', year: 'numeric' }
        : long
          ? { day: 'numeric', month: 'numeric', year: 'numeric' }
          : { day: 'numeric', month: 'numeric' };
    return new Intl.DateTimeFormat(this.language.intlLocale(), options).format(date);
  }
}
