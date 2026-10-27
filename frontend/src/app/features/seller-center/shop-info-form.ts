import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Textarea } from 'primeng/textarea';
import { errorKey } from '../../core/api/api-error';
import { MyShop, ShopInfoInput } from '../../core/api/api.models';
import { UserApi } from '../../core/api/user.api';

const MAX_LOGO_BYTES = 5 * 1024 * 1024;

/** Form thông tin shop (tên, mô tả, logo) dùng khi đăng ký mở shop và khi sửa hồ sơ shop. */
@Component({
  selector: 'app-shop-info-form',
  imports: [ReactiveFormsModule, TranslocoDirective, InputText, Textarea, ButtonDirective],
  template: `
    <form *transloco="let t; prefix: 'seller.shopForm'" [formGroup]="form" (ngSubmit)="submit()"
          class="flex flex-col gap-4" novalidate>
      <div class="flex items-center gap-4">
        <span class="size-20 shrink-0 overflow-hidden rounded-full border border-gray-200 bg-gray-100">
          @if (logoUrl()) {
            <img [src]="logoUrl()" [alt]="t('logo')" class="size-full object-cover" />
          } @else {
            <i class="pi pi-shop flex size-full items-center justify-center text-3xl text-gray-300" aria-hidden="true"></i>
          }
        </span>
        <div class="flex flex-col gap-1">
          <label class="w-fit cursor-pointer rounded-sm border border-gray-300 px-4 py-1.5 text-sm hover:bg-gray-50">
            {{ t(uploading() ? 'uploading' : 'chooseLogo') }}
            <input type="file" accept="image/jpeg,image/png,image/webp" class="sr-only" [disabled]="uploading()"
                   (change)="onLogo($event)" />
          </label>
          <small class="text-gray-400">{{ t('logoHint') }}</small>
        </div>
      </div>

      <label class="flex flex-col gap-1">
        <span class="text-sm text-gray-600">{{ t('name') }}</span>
        <input pInputText formControlName="name" maxlength="100" [placeholder]="t('namePlaceholder')" />
        @if (form.controls.name.invalid && form.controls.name.touched) {
          <small class="text-red-600">{{ t('nameError') }}</small>
        }
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-gray-600">{{ t('description') }}</span>
        <textarea pTextarea formControlName="description" rows="4" maxlength="2000" [placeholder]="t('descriptionPlaceholder')"></textarea>
      </label>
      <div class="flex justify-end">
        <button pButton type="submit" [disabled]="saving() || uploading()">{{ saving() ? t('saving') : submitLabel() }}</button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopInfoForm {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly userApi = inject(UserApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  readonly shop = input<MyShop | null>(null);
  readonly saving = input(false);
  readonly submitLabel = input.required<string>();
  readonly saved = output<ShopInfoInput>();

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
    description: ['', Validators.maxLength(2000)],
  });
  protected readonly logoUrl = signal<string | null>(null);
  protected readonly uploading = signal(false);

  constructor() {
    effect(() => {
      const shop = this.shop();
      if (shop) {
        this.form.setValue({ name: shop.name, description: shop.description ?? '' });
        this.logoUrl.set(shop.logoUrl);
      }
    });
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.saved.emit({ name: v.name.trim(), description: v.description.trim() || null, logoUrl: this.logoUrl() });
  }

  protected onLogo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_LOGO_BYTES) {
      this.messages.add({ severity: 'warn', summary: this.transloco.translate('errors.FILE_TOO_LARGE') });
      return;
    }
    this.uploading.set(true);
    this.userApi.uploadImage(file).subscribe({
      next: (media) => {
        this.logoUrl.set(media.url);
        this.uploading.set(false);
      },
      error: (err) => {
        this.uploading.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}
