import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { ApiError, errorKey } from '../../core/api/api-error';
import { OrderDetail as OrderDetailModel } from '../../core/api/api.models';
import { OrderApi } from '../../core/api/order.api';
import { DivisionsService, formatAddressLine } from '../../shared/divisions.service';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { OrderItems } from './order-items';

@Component({
  selector: 'app-order-detail',
  imports: [RouterLink, TranslocoDirective, ButtonDirective, VndPipe, DateTimePipe, EmptyState, OrderItems],
  template: `
    <ng-container *transloco="let t">
      <a routerLink="/account/orders" class="mb-4 inline-flex items-center gap-1 text-sm text-gray-600 hover:text-brand-500">
        <i class="pi pi-angle-left" aria-hidden="true"></i>{{ t('orders.back') }}
      </a>

      @if (order(); as o) {
        <header class="mb-4 flex flex-col gap-1 border-b border-gray-100 pb-4 text-sm sm:flex-row sm:items-center sm:justify-between">
          <span class="text-gray-600">{{ t('orders.code') }} <span class="font-mono text-xs text-gray-800">{{ o.code }}</span></span>
          <span class="font-medium uppercase text-brand-500">{{ t('orderStatus.' + o.status) }}</span>
        </header>

        <div class="grid gap-6 md:grid-cols-2">
          <section [attr.aria-label]="t('orders.shippingAddress')">
            <h2 class="mb-2 font-medium">{{ t('orders.shippingAddress') }}</h2>
            <p class="text-sm"><b>{{ o.shippingAddress.receiverName }}</b></p>
            <p class="text-sm text-gray-600">{{ o.shippingAddress.phone }}</p>
            <p class="text-sm text-gray-600">{{ addressLine() }}</p>
            @if (o.note) {
              <p class="mt-2 text-sm text-gray-600">{{ t('checkout.note') }} {{ o.note }}</p>
            }
          </section>
          <section [attr.aria-label]="t('orders.timeline')">
            <h2 class="mb-2 font-medium">{{ t('orders.timeline') }}</h2>
            <ol class="relative border-l border-gray-200 pl-4 text-sm">
              @for (h of history(); track $index; let first = $first) {
                <li class="mb-3 last:mb-0">
                  <span class="absolute -left-1.5 mt-1.5 size-3 rounded-full" [class]="first ? 'bg-brand-500' : 'bg-gray-300'"></span>
                  <p [class.text-brand-500]="first" [class.font-medium]="first">{{ t('orderStatus.' + h.toStatus) }}</p>
                  <p class="text-xs text-gray-500">{{ h.createdAt | dateTime }}</p>
                  @if (h.note) {
                    <p class="text-xs text-gray-600">{{ h.note }}</p>
                  }
                </li>
              }
            </ol>
          </section>
        </div>

        <section class="mt-6 rounded-sm border border-gray-100">
          <h2 class="flex items-center gap-2 border-b border-gray-100 px-4 py-3 text-sm font-medium">
            <i class="pi pi-shop text-gray-500" aria-hidden="true"></i>{{ o.shop?.name }}
          </h2>
          <div class="px-4"><app-order-items [items]="o.items" /></div>
          <dl class="grid grid-cols-[1fr_auto] gap-x-6 gap-y-2 border-t border-gray-100 px-4 py-3 text-sm sm:ml-auto sm:max-w-sm">
            <dt class="text-gray-600">{{ t('checkout.subtotal') }}</dt>
            <dd class="text-right">{{ o.subtotal | vnd }}</dd>
            <dt class="text-gray-600">{{ t('checkout.shippingTotal') }}</dt>
            <dd class="text-right">{{ o.shippingFee | vnd }}</dd>
            <dt class="text-gray-600">{{ t('orders.total') }}</dt>
            <dd class="text-right text-xl font-medium text-brand-500">{{ o.total | vnd }}</dd>
            <dt class="text-gray-600">{{ t('checkout.payment') }}</dt>
            <dd class="text-right">{{ t('checkout.methods.' + o.paymentMethod) }}</dd>
          </dl>
        </section>

        @if (o.cancellable) {
          <div class="mt-4 flex justify-end">
            <button pButton type="button" [outlined]="true" severity="secondary" [disabled]="cancelling()" (click)="cancel(o)">
              {{ t('orders.cancel') }}
            </button>
          </div>
        }
      } @else if (notFound()) {
        <app-empty-state [title]="t('errors.ORDER_NOT_FOUND')" icon="pi-file" />
      } @else {
        <p class="py-8 text-center text-gray-500">{{ t('common.loading') }}</p>
      }
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class OrderDetail {
  private readonly api = inject(OrderApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });

  /** Route param :code */
  readonly code = input.required<string>();

  protected readonly order = signal<OrderDetailModel | null>(null);
  protected readonly notFound = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly addressLine = computed(() => {
    const o = this.order();
    return o ? formatAddressLine(this.provinces(), o.shippingAddress) : '';
  });
  /** Mới nhất lên đầu. */
  protected readonly history = computed(() => [...(this.order()?.history ?? [])].reverse());

  constructor() {
    effect(() => {
      const code = this.code();
      this.api.order(code).subscribe({
        next: (o) => this.order.set(o),
        error: (err) => {
          if (ApiError.from(err).status === 404) {
            this.notFound.set(true);
          } else {
            this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
          }
        },
      });
    });
  }

  protected cancel(o: OrderDetailModel): void {
    if (!confirm(this.transloco.translate('orders.confirmCancel'))) {
      return;
    }
    this.cancelling.set(true);
    this.api.cancel(o.code).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.cancelling.set(false);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('orders.cancelled') });
      },
      error: (err) => {
        this.cancelling.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
