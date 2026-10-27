import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Address, AddressInput } from '../../core/api/api.models';
import { VN_PHONE } from '../validators';
import { DivisionsService } from '../divisions.service';

/** Form thêm/sửa địa chỉ: chọn tỉnh/thành → xã/phường (địa giới 2 cấp). */
@Component({
  selector: 'app-address-form',
  imports: [ReactiveFormsModule, TranslocoDirective, TranslocoPipe, InputText, Select, Checkbox, ButtonDirective],
  template: `
    <form *transloco="let t; prefix: 'address.form'" [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4" novalidate>
      <div class="grid gap-4 sm:grid-cols-2">
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('receiverName') }}</span>
          <input pInputText formControlName="receiverName" autocomplete="name" />
          @if (showError('receiverName')) {
            <small class="text-red-600">{{ t('receiverNameError') }}</small>
          }
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('phone') }}</span>
          <input pInputText type="tel" formControlName="phone" autocomplete="tel" />
          @if (showError('phone')) {
            <small class="text-red-600">{{ 'validation.phone' | transloco }}</small>
          }
        </label>
      </div>

      <div class="flex flex-col gap-1">
        <label for="province" class="text-sm text-gray-600">{{ t('province') }}</label>
        <p-select inputId="province" formControlName="provinceCode" [options]="provinces()" optionLabel="name"
                  optionValue="code" [filter]="true" filterBy="name" [placeholder]="t('provincePlaceholder')"
                  [emptyFilterMessage]="'common.noResults' | transloco" styleClass="w-full" appendTo="body" />
        @if (showError('provinceCode')) {
          <small class="text-red-600">{{ t('provinceError') }}</small>
        }
      </div>
      <div class="flex flex-col gap-1">
        <label for="ward" class="text-sm text-gray-600">{{ t('ward') }}</label>
        <p-select inputId="ward" formControlName="wardCode" [options]="wards()" optionLabel="name" optionValue="code"
                  [filter]="true" filterBy="name" [placeholder]="t('wardPlaceholder')" [emptyFilterMessage]="'common.noResults' | transloco"
                  [virtualScroll]="wards().length > 100" [virtualScrollItemSize]="38" styleClass="w-full" appendTo="body" />
        @if (showError('wardCode')) {
          <small class="text-red-600">{{ t('wardError') }}</small>
        }
      </div>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-gray-600">{{ t('detail') }}</span>
        <input pInputText formControlName="detail" [placeholder]="t('detailPlaceholder')" autocomplete="street-address" />
        @if (showError('detail')) {
          <small class="text-red-600">{{ t('detailError') }}</small>
        }
      </label>
      @if (showDefault()) {
        <label class="flex items-center gap-2 text-sm" [class.opacity-60]="lockDefault()">
          <p-checkbox formControlName="isDefault" [binary]="true" />
          {{ t('setDefault') }}
        </label>
      }

      <div class="flex justify-end gap-2">
        <button pButton type="button" [text]="true" severity="secondary" (click)="cancelled.emit()">{{ 'common.back' | transloco }}</button>
        <button pButton type="submit" [disabled]="saving()">{{ (saving() ? 'common.saving' : 'common.done') | transloco }}</button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressForm {
  private readonly fb = inject(NonNullableFormBuilder);

  /** Địa chỉ đang sửa (null = thêm mới). */
  readonly address = input<Address | null>(null);
  readonly saving = input(false);
  /** Ẩn ô "đặt làm mặc định" (vd. địa chỉ lấy hàng của shop). */
  readonly showDefault = input(true);
  readonly saved = output<AddressInput>();
  readonly cancelled = output<void>();

  protected readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });
  protected readonly form = this.fb.group({
    receiverName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', [Validators.required, Validators.pattern(VN_PHONE)]],
    provinceCode: ['', Validators.required],
    wardCode: ['', Validators.required],
    detail: ['', [Validators.required, Validators.maxLength(255)]],
    isDefault: [false],
  });
  private readonly submitted = signal(false);
  private readonly provinceCode = toSignal(this.form.controls.provinceCode.valueChanges, { initialValue: '' });
  protected readonly wards = computed(() => this.provinces().find((p) => p.code === this.provinceCode())?.wards ?? []);
  /** Địa chỉ đang là mặc định thì không bỏ mặc định được (phải chọn địa chỉ khác làm mặc định). */
  protected readonly lockDefault = computed(() => this.address()?.isDefault ?? false);

  constructor() {
    effect(() => {
      const a = this.address();
      this.form.reset({
        receiverName: a?.receiverName ?? '',
        phone: a?.phone ?? '',
        provinceCode: a?.provinceCode ?? '',
        wardCode: a?.wardCode ?? '',
        detail: a?.detail ?? '',
        isDefault: a?.isDefault ?? false,
      });
      if (a?.isDefault) {
        this.form.controls.isDefault.disable();
      } else {
        this.form.controls.isDefault.enable();
      }
      this.submitted.set(false);
    });
    // Đổi tỉnh → xóa xã đã chọn nếu không thuộc tỉnh mới
    this.form.controls.provinceCode.valueChanges.subscribe((code) => {
      const ward = this.form.controls.wardCode.value;
      const province = this.provinces().find((p) => p.code === code);
      if (ward && !province?.wards.some((w) => w.code === ward)) {
        this.form.controls.wardCode.setValue('');
      }
    });
  }

  protected showError(control: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.touched || this.submitted());
  }

  protected submit(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.saved.emit({
      receiverName: v.receiverName.trim(),
      phone: v.phone.trim(),
      provinceCode: v.provinceCode,
      wardCode: v.wardCode,
      detail: v.detail.trim(),
      isDefault: v.isDefault,
    });
  }
}
