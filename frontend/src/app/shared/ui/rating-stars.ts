import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** 5 ngôi sao, tô theo điểm (làm tròn 0,5). */
@Component({
  selector: 'app-rating-stars',
  imports: [TranslocoPipe],
  template: `
    <span class="inline-flex items-center gap-px text-amber-400" role="img" [attr.aria-label]="'product.ratingLabel' | transloco: { rating: rating() }">
      @for (star of stars(); track $index) {
        <i class="pi text-[0.7em]" [class]="star" aria-hidden="true"></i>
      }
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RatingStars {
  readonly rating = input.required<number>();

  protected readonly stars = computed(() => {
    const r = Math.round(this.rating() * 2) / 2;
    return [1, 2, 3, 4, 5].map((i) => (r >= i ? 'pi-star-fill' : r >= i - 0.5 ? 'pi-star-half-fill' : 'pi-star'));
  });
}
