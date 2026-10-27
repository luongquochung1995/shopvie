import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { ApiError, errorKey } from '../../core/api/api-error';
import { Profile as ProfileModel } from '../../core/api/api.models';
import { UserApi } from '../../core/api/user.api';
import { AuthStore } from '../../core/auth/auth.store';
import { VN_PHONE } from '../../shared/validators';

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, TranslocoDirective, InputText, ButtonDirective],
  template: `
    <ng-container *transloco="let t">
      <h1 class="text-lg font-medium">{{ t('account.profile.title') }}</h1>
      <p class="mb-6 border-b border-gray-100 pb-4 text-sm text-gray-500">{{ t('account.profile.subtitle') }}</p>

      @if (profile(); as p) {
        <div class="flex flex-col-reverse gap-8 md:flex-row">
          <form [formGroup]="form" (ngSubmit)="save()" class="grid flex-1 grid-cols-1 gap-4 sm:grid-cols-[8rem_1fr] sm:items-center" novalidate>
            <span class="text-sm text-gray-500">{{ t('auth.email') }}</span>
            <span>{{ p.email }}</span>

            <label for="fullName" class="text-sm text-gray-500">{{ t('auth.fullName') }}</label>
            <div>
              <input pInputText id="fullName" formControlName="fullName" class="w-full" />
              @if (form.controls.fullName.invalid && form.controls.fullName.touched) {
                <small class="text-red-600">{{ t('validation.fullName') }}</small>
              }
            </div>

            <label for="phone" class="text-sm text-gray-500">{{ t('auth.phone') }}</label>
            <div>
              <input pInputText id="phone" type="tel" formControlName="phone" class="w-full" />
              @if (phoneErrorKey(); as key) {
                <small class="text-red-600">{{ t(key) }}</small>
              } @else if (form.controls.phone.invalid && form.controls.phone.touched) {
                <small class="text-red-600">{{ t('validation.phone') }}</small>
              }
            </div>

            <span></span>
            <div>
              <button pButton type="submit" [disabled]="saving()">{{ t(saving() ? 'common.saving' : 'common.save') }}</button>
            </div>
          </form>

          <div class="flex flex-col items-center gap-3 md:w-56 md:border-l md:border-gray-100">
            <span class="size-24 overflow-hidden rounded-full bg-gray-100">
              @if (avatarUrl()) {
                <img [src]="avatarUrl()" [alt]="t('account.profile.avatar')" class="size-full object-cover" />
              } @else {
                <i class="pi pi-user flex size-full items-center justify-center text-4xl text-gray-300" aria-hidden="true"></i>
              }
            </span>
            <label class="cursor-pointer rounded-sm border border-gray-300 px-4 py-1.5 text-sm hover:bg-gray-50">
              {{ t(uploading() ? 'account.profile.uploading' : 'account.profile.chooseImage') }}
              <input type="file" accept="image/jpeg,image/png,image/webp" class="sr-only" [disabled]="uploading()"
                     (change)="onAvatar($event)" />
            </label>
            <p class="whitespace-pre-line text-center text-xs text-gray-400">{{ t('account.profile.imageHint') }}</p>
          </div>
        </div>
      } @else if (loadErrorKey(); as key) {
        <p class="text-gray-500">{{ t(key) }}</p>
      }
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Profile {
  private readonly api = inject(UserApi);
  private readonly auth = inject(AuthStore);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', Validators.pattern(VN_PHONE)],
  });
  protected readonly profile = signal<ProfileModel | null>(null);
  protected readonly avatarUrl = signal<string | null>(null);
  protected readonly loadErrorKey = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly phoneErrorKey = signal<string | null>(null);

  constructor() {
    this.api.me().subscribe({
      next: (p) => this.load(p),
      error: (err) => this.loadErrorKey.set(errorKey(err)),
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.phoneErrorKey.set(null);
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api.updateMe({ fullName: v.fullName.trim(), phone: v.phone.trim() || null, avatarUrl: this.avatarUrl() }).subscribe({
      next: (p) => {
        this.load(p);
        this.auth.updateUser({ fullName: p.fullName });
        this.toast('success', 'account.profile.saved');
        this.saving.set(false);
      },
      error: (err) => {
        this.saving.set(false);
        if (ApiError.from(err).code === 'PHONE_ALREADY_EXISTS') {
          this.phoneErrorKey.set(errorKey(err));
        } else {
          this.toast('error', errorKey(err));
        }
      },
    });
  }

  protected onAvatar(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      this.toast('warn', 'errors.FILE_TOO_LARGE');
      return;
    }
    this.uploading.set(true);
    this.api.uploadImage(file).subscribe({
      next: (media) => {
        this.avatarUrl.set(media.url);
        this.uploading.set(false);
        this.toast('info', 'account.profile.uploaded', 'account.profile.uploadedHint');
      },
      error: (err) => {
        this.uploading.set(false);
        this.toast('error', errorKey(err));
      },
    });
  }

  private toast(severity: string, summaryKey: string, detailKey?: string): void {
    this.messages.add({
      severity,
      summary: this.transloco.translate(summaryKey),
      detail: detailKey ? this.transloco.translate(detailKey) : undefined,
    });
  }

  private load(p: ProfileModel): void {
    this.profile.set(p);
    this.avatarUrl.set(p.avatarUrl);
    this.form.setValue({ fullName: p.fullName, phone: p.phone ?? '' });
  }
}
