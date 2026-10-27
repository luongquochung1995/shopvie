import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { errorKey } from '../../core/api/api-error';
import { AuthStore } from '../../core/auth/auth.store';
import { safeReturnUrl } from './safe-return-url';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, InputText, ButtonDirective, Message],
  template: `
    <section *transloco="let t" class="mx-auto my-4 w-full max-w-md rounded-sm bg-white p-6 shadow-sm md:my-10 md:p-8">
      <h1 class="mb-6 text-xl font-medium">{{ t('auth.login.title') }}</h1>
      @if (errorKey(); as key) {
        <p-message severity="error" styleClass="mb-4" [text]="t(key)" />
      }
      <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4" novalidate>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('auth.email') }}</span>
          <input pInputText type="email" formControlName="email" autocomplete="email"
                 [class.ng-dirty]="submitted()" />
          @if (submitted() && form.controls.email.invalid) {
            <small class="text-red-600">{{ t('validation.email') }}</small>
          }
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-sm text-gray-600">{{ t('auth.password') }}</span>
          <span class="relative">
            <input pInputText [type]="showPassword() ? 'text' : 'password'" formControlName="password"
                   autocomplete="current-password" class="w-full pr-10" [class.ng-dirty]="submitted()" />
            <button type="button" class="absolute inset-y-0 right-0 px-3 text-gray-500"
                    [attr.aria-label]="t(showPassword() ? 'auth.hidePassword' : 'auth.showPassword')"
                    (click)="showPassword.set(!showPassword())">
              <i class="pi" [class]="showPassword() ? 'pi-eye-slash' : 'pi-eye'" aria-hidden="true"></i>
            </button>
          </span>
          @if (submitted() && form.controls.password.invalid) {
            <small class="text-red-600">{{ t('validation.passwordRequired') }}</small>
          }
        </label>
        <button pButton type="submit" class="w-full justify-center" [disabled]="loading()">
          {{ t(loading() ? 'auth.login.submitting' : 'auth.login.submit') }}
        </button>
      </form>
      <p class="mt-6 text-center text-sm text-gray-500">
        {{ t('auth.login.newHere') }}
        <a routerLink="/auth/register" [queryParams]="{ returnUrl: returnUrl() }" class="font-medium text-brand-500">{{ t('auth.register.link') }}</a>
      </p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Login {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Query param ?returnUrl=... (component input binding). */
  readonly returnUrl = input<string>();

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly submitted = signal(false);
  protected readonly loading = signal(false);
  /** Key dịch của lỗi đang hiển thị. */
  protected readonly errorKey = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.errorKey.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password).subscribe({
      next: () => this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (err) => {
        this.errorKey.set(errorKey(err));
        this.loading.set(false);
      },
    });
  }
}
