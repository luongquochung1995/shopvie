import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { Page, ProductCard, ProductSort } from '../../core/api/api.models';
import { CatalogApi, findCategoryPath } from '../../core/api/catalog.api';
import { LanguageService } from '../../core/i18n/language.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { ProductGrid } from '../../shared/ui/product-grid';

const PAGE_SIZE = 30;
const SORTS: { value: ProductSort; labelKey: string }[] = [
  { value: 'createdAt,desc', labelKey: 'sort.newest' },
  { value: 'soldCount,desc', labelKey: 'sort.bestSelling' },
];

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ok'; page: Page<ProductCard> };

@Component({
  selector: 'app-product-list',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, InputNumber, Paginator, ProductGrid, EmptyState],
  templateUrl: './product-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ProductList {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  // Route param + query params (component input binding)
  readonly slug = input.required<string>();
  readonly sort = input<string>();
  readonly minPrice = input<string>();
  readonly maxPrice = input<string>();
  readonly page = input<string>();

  protected readonly sorts = SORTS;
  protected readonly locale = this.language.intlLocale;
  protected readonly filtersOpen = signal(false);
  protected readonly priceFrom = signal<number | null>(null);
  protected readonly priceTo = signal<number | null>(null);

  private readonly categories = toSignal(this.api.categories().pipe(catchError(() => of(null))));
  /** Đường dẫn từ danh mục gốc tới danh mục hiện tại; null = slug không tồn tại; undefined = đang tải. */
  protected readonly path = computed(() => {
    const roots = this.categories();
    return roots === undefined ? undefined : roots === null ? null : findCategoryPath(roots, this.slug());
  });
  protected readonly category = computed(() => this.path()?.at(-1) ?? null);
  /** Danh mục con hiển thị ở sidebar: con của danh mục gốc. */
  protected readonly sideCategories = computed(() => this.path()?.[0] ?? null);

  protected readonly currentSort = computed<ProductSort>(() => {
    const s = this.sort();
    return s === 'soldCount,desc' || s === 'minPrice,asc' || s === 'minPrice,desc' ? s : 'createdAt,desc';
  });
  protected readonly currentPage = computed(() => Math.max(0, Number(this.page() ?? 0) || 0));
  private readonly minPriceValue = computed(() => toPrice(this.minPrice()));
  private readonly maxPriceValue = computed(() => toPrice(this.maxPrice()));
  protected readonly hasPriceFilter = computed(() => this.minPriceValue() !== undefined || this.maxPriceValue() !== undefined);

  private readonly query = computed(() => {
    const category = this.category();
    return category
      ? {
          categoryId: category.id,
          sort: this.currentSort(),
          minPrice: this.minPriceValue(),
          maxPrice: this.maxPriceValue(),
          page: this.currentPage(),
          size: PAGE_SIZE,
        }
      : null;
  });

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap((q) =>
        q === null
          ? of<ListState>({ status: 'loading' })
          : this.api.products(q).pipe(
              map((page): ListState => ({ status: 'ok', page })),
              catchError(() => of<ListState>({ status: 'error' })),
              startWith<ListState>({ status: 'loading' }),
            ),
      ),
    ),
    { initialValue: { status: 'loading' } as ListState },
  );
  protected readonly items = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.page.items : [];
  });

  constructor() {
    // Đồng bộ ô lọc giá theo URL và tiêu đề trang theo danh mục
    effect(() => {
      this.priceFrom.set(this.minPriceValue() ?? null);
      this.priceTo.set(this.maxPriceValue() ?? null);
    });
    effect(() => {
      const c = this.category();
      this.language.lang(); // dịch lại tiêu đề khi đổi ngôn ngữ
      if (c) {
        this.title.setTitle(this.transloco.translate('title.category', { name: c.name }));
      } else if (this.path() === null) {
        this.title.setTitle(this.transloco.translate('title.notFound'));
      }
    });
  }

  protected setSort(sort: ProductSort): void {
    this.navigate({ sort, page: null });
  }

  protected onPriceSort(value: string): void {
    this.setSort(value as ProductSort);
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
