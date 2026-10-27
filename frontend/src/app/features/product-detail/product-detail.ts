import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { KeyValuePipe } from '@angular/common';
import { Meta, Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ApiError, errorKey } from '../../core/api/api-error';
import { ProductDetail as ProductDetailModel } from '../../core/api/api.models';
import { CartApi } from '../../core/api/cart.api';
import { CatalogApi } from '../../core/api/catalog.api';
import { AuthStore } from '../../core/auth/auth.store';
import { CartStore } from '../../core/cart/cart.store';
import { LanguageService } from '../../core/i18n/language.service';
import { CompactCountPipe, formatVndRange } from '../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { RatingStars } from '../../shared/ui/rating-stars';
import {
  findVariant,
  imageFor,
  isValueAvailable,
  priceRange,
  Selection,
  stockFor,
  toggleValue,
} from './variant-selection';

type DetailState =
  | { status: 'loading' }
  | { status: 'not-found' }
  | { status: 'error' }
  | { status: 'ok'; product: ProductDetailModel };

@Component({
  selector: 'app-product-detail',
  imports: [RouterLink, KeyValuePipe, TranslocoDirective, TranslocoPipe, ButtonDirective, CompactCountPipe, RatingStars, EmptyState],
  templateUrl: './product-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ProductDetail {
  private readonly api = inject(CatalogApi);
  private readonly messages = inject(MessageService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);
  private readonly cartApi = inject(CartApi);
  private readonly cartStore = inject(CartStore);

  readonly slug = input.required<string>();

  protected readonly state = toSignal(
    toObservable(this.slug).pipe(
      switchMap((slug) =>
        this.api.product(slug).pipe(
          map((product): DetailState => ({ status: 'ok', product })),
          catchError((err) =>
            of<DetailState>(ApiError.from(err).status === 404 ? { status: 'not-found' } : { status: 'error' }),
          ),
          startWith<DetailState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );
  protected readonly product = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.product : null;
  });

  protected readonly selection = signal<Selection>({});
  protected readonly quantity = signal(1);
  protected readonly activeImage = signal<string | null>(null);
  protected readonly adding = signal(false);

  protected readonly selectedVariant = computed(() => {
    const p = this.product();
    return p ? findVariant(p.variantOptions, p.variants, this.selection()) : undefined;
  });
  protected readonly priceText = computed(() => {
    const p = this.product();
    if (!p) {
      return '';
    }
    const range = priceRange(p.variants, this.selection()) ?? { min: p.minPrice, max: p.maxPrice };
    return formatVndRange(range.min, range.max, this.language.intlLocale());
  });
  protected readonly stock = computed(() => {
    const p = this.product();
    return p ? stockFor(p.variants, this.selection()) : 0;
  });
  /** Ảnh lớn: ảnh người dùng chọn → ảnh của phân loại đang chọn → ảnh đầu tiên. */
  protected readonly mainImage = computed(() => {
    const p = this.product();
    return this.activeImage() ?? (p ? imageFor(p.variants, this.selection()) ?? p.images[0] ?? null : null);
  });
  protected readonly attributes = computed(() => this.product()?.attributes as Record<string, string> | undefined);

  constructor() {
    // Đổi sản phẩm → reset lựa chọn
    effect(() => {
      this.product();
      this.selection.set({});
      this.quantity.set(1);
      this.activeImage.set(null);
    });
    // Title/meta cho SEO (SSR); dịch lại khi đổi ngôn ngữ
    effect(() => {
      const p = this.product();
      this.language.lang();
      if (p) {
        this.title.setTitle(this.transloco.translate('title.product', { name: p.name }));
        this.meta.updateTag({ name: 'description', content: (p.description ?? p.name).slice(0, 160) });
      } else if (this.state().status === 'not-found') {
        this.title.setTitle(this.transloco.translate('title.notFound'));
      }
    });
  }

  protected isAvailable(optionName: string, value: string): boolean {
    const p = this.product();
    return !!p && isValueAvailable(p.variants, this.selection(), optionName, value);
  }

  protected select(optionName: string, value: string): void {
    this.selection.update((s) => toggleValue(s, optionName, value));
    this.activeImage.set(null);
    this.quantity.update((q) => Math.max(1, Math.min(q, this.stock() || 1)));
  }

  protected changeQuantity(delta: number): void {
    this.setQuantity(this.quantity() + delta);
  }

  protected setQuantity(value: number): void {
    const max = this.selectedVariant()?.stock ?? this.stock();
    this.quantity.set(Math.max(1, Math.min(Number.isFinite(value) ? Math.floor(value) : 1, Math.max(max, 1))));
  }

  /** Thêm SKU đang chọn vào giỏ; `buyNow` = thêm rồi chuyển tới giỏ hàng. */
  protected addToCart(buyNow = false): void {
    const variant = this.selectedVariant();
    if (!variant) {
      this.toast('warn', 'productDetail.selectVariant');
      return;
    }
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/auth/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.adding.set(true);
    this.cartApi.add(variant.id, this.quantity()).subscribe({
      next: (cart) => {
        this.adding.set(false);
        this.cartStore.setCount(cart.itemCount);
        if (buyNow) {
          void this.router.navigate(['/cart']);
        } else {
          this.toast('success', 'productDetail.addedToCart');
        }
      },
      error: (err) => {
        this.adding.set(false);
        const e = ApiError.from(err);
        this.toast('error', errorKey(err), { count: e.fieldError('quantity') ?? 0 });
      },
    });
  }

  private toast(severity: string, key: string, params?: Record<string, unknown>): void {
    this.messages.add({ severity, summary: this.transloco.translate(key, params) });
  }
}
