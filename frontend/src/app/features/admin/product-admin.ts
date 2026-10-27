import { ChangeDetectionStrategy, Component, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { AdminApi } from '../../core/api/admin.api';
import { errorKey } from '../../core/api/api-error';
import { AdminProduct, ProductStatus } from '../../core/api/api.models';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { ModerationDialog } from './moderation-dialog';

const PAGE_SIZE = 20;
const STATUSES: (ProductStatus | null)[] = [null, 'ACTIVE', 'HIDDEN', 'BANNED'];

/** Hậu kiểm sản phẩm: rà soát theo trạng thái / tên, khóa (kèm lý do, seller được thông báo) / mở khóa. */
@Component({
  selector: 'app-product-admin',
  imports: [FormsModule, RouterLink, TranslocoDirective, TranslocoPipe, ButtonDirective, Paginator, VndPipe, EmptyState, ModerationDialog],
  template: `
    <div *transloco="let t; prefix: 'admin.products'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <h1 class="mb-4 text-lg font-medium">{{ t('title') }}</h1>
      <nav class="-mx-4 mb-3 flex overflow-x-auto border-b border-gray-200 px-4 text-sm md:mx-0 md:px-0" [attr.aria-label]="t('tabs')">
        @for (s of statuses; track s) {
          <button type="button" class="shrink-0 border-b-2 px-4 py-3" [attr.aria-pressed]="status() === s"
                  [class]="status() === s ? 'border-brand-500 text-brand-500' : 'border-transparent hover:text-brand-500'"
                  (click)="setStatus(s)">{{ s ? ('productStatus.' + s | transloco) : t('all') }}</button>
        }
      </nav>
      <form class="mb-4 flex gap-2 text-sm" (ngSubmit)="search()">
        <input type="search" name="q" class="min-w-0 flex-1 rounded-sm border border-gray-300 px-2 py-1.5" [placeholder]="t('searchPlaceholder')"
               [attr.aria-label]="t('searchPlaceholder')" [ngModel]="keyword()" (ngModelChange)="keyword.set($event)" />
        <button pButton type="submit" size="small">{{ t('search') }}</button>
      </form>

      @if (!loading() && products().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-box" />
      }
      <ul class="divide-y divide-gray-100">
        @for (p of products(); track p.id) {
          <li class="flex flex-col gap-3 py-3 text-sm sm:flex-row sm:items-center" [attr.data-product]="p.id">
            <span class="size-14 shrink-0 overflow-hidden bg-gray-50">
              @if (p.thumbnailUrl) {
                <img [src]="p.thumbnailUrl" alt="" loading="lazy" class="size-full object-cover" />
              }
            </span>
            <div class="min-w-0 flex-1">
              @if (p.status === 'ACTIVE') {
                <a [routerLink]="['/p', p.slug]" class="line-clamp-1 font-medium hover:text-brand-500">{{ p.name }}</a>
              } @else {
                <p class="line-clamp-1 font-medium">{{ p.name }}</p>
              }
              <p class="text-gray-500">{{ p.shop?.name ?? '—' }} · {{ p.minPrice | vnd }} · {{ t('sold', { count: p.soldCount }) }}</p>
              @if (p.banReason) {
                <p class="text-red-700">{{ t('banReason', { reason: p.banReason }) }}</p>
              }
            </div>
            <span class="shrink-0 rounded-sm px-2 py-0.5 text-xs" [class]="p.status === 'BANNED' ? 'bg-red-50 text-red-700' : 'bg-gray-100 text-gray-600'">
              {{ 'productStatus.' + p.status | transloco }}</span>
            @if (p.status === 'BANNED') {
              <button pButton type="button" size="small" class="shrink-0" [disabled]="busyId() === p.id" (click)="unban(p)">{{ t('unban') }}</button>
            } @else {
              <button pButton type="button" size="small" [outlined]="true" severity="danger" class="shrink-0" (click)="openBan(p)">{{ t('ban') }}</button>
            }
          </li>
        }
      </ul>
      @if (totalItems() > pageSize) {
        <p-paginator styleClass="mt-2 !bg-transparent" [first]="page() * pageSize" [rows]="pageSize" [totalRecords]="totalItems()"
                     (onPageChange)="onPage($event)" />
      }
      <app-moderation-dialog #dialog [header]="t('banTitle', { name: banning()?.name ?? '' })" [confirmLabel]="t('ban')"
                             [visible]="banning() !== null" [busy]="busyId() !== null" (cancelled)="banning.set(null)" (confirmed)="ban($event)" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ProductAdmin {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly dialog = viewChild.required<ModerationDialog>('dialog');

  protected readonly statuses = STATUSES;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly status = signal<ProductStatus | null>(null);
  protected readonly keyword = signal('');
  private readonly appliedKeyword = signal('');
  protected readonly page = signal(0);
  protected readonly products = signal<AdminProduct[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly loading = signal(true);
  protected readonly busyId = signal<number | null>(null);
  protected readonly banning = signal<AdminProduct | null>(null);

  constructor() {
    effect(() => this.load(this.status(), this.appliedKeyword(), this.page()));
  }

  protected setStatus(s: ProductStatus | null): void {
    this.page.set(0);
    this.status.set(s);
  }

  protected search(): void {
    this.page.set(0);
    this.appliedKeyword.set(this.keyword().trim());
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  protected openBan(p: AdminProduct): void {
    this.dialog().reset();
    this.banning.set(p);
  }

  protected ban(reason: string): void {
    const p = this.banning();
    if (!p) {
      return;
    }
    this.busyId.set(p.id);
    this.api.banProduct(p.id, reason).subscribe({
      next: (updated) => this.replace(updated, 'admin.products.banned'),
      error: (err) => this.fail(err),
    });
  }

  protected unban(p: AdminProduct): void {
    this.busyId.set(p.id);
    this.api.unbanProduct(p.id).subscribe({
      next: (updated) => this.replace(updated, 'admin.products.unbanned'),
      error: (err) => this.fail(err),
    });
  }

  private replace(updated: AdminProduct, key: string): void {
    this.busyId.set(null);
    this.banning.set(null);
    this.products.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
    this.messages.add({ severity: 'success', summary: this.transloco.translate(key, { name: updated.name }) });
  }

  private fail(err: unknown): void {
    this.busyId.set(null);
    this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
  }

  private load(status: ProductStatus | null, q: string, page: number): void {
    this.loading.set(true);
    this.api.products({ status, q, page, size: PAGE_SIZE }).subscribe({
      next: (p) => {
        this.products.set(p.items);
        this.totalItems.set(p.totalItems);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.fail(err);
      },
    });
  }
}
