import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';

/** Hộp thoại nhập lý do khóa (tài khoản / sản phẩm). */
@Component({
  selector: 'app-moderation-dialog',
  imports: [FormsModule, TranslocoPipe, ButtonDirective, Dialog, Textarea],
  template: `
    <p-dialog [header]="header()" [visible]="visible()" (visibleChange)="!$event && cancelled.emit()" [modal]="true" [draggable]="false"
              [style]="{ width: '28rem' }" [breakpoints]="{ '640px': '95vw' }">
      <textarea pTextarea class="w-full" rows="3" maxlength="500" [placeholder]="'admin.reasonPlaceholder' | transloco"
                [attr.aria-label]="'admin.reasonPlaceholder' | transloco" [ngModel]="reason()" (ngModelChange)="reason.set($event)"></textarea>
      <ng-template #footer>
        <button pButton type="button" [text]="true" severity="secondary" (click)="cancelled.emit()">{{ 'common.back' | transloco }}</button>
        <button pButton type="button" severity="danger" [disabled]="!reason().trim() || busy()" (click)="confirmed.emit(reason().trim())">
          {{ confirmLabel() }}</button>
      </ng-template>
    </p-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModerationDialog {
  readonly header = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly visible = input(false);
  readonly busy = input(false);
  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  protected readonly reason = signal('');

  /** Gọi trước khi mở để xóa lý do cũ. */
  reset(): void {
    this.reason.set('');
  }
}
