import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { OrderItem } from '../../core/api/api.models';
import { VndPipe } from '../../shared/pipes/vnd.pipe';

/** Danh sách sản phẩm của một đơn (dùng ở trang Đơn mua và chi tiết đơn). */
@Component({
  selector: 'app-order-items',
  imports: [RouterLink, TranslocoDirective, VndPipe],
  template: `
    <ul *transloco="let t" class="divide-y divide-gray-50">
      @for (item of items(); track $index) {
        <li class="flex gap-3 py-3 text-sm">
          <a [routerLink]="['/p', item.productSlug]" class="size-16 shrink-0 overflow-hidden border border-gray-100 bg-gray-50">
            @if (item.imageUrl) {
              <img [src]="item.imageUrl" alt="" loading="lazy" class="size-full object-cover" />
            }
          </a>
          <div class="min-w-0 flex-1">
            <a [routerLink]="['/p', item.productSlug]" class="line-clamp-2 hover:text-brand-500">{{ item.productName }}</a>
            @if (item.variantName) {
              <p class="text-gray-500">{{ t('cart.variant', { name: item.variantName }) }}</p>
            }
            <p class="text-gray-500">×{{ item.quantity }}</p>
          </div>
          <span class="shrink-0 text-brand-500">{{ item.unitPrice | vnd }}</span>
        </li>
      }
    </ul>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderItems {
  readonly items = input.required<OrderItem[]>();
}
