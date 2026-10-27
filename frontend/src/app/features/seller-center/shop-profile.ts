import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { errorKey } from '../../core/api/api-error';
import { ShopInfoInput } from '../../core/api/api.models';
import { SellerApi } from '../../core/api/seller.api';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { SellerShopStore } from './seller-shop.store';
import { ShopInfoForm } from './shop-info-form';
import { ShopStatusTag } from './shop-status-tag';

/** Hồ sơ shop: trạng thái duyệt + sửa thông tin (shop bị từ chối được gửi duyệt lại khi lưu). */
@Component({
  selector: 'app-shop-profile',
  imports: [TranslocoDirective, DateTimePipe, ShopInfoForm, ShopStatusTag],
  template: `
    <div *transloco="let t; prefix: 'seller.shop'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <h1 class="text-lg font-medium">{{ t('title') }}</h1>
      @if (store.shop(); as shop) {
        <dl class="mb-6 grid grid-cols-[8rem_1fr] gap-y-1 border-b border-gray-100 pb-4 text-sm">
          <dt class="text-gray-500">{{ t('status') }}</dt>
          <dd><app-shop-status-tag [status]="shop.status" /></dd>
          <dt class="text-gray-500">{{ t('createdAt') }}</dt>
          <dd>{{ shop.createdAt | dateTime }}</dd>
        </dl>
        @if (shop.status === 'REJECTED') {
          <p class="mb-4 text-sm text-gray-600">{{ t('resubmitHint') }}</p>
        }
        <app-shop-info-form [shop]="shop" [saving]="saving()"
                            [submitLabel]="t(shop.status === 'REJECTED' ? 'resubmit' : 'save')" (saved)="save($event)" />
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ShopProfile {
  private readonly api = inject(SellerApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  protected readonly store = inject(SellerShopStore);
  protected readonly saving = signal(false);

  protected save(input: ShopInfoInput): void {
    const wasRejected = this.store.shop()?.status === 'REJECTED';
    this.saving.set(true);
    this.api.updateShop(input).subscribe({
      next: (shop) => {
        this.store.set(shop);
        this.saving.set(false);
        const key = wasRejected ? 'seller.shop.resubmitted' : 'seller.shop.saved';
        this.messages.add({ severity: 'success', summary: this.transloco.translate(key) });
      },
      error: (err) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
