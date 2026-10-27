import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { ApiError, errorKey } from '../../core/api/api-error';
import { AuthStore } from '../../core/auth/auth.store';
import { VN_PHONE } from '../../shared/validators';
import { safeReturnUrl } from './safe-return-url';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, InputText, ButtonDirective, Message],
  template: `
    <section *transloco="let t" class="mx-auto my-4 w-full max-w-md rounded-sm bg-white p-6 shadow-sm md:my-10 md:p-8">
      <h1 class="mb-6 text-xl font-medium">{{ t('auth.register.title') }}</h1>
      @if (errorKey(); as key) {
        <p-message severity="error" styleClass="mb-4" [text]="t(key)" />
      }
      <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4" novalidate>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('auth.fullName') }}</span>
          <input pInputText formControlName="fullName" autocomplete="name" [class.ng-dirty]="submitted()" />
          @if (submitted() && form.controls.fullName.invalid) {
            <small class="text-red-600">{{ t('validation.fullName') }}</small>
          }
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('auth.email') }}</span>
          <input pInputText type="email" formControlName="email" autocomplete="email" [class.ng-dirty]="submitted()" />
          @if (submitted() && form.controls.email.invalid) {
            <small class="text-red-600">{{ t(emailErrorKey() ?? 'validation.email') }}</small>
          }
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('auth.phone') }} <span class="text-gray-400">({{ t('common.optional') }})</span></span>
          <input pInputText type="tel" formControlName="phone" autocomplete="tel" [class.ng-dirty]="submitted()" />
          @if (submitted() && form.controls.phone.invalid) {
            <small class="text-red-600">{{ t('validation.phone') }}</small>
          }
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('auth.password') }}</span>
          <input pInputText type="password" formControlName="password" autocomplete="new-password"
                 [class.ng-dirty]="submitted()" />
          @if (submitted() && form.controls.password.invalid) {
            <small class="text-red-600">{{ t('validation.passwordLength') }}</small>
          }
        </label>
        <button pButton type="submit" class="w-full justify-center" [disabled]="loading()">
          {{ t(loading() ? 'auth.register.submitting' : 'auth.register.submit') }}
        </button>
      </form>
      <p class="mt-6 text-center text-sm text-gray-500">
        {{ t('auth.register.haveAccount') }}
        <a routerLink="/auth/login" [queryParams]="{ returnUrl: returnUrl() }" class="font-medium text-brand-500">{{ t('auth.login.link') }}</a>
      </p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Register {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.pattern(VN_PHONE)],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly submitted = signal(false);
  protected readonly loading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly emailErrorKey = signal<string | null>(null);

  protected submit(): void {
    this.submitted.set(true);
    this.emailErrorKey.set(null);
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.errorKey.set(null);
    const v = this.form.getRawValue();
    this.auth
      .register({ fullName: v.fullName.trim(), email: v.email.trim(), password: v.password, phone: v.phone.trim() || null })
      .subscribe({
        next: () => this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (err) => {
          this.loading.set(false);
          if (ApiError.from(err).code === 'EMAIL_ALREADY_EXISTS') {
            this.emailErrorKey.set(errorKey(err));
            this.form.controls.email.setErrors({ taken: true });
          } else {
            this.errorKey.set(errorKey(err));
          }
        },
      });
  }
}
