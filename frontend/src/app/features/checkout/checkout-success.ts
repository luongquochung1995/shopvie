import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { VndPipe } from '../../shared/pipes/vnd.pipe';

/** Trang đặt hàng thành công: ?orders=code1,code2&total=... */
@Component({
  selector: 'app-checkout-success',
  imports: [RouterLink, TranslocoDirective, ButtonDirective, VndPipe],
  template: `
    <section *transloco="let t; prefix: 'checkout.success'"
             class="mx-auto my-6 flex max-w-xl flex-col items-center gap-4 rounded-sm bg-white p-8 text-center shadow-sm">
      <i class="pi pi-check-circle text-6xl text-green-600" aria-hidden="true"></i>
      <h1 class="text-xl font-medium">{{ t('title') }}</h1>
      <p class="text-gray-600">{{ t('description', { count: codes().length }) }}</p>
      @if (total()) {
        <p>{{ t('amount') }} <b class="text-lg text-brand-500">{{ total() | vnd }}</b></p>
      }
      <ul class="w-full space-y-1 text-sm">
        @for (code of codes(); track code) {
          <li>
            <a [routerLink]="['/account/orders', code]" class="font-mono text-xs text-blue-600 hover:underline">{{ code }}</a>
          </li>
        }
      </ul>
      <div class="flex flex-wrap justify-center gap-3">
        <a pButton routerLink="/account/orders" [outlined]="true">{{ t('viewOrders') }}</a>
        <a pButton routerLink="/">{{ t('continue') }}</a>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class CheckoutSuccess {
  readonly orders = input<string>();
  readonly totalParam = input<string>(undefined, { alias: 'total' });

  protected readonly codes = computed(() => (this.orders() ?? '').split(',').filter(Boolean));
  protected readonly total = computed(() => Number(this.totalParam()) || 0);
}
