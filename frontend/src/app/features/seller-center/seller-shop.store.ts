import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { MyShop } from '../../core/api/api.models';
import { SellerApi } from '../../core/api/seller.api';

/** Shop của seller đang đăng nhập, dùng chung giữa layout và các trang của Seller Center. */
@Injectable({ providedIn: 'root' })
export class SellerShopStore {
  private readonly api = inject(SellerApi);

  readonly shop = signal<MyShop | null>(null);
  /** Chỉ shop ACTIVE mới đăng bán và xử lý đơn được. */
  readonly active = computed(() => this.shop()?.status === 'ACTIVE');

  load(): Observable<MyShop> {
    return this.api.myShop().pipe(tap((shop) => this.shop.set(shop)));
  }

  set(shop: MyShop): void {
    this.shop.set(shop);
  }
}
