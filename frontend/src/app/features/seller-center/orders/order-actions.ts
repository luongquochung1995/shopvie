import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';
import { errorKey } from '../../../core/api/api-error';
import { SellerAction, SellerOrderDetail } from '../../../core/api/api.models';
import { SellerApi } from '../../../core/api/seller.api';

/** Nút thao tác của seller với một đơn (xác nhận / giao vận chuyển / đã giao / hủy kèm lý do). */
@Component({
  selector: 'app-seller-order-actions',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Dialog, Textarea],
  template: `
    <ng-container *transloco="let t; prefix: 'seller.orders'">
      <div class="flex flex-wrap justify-end gap-2">
        @for (action of actions(); track action) {
          @if (action === 'CANCEL') {
            <button pButton type="button" size="small" [outlined]="true" severity="secondary" [disabled]="busy()"
                    (click)="openCancel()">{{ t('action.CANCEL') }}</button>
          } @else {
            <button pButton type="button" size="small" [disabled]="busy()" (click)="perform(action)">{{ t('action.' + action) }}</button>
          }
        }
      </div>

      <p-dialog [header]="t('cancelTitle')" [visible]="cancelOpen()" (visibleChange)="cancelOpen.set($event)" [modal]="true"
                [draggable]="false" [style]="{ width: '28rem' }" [breakpoints]="{ '640px': '95vw' }">
        <p class="mb-2 text-sm text-gray-600">{{ t('cancelHint') }}</p>
        <div class="mb-2 flex flex-wrap gap-2">
          @for (r of quickReasons; track r) {
            <button type="button" class="rounded-sm border border-gray-300 px-2 py-0.5 text-xs hover:border-brand-500 hover:text-brand-500"
                    (click)="reason.set(t('reasons.' + r))">{{ t('reasons.' + r) }}</button>
          }
        </div>
        <textarea pTextarea class="w-full" rows="3" maxlength="500" [attr.aria-label]="t('cancelReason')"
                  [ngModel]="reason()" (ngModelChange)="reason.set($event)"></textarea>
        <ng-template #footer>
          <button pButton type="button" [text]="true" severity="secondary" (click)="cancelOpen.set(false)">{{ t('keep') }}</button>
          <button pButton type="button" severity="danger" [disabled]="busy() || !reason().trim()" (click)="perform('CANCEL')">
            {{ t('confirmCancel') }}
          </button>
        </ng-template>
      </p-dialog>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerOrderActions {
  private readonly api = inject(SellerApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  readonly code = input.required<string>();
  readonly actions = input.required<SellerAction[]>();
  readonly changed = output<SellerOrderDetail>();

  protected readonly busy = signal(false);
  protected readonly cancelOpen = signal(false);
  protected readonly reason = signal('');
  protected readonly quickReasons = ['outOfStock', 'cannotContact', 'buyerRequest'];

  protected openCancel(): void {
    this.reason.set('');
    this.cancelOpen.set(true);
  }

  protected perform(action: SellerAction): void {
    this.busy.set(true);
    this.api.performOrderAction(this.code(), action, action === 'CANCEL' ? this.reason().trim() : null).subscribe({
      next: (order) => {
        this.busy.set(false);
        this.cancelOpen.set(false);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('seller.orders.done.' + action) });
        this.changed.emit(order);
      },
      error: (err) => {
        this.busy.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
