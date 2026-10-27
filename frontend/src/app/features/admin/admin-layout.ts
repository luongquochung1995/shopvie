import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';

/** Khung khu quản trị: thanh tab giữa các trang admin. */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'admin.nav'">
      <nav class="mb-3 flex gap-1 overflow-x-auto rounded-sm bg-white px-2 text-sm shadow-sm" [attr.aria-label]="t('label')">
        @for (link of links; track link.path) {
          <a [routerLink]="link.path" routerLinkActive="!border-brand-500 text-brand-500" [routerLinkActiveOptions]="{ exact: link.exact ?? false }"
             class="flex shrink-0 items-center gap-2 border-b-2 border-transparent px-4 py-3 hover:text-brand-500">
            <i class="pi {{ link.icon }}" aria-hidden="true"></i>{{ t(link.labelKey) }}
          </a>
        }
      </nav>
      <router-outlet />
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class AdminLayout {
  protected readonly links = [
    { path: '/admin', labelKey: 'dashboard', icon: 'pi-chart-bar', exact: true },
    { path: '/admin/shops', labelKey: 'shops', icon: 'pi-shop' },
    { path: '/admin/products', labelKey: 'products', icon: 'pi-box' },
    { path: '/admin/categories', labelKey: 'categories', icon: 'pi-sitemap' },
    { path: '/admin/users', labelKey: 'users', icon: 'pi-users' },
    { path: '/admin/vouchers', labelKey: 'vouchers', icon: 'pi-ticket' },
    { path: '/admin/flash-sales', labelKey: 'flashSales', icon: 'pi-bolt' },
    { path: '/admin/banners', labelKey: 'banners', icon: 'pi-image' },
  ];
}
