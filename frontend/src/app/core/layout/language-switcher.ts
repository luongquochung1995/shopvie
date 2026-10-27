import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LANGS } from '../i18n/i18n.config';
import { LanguageService } from '../i18n/language.service';

/** Chuyển ngôn ngữ VI | EN (không tải lại trang, lưu vào cookie). */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslocoPipe],
  template: `
    <span class="inline-flex items-center gap-1" role="group" [attr.aria-label]="'language.label' | transloco">
      <i class="pi pi-globe" aria-hidden="true"></i>
      @for (lang of langs; track lang; let last = $last) {
        <button type="button" class="uppercase hover:opacity-80" [class.font-bold]="language.lang() === lang"
                [class.underline]="language.lang() === lang" [attr.aria-pressed]="language.lang() === lang"
                [attr.lang]="lang" [attr.title]="'language.' + lang | transloco" (click)="language.use(lang)">
          {{ lang }}
        </button>
        @if (!last) {
          <span class="opacity-60" aria-hidden="true">|</span>
        }
      }
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguageSwitcher {
  protected readonly language = inject(LanguageService);
  protected readonly langs = LANGS;
}
