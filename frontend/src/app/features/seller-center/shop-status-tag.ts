import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ShopStatus } from '../../core/api/api.models';

/** Nhãn trạng thái shop (có chữ, không chỉ dựa vào màu). */
@Component({
  selector: 'app-shop-status-tag',
  imports: [TranslocoPipe],
  template: `<span class="inline-block rounded-sm px-1.5 text-xs" [class]="tone()">{{ 'shopStatus.' + status() | transloco }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopStatusTag {
  readonly status = input.required<ShopStatus>();
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'ACTIVE':
        return 'bg-green-50 text-green-700';
      case 'PENDING':
        return 'bg-amber-50 text-amber-800';
      default:
        return 'bg-red-50 text-red-700';
    }
  });
}
