import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { ApiError, errorKey } from '../../core/api/api-error';
import { Address, AddressInput, CheckoutPreview, PaymentMethod, Voucher } from '../../core/api/api.models';
import { OrderApi } from '../../core/api/order.api';
import { PromotionApi } from '../../core/api/promotion.api';
import { UserApi } from '../../core/api/user.api';
import { CartStore } from '../../core/cart/cart.store';
import { DivisionsService, formatAddressLine } from '../../shared/divisions.service';
import { PaymentRedirect } from '../../shared/payment-redirect';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { VoucherValuePipe } from '../../shared/pipes/voucher-value.pipe';
import { AddressForm } from '../../shared/ui/address-form';

@Component({
  selector: 'app-checkout',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, Dialog, VndPipe, VoucherValuePipe, AddressForm],
  templateUrl: './checkout.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Checkout {
  private readonly orderApi = inject(OrderApi);
  private readonly userApi = inject(UserApi);
  private readonly promotionApi = inject(PromotionApi);
  private readonly cartStore = inject(CartStore);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly paymentRedirect = inject(PaymentRedirect);
  private readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });

  protected readonly preview = signal<CheckoutPreview | null>(null);
  protected readonly loading = signal(true);
  protected readonly placing = signal(false);
  /** shopId → lời nhắn */
  protected readonly notes = signal<Record<number, string>>({});
  protected readonly addresses = signal<Address[]>([]);
  protected readonly addressDialog = signal<'pick' | 'new' | null>(null);
  protected readonly savingAddress = signal(false);
  protected readonly paymentMethod = signal<PaymentMethod>('COD');
  /** Mã voucher đã áp (backend chuẩn hóa chữ hoa). */
  protected readonly voucherCodes = signal<string[]>([]);
  protected readonly voucherInput = signal('');
  protected readonly voucherError = signal<string | null>(null);
  protected readonly applyingVoucher = signal(false);
  protected readonly voucherDialog = signal(false);
  protected readonly availableVouchers = signal<Voucher[]>([]);

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

  protected applyVoucherInput(): void {
    const code = this.voucherInput().trim().toUpperCase();
    if (code && !this.voucherCodes().includes(code)) {
      this.applyVouchers([...this.voucherCodes(), code], () => this.voucherInput.set(''));
    }
  }

  protected toggleVoucher(v: Voucher): void {
    const codes = this.voucherCodes();
    if (codes.includes(v.code)) {
      this.removeVoucher(v.code);
      return;
    }
    // Mỗi đơn 1 voucher sàn và mỗi shop 1 voucher shop: chọn voucher mới thay voucher cùng loại
    const sameKind = this.availableVouchers().filter((x) => x.shopId === v.shopId).map((x) => x.code);
    this.applyVouchers([...codes.filter((c) => !sameKind.includes(c)), v.code], () => this.voucherDialog.set(false));
  }

  protected removeVoucher(code: string): void {
    this.applyVouchers(this.voucherCodes().filter((c) => c !== code));
  }

  protected openVoucherPicker(): void {
    const shopIds = (this.preview()?.groups ?? []).map((g) => g.shop.id);
    this.promotionApi.availableVouchers(shopIds).subscribe({
      next: (list) => {
        this.availableVouchers.set(list);
        this.voucherError.set(null);
        this.voucherDialog.set(true);
      },
      error: (err) => this.toast('error', errorKey(err)),
    });
  }

  /** Tính lại với bộ mã mới; mã không áp được → báo lý do, giữ nguyên số liệu cũ. */
  private applyVouchers(codes: string[], done?: () => void): void {
    this.applyingVoucher.set(true);
    this.voucherError.set(null);
    this.orderApi.preview(this.preview()?.address?.id, codes).subscribe({
      next: (preview) => {
        this.applyingVoucher.set(false);
        this.preview.set(preview);
        this.voucherCodes.set(preview.voucherCodes ?? []);
        done?.();
      },
      error: (err) => {
        this.applyingVoucher.set(false);
        this.voucherError.set(voucherErrorKey(err));
      },
    });
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
      .placeOrder({
        addressId: p.address.id,
        paymentMethod: this.paymentMethod(),
        notes: this.notes(),
        expectedTotal: p.total,
        voucherCodes: this.voucherCodes(),
      })
      .subscribe({
        next: (result) => {
          this.cartStore.refresh();
          if (result.paymentUrl) {
            // Thanh toán online: sang cổng thanh toán, cổng chuyển về /payment/result
            this.paymentRedirect.go(result.paymentUrl);
            return;
          }
          void this.router.navigate(['/checkout/success'], {
            queryParams: { orders: result.orders.map((o) => o.code).join(','), total: result.total },
          });
        },
        error: (err) => {
          this.placing.set(false);
          this.toast('error', errorKey(err));
          // Giá/tồn kho đã thay đổi: tải lại để người dùng xem số liệu mới
          const code = ApiError.from(err).code;
          if (['PRICE_CHANGED', 'OUT_OF_STOCK', 'PRODUCT_UNAVAILABLE'].includes(code) || code.startsWith('VOUCHER_')) {
            this.load(p.address?.id);
          }
        },
      });
  }

  private load(addressId?: number | null): void {
    this.loading.set(true);
    this.orderApi.preview(addressId, this.voucherCodes()).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.voucherCodes.set(preview.voucherCodes ?? []);
        if (!preview.paymentMethods.includes(this.paymentMethod())) {
          this.paymentMethod.set('COD');
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const code = ApiError.from(err).code;
        if (code.startsWith('VOUCHER_') && this.voucherCodes().length) {
          // Voucher không còn áp được (đổi địa chỉ, hết lượt...): bỏ voucher và tính lại
          this.voucherError.set(voucherErrorKey(err));
          this.voucherCodes.set([]);
          this.load(addressId);
        } else if (code === 'NO_ITEMS_SELECTED') {
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

/** Lý do voucher không áp được: `error.details[].message` (MIN_ORDER, EXHAUSTED...) nếu có, không thì mã lỗi. */
function voucherErrorKey(err: unknown): string {
  const e = ApiError.from(err);
  const reason = e.details[0]?.message;
  return e.code === 'VOUCHER_NOT_APPLICABLE' && reason ? `checkout.voucherReason.${reason}` : errorKey(err);
}
