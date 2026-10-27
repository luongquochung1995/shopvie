import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Observable, Subscription } from 'rxjs';
import { errorKey } from '../../../core/api/api-error';
import { ProductStatus, SellerProductRow } from '../../../core/api/api.models';
import { SellerApi } from '../../../core/api/seller.api';
import { formatVndRange } from '../../../shared/pipes/vnd.pipe';
import { LanguageService } from '../../../core/i18n/language.service';
import { EmptyState } from '../../../shared/ui/empty-state';
import { SellerShopStore } from '../seller-shop.store';

const PAGE_SIZE = 10;
const TABS = ['ALL', 'ACTIVE', 'HIDDEN', 'BANNED'] as const;
type Tab = (typeof TABS)[number];

/** Danh sách sản phẩm của shop: lọc theo trạng thái, tìm theo tên, ẩn/hiện, sửa, xóa. */
@Component({
  selector: 'app-seller-product-list',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, InputText, EmptyState],
  template: `
    <div *transloco="let t; prefix: 'seller.products'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        @if (shop.active()) {
          <a pButton routerLink="/seller-center/products/new"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('add') }}</a>
        }
      </div>

      <nav class="-mx-4 mb-4 flex overflow-x-auto border-b border-gray-200 px-4 text-sm md:mx-0 md:px-0" [attr.aria-label]="t('tabs')">
        @for (tab of tabs; track tab) {
          <a [routerLink]="[]" [queryParams]="{ status: tab === 'ALL' ? null : tab }" class="shrink-0 border-b-2 px-4 py-3"
             [class]="currentTab() === tab ? 'border-brand-500 text-brand-500' : 'border-transparent hover:text-brand-500'"
             [attr.aria-current]="currentTab() === tab ? 'page' : null">{{ t('tab.' + tab) }}</a>
        }
      </nav>

      <form class="mb-4 flex gap-2" role="search" (ngSubmit)="search()">
        <input pInputText type="search" name="q" class="min-w-0 flex-1" [placeholder]="t('searchPlaceholder')"
               [attr.aria-label]="t('searchPlaceholder')" [ngModel]="keyword()" (ngModelChange)="keyword.set($event)" />
        <button pButton type="submit" [outlined]="true">{{ t('search') }}</button>
      </form>

      @if (!loading() && rows().length === 0) {
        <app-empty-state [title]="t(appliedKeyword() ? 'noMatch' : 'empty')" icon="pi-box" />
      }

      @if (rows().length > 0) {
        <div class="hidden grid-cols-[minmax(0,1fr)_9rem_5rem_5rem_7rem] gap-3 border-b border-gray-100 pb-2 text-xs text-gray-500 md:grid">
          <span>{{ t('col.product') }}</span><span>{{ t('col.price') }}</span><span class="text-right">{{ t('col.stock') }}</span>
          <span class="text-right">{{ t('col.sold') }}</span><span class="text-right">{{ t('col.actions') }}</span>
        </div>
      }
      <ul class="divide-y divide-gray-100">
        @for (p of rows(); track p.id) {
          <li class="grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 py-3 text-sm md:grid-cols-[minmax(0,1fr)_9rem_5rem_5rem_7rem] md:items-center">
            <div class="col-span-2 flex min-w-0 gap-3 md:col-span-1">
              <span class="size-14 shrink-0 overflow-hidden border border-gray-100 bg-gray-50">
                @if (p.thumbnailUrl) {
                  <img [src]="p.thumbnailUrl" alt="" loading="lazy" class="size-full object-cover" />
                }
              </span>
              <div class="min-w-0">
                <a [routerLink]="['/seller-center/products', p.id]" class="line-clamp-2 hover:text-brand-500">{{ p.name }}</a>
                <p class="text-xs text-gray-500">
                  <span class="mr-2 rounded-sm px-1" [class]="statusTone(p.status)">{{ 'productStatus.' + p.status | transloco }}</span>
                  {{ t('variants', { count: p.variantCount }) }}
                </p>
              </div>
            </div>
            <span class="text-brand-500 md:text-gray-800">{{ priceRange(p) }}</span>
            <span class="text-right text-gray-600 md:text-gray-800" [class.text-red-600]="p.totalStock === 0">
              <span class="md:hidden">{{ t('col.stock') }}: </span>{{ p.totalStock }}
            </span>
            <span class="hidden text-right md:block">{{ p.soldCount }}</span>
            <div class="col-span-2 flex justify-end gap-3 text-blue-600 md:col-span-1">
              <a [routerLink]="['/seller-center/products', p.id]" class="hover:underline">{{ t('edit') }}</a>
              @if (p.status === 'ACTIVE' || p.status === 'HIDDEN') {
                <button type="button" class="hover:underline disabled:opacity-50" [disabled]="busyId() === p.id || !shop.active()"
                        (click)="toggle(p)">{{ t(p.status === 'ACTIVE' ? 'hide' : 'show') }}</button>
              }
              <button type="button" class="hover:underline disabled:opacity-50" [disabled]="busyId() === p.id" (click)="remove(p)">
                {{ t('delete') }}
              </button>
            </div>
          </li>
        }
      </ul>

      @if (loading()) {
        <p class="py-6 text-center text-gray-500">{{ 'common.loading' | transloco }}</p>
      } @else if (totalPages() > 1) {
        <nav class="mt-4 flex items-center justify-center gap-3 text-sm" [attr.aria-label]="t('pagination')">
          <button pButton type="button" size="small" [outlined]="true" [disabled]="page() === 0" (click)="page.set(page() - 1)"
                  [attr.aria-label]="t('prev')"><i class="pi pi-angle-left" aria-hidden="true"></i></button>
          <span>{{ t('pageOf', { page: page() + 1, total: totalPages() }) }}</span>
          <button pButton type="button" size="small" [outlined]="true" [disabled]="page() + 1 >= totalPages()"
                  (click)="page.set(page() + 1)" [attr.aria-label]="t('next')"><i class="pi pi-angle-right" aria-hidden="true"></i></button>
        </nav>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class SellerProductList {
  private readonly api = inject(SellerApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly shop = inject(SellerShopStore);

  /** Query param ?status=... */
  readonly status = input<string>();

  protected readonly tabs = TABS;
  protected readonly currentTab = computed<Tab>(() => {
    const s = this.status();
    return (TABS as readonly string[]).includes(s ?? '') ? (s as Tab) : 'ALL';
  });
  protected readonly keyword = signal('');
  protected readonly appliedKeyword = signal('');
  protected readonly page = signal(0);
  protected readonly rows = signal<SellerProductRow[]>([]);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(false);
  protected readonly busyId = signal<number | null>(null);
  private request?: Subscription;

  constructor() {
    // Đổi tab → về trang đầu
    effect(() => {
      this.currentTab();
      this.page.set(0);
    });
    effect(() => this.fetch(this.currentTab(), this.appliedKeyword(), this.page()));
  }

  protected search(): void {
    this.page.set(0);
    this.appliedKeyword.set(this.keyword().trim());
  }

  protected priceRange(p: SellerProductRow): string {
    return formatVndRange(p.minPrice, p.maxPrice, this.language.intlLocale());
  }

  protected statusTone(status: ProductStatus): string {
    return status === 'ACTIVE' ? 'bg-green-50 text-green-700' : status === 'BANNED' ? 'bg-red-50 text-red-700' : 'bg-gray-100 text-gray-600';
  }

  protected toggle(p: SellerProductRow): void {
    const visible = p.status !== 'ACTIVE';
    this.run(p, this.api.setVisibility(p.id, visible), visible ? 'seller.products.shown' : 'seller.products.hidden');
  }

  protected remove(p: SellerProductRow): void {
    if (confirm(this.transloco.translate('seller.products.confirmDelete', { name: p.name }))) {
      this.run(p, this.api.deleteProduct(p.id), 'seller.products.deleted');
    }
  }

  private run(p: SellerProductRow, request: Observable<unknown>, successKey: string): void {
    this.busyId.set(p.id);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        this.messages.add({ severity: 'success', summary: this.transloco.translate(successKey) });
        this.fetch(this.currentTab(), this.appliedKeyword(), this.page());
      },
      error: (err) => {
        this.busyId.set(null);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }

  private fetch(tab: Tab, q: string, page: number): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.api
      .products({ status: tab === 'ALL' ? null : tab, q, page, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          // Trang hiện tại trống sau khi xóa/ẩn → lùi về trang trước
          if (res.items.length === 0 && page > 0) {
            this.page.set(page - 1);
            return;
          }
          this.rows.set(res.items);
          this.totalPages.set(res.totalPages);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        },
      });
  }
}
