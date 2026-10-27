import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { errorKey } from '../../core/api/api-error';
import { OrderDetail, OrderItem, Review } from '../../core/api/api.models';
import { ReviewApi } from '../../core/api/review.api';
import { RatingStars } from '../../shared/ui/rating-stars';

const MAX_CONTENT = 1000;

/** Đánh giá các món của đơn đã hoàn thành: mỗi món 1 lần (1-5 sao + nội dung). */
@Component({
  selector: 'app-order-reviews',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, RatingStars],
  template: `
    <section *transloco="let t; prefix: 'reviews'" class="mt-6 rounded-sm border border-gray-100" aria-labelledby="order-reviews">
      <h2 id="order-reviews" class="border-b border-gray-100 px-4 py-3 text-sm font-medium">{{ t('rateOrder') }}</h2>
      <ul class="divide-y divide-gray-50 px-4">
        @for (item of order().items; track item.id) {
          <li class="py-3 text-sm" [attr.data-review-item]="item.id">
            <div class="flex items-center gap-3">
              <span class="min-w-0 flex-1 truncate">{{ item.productName }}@if (item.variantName) { <span class="text-gray-500">({{ item.variantName }})</span> }</span>
              @if (!reviewOf(item) && editing() !== item.id) {
                <button pButton type="button" size="small" [outlined]="true" (click)="startReview(item)">{{ t('write') }}</button>
              }
            </div>
            @if (reviewOf(item); as r) {
              <div class="mt-2 rounded-sm bg-gray-50 p-3">
                <app-rating-stars [rating]="r.rating" />
                @if (r.content) {
                  <p class="mt-1 whitespace-pre-line text-gray-700">{{ r.content }}</p>
                }
                @if (r.sellerReply) {
                  <p class="mt-2 text-xs text-gray-500">{{ t('sellerReply') }}: {{ r.sellerReply }}</p>
                }
              </div>
            } @else if (editing() === item.id) {
              <form class="mt-2 flex flex-col gap-2 rounded-sm bg-gray-50 p-3" (ngSubmit)="submit(item)">
                <div class="flex items-center gap-1" role="radiogroup" [attr.aria-label]="t('rating')">
                  @for (star of [1, 2, 3, 4, 5]; track star) {
                    <button type="button" role="radio" class="p-0.5 text-2xl text-amber-400" [attr.aria-checked]="rating() === star"
                            [attr.aria-label]="t('stars', { count: star })" (click)="rating.set(star)">
                      <i class="pi" [class]="star <= rating() ? 'pi-star-fill' : 'pi-star'" aria-hidden="true"></i>
                    </button>
                  }
                  <span class="ml-2 text-amber-600">{{ t('ratingText.' + rating()) }}</span>
                </div>
                <textarea name="content" rows="3" [maxlength]="maxContent" class="w-full rounded-sm border border-gray-300 p-2"
                          [placeholder]="t('contentPlaceholder')" [attr.aria-label]="t('content')"
                          [ngModel]="content()" (ngModelChange)="content.set($event)"></textarea>
                <div class="flex justify-end gap-2">
                  <button pButton type="button" size="small" [text]="true" severity="secondary" (click)="editing.set(null)">{{ t('cancel') }}</button>
                  <button pButton type="submit" size="small" [disabled]="saving()">{{ t('submit') }}</button>
                </div>
              </form>
            }
          </li>
        }
      </ul>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderReviews {
  private readonly api = inject(ReviewApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  readonly order = input.required<OrderDetail>();

  protected readonly maxContent = MAX_CONTENT;
  private readonly reviews = signal<Review[]>([]);
  private readonly byItem = computed(() => new Map(this.reviews().map((r) => [r.orderItemId, r])));
  protected readonly editing = signal<number | null>(null);
  protected readonly rating = signal(5);
  protected readonly content = signal('');
  protected readonly saving = signal(false);

  constructor() {
    effect(() => {
      this.api.orderReviews(this.order().code).subscribe({ next: (list) => this.reviews.set(list), error: () => this.reviews.set([]) });
    });
  }

  protected reviewOf(item: OrderItem): Review | undefined {
    return this.byItem().get(item.id);
  }

  protected startReview(item: OrderItem): void {
    this.rating.set(5);
    this.content.set('');
    this.editing.set(item.id);
  }

  protected submit(item: OrderItem): void {
    this.saving.set(true);
    const content = this.content().trim();
    this.api.create(this.order().code, item.id, { rating: this.rating(), content: content || null, media: [] }).subscribe({
      next: (review) => {
        this.saving.set(false);
        this.editing.set(null);
        this.reviews.update((list) => [...list, review]);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('reviews.thanks') });
      },
      error: (err) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
