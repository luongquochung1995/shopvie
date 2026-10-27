import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { catchError, of } from 'rxjs';
import { ProductCard } from '../../core/api/api.models';
import { CatalogApi } from '../../core/api/catalog.api';
import { ProductGrid } from '../../shared/ui/product-grid';
import { HomeBanner } from './home-banner';

const PAGE_SIZE = 24;

@Component({
  selector: 'app-home',
  imports: [RouterLink, TranslocoDirective, ButtonDirective, HomeBanner, ProductGrid],
  template: `
    <div *transloco="let t; prefix: 'home'" class="flex flex-col gap-4">
      <app-home-banner />

      <section class="rounded-sm bg-white shadow-sm" aria-labelledby="home-categories">
        <h2 id="home-categories" class="border-b border-gray-100 px-4 py-3 text-base font-medium uppercase text-gray-500">
          {{ t('categories') }}
        </h2>
        <ul class="grid grid-cols-4 sm:grid-cols-5 lg:grid-cols-10">
          @for (c of categoryTiles(); track c.id) {
            <li>
              <a [routerLink]="['/c', c.slug]"
                 class="flex h-full flex-col items-center gap-2 border-b border-r border-gray-100 p-2 text-center hover:shadow-md">
                <span class="size-14 overflow-hidden rounded-full bg-brand-50">
                  @if (c.iconUrl) {
                    <img [src]="c.iconUrl" alt="" loading="lazy" class="size-full object-cover" />
                  }
                </span>
                <span class="line-clamp-2 text-xs text-gray-700 sm:text-sm">{{ c.name }}</span>
              </a>
            </li>
          }
        </ul>
      </section>

      <section aria-labelledby="home-suggest">
        <h2 id="home-suggest"
            class="sticky top-0 z-10 mb-2 border-b-4 border-brand-500 bg-white py-3 text-center text-base font-medium uppercase text-brand-500 shadow-sm">
          {{ t('suggestions') }}
        </h2>
        @if (error()) {
          <p class="py-8 text-center text-gray-500">{{ t('loadError') }}</p>
        } @else {
          <app-product-grid [products]="products()" [loading]="products().length === 0 && loading()" />
        }
        @if (hasMore()) {
          <div class="mt-4 flex justify-center">
            <button pButton type="button" [outlined]="true" class="w-64 justify-center !bg-white"
                    [disabled]="loading()" (click)="loadMore()">
              {{ loading() ? t('loading') : t('loadMore') }}
            </button>
          </div>
        }
      </section>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Home {
  private readonly api = inject(CatalogApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly categories = toSignal(this.api.categories().pipe(catchError(() => of([]))), { initialValue: [] });
  /** Danh mục gốc + danh mục con (lưới icon như trang chủ sàn TMĐT). */
  protected readonly categoryTiles = computed(() => this.categories().flatMap((root) => [root, ...root.children]));

  protected readonly products = signal<ProductCard[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  private readonly page = signal(0);
  private readonly totalPages = signal(1);
  protected readonly hasMore = computed(() => !this.error() && this.page() + 1 < this.totalPages());

  constructor() {
    this.fetch(0);
  }

  protected loadMore(): void {
    this.fetch(this.page() + 1);
  }

  private fetch(page: number): void {
    this.loading.set(true);
    this.api
      .products({ sort: 'soldCount,desc', page, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.products.update((list) => [...list, ...res.items]);
          this.page.set(res.page);
          this.totalPages.set(res.totalPages);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(this.products().length === 0);
          this.loading.set(false);
        },
      });
  }
}
