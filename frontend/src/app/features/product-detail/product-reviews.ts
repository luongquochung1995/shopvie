import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslocoDirective } from '@jsverse/transloco';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { ProductReviews as ProductReviewsModel, ReviewSummary } from '../../core/api/api.models';
import { ReviewApi } from '../../core/api/review.api';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { RatingStars } from '../../shared/ui/rating-stars';

type Filter = { rating: number | null; withMedia: boolean };
type State = { status: 'loading' } | { status: 'error' } | { status: 'ok'; data: ProductReviewsModel };

const PAGE_SIZE = 10;

/** Đánh giá sản phẩm ở trang chi tiết: tổng quan theo số sao, lọc, danh sách kèm phản hồi của shop. */
@Component({
  selector: 'app-product-reviews',
  imports: [TranslocoDirective, Paginator, DateTimePipe, RatingStars],
  template: `
    <section *transloco="let t; prefix: 'reviews'" class="rounded-sm bg-white p-4 shadow-sm md:p-6" aria-labelledby="product-reviews">
      <h2 id="product-reviews" class="mb-4 text-lg font-medium uppercase">{{ t('title') }}</h2>
      @if (summary(); as s) {
        <div class="mb-4 flex flex-col gap-4 rounded-sm border border-brand-100 bg-brand-50/50 p-4 md:flex-row md:items-center">
          <div class="shrink-0 text-center text-brand-500 md:w-40">
            <p><span class="text-3xl">{{ s.ratingAvg }}</span> {{ t('outOf5') }}</p>
            <app-rating-stars class="text-xl" [rating]="s.ratingAvg" />
          </div>
          <div class="flex flex-wrap gap-2" role="group" [attr.aria-label]="t('filter')">
            <button type="button" class="rounded-sm border bg-white px-3 py-1.5 text-sm" [class]="chipClass(filter().rating === null && !filter().withMedia)"
                    (click)="setFilter({ rating: null, withMedia: false })">{{ t('all') }} ({{ s.ratingCount }})</button>
            @for (star of [5, 4, 3, 2, 1]; track star) {
              <button type="button" class="rounded-sm border bg-white px-3 py-1.5 text-sm" [class]="chipClass(filter().rating === star)"
                      (click)="setFilter({ rating: star, withMedia: false })">{{ t('stars', { count: star }) }} ({{ s.counts[star] ?? 0 }})</button>
            }
            <button type="button" class="rounded-sm border bg-white px-3 py-1.5 text-sm" [class]="chipClass(filter().withMedia)"
                    (click)="setFilter({ rating: null, withMedia: true })">{{ t('withMedia') }} ({{ s.withMedia }})</button>
          </div>
        </div>
      }

      @switch (state().status) {
        @case ('error') {
          <p class="py-6 text-center text-sm text-gray-500">{{ t('loadError') }}</p>
        }
        @case ('ok') {
          @if (items().length === 0) {
            <p class="py-6 text-center text-sm text-gray-500">{{ t('empty') }}</p>
          }
          <ul class="divide-y divide-gray-100">
            @for (r of items(); track r.id) {
              <li class="flex gap-3 py-4 text-sm">
                <span class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gray-100 text-gray-500">
                  <i class="pi pi-user" aria-hidden="true"></i>
                </span>
                <div class="min-w-0 flex-1">
                  <p class="text-xs">{{ r.reviewerName }}</p>
                  <app-rating-stars [rating]="r.rating" />
                  <p class="text-xs text-gray-500">
                    {{ r.createdAt | dateTime }}@if (r.variantName) { | {{ t('variant', { name: r.variantName }) }} }
                  </p>
                  @if (r.content) {
                    <p class="mt-2 whitespace-pre-line">{{ r.content }}</p>
                  }
                  @if (r.media.length) {
                    <div class="mt-2 flex flex-wrap gap-2">
                      @for (m of r.media; track m) {
                        <a [href]="m" target="_blank" rel="noopener noreferrer" class="size-20 overflow-hidden bg-gray-50">
                          <img [src]="m" alt="" loading="lazy" class="size-full object-cover" />
                        </a>
                      }
                    </div>
                  }
                  @if (r.sellerReply) {
                    <div class="mt-3 rounded-sm bg-gray-50 p-3">
                      <p class="mb-1 text-xs font-medium">{{ t('sellerReply') }}</p>
                      <p class="whitespace-pre-line text-gray-700">{{ r.sellerReply }}</p>
                    </div>
                  }
                </div>
              </li>
            }
          </ul>
          @if (state(); as s) {
            @if (s.status === 'ok' && s.data.totalPages > 1) {
              <p-paginator styleClass="mt-2 !bg-transparent" [first]="s.data.page * s.data.size" [rows]="s.data.size"
                           [totalRecords]="s.data.totalItems" (onPageChange)="onPage($event)" />
            }
          }
        }
        @default {
          <p class="py-6 text-center text-sm text-gray-500">{{ t('loading') }}</p>
        }
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductReviews {
  private readonly api = inject(ReviewApi);

  readonly slug = input.required<string>();

  protected readonly filter = signal<Filter>({ rating: null, withMedia: false });
  private readonly page = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.slug), toObservable(this.filter), toObservable(this.page)]).pipe(
      switchMap(([slug, f, page]) =>
        this.api.productReviews(slug, page, f.rating, f.withMedia, PAGE_SIZE).pipe(
          map((data): State => ({ status: 'ok', data })),
          catchError(() => of<State>({ status: 'error' })),
          startWith<State>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as State },
  );
  /** Tổng quan giữ nguyên trong lúc tải trang/lọc mới (tránh nhấp nháy). */
  protected readonly summary = linkedSignal<State, ReviewSummary | null>({
    source: this.state,
    computation: (st, previous) => (st.status === 'ok' ? st.data.summary : (previous?.value ?? null)),
  });
  protected readonly items = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data.items : [];
  });

  protected setFilter(f: Filter): void {
    this.page.set(0);
    this.filter.set(f);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  protected chipClass(active: boolean): string {
    return active ? 'border-brand-500 text-brand-500' : 'border-gray-200';
  }
}
