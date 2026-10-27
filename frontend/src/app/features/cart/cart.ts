import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Observable } from 'rxjs';
import { ApiError, errorKey } from '../../core/api/api-error';
import { Cart as CartModel, CartItem, CartShopGroup } from '../../core/api/api.models';
import { CartApi } from '../../core/api/cart.api';
import { CartStore } from '../../core/cart/cart.store';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { QuantityStepper } from '../../shared/ui/quantity-stepper';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, TranslocoDirective, TranslocoPipe, ButtonDirective, VndPipe, EmptyState, QuantityStepper],
  templateUrl: './cart.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Cart {
  private readonly api = inject(CartApi);
  private readonly store = inject(CartStore);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly cart = signal<CartModel | null>(null);
  protected readonly busy = signal(false);
  protected readonly loadFailed = signal(false);

  /** Các dòng còn mua được (checkbox chỉ áp dụng cho chúng). */
  private readonly availableItems = computed(() =>
    (this.cart()?.shops ?? []).flatMap((s) => s.items).filter((i) => i.available),
  );
  protected readonly allSelected = computed(() => {
    const items = this.availableItems();
    return items.length > 0 && items.every((i) => i.selected);
  });
  protected readonly selectedIds = computed(() =>
    (this.cart()?.shops ?? []).flatMap((s) => s.items).filter((i) => i.selected).map((i) => i.id),
  );

  constructor() {
    this.api.cart().subscribe({
      next: (cart) => this.apply(cart),
      error: () => this.loadFailed.set(true),
    });
  }

  protected shopSelected(group: CartShopGroup): boolean {
    const items = group.items.filter((i) => i.available);
    return items.length > 0 && items.every((i) => i.selected);
  }

  protected toggleItem(item: CartItem, selected: boolean): void {
    this.run(this.api.update(item.id, { selected }));
  }

  protected toggleShop(group: CartShopGroup, selected: boolean): void {
    const ids = group.items.filter((i) => i.available).map((i) => i.id);
    if (ids.length) {
      this.run(this.api.select(ids, selected));
    }
  }

  protected toggleAll(selected: boolean): void {
    const ids = this.availableItems().map((i) => i.id);
    if (ids.length) {
      this.run(this.api.select(ids, selected));
    }
  }

  protected changeQuantity(item: CartItem, quantity: number): void {
    if (quantity !== item.quantity) {
      this.run(this.api.update(item.id, { quantity }));
    }
  }

  protected remove(ids: number[]): void {
    if (ids.length) {
      this.run(this.api.remove(ids));
    }
  }

  protected checkout(): void {
    void this.router.navigate(['/checkout']);
  }

  private run(request: Observable<CartModel>): void {
    this.busy.set(true);
    request.subscribe({
      next: (cart) => {
        this.apply(cart);
        this.busy.set(false);
      },
      error: (err) => {
        this.busy.set(false);
        this.messages.add({
          severity: 'error',
          summary: this.transloco.translate(errorKey(err), { count: ApiError.from(err).fieldError('quantity') ?? 0 }),
        });
      },
    });
  }

  private apply(cart: CartModel): void {
    this.cart.set(cart);
    this.store.setCount(cart.itemCount);
  }
}
