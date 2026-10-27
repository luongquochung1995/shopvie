import { inject, Pipe, PipeTransform } from '@angular/core';
import { LanguageService } from '../../core/i18n/language.service';

/** ISO-8601 → "26/10/2026 09:41" (vi) / "10/26/2026, 9:41 AM" (en), theo giờ máy người dùng. */
@Pipe({ name: 'dateTime', pure: false })
export class DateTimePipe implements PipeTransform {
  private readonly language = inject(LanguageService);

  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    return new Intl.DateTimeFormat(this.language.intlLocale(), { dateStyle: 'short', timeStyle: 'short' }).format(
      new Date(value),
    );
  }
}
