import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';
import { ProductCard as ProductCardModel } from '../../core/api/api.models';
import { ProductCard } from './product-card';

/** Lưới sản phẩm responsive (2 cột mobile → 6 cột desktop) kèm skeleton khi đang tải. */
@Component({
  selector: 'app-product-grid',
  imports: [ProductCard, Skeleton],
  template: `
    <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
      @if (loading()) {
        @for (i of placeholders; track i) {
          <div class="rounded-sm bg-white p-2 shadow-sm">
            <p-skeleton styleClass="!aspect-square !h-auto" />
            <p-skeleton styleClass="mt-2" height="2.5rem" />
            <p-skeleton styleClass="mt-2" width="50%" />
          </div>
        }
      } @else {
        @for (p of products(); track p.id) {
          <app-product-card [product]="p" />
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductGrid {
  readonly products = input.required<ProductCardModel[]>();
  readonly loading = input(false);

  protected readonly placeholders = Array.from({ length: 12 }, (_, i) => i);
}
