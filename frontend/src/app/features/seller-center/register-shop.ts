import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { switchMap } from 'rxjs';
import { errorKey } from '../../core/api/api-error';
import { ShopInfoInput } from '../../core/api/api.models';
import { SellerApi } from '../../core/api/seller.api';
import { AuthStore } from '../../core/auth/auth.store';
import { SellerShopStore } from './seller-shop.store';
import { ShopInfoForm } from './shop-info-form';

/** Đăng ký mở shop: người mua điền thông tin → shop chờ duyệt, tài khoản thành người bán. */
@Component({
  selector: 'app-register-shop',
  imports: [RouterLink, TranslocoDirective, ButtonDirective, ShopInfoForm],
  template: `
    <div *transloco="let t; prefix: 'seller.register'" class="mx-auto max-w-2xl rounded-sm bg-white p-4 shadow-sm md:p-8">
      <h1 class="text-xl font-medium">{{ t('title') }}</h1>
      <p class="mb-6 text-sm text-gray-500">{{ t('subtitle') }}</p>

      @if (auth.role() === 'ADMIN') {
        <p class="rounded-sm bg-gray-50 p-4 text-sm text-gray-600">{{ t('adminNotAllowed') }}</p>
        <a pButton routerLink="/admin/shops" class="mt-4">{{ t('goAdmin') }}</a>
      } @else {
        <ol class="mb-6 grid gap-2 text-sm text-gray-600 sm:grid-cols-3">
          @for (step of steps; track step; let i = $index) {
            <li class="flex items-center gap-2 rounded-sm bg-brand-50/50 p-2">
              <span class="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-500 text-xs text-white">{{ i + 1 }}</span>
              {{ t(step) }}
            </li>
          }
        </ol>
        <app-shop-info-form [saving]="saving()" [submitLabel]="t('submit')" (saved)="register($event)" />
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class RegisterShop {
  private readonly api = inject(SellerApi);
  private readonly store = inject(SellerShopStore);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  protected readonly auth = inject(AuthStore);

  protected readonly saving = signal(false);
  protected readonly steps = ['step1', 'step2', 'step3'];

  protected register(input: ShopInfoInput): void {
    this.saving.set(true);
    this.api
      .registerShop(input)
      // Làm mới access token để nhận role SELLER mới
      .pipe(switchMap((shop) => this.auth.refresh().pipe(switchMap(() => [shop]))))
      .subscribe({
        next: (shop) => {
          this.store.set(shop);
          this.messages.add({ severity: 'success', summary: this.transloco.translate('seller.register.done') });
          this.router.navigateByUrl('/seller-center');
        },
        error: (err) => {
          this.saving.set(false);
          this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
        },
      });
  }
}
