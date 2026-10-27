import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { errorKey } from '../../core/api/api-error';
import { OrderDetail, OrderItem, Review } from '../../core/api/api.models';
import { ReviewApi } from '../../core/api/review.api';
import { UserApi } from '../../core/api/user.api';
import { RatingStars } from '../../shared/ui/rating-stars';

const MAX_CONTENT = 1000;
/** Khớp giới hạn của API đánh giá (≤ 5 ảnh) và upload (jpg/png/webp ≤ 5MB). */
const MAX_MEDIA = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/** Đánh giá các món của đơn đã hoàn thành: mỗi món 1 lần (1-5 sao + nội dung + tối đa 5 ảnh). */
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
                @if (r.media.length) {
                  <div class="mt-2 flex flex-wrap gap-2">
                    @for (m of r.media; track m) {
                      <img [src]="m" alt="" loading="lazy" class="size-16 rounded-sm object-cover" />
                    }
                  </div>
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
                <div class="flex flex-wrap items-center gap-2" data-review-media>
                  @for (m of media(); track m) {
                    <div class="relative size-16">
                      <img [src]="m" alt="" class="size-full rounded-sm object-cover" />
                      <button type="button" class="absolute -right-1.5 -top-1.5 flex size-5 items-center justify-center rounded-full bg-gray-700 text-white"
                              [attr.aria-label]="t('removePhoto')" (click)="removeMedia(m)">
                        <i class="pi pi-times text-[10px]" aria-hidden="true"></i>
                      </button>
                    </div>
                  }
                  @if (media().length + uploading() < maxMedia) {
                    <label class="flex size-16 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-sm border border-dashed border-brand-500 text-xs text-brand-500"
                           [class.opacity-50]="uploading() > 0">
                      <i class="pi pi-camera" aria-hidden="true"></i>
                      <span>{{ t('addPhoto') }}</span>
                      <input type="file" accept="image/jpeg,image/png,image/webp" multiple class="sr-only" (change)="onFiles($event)" />
                    </label>
                  }
                  <span class="text-xs text-gray-500">{{ t('photoCount', { count: media().length, max: maxMedia }) }}</span>
                </div>
                <div class="flex justify-end gap-2">
                  <button pButton type="button" size="small" [text]="true" severity="secondary" (click)="editing.set(null)">{{ t('cancel') }}</button>
                  <button pButton type="submit" size="small" [disabled]="saving() || uploading() > 0">{{ t('submit') }}</button>
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
  private readonly userApi = inject(UserApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  readonly order = input.required<OrderDetail>();

  protected readonly maxContent = MAX_CONTENT;
  protected readonly maxMedia = MAX_MEDIA;
  private readonly reviews = signal<Review[]>([]);
  private readonly byItem = computed(() => new Map(this.reviews().map((r) => [r.orderItemId, r])));
  protected readonly editing = signal<number | null>(null);
  protected readonly rating = signal(5);
  protected readonly content = signal('');
  protected readonly saving = signal(false);
  protected readonly media = signal<string[]>([]);
  protected readonly uploading = signal(0);

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
    this.media.set([]);
    this.editing.set(item.id);
  }

  protected onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []).slice(0, MAX_MEDIA - this.media().length - this.uploading());
    input.value = '';
    for (const file of files) {
      if (file.size > MAX_IMAGE_BYTES) {
        this.messages.add({ severity: 'warn', summary: this.transloco.translate('errors.FILE_TOO_LARGE') });
        continue;
      }
      this.uploading.update((n) => n + 1);
      this.userApi.uploadImage(file).subscribe({
        next: (uploaded) => {
          this.uploading.update((n) => n - 1);
          this.media.update((list) => (list.length < MAX_MEDIA ? [...list, uploaded.url] : list));
        },
        error: (err) => {
          this.uploading.update((n) => n - 1);
          this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        },
      });
    }
  }

  protected removeMedia(url: string): void {
    this.media.update((list) => list.filter((m) => m !== url));
  }

  protected submit(item: OrderItem): void {
    this.saving.set(true);
    const content = this.content().trim();
    this.api.create(this.order().code, item.id, { rating: this.rating(), content: content || null, media: this.media() }).subscribe({
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
