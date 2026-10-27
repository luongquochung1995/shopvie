import { isPlatformBrowser } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, PLATFORM_ID, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, filter, interval, of, switchMap, take, timeout } from 'rxjs';
import { ApiError, errorKey } from '../../core/api/api-error';
import { FlashSale, FlashSaleItem, FlashSaleReservation } from '../../core/api/api.models';
import { PromotionApi } from '../../core/api/promotion.api';
import { AuthStore } from '../../core/auth/auth.store';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { Countdown } from '../../shared/ui/countdown';

/** Hỏi trạng thái lượt giữ suất mỗi giây, tối đa 30 giây. */
const POLL_MS = 1000;
const POLL_TIMEOUT_MS = 30_000;

/**
 * Khu Flash Sale trang chủ: phiên đang chạy (hoặc sắp mở) với đồng hồ đếm ngược. "Mua ngay" giữ suất trên server
 * (202) rồi hỏi trạng thái tới khi đơn COD được tạo.
 */
@Component({
  selector: 'app-flash-sale-section',
  imports: [RouterLink, TranslocoDirective, VndPipe, Countdown],
  template: `
    @if (sale(); as s) {
      <section *transloco="let t; prefix: 'flashSale'" class="rounded-sm bg-white shadow-sm" aria-labelledby="flash-sale-title">
        <div class="flex flex-wrap items-center gap-3 border-b border-gray-100 px-4 py-3">
          <h2 id="flash-sale-title" class="flex items-center gap-1 text-xl font-bold italic text-brand-500">
            <i class="pi pi-bolt" aria-hidden="true"></i>FLASH SALE
          </h2>
          <span class="text-sm text-gray-600">{{ t(s.running ? 'endsIn' : 'startsIn') }}</span>
          <span class="rounded-sm bg-gray-900 px-2 py-0.5 text-sm text-white" data-testid="flash-sale-countdown">
            <app-countdown [until]="s.running ? s.endAt : s.startAt" />
          </span>
          <span class="ml-auto truncate text-sm text-gray-500">{{ s.name }}</span>
        </div>
        <ul class="grid grid-flow-col auto-cols-[10rem] gap-2 overflow-x-auto p-3 sm:auto-cols-[11rem]">
          @for (item of s.items; track item.id) {
            <li class="flex flex-col rounded-sm border border-gray-100 p-2 text-center" [attr.data-item]="item.id">
              <a [routerLink]="item.productSlug ? ['/p', item.productSlug] : null" class="relative block aspect-square overflow-hidden bg-gray-50">
                @if (item.imageUrl) {
                  <img [src]="item.imageUrl" [alt]="item.productName ?? ''" loading="lazy" class="size-full object-cover" />
                }
                <span class="absolute right-0 top-0 bg-yellow-300 px-1 text-xs font-medium text-brand-600">-{{ discountPercent(item) }}%</span>
              </a>
              <p class="mt-1 line-clamp-1 text-xs text-gray-700" [title]="item.productName ?? ''">{{ item.productName }}</p>
              <p class="text-lg font-medium text-brand-500">{{ item.salePrice | vnd }}</p>
              <p class="text-xs text-gray-400 line-through">{{ item.originalPrice | vnd }}</p>
              <div class="relative mt-1 h-4 overflow-hidden rounded-full bg-brand-100"
                   role="progressbar" [attr.aria-valuenow]="item.quota - item.remaining" aria-valuemin="0" [attr.aria-valuemax]="item.quota">
                <div class="h-full bg-gradient-to-r from-brand-400 to-brand-600" [style.width.%]="soldPercent(item)"></div>
                <span class="absolute inset-0 text-[10px] font-medium uppercase leading-4 text-white drop-shadow">
                  {{ item.remaining === 0 ? t('soldOut') : item.remaining <= item.quota * 0.2 ? t('almostGone') : t('sold', { count: item.quota - item.remaining }) }}
                </span>
              </div>
              <button type="button" class="mt-2 rounded-sm px-2 py-1.5 text-sm text-white disabled:cursor-not-allowed disabled:bg-gray-300"
                      [class]="s.running ? 'bg-brand-500 hover:bg-brand-600' : 'bg-gray-400'"
                      [disabled]="!s.running || item.remaining === 0 || busyItem() !== null"
                      (click)="buy(item)">
                {{ busyItem() === item.id ? t('processing') : !s.running ? t('upcoming') : item.remaining === 0 ? t('soldOut') : t('buyNow') }}
              </button>
            </li>
          }
        </ul>
      </section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlashSaleSection {
  private readonly api = inject(PromotionApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly sales = signal<FlashSale[]>([]);
  /** Phiên hiển thị: đang chạy, không có thì phiên sắp mở sớm nhất. */
  protected readonly sale = computed(() => {
    const list = this.sales().filter((s) => s.items.length > 0);
    return list.find((s) => s.running) ?? list[0] ?? null;
  });
  protected readonly busyItem = signal<number | null>(null);
  private reloadTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.load();
    this.destroyRef.onDestroy(() => clearTimeout(this.reloadTimer));
  }

  protected discountPercent(item: FlashSaleItem): number {
    return item.originalPrice > 0 ? Math.round((1 - item.salePrice / item.originalPrice) * 100) : 0;
  }

  protected soldPercent(item: FlashSaleItem): number {
    return item.quota > 0 ? Math.min(100, ((item.quota - item.remaining) / item.quota) * 100) : 0;
  }

  protected buy(item: FlashSaleItem): void {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/auth/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.busyItem.set(item.id);
    this.api.purchaseFlashSale(item.id).subscribe({
      next: (r) => this.awaitResult(item, r),
      error: (err) => {
        this.busyItem.set(null);
        const code = ApiError.from(err).code;
        if (code === 'ADDRESS_NOT_FOUND') {
          this.toast('warn', 'flashSale.needAddress');
          void this.router.navigate(['/account/addresses']);
          return;
        }
        this.toast('error', errorKey(err));
        if (code === 'FLASH_SALE_SOLD_OUT' || code === 'FLASH_SALE_NOT_ACTIVE') {
          this.load();
        }
      },
    });
  }

  /** Hỏi trạng thái tới khi có đơn (CONFIRMED) hoặc thất bại (suất đã được trả lại). */
  private awaitResult(item: FlashSaleItem, reservation: FlashSaleReservation): void {
    this.toast('info', 'flashSale.reserved');
    interval(POLL_MS)
      .pipe(
        switchMap(() => this.api.reservation(reservation.reservationId).pipe(catchError(() => of(null)))),
        filter((r): r is FlashSaleReservation => r !== null),
        filter((r) => r.status !== 'PENDING'),
        take(1),
        timeout(POLL_TIMEOUT_MS),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (r) => {
          this.busyItem.set(null);
          this.load();
          if (r.status === 'CONFIRMED' && r.orderCode) {
            this.toast('success', 'flashSale.success');
            void this.router.navigate(['/account/orders', r.orderCode]);
          } else {
            this.toast('error', r.reason ? `flashSale.failed.${r.reason}` : 'flashSale.failed.default');
          }
        },
        error: () => {
          this.busyItem.set(null);
          this.toast('warn', 'flashSale.slow');
        },
      });
    // Cập nhật ngay số suất còn lại
    this.sales.update((list) =>
      list.map((s) => ({ ...s, items: s.items.map((i) => (i.id === item.id ? { ...i, remaining: Math.max(0, i.remaining - 1) } : i)) })),
    );
  }

  private load(): void {
    this.api
      .currentFlashSales()
      .pipe(catchError(() => of<FlashSale[]>([])))
      .subscribe((list) => {
        this.sales.set(list);
        this.scheduleReload();
      });
  }

  /** Tải lại đúng lúc phiên bắt đầu/kết thúc để nút "Mua ngay" và đồng hồ đổi trạng thái. */
  private scheduleReload(): void {
    clearTimeout(this.reloadTimer);
    const s = this.sale();
    if (!this.isBrowser || !s) {
      return;
    }
    const wait = Date.parse(s.running ? s.endAt : s.startAt) - Date.now();
    if (wait > 0 && wait < 24 * 3600_000) {
      this.reloadTimer = setTimeout(() => this.load(), wait + 500);
    }
  }

  private toast(severity: string, key: string): void {
    this.messages.add({ severity, summary: this.transloco.translate(key) });
  }
}
