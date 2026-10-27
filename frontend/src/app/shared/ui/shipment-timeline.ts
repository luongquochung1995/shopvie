import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { Shipment } from '../../core/api/api.models';
import { DateTimePipe } from '../pipes/date-time.pipe';

/** Vận đơn: hãng, mã vận đơn, dự kiến giao, hành trình (mới nhất trước). */
@Component({
  selector: 'app-shipment-timeline',
  imports: [TranslocoDirective, DateTimePipe],
  template: `
    <section *transloco="let t; prefix: 'shipment'" [attr.aria-label]="t('title')">
      <h2 class="mb-2 font-medium">{{ t('title') }}</h2>
      <dl class="mb-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <dt class="text-gray-500">{{ t('carrier') }}</dt>
        <dd>{{ t('provider.' + shipment().provider) }}</dd>
        <dt class="text-gray-500">{{ t('trackingCode') }}</dt>
        <dd class="font-mono text-xs leading-5">{{ shipment().trackingCode }}</dd>
        @if (shipment().expectedDeliveryAt; as eta) {
          <dt class="text-gray-500">{{ t('eta') }}</dt>
          <dd>{{ eta | dateTime }}</dd>
        }
      </dl>
      <ol class="relative border-l border-gray-200 pl-4 text-sm">
        @for (e of shipment().events; track $index; let first = $first) {
          <li class="mb-3 last:mb-0">
            <span class="absolute -left-1.5 mt-1.5 size-3 rounded-full" [class]="first ? 'bg-green-600' : 'bg-gray-300'"></span>
            <p [class.text-green-700]="first" [class.font-medium]="first">{{ t('status.' + e.status) }}</p>
            @if (e.description) {
              <p class="text-xs text-gray-600">{{ e.description }}</p>
            }
            <p class="text-xs text-gray-500">{{ e.occurredAt | dateTime }}</p>
          </li>
        }
      </ol>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShipmentTimeline {
  readonly shipment = input.required<Shipment>();
}
