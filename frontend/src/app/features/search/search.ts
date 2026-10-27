import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { Page, ProductCard, SearchHit, SearchSort } from '../../core/api/api.models';
import { SearchApi } from '../../core/api/search.api';
import { LanguageService } from '../../core/i18n/language.service';
import { Seo } from '../../core/seo/seo.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { ProductGrid } from '../../shared/ui/product-grid';

const PAGE_SIZE = 30;
const SORTS: { value: SearchSort; labelKey: string }[] = [
  { value: 'RELEVANCE', labelKey: 'search.sort.relevance' },
  { value: 'NEWEST', labelKey: 'productList.sort.newest' },
  { value: 'BEST_SELLING', labelKey: 'productList.sort.bestSelling' },
];
const ALL_SORTS = new Set<SearchSort>(['RELEVANCE', 'NEWEST', 'BEST_SELLING', 'PRICE_ASC', 'PRICE_DESC']);

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; unavailable: boolean }
  | { status: 'ok'; page: Page<SearchHit> };

/** Kết quả tìm kiếm full-text (P5-7): sắp xếp, lọc giá, phân trang — trạng thái nằm trên URL. */
@Component({
  selector: 'app-search',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, InputNumber, Paginator, ProductGrid, EmptyState],
  template: `
    <ng-container *transloco="let t">
      <h1 class="mb-3 text-lg">
        @if (keyword()) {
          <i class="pi pi-search mr-2 text-gray-500" aria-hidden="true"></i>{{ t('search.resultsFor') }}
          <span class="font-medium text-brand-500">“{{ keyword() }}”</span>
          @if (state(); as s) {
            @if (s.status === 'ok') {
              <span class="ml-2 text-sm text-gray-500">{{ t('search.count', { count: s.page.totalItems }) }}</span>
            }
          }
        } @else {
          {{ t('search.enterKeyword') }}
        }
      </h1>

      @if (keyword()) {
        <div class="flex gap-4">
          <aside class="fixed inset-0 z-40 overflow-y-auto bg-white p-4 md:static md:z-auto md:block md:w-52 md:shrink-0 md:bg-transparent md:p-0"
                 [class.hidden]="!filtersOpen()" [attr.aria-label]="t('productList.filters')">
            <div class="mb-4 flex items-center justify-between md:hidden">
              <span class="font-medium">{{ t('productList.filters') }}</span>
              <button type="button" class="p-2" [attr.aria-label]="t('productList.closeFilters')" (click)="filtersOpen.set(false)">
                <i class="pi pi-times" aria-hidden="true"></i>
              </button>
            </div>
            <h2 class="mb-2 flex items-center gap-2 font-medium"><i class="pi pi-filter" aria-hidden="true"></i>{{ t('productList.priceRange') }}</h2>
            <form class="flex flex-col gap-2" (ngSubmit)="applyPrice()">
              <div class="flex items-center gap-2">
                <p-inputNumber name="from" [ngModel]="priceFrom()" (ngModelChange)="priceFrom.set($event)" [min]="0"
                               [placeholder]="t('productList.priceFrom')" [locale]="locale()" inputStyleClass="w-full !px-2 !py-1.5 text-sm"
                               [ariaLabel]="t('productList.priceFromLabel')" />
                <span class="text-gray-400" aria-hidden="true">–</span>
                <p-inputNumber name="to" [ngModel]="priceTo()" (ngModelChange)="priceTo.set($event)" [min]="0"
                               [placeholder]="t('productList.priceTo')" [locale]="locale()" inputStyleClass="w-full !px-2 !py-1.5 text-sm"
                               [ariaLabel]="t('productList.priceToLabel')" />
              </div>
              <button pButton type="submit" size="small" class="w-full justify-center">{{ t('productList.apply') }}</button>
            </form>
            @if (hasPriceFilter()) {
              <button pButton type="button" size="small" [outlined]="true" class="mt-4 w-full justify-center"
                      (click)="clearFilters()">{{ t('productList.clearAll') }}</button>
            }
          </aside>

          <section class="min-w-0 flex-1">
            <div class="mb-3 flex flex-wrap items-center gap-2 rounded-sm bg-gray-200/70 px-3 py-2 text-sm">
              <span class="hidden text-gray-600 sm:inline">{{ t('productList.sortBy') }}</span>
              @for (s of sorts; track s.value) {
                <button type="button" class="rounded-sm px-3 py-1.5 shadow-sm"
                        [class]="currentSort() === s.value ? 'bg-brand-500 text-white' : 'bg-white'"
                        [attr.aria-pressed]="currentSort() === s.value" (click)="setSort(s.value)">{{ t(s.labelKey) }}</button>
              }
              <select class="rounded-sm bg-white px-2 py-1.5 shadow-sm"
                      [class.text-brand-500]="currentSort().startsWith('PRICE')" [attr.aria-label]="t('productList.sortByPrice')"
                      [value]="currentSort().startsWith('PRICE') ? currentSort() : ''"
                      (change)="setSort($any($event.target).value)">
                <option value="" disabled>{{ t('productList.sort.price') }}</option>
                <option value="PRICE_ASC">{{ t('productList.sort.priceAsc') }}</option>
                <option value="PRICE_DESC">{{ t('productList.sort.priceDesc') }}</option>
              </select>
              <button type="button" class="ml-auto flex items-center gap-1 rounded-sm bg-white px-3 py-1.5 shadow-sm md:hidden"
                      (click)="filtersOpen.set(true)">
                <i class="pi pi-filter" aria-hidden="true"></i>{{ t('productList.filter') }}
              </button>
            </div>

            @switch (state().status) {
              @case ('error') {
                <app-empty-state [title]="t(unavailable() ? 'search.unavailable' : 'productList.loadError')"
                                 [description]="'common.tryAgainLater' | transloco" icon="pi-exclamation-circle" />
              }
              @case ('ok') {
                @if (cards().length === 0) {
                  <app-empty-state [title]="t('search.noResults', { q: keyword() })" [description]="t('search.noResultsHint')" icon="pi-search">
                    <a routerLink="/" class="text-brand-500">{{ t('common.backHome') }}</a>
                  </app-empty-state>
                } @else {
                  <app-product-grid [products]="cards()" />
                }
                @if (state(); as s) {
                  @if (s.status === 'ok' && s.page.totalPages > 1) {
                    <p-paginator styleClass="mt-4 !bg-transparent" [first]="s.page.page * s.page.size" [rows]="s.page.size"
                                 [totalRecords]="s.page.totalItems" (onPageChange)="onPage($event)" />
                  }
                }
              }
              @default {
                <app-product-grid [products]="[]" [loading]="true" />
              }
            }
          </section>
        </div>
      }
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Search {
  private readonly api = inject(SearchApi);
  private readonly router = inject(Router);
  private readonly seo = inject(Seo);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  // Query params (component input binding)
  readonly q = input<string>();
  readonly sort = input<string>();
  readonly minPrice = input<string>();
  readonly maxPrice = input<string>();
  readonly page = input<string>();

  protected readonly sorts = SORTS;
  protected readonly locale = this.language.intlLocale;
  protected readonly filtersOpen = signal(false);
  protected readonly priceFrom = signal<number | null>(null);
  protected readonly priceTo = signal<number | null>(null);

  protected readonly keyword = computed(() => (this.q() ?? '').trim().slice(0, 100));
  protected readonly currentSort = computed<SearchSort>(() => {
    const s = this.sort() as SearchSort;
    return ALL_SORTS.has(s) ? s : 'RELEVANCE';
  });
  private readonly currentPage = computed(() => Math.max(0, Number(this.page() ?? 0) || 0));
  private readonly minPriceValue = computed(() => toPrice(this.minPrice()));
  private readonly maxPriceValue = computed(() => toPrice(this.maxPrice()));
  protected readonly hasPriceFilter = computed(() => this.minPriceValue() !== undefined || this.maxPriceValue() !== undefined);

  private readonly query = computed(() =>
    this.keyword()
      ? {
          q: this.keyword(),
          sort: this.currentSort(),
          minPrice: this.minPriceValue(),
          maxPrice: this.maxPriceValue(),
          page: this.currentPage(),
          size: PAGE_SIZE,
        }
      : null,
  );

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap((q) =>
        q === null
          ? of<SearchState>({ status: 'idle' })
          : this.api.search(q).pipe(
              map((page): SearchState => ({ status: 'ok', page })),
              catchError((err: { status?: number }) => of<SearchState>({ status: 'error', unavailable: err?.status === 503 })),
              startWith<SearchState>({ status: 'loading' }),
            ),
      ),
    ),
    { initialValue: { status: 'loading' } as SearchState },
  );
  protected readonly unavailable = computed(() => {
    const s = this.state();
    return s.status === 'error' && s.unavailable;
  });
  /** Kết quả hiển thị bằng thẻ sản phẩm dùng chung. */
  protected readonly cards = computed<ProductCard[]>(() => {
    const s = this.state();
    return s.status === 'ok'
      ? s.page.items.map((h) => ({
          id: h.id, name: h.name, slug: h.slug, thumbnailUrl: h.thumbnailUrl, minPrice: h.minPrice, maxPrice: h.maxPrice,
          soldCount: h.soldCount, ratingAvg: h.ratingAvg, ratingCount: h.ratingCount, createdAt: '',
        }))
      : [];
  });

  constructor() {
    effect(() => {
      this.priceFrom.set(this.minPriceValue() ?? null);
      this.priceTo.set(this.maxPriceValue() ?? null);
    });
    effect(() => {
      const q = this.keyword();
      this.language.lang();
      // Trang kết quả tìm kiếm không lập chỉ mục (nội dung trùng lặp vô hạn theo từ khóa)
      this.seo.set({
        title: q ? this.transloco.translate('title.searchFor', { q }) : this.transloco.translate('title.search'),
        description: this.transloco.translate('seo.home'),
        noindex: true,
      });
    });
  }

  protected setSort(sort: SearchSort): void {
    this.navigate({ sort: sort === 'RELEVANCE' ? null : sort, page: null });
  }

  protected applyPrice(): void {
    let from = this.priceFrom();
    let to = this.priceTo();
    if (from !== null && to !== null && from > to) {
      [from, to] = [to, from];
    }
    this.navigate({ minPrice: from, maxPrice: to, page: null });
    this.filtersOpen.set(false);
  }

  protected clearFilters(): void {
    this.navigate({ minPrice: null, maxPrice: null, page: null });
  }

  protected onPage(event: PaginatorState): void {
    this.navigate({ page: event.page ? event.page : null });
  }

  private navigate(params: Record<string, string | number | null>): void {
    void this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge' });
  }
}

function toPrice(value: string | undefined): number | undefined {
  const n = Number(value);
  return value !== undefined && value !== '' && Number.isFinite(n) && n >= 0 ? Math.floor(n) : undefined;
}
