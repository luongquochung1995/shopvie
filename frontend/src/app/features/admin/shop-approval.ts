import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';
import { Observable, Subscription } from 'rxjs';
import { AdminApi } from '../../core/api/admin.api';
import { errorKey } from '../../core/api/api-error';
import { AdminShop, SHOP_STATUSES, ShopStatus } from '../../core/api/api.models';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { ShopStatusTag } from '../seller-center/shop-status-tag';

const PAGE_SIZE = 20;

/** Admin duyệt shop mới đăng ký: danh sách theo trạng thái (mặc định Chờ duyệt), duyệt hoặc từ chối kèm lý do. */
@Component({
  selector: 'app-shop-approval',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, Dialog, Textarea, DateTimePipe, EmptyState, ShopStatusTag],
  template: `
    <div *transloco="let t; prefix: 'admin.shops'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <h1 class="mb-4 text-lg font-medium">{{ t('title') }}</h1>
      <nav class="-mx-4 mb-4 flex overflow-x-auto border-b border-gray-200 px-4 text-sm md:mx-0 md:px-0" [attr.aria-label]="t('tabs')">
        @for (s of statuses; track s) {
          <a [routerLink]="[]" [queryParams]="{ status: s }" class="shrink-0 border-b-2 px-4 py-3"
             [class]="current() === s ? 'border-brand-500 text-brand-500' : 'border-transparent hover:text-brand-500'"
             [attr.aria-current]="current() === s ? 'page' : null">{{ 'shopStatus.' + s | transloco }}</a>
        }
      </nav>

      @if (!loading() && shops().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-shop" />
      }
      <ul class="divide-y divide-gray-100">
        @for (s of shops(); track s.id) {
          <li class="flex flex-col gap-3 py-4 sm:flex-row sm:items-start">
            <span class="size-12 shrink-0 overflow-hidden rounded-full bg-gray-100">
              @if (s.logoUrl) {
                <img [src]="s.logoUrl" alt="" class="size-full object-cover" />
              } @else {
                <i class="pi pi-shop flex size-full items-center justify-center text-gray-400" aria-hidden="true"></i>
              }
            </span>
            <div class="min-w-0 flex-1 text-sm">
              <p class="flex flex-wrap items-center gap-2"><span class="font-medium">{{ s.name }}</span><app-shop-status-tag [status]="s.status" /></p>
              <p class="text-gray-500">{{ t('owner', { name: s.owner?.fullName ?? '—', email: s.owner?.email ?? '—' }) }}</p>
              <p class="text-xs text-gray-400">{{ t('registeredAt') }} {{ s.createdAt | dateTime }}</p>
              @if (s.description) {
                <p class="mt-1 line-clamp-3 whitespace-pre-line text-gray-600">{{ s.description }}</p>
              }
              @if (s.rejectReason) {
                <p class="mt-1 text-red-700">{{ t('reason', { reason: s.rejectReason }) }}</p>
              }
            </div>
            @if (s.status === 'PENDING') {
              <div class="flex shrink-0 gap-2">
                <button pButton type="button" size="small" [outlined]="true" severity="secondary" [disabled]="busyId() === s.id"
                        (click)="openReject(s)">{{ t('reject') }}</button>
                <button pButton type="button" size="small" [disabled]="busyId() === s.id" (click)="approve(s)">{{ t('approve') }}</button>
              </div>
            }
          </li>
        }
      </ul>
      @if (loading()) {
        <p class="py-6 text-center text-gray-500">{{ 'common.loading' | transloco }}</p>
      } @else if (hasMore()) {
        <div class="mt-4 flex justify-center">
          <button pButton type="button" [outlined]="true" (click)="fetch(page() + 1)">{{ 'home.loadMore' | transloco }}</button>
        </div>
      }

      <p-dialog [header]="t('rejectTitle', { name: rejecting()?.name ?? '' })" [visible]="rejecting() !== null"
                (visibleChange)="!$event && rejecting.set(null)" [modal]="true" [draggable]="false"
                [style]="{ width: '28rem' }" [breakpoints]="{ '640px': '95vw' }">
        <textarea pTextarea class="w-full" rows="3" maxlength="500" [placeholder]="t('reasonPlaceholder')" [attr.aria-label]="t('reasonPlaceholder')"
                  [ngModel]="reason()" (ngModelChange)="reason.set($event)"></textarea>
        <ng-template #footer>
          <button pButton type="button" [text]="true" severity="secondary" (click)="rejecting.set(null)">{{ 'common.back' | transloco }}</button>
          <button pButton type="button" severity="danger" [disabled]="!reason().trim() || busyId() !== null" (click)="reject()">{{ t('reject') }}</button>
        </ng-template>
      </p-dialog>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ShopApproval {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  /** Query param ?status=... (mặc định PENDING). */
  readonly status = input<string>();

  protected readonly statuses = SHOP_STATUSES;
  protected readonly current = computed<ShopStatus>(() =>
    (SHOP_STATUSES as readonly string[]).includes(this.status() ?? '') ? (this.status() as ShopStatus) : 'PENDING',
  );
  protected readonly shops = signal<AdminShop[]>([]);
  protected readonly loading = signal(false);
  protected readonly page = signal(0);
  private readonly totalPages = signal(0);
  protected readonly hasMore = computed(() => this.page() + 1 < this.totalPages());
  protected readonly busyId = signal<number | null>(null);
  protected readonly rejecting = signal<AdminShop | null>(null);
  protected readonly reason = signal('');
  private request?: Subscription;

  constructor() {
    effect(() => {
      this.current();
      this.shops.set([]);
      this.fetch(0);
    });
  }

  protected approve(s: AdminShop): void {
    this.run(s, this.api.approveShop(s.id), 'admin.shops.approved');
  }

  protected openReject(s: AdminShop): void {
    this.reason.set('');
    this.rejecting.set(s);
  }

  protected reject(): void {
    const s = this.rejecting();
    if (s) {
      this.run(s, this.api.rejectShop(s.id, this.reason().trim()), 'admin.shops.rejected');
    }
  }

  protected fetch(page: number): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.api
      .shops(this.current(), page, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.shops.update((list) => (page === 0 ? res.items : [...list, ...res.items]));
          this.page.set(res.page);
          this.totalPages.set(res.totalPages);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        },
      });
  }

  private run(s: AdminShop, request: Observable<AdminShop>, successKey: string): void {
    this.busyId.set(s.id);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        this.rejecting.set(null);
        this.messages.add({ severity: 'success', summary: this.transloco.translate(successKey, { name: s.name }) });
        // Shop đã đổi trạng thái → rời khỏi tab hiện tại
        this.shops.update((list) => list.filter((x) => x.id !== s.id));
      },
      error: (err) => {
        this.busyId.set(null);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
