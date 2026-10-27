import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { errorKey } from '../../core/api/api-error';
import { OrderApi } from '../../core/api/order.api';
import { PaymentRedirect } from '../../shared/payment-redirect';
import { VndPipe } from '../../shared/pipes/vnd.pipe';

/**
 * Cổng thanh toán giả lập (chỉ khi backend bật app.payment.sandbox-gateway hoặc chạy mock): người dùng chọn kết quả,
 * backend ký callback như VNPay/MoMo thật rồi trình duyệt đi qua return URL như với cổng thật.
 * Query: ?ref=&method=VNPAY|MOMO&amount=
 */
@Component({
  selector: 'app-payment-sandbox',
  imports: [TranslocoDirective, TranslocoPipe, ButtonDirective, VndPipe],
  template: `
    <section *transloco="let t; prefix: 'payment.sandbox'" class="mx-auto my-6 max-w-md rounded-sm bg-white shadow-sm">
      <header class="rounded-t-sm px-6 py-4 text-white" [class]="method() === 'MOMO' ? 'bg-pink-700' : 'bg-blue-700'">
        <p class="text-lg font-semibold">{{ t('gateway', { name: method() === 'MOMO' ? 'MoMo' : 'VNPay' }) }}</p>
        <p class="text-xs opacity-90">{{ t('notice') }}</p>
      </header>
      <div class="flex flex-col gap-4 p-6">
        <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-gray-500">{{ t('merchant') }}</dt>
          <dd class="text-right font-medium">{{ 'common.appName' | transloco }}</dd>
          <dt class="text-gray-500">{{ t('reference') }}</dt>
          <dd class="truncate text-right font-mono text-xs">{{ ref() }}</dd>
          <dt class="text-gray-500">{{ t('amount') }}</dt>
          <dd class="text-right text-xl font-medium text-brand-500">{{ amountValue() | vnd }}</dd>
        </dl>
        <button pButton type="button" class="justify-center" [disabled]="busy()" (click)="complete(true)">{{ t('pay') }}</button>
        <button pButton type="button" class="justify-center" [outlined]="true" severity="secondary" [disabled]="busy()"
                (click)="complete(false)">{{ t('cancel') }}</button>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class PaymentSandbox {
  private readonly api = inject(OrderApi);
  private readonly redirect = inject(PaymentRedirect);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  readonly ref = input<string>('');
  readonly method = input<string>('VNPAY');
  readonly amount = input<string>('0');

  protected readonly amountValue = computed(() => Number(this.amount()) || 0);
  protected readonly busy = signal(false);

  protected complete(success: boolean): void {
    this.busy.set(true);
    this.api.completeSandboxPayment(this.ref(), success).subscribe({
      next: (url) => this.redirect.go(url),
      error: (err) => {
        this.busy.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
