import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthStore } from '../../core/auth/auth.store';

@Component({
  selector: 'app-account-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoDirective],
  template: `
    <div *transloco="let t; prefix: 'account'" class="flex flex-col gap-4 md:flex-row">
      <aside class="md:w-48 md:shrink-0" [attr.aria-label]="t('nav')">
        <p class="mb-3 hidden items-center gap-2 border-b border-gray-200 pb-3 md:flex">
          <i class="pi pi-user rounded-full bg-gray-200 p-3 text-gray-500" aria-hidden="true"></i>
          <span class="truncate font-medium">{{ auth.user()?.fullName }}</span>
        </p>
        <nav class="flex gap-2 overflow-x-auto text-sm md:flex-col md:gap-0">
          @for (link of links; track link.path) {
            <a [routerLink]="link.path" routerLinkActive="!text-brand-500 font-medium"
               class="flex shrink-0 items-center gap-2 rounded-sm bg-white px-3 py-2 text-gray-700 md:bg-transparent md:px-0">
              <i class="pi text-brand-500" [class]="link.icon" aria-hidden="true"></i>{{ t(link.labelKey) }}
            </a>
          }
        </nav>
      </aside>
      <section class="min-w-0 flex-1 rounded-sm bg-white p-4 shadow-sm md:p-6">
        <router-outlet />
      </section>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class AccountLayout {
  protected readonly auth = inject(AuthStore);
  protected readonly links = [
    { path: '/account/profile', labelKey: 'profileLink', icon: 'pi-id-card' },
    { path: '/account/addresses', labelKey: 'addressesLink', icon: 'pi-map-marker' },
    { path: '/account/orders', labelKey: 'ordersLink', icon: 'pi-file' },
  ];
}
