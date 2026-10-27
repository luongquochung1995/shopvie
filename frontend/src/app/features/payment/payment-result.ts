import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { errorKey } from '../../core/api/api-error';
import { CheckoutPayment } from '../../core/api/api.models';
import { OrderApi } from '../../core/api/order.api';
import { CartStore } from '../../core/cart/cart.store';
import { PaymentRedirect } from '../../shared/payment-redirect';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { Countdown } from '../../shared/ui/countdown';

const POLL_MS = 1500;
const MAX_POLLS = 8;

/**
 * Kết quả thanh toán sau khi rời cổng: ?status=success|failed|invalid&checkoutId=...
 * Trạng thái thật lấy từ backend (IPN có thể đến trễ hơn trình duyệt) — hỏi lại vài lần khi còn PENDING.
 */
@Component({
  selector: 'app-payment-result',
  imports: [RouterLink, TranslocoDirective, ButtonDirective, VndPipe, Countdown],
  template: `
    <section *transloco="let t; prefix: 'payment.result'"
             class="mx-auto my-6 flex max-w-xl flex-col items-center gap-4 rounded-sm bg-white p-8 text-center shadow-sm"
             aria-live="polite">
      @if (!checkoutId()) {
        <i class="pi pi-exclamation-triangle text-6xl text-amber-500" aria-hidden="true"></i>
        <h1 class="text-xl font-medium">{{ t('invalidTitle') }}</h1>
        <p class="text-gray-600">{{ t('invalidHint') }}</p>
        <a pButton routerLink="/account/orders">{{ t('viewOrders') }}</a>
      } @else if (payment(); as p) {
        @switch (p.status) {
          @case ('PAID') {
            <i class="pi pi-check-circle text-6xl text-green-600" aria-hidden="true"></i>
            <h1 class="text-xl font-medium">{{ t('paidTitle') }}</h1>
            <p class="text-gray-600">{{ t('paidHint', { count: p.orderCodes.length }) }}</p>
            <p>{{ t('amount') }} <b class="text-lg text-brand-500">{{ p.total | vnd }}</b></p>
          }
          @case ('CANCELLED') {
            <i class="pi pi-times-circle text-6xl text-red-600" aria-hidden="true"></i>
            <h1 class="text-xl font-medium">{{ t('cancelledTitle') }}</h1>
            <p class="text-gray-600">{{ t('cancelledHint') }}</p>
          }
          @case ('NOT_REQUIRED') {
            <i class="pi pi-check-circle text-6xl text-green-600" aria-hidden="true"></i>
            <h1 class="text-xl font-medium">{{ t('codTitle') }}</h1>
          }
          @default {
            @if (p.status === 'PENDING' && polling()) {
              <i class="pi pi-spin pi-spinner text-5xl text-brand-500" aria-hidden="true"></i>
              <h1 class="text-xl font-medium">{{ t('checking') }}</h1>
            } @else {
              <i class="pi pi-exclamation-circle text-6xl text-amber-500" aria-hidden="true"></i>
              <h1 class="text-xl font-medium">{{ t(p.status === 'FAILED' ? 'failedTitle' : 'pendingTitle') }}</h1>
              <p class="text-gray-600">{{ t('retryHint') }}</p>
              @if (p.expiresAt) {
                <p class="text-sm">{{ t('deadline') }} <app-countdown [until]="p.expiresAt" class="text-brand-500" /></p>
              }
              <button pButton type="button" [disabled]="retrying()" (click)="retry(p)">{{ t('retry', { amount: (p.total | vnd) }) }}</button>
            }
          }
        }
        <div class="flex flex-wrap justify-center gap-3">
          <a pButton routerLink="/account/orders" [outlined]="true">{{ t('viewOrders') }}</a>
          <a pButton routerLink="/" [text]="true">{{ t('continue') }}</a>
        </div>
      } @else {
        <i class="pi pi-spin pi-spinner text-5xl text-brand-500" aria-hidden="true"></i>
        <h1 class="text-xl font-medium">{{ t('checking') }}</h1>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class PaymentResult {
  private readonly api = inject(OrderApi);
  private readonly redirect = inject(PaymentRedirect);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly cartStore = inject(CartStore);

  readonly checkoutId = input<string>();
  /** Gợi ý từ return URL (success|failed|invalid); trạng thái chính thức lấy từ backend. */
  readonly status = input<string>();

  protected readonly payment = signal<CheckoutPayment | null>(null);
  protected readonly polling = signal(true);
  protected readonly retrying = signal(false);
  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
    effect(() => {
      const id = this.checkoutId();
      if (id) {
        untracked(() => {
          this.cartStore.refresh();
          this.poll(id, 0);
        });
      }
    });
  }

  protected retry(p: CheckoutPayment): void {
    this.retrying.set(true);
    this.api.retryPayment(p.checkoutId).subscribe({
      next: (link) => this.redirect.go(link.paymentUrl),
      error: (err) => {
        this.retrying.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        this.poll(p.checkoutId, MAX_POLLS);
      },
    });
  }

  private poll(checkoutId: string, attempt: number): void {
    this.api.paymentStatus(checkoutId).subscribe({
      next: (p) => {
        this.payment.set(p);
        // Trả về từ cổng với kết quả thành công nhưng IPN chưa tới → hỏi lại
        if (p.status === 'PENDING' && attempt < MAX_POLLS && this.status() !== 'failed') {
          this.timer = setTimeout(() => this.poll(checkoutId, attempt + 1), POLL_MS);
        } else {
          this.polling.set(false);
        }
      },
      error: (err) => {
        this.polling.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
