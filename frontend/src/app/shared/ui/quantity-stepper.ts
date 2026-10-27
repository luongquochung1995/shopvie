import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** Ô số lượng [−] n [+] giới hạn trong [1, max]. */
@Component({
  selector: 'app-quantity-stepper',
  imports: [TranslocoPipe],
  template: `
    <span class="inline-flex border border-gray-300">
      <button type="button" class="size-8 border-r border-gray-300 disabled:opacity-40"
              [attr.aria-label]="'productDetail.decrease' | transloco" [disabled]="disabled() || value() <= 1"
              (click)="emit(value() - 1)">−</button>
      <input type="number" inputmode="numeric" min="1" class="w-12 text-center outline-none"
             [attr.aria-label]="'productDetail.quantity' | transloco" [value]="value()" [disabled]="disabled()"
             (change)="emit(+$any($event.target).value, $event)" />
      <button type="button" class="size-8 border-l border-gray-300 disabled:opacity-40"
              [attr.aria-label]="'productDetail.increase' | transloco" [disabled]="disabled() || value() >= max()"
              (click)="emit(value() + 1)">+</button>
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuantityStepper {
  readonly value = input.required<number>();
  readonly max = input(999);
  readonly disabled = input(false);
  readonly valueChange = output<number>();

  protected emit(raw: number, event?: Event): void {
    const next = Math.max(1, Math.min(Number.isFinite(raw) ? Math.floor(raw) : 1, Math.max(this.max(), 1)));
    if (event) {
      // Đưa ô nhập về giá trị hợp lệ nếu người dùng gõ quá giới hạn
      (event.target as HTMLInputElement).value = String(next);
    }
    if (next !== this.value()) {
      this.valueChange.emit(next);
    }
  }
}
