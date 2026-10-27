import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { ApiError, errorKey } from '../../core/api/api-error';
import { Address, AddressInput, CheckoutPreview } from '../../core/api/api.models';
import { OrderApi } from '../../core/api/order.api';
import { UserApi } from '../../core/api/user.api';
import { CartStore } from '../../core/cart/cart.store';
import { DivisionsService, formatAddressLine } from '../../shared/divisions.service';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { AddressForm } from '../../shared/ui/address-form';

@Component({
  selector: 'app-checkout',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, Dialog, VndPipe, AddressForm],
  templateUrl: './checkout.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Checkout {
  private readonly orderApi = inject(OrderApi);
  private readonly userApi = inject(UserApi);
  private readonly cartStore = inject(CartStore);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });

  protected readonly preview = signal<CheckoutPreview | null>(null);
  protected readonly loading = signal(true);
  protected readonly placing = signal(false);
  /** shopId → lời nhắn */
  protected readonly notes = signal<Record<number, string>>({});
  protected readonly addresses = signal<Address[]>([]);
  protected readonly addressDialog = signal<'pick' | 'new' | null>(null);
  protected readonly savingAddress = signal(false);

  protected readonly addressLine = computed(() => {
    const a = this.preview()?.address;
    return a ? formatAddressLine(this.provinces(), a) : '';
  });
  protected readonly lineOf = (a: Address) => formatAddressLine(this.provinces(), a);
  protected readonly itemCount = computed(() =>
    (this.preview()?.groups ?? []).reduce((n, g) => n + g.items.reduce((m, i) => m + i.quantity, 0), 0),
  );

  constructor() {
    this.load();
  }

  protected setNote(shopId: number, note: string): void {
    this.notes.update((n) => ({ ...n, [shopId]: note }));
  }

  protected openAddressPicker(): void {
    this.userApi.addresses().subscribe({
      next: (list) => {
        this.addresses.set(list);
        this.addressDialog.set(list.length ? 'pick' : 'new');
      },
      error: (err) => this.toast('error', errorKey(err)),
    });
  }

  protected chooseAddress(a: Address): void {
    this.addressDialog.set(null);
    this.load(a.id);
  }

  protected createAddress(input: AddressInput): void {
    this.savingAddress.set(true);
    this.userApi.createAddress(input).subscribe({
      next: (a) => {
        this.savingAddress.set(false);
        this.addressDialog.set(null);
        this.load(a.id);
      },
      error: (err) => {
        this.savingAddress.set(false);
        this.toast('error', errorKey(err));
      },
    });
  }

  protected placeOrder(): void {
    const p = this.preview();
    if (!p?.address || p.hasUnavailableItems) {
      return;
    }
    this.placing.set(true);
    this.orderApi
      .placeOrder({ addressId: p.address.id, paymentMethod: 'COD', notes: this.notes(), expectedTotal: p.total })
      .subscribe({
        next: (result) => {
          this.cartStore.refresh();
          void this.router.navigate(['/checkout/success'], {
            queryParams: { orders: result.orders.map((o) => o.code).join(','), total: result.total },
          });
        },
        error: (err) => {
          this.placing.set(false);
          this.toast('error', errorKey(err));
          // Giá/tồn kho đã thay đổi: tải lại để người dùng xem số liệu mới
          if (['PRICE_CHANGED', 'OUT_OF_STOCK', 'PRODUCT_UNAVAILABLE'].includes(ApiError.from(err).code)) {
            this.load(p.address?.id);
          }
        },
      });
  }

  private load(addressId?: number | null): void {
    this.loading.set(true);
    this.orderApi.preview(addressId).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (ApiError.from(err).code === 'NO_ITEMS_SELECTED') {
          void this.router.navigate(['/cart']);
        } else {
          this.toast('error', errorKey(err));
        }
      },
    });
  }

  private toast(severity: string, key: string): void {
    this.messages.add({ severity, summary: this.transloco.translate(key) });
  }
}
