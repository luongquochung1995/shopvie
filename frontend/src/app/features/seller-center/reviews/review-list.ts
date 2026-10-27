import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { errorKey } from '../../../core/api/api-error';
import { SellerReview } from '../../../core/api/api.models';
import { ReviewApi } from '../../../core/api/review.api';
import { DateTimePipe } from '../../../shared/pipes/date-time.pipe';
import { EmptyState } from '../../../shared/ui/empty-state';
import { RatingStars } from '../../../shared/ui/rating-stars';

const PAGE_SIZE = 20;

/** Đánh giá sản phẩm của shop; seller phản hồi mỗi đánh giá một lần. */
@Component({
  selector: 'app-seller-review-list',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Paginator, DateTimePipe, EmptyState, RatingStars],
  template: `
    <div *transloco="let t; prefix: 'seller.reviews'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        <label class="flex items-center gap-2 text-sm">
          <input type="checkbox" [ngModel]="unrepliedOnly()" (ngModelChange)="setUnreplied($event)" />{{ t('unrepliedOnly') }}
        </label>
      </div>
      @if (!loading() && reviews().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-star" />
      }
      <ul class="divide-y divide-gray-100">
        @for (r of reviews(); track r.id) {
          <li class="py-4 text-sm" [attr.data-review]="r.id">
            <p class="font-medium">{{ r.productName }}@if (r.variantName) { <span class="font-normal text-gray-500">({{ r.variantName }})</span> }</p>
            <p class="flex items-center gap-2 text-xs text-gray-500">
              <app-rating-stars [rating]="r.rating" /> {{ r.reviewerName }} · {{ r.createdAt | dateTime }}
            </p>
            @if (r.content) {
              <p class="mt-1 whitespace-pre-line">{{ r.content }}</p>
            }
            @if (r.sellerReply) {
              <div class="mt-2 rounded-sm bg-gray-50 p-3">
                <p class="text-xs font-medium">{{ t('yourReply') }} · {{ r.sellerRepliedAt | dateTime }}</p>
                <p class="whitespace-pre-line text-gray-700">{{ r.sellerReply }}</p>
              </div>
            } @else if (replying() === r.id) {
              <form class="mt-2 flex flex-col gap-2" (ngSubmit)="reply(r)">
                <textarea name="reply" rows="3" maxlength="1000" required class="rounded-sm border border-gray-300 p-2"
                          [attr.aria-label]="t('reply')" [ngModel]="replyText()" (ngModelChange)="replyText.set($event)"></textarea>
                <div class="flex justify-end gap-2">
                  <button pButton type="button" size="small" [text]="true" severity="secondary" (click)="replying.set(null)">{{ t('cancel') }}</button>
                  <button pButton type="submit" size="small" [disabled]="saving() || !replyText().trim()">{{ t('send') }}</button>
                </div>
              </form>
            } @else {
              <button pButton type="button" size="small" [outlined]="true" class="mt-2" (click)="startReply(r)">{{ t('reply') }}</button>
            }
          </li>
        }
      </ul>
      @if (totalItems() > pageSize) {
        <p-paginator styleClass="mt-2 !bg-transparent" [first]="page() * pageSize" [rows]="pageSize" [totalRecords]="totalItems()"
                     (onPageChange)="onPage($event)" />
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ReviewList {
  private readonly api = inject(ReviewApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly reviews = signal<SellerReview[]>([]);
  protected readonly loading = signal(true);
  protected readonly unrepliedOnly = signal(false);
  protected readonly page = signal(0);
  protected readonly totalItems = signal(0);
  protected readonly replying = signal<number | null>(null);
  protected readonly replyText = signal('');
  protected readonly saving = signal(false);

  constructor() {
    effect(() => {
      const unreplied = this.unrepliedOnly();
      const page = this.page();
      this.loading.set(true);
      this.api.sellerReviews(unreplied, page, PAGE_SIZE).subscribe({
        next: (p) => {
          this.reviews.set(p.items);
          this.totalItems.set(p.totalItems);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        },
      });
    });
  }

  protected setUnreplied(value: boolean): void {
    this.page.set(0);
    this.unrepliedOnly.set(value);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  protected startReply(r: SellerReview): void {
    this.replyText.set('');
    this.replying.set(r.id);
  }

  protected reply(r: SellerReview): void {
    this.saving.set(true);
    this.api.reply(r.id, this.replyText().trim()).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.replying.set(null);
        this.reviews.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
      },
      error: (err) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
