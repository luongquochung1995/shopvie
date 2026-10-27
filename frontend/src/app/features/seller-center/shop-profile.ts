import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { errorKey } from '../../core/api/api-error';
import { Address, AddressInput, ShopInfoInput } from '../../core/api/api.models';
import { SellerApi } from '../../core/api/seller.api';
import { DivisionsService, formatAddressLine } from '../../shared/divisions.service';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { AddressForm } from '../../shared/ui/address-form';
import { SellerShopStore } from './seller-shop.store';
import { ShopInfoForm } from './shop-info-form';
import { ShopStatusTag } from './shop-status-tag';

/**
 * Hồ sơ shop: trạng thái duyệt, thông tin shop (shop bị từ chối được gửi duyệt lại khi lưu) và địa chỉ lấy hàng
 * (dùng tính phí ship và tạo vận đơn).
 */
@Component({
  selector: 'app-shop-profile',
  imports: [TranslocoDirective, ButtonDirective, Dialog, DateTimePipe, ShopInfoForm, ShopStatusTag, AddressForm],
  template: `
    <div *transloco="let t; prefix: 'seller.shop'" class="flex flex-col gap-4">
      <section class="rounded-sm bg-white p-4 shadow-sm md:p-6">
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
      </section>

      @if (store.shop(); as shop) {
        <section class="rounded-sm bg-white p-4 shadow-sm md:p-6" aria-labelledby="pickup-title">
          <div class="mb-2 flex items-center justify-between gap-2">
            <h2 id="pickup-title" class="text-lg font-medium">{{ t('pickup.title') }}</h2>
            <button pButton type="button" size="small" [outlined]="true" (click)="pickupOpen.set(true)">
              {{ t(shop.pickupAddress ? 'pickup.edit' : 'pickup.add') }}
            </button>
          </div>
          @if (shop.pickupAddress; as p) {
            <p class="text-sm"><b>{{ p.name }}</b><span class="mx-2 text-gray-300">|</span><span class="text-gray-500">{{ p.phone }}</span></p>
            <p class="text-sm text-gray-600">{{ pickupLine() }}</p>
          } @else {
            <p class="rounded-sm bg-amber-50 p-3 text-sm text-amber-900">{{ t('pickup.missing') }}</p>
          }
          <p class="mt-2 text-xs text-gray-400">{{ t('pickup.hint') }}</p>
        </section>
      }

      <p-dialog *transloco="let t; prefix: 'seller.shop.pickup'" [header]="t('title')" [visible]="pickupOpen()"
                (visibleChange)="pickupOpen.set($event)" [modal]="true" [draggable]="false"
                [style]="{ width: '36rem' }" [breakpoints]="{ '640px': '95vw' }">
        @if (pickupOpen()) {
          <app-address-form [address]="pickupAsAddress()" [showDefault]="false" [saving]="savingPickup()"
                            (saved)="savePickup($event)" (cancelled)="pickupOpen.set(false)" />
        }
      </p-dialog>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ShopProfile {
  private readonly api = inject(SellerApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });
  protected readonly store = inject(SellerShopStore);
  protected readonly saving = signal(false);
  protected readonly savingPickup = signal(false);
  protected readonly pickupOpen = signal(false);

  protected readonly pickupLine = computed(() => {
    const p = this.store.shop()?.pickupAddress;
    return p ? formatAddressLine(this.provinces(), p) : '';
  });
  /** Địa chỉ lấy hàng dưới dạng địa chỉ để dùng lại form sổ địa chỉ. */
  protected readonly pickupAsAddress = computed<Address | null>(() => {
    const p = this.store.shop()?.pickupAddress;
    return p
      ? { id: 0, receiverName: p.name, phone: p.phone, provinceCode: p.provinceCode, districtCode: null, wardCode: p.wardCode, detail: p.detail, isDefault: false }
      : null;
  });

  protected save(input: ShopInfoInput): void {
    const wasRejected = this.store.shop()?.status === 'REJECTED';
    this.saving.set(true);
    this.api.updateShop(input).subscribe({
      next: (shop) => {
        this.store.set(shop);
        this.saving.set(false);
        this.toast('success', wasRejected ? 'seller.shop.resubmitted' : 'seller.shop.saved');
      },
      error: (err) => {
        this.saving.set(false);
        this.toast('error', errorKey(err));
      },
    });
  }

  protected savePickup(input: AddressInput): void {
    this.savingPickup.set(true);
    this.api
      .updatePickupAddress({
        name: input.receiverName,
        phone: input.phone,
        provinceCode: input.provinceCode,
        wardCode: input.wardCode,
        detail: input.detail,
      })
      .subscribe({
        next: (shop) => {
          this.store.set(shop);
          this.savingPickup.set(false);
          this.pickupOpen.set(false);
          this.toast('success', 'seller.shop.pickup.saved');
        },
        error: (err) => {
          this.savingPickup.set(false);
          this.toast('error', errorKey(err));
        },
      });
  }

  private toast(severity: string, key: string): void {
    this.messages.add({ severity, summary: this.transloco.translate(key) });
  }
}
