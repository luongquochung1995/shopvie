import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { errorKey } from '../../core/api/api-error';
import { SellerShopStore } from './seller-shop.store';
import { ShopStatusTag } from './shop-status-tag';

/** Khung Seller Center: thanh điều hướng (sidebar trên desktop, tab cuộn ngang trên mobile) + trạng thái duyệt shop. */
@Component({
  selector: 'app-seller-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoDirective, TranslocoPipe, ShopStatusTag],
  template: `
    <div *transloco="let t; prefix: 'seller'" class="flex flex-col gap-4 md:flex-row">
      <aside class="md:w-52 md:shrink-0" [attr.aria-label]="t('nav.label')">
        <a routerLink="/seller-center/shop" class="mb-3 hidden items-center gap-2 border-b border-gray-200 pb-3 md:flex">
          <span class="size-10 shrink-0 overflow-hidden rounded-full bg-gray-200">
            @if (store.shop()?.logoUrl; as logo) {
              <img [src]="logo" alt="" class="size-full object-cover" />
            } @else {
              <i class="pi pi-shop flex size-full items-center justify-center text-gray-500" aria-hidden="true"></i>
            }
          </span>
          <span class="min-w-0">
            <span class="block truncate font-medium">{{ store.shop()?.name ?? t('nav.title') }}</span>
            @if (store.shop(); as shop) {
              <app-shop-status-tag [status]="shop.status" />
            }
          </span>
        </a>
        <nav class="flex gap-2 overflow-x-auto text-sm md:flex-col md:gap-0">
          @for (link of links; track link.path) {
            <a [routerLink]="link.path" routerLinkActive="!text-brand-500 font-medium" [routerLinkActiveOptions]="{ exact: link.exact }"
               class="flex shrink-0 items-center gap-2 rounded-sm bg-white px-3 py-2 text-gray-700 md:bg-transparent md:px-0">
              <i class="pi text-brand-500" [class]="link.icon" aria-hidden="true"></i>{{ t(link.labelKey) }}
            </a>
          }
        </nav>
      </aside>

      <section class="min-w-0 flex-1">
        @if (store.shop(); as shop) {
          @if (shop.status === 'PENDING') {
            <p class="mb-4 flex items-start gap-2 rounded-sm border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800" role="status">
              <i class="pi pi-clock mt-0.5" aria-hidden="true"></i>{{ t('banner.pending') }}
            </p>
          } @else if (shop.status === 'REJECTED') {
            <div class="mb-4 rounded-sm border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
              <p class="flex items-start gap-2"><i class="pi pi-times-circle mt-0.5" aria-hidden="true"></i>{{ t('banner.rejected') }}</p>
              @if (shop.rejectReason) {
                <p class="mt-1 pl-6">{{ t('banner.reason', { reason: shop.rejectReason }) }}</p>
              }
              <a routerLink="/seller-center/shop" class="mt-1 inline-block pl-6 font-medium underline">{{ t('banner.fixAndResubmit') }}</a>
            </div>
          } @else if (shop.status === 'BANNED') {
            <p class="mb-4 flex items-start gap-2 rounded-sm border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
              <i class="pi pi-ban mt-0.5" aria-hidden="true"></i>{{ t('banner.banned') }}
            </p>
          }
          <router-outlet />
        } @else if (loadErrorKey(); as key) {
          <p class="rounded-sm bg-white p-6 text-center text-gray-500 shadow-sm">{{ key | transloco }}</p>
        } @else {
          <p class="rounded-sm bg-white p-6 text-center text-gray-500 shadow-sm">{{ t('loading') }}</p>
        }
      </section>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class SellerLayout {
  protected readonly store = inject(SellerShopStore);
  protected readonly loadErrorKey = signal<string | null>(null);
  protected readonly links = [
    { path: '/seller-center', labelKey: 'nav.dashboard', icon: 'pi-chart-bar', exact: true },
    { path: '/seller-center/orders', labelKey: 'nav.orders', icon: 'pi-file', exact: false },
    { path: '/seller-center/products', labelKey: 'nav.products', icon: 'pi-box', exact: true },
    { path: '/seller-center/products/new', labelKey: 'nav.addProduct', icon: 'pi-plus-circle', exact: true },
    { path: '/seller-center/shop', labelKey: 'nav.shop', icon: 'pi-shop', exact: true },
  ];

  constructor() {
    this.store.load().subscribe({ error: (err) => this.loadErrorKey.set(errorKey(err)) });
  }
}
