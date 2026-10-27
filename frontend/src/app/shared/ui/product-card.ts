import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProductCard as ProductCardModel } from '../../core/api/api.models';
import { CompactCountPipe, VndPipe } from '../pipes/vnd.pipe';
import { RatingStars } from './rating-stars';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink, TranslocoPipe, VndPipe, CompactCountPipe, RatingStars],
  template: `
    @let p = product();
    <a
      [routerLink]="['/p', p.slug]"
      class="group flex h-full flex-col overflow-hidden rounded-sm bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md hover:outline hover:outline-1 hover:outline-brand-500"
    >
      <div class="relative aspect-square bg-gray-100">
        @if (p.thumbnailUrl) {
          <img [src]="p.thumbnailUrl" [alt]="p.name" loading="lazy" decoding="async" class="absolute inset-0 size-full object-cover" />
        }
      </div>
      <div class="flex flex-1 flex-col gap-1 p-2">
        <h3 class="line-clamp-2 min-h-[2.5rem] text-sm leading-5 text-gray-800">{{ p.name }}</h3>
        <div class="mt-auto flex items-end justify-between gap-1">
          <span class="text-base font-medium text-brand-500">{{ p.minPrice | vnd }}</span>
          <span class="shrink-0 text-xs text-gray-500">{{ 'product.sold' | transloco: { count: (p.soldCount | compactCount) } }}</span>
        </div>
        @if (p.ratingCount > 0) {
          <app-rating-stars [rating]="p.ratingAvg" class="text-sm" />
        }
      </div>
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCard {
  readonly product = input.required<ProductCardModel>();
}
