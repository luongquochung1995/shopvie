import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { ApiError, errorKey } from '../../../core/api/api-error';
import { SellerOrderDetail as Detail } from '../../../core/api/api.models';
import { SellerApi } from '../../../core/api/seller.api';
import { DivisionsService, formatAddressLine } from '../../../shared/divisions.service';
import { DateTimePipe } from '../../../shared/pipes/date-time.pipe';
import { VndPipe } from '../../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../../shared/ui/empty-state';
import { ShipmentTimeline } from '../../../shared/ui/shipment-timeline';
import { OrderItems } from '../../account/order-items';
import { SellerOrderActions } from './order-actions';

/** Chi tiết đơn của shop: người nhận, lời nhắn, lịch sử trạng thái, thanh toán và thao tác xử lý. */
@Component({
  selector: 'app-seller-order-detail',
  imports: [RouterLink, TranslocoDirective, TranslocoPipe, VndPipe, DateTimePipe, EmptyState, OrderItems, SellerOrderActions, ShipmentTimeline],
  template: `
    <div *transloco="let t; prefix: 'seller.orders'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <a routerLink="/seller-center/orders" class="mb-4 inline-flex items-center gap-1 text-sm text-gray-600 hover:text-brand-500">
        <i class="pi pi-angle-left" aria-hidden="true"></i>{{ t('back') }}
      </a>

      @if (order(); as o) {
        <header class="mb-4 flex flex-col gap-2 border-b border-gray-100 pb-4 text-sm sm:flex-row sm:items-center sm:justify-between">
          <span class="text-gray-600">{{ 'orders.code' | transloco }} <span class="font-mono text-xs text-gray-800">{{ o.code }}</span></span>
          <span class="font-medium uppercase text-brand-500">{{ (o.awaitingPayment ? 'orders.awaitingPayment' : 'orderStatus.' + o.status) | transloco }}</span>
        </header>

        <div class="grid gap-6 md:grid-cols-2">
          <section [attr.aria-label]="'orders.shippingAddress' | transloco">
            <h2 class="mb-2 font-medium">{{ 'orders.shippingAddress' | transloco }}</h2>
            <p class="text-sm"><b>{{ o.shippingAddress.receiverName }}</b></p>
            <p class="text-sm text-gray-600">{{ o.shippingAddress.phone }}</p>
            <p class="text-sm text-gray-600">{{ addressLine() }}</p>
            @if (o.note) {
              <p class="mt-2 text-sm text-gray-600">{{ 'checkout.note' | transloco }} {{ o.note }}</p>
            }
          </section>
          <section [attr.aria-label]="'orders.timeline' | transloco">
            <h2 class="mb-2 font-medium">{{ 'orders.timeline' | transloco }}</h2>
            <ol class="relative border-l border-gray-200 pl-4 text-sm">
              @for (h of history(); track $index; let first = $first) {
                <li class="mb-3 last:mb-0">
                  <span class="absolute -left-1.5 mt-1.5 size-3 rounded-full" [class]="first ? 'bg-brand-500' : 'bg-gray-300'"></span>
                  <p [class.text-brand-500]="first" [class.font-medium]="first">{{ 'orderStatus.' + h.toStatus | transloco }}</p>
                  <p class="text-xs text-gray-500">{{ h.createdAt | dateTime }}</p>
                  @if (h.note) {
                    <p class="text-xs text-gray-600">{{ h.note }}</p>
                  }
                </li>
              }
            </ol>
          </section>
        </div>

        @if (o.awaitingPayment) {
          <p class="mt-4 rounded-sm bg-amber-50 p-3 text-sm text-amber-900" role="status">{{ t('awaitingPaymentHint') }}</p>
        }
        @if (o.shipment; as shipment) {
          <div class="mt-6 rounded-sm border border-gray-100 p-4"><app-shipment-timeline [shipment]="shipment" /></div>
        }

        <section class="mt-6 rounded-sm border border-gray-100">
          <div class="px-4"><app-order-items [items]="o.items" /></div>
          <dl class="grid grid-cols-[1fr_auto] gap-x-6 gap-y-2 border-t border-gray-100 px-4 py-3 text-sm sm:ml-auto sm:max-w-sm">
            <dt class="text-gray-600">{{ 'checkout.subtotal' | transloco }}</dt>
            <dd class="text-right">{{ o.subtotal | vnd }}</dd>
            <dt class="text-gray-600">{{ 'checkout.shippingTotal' | transloco }}</dt>
            <dd class="text-right">{{ o.shippingFee | vnd }}</dd>
            <dt class="text-gray-600">{{ 'orders.total' | transloco }}</dt>
            <dd class="text-right text-xl font-medium text-brand-500">{{ o.total | vnd }}</dd>
            <dt class="text-gray-600">{{ 'checkout.payment' | transloco }}</dt>
            <dd class="text-right">{{ 'checkout.methods.' + o.paymentMethod | transloco }}</dd>
            <dt class="text-gray-600">{{ t('paymentStatus') }}</dt>
            <dd class="text-right">{{ t('payment.' + o.paymentStatus) }}</dd>
          </dl>
        </section>

        @if (o.actions.length) {
          <div class="mt-4">
            <app-seller-order-actions [code]="o.code" [actions]="o.actions" (changed)="order.set($event)" />
          </div>
        }
      } @else if (notFound()) {
        <app-empty-state [title]="'errors.ORDER_NOT_FOUND' | transloco" icon="pi-file" />
      } @else if (errorKey(); as key) {
        <p class="py-8 text-center text-gray-500">{{ key | transloco }}</p>
      } @else {
        <p class="py-8 text-center text-gray-500">{{ 'common.loading' | transloco }}</p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class SellerOrderDetail {
  private readonly api = inject(SellerApi);
  private readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });

  /** Route param :code */
  readonly code = input.required<string>();

  protected readonly order = signal<Detail | null>(null);
  protected readonly notFound = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly addressLine = computed(() => {
    const o = this.order();
    return o ? formatAddressLine(this.provinces(), o.shippingAddress) : '';
  });
  /** Mới nhất lên đầu. */
  protected readonly history = computed(() => [...(this.order()?.history ?? [])].reverse());

  constructor() {
    effect(() => {
      this.api.order(this.code()).subscribe({
        next: (o) => this.order.set(o),
        error: (err) =>
          ApiError.from(err).status === 404 ? this.notFound.set(true) : this.errorKey.set(errorKey(err)),
      });
    });
  }
}
