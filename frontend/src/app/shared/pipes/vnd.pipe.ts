import { inject, Pipe, PipeTransform } from '@angular/core';
import { LanguageService } from '../../core/i18n/language.service';

const formatters = new Map<string, Intl.NumberFormat>();
const numberFormat = (locale: string, options?: Intl.NumberFormatOptions) => {
  const key = locale + JSON.stringify(options ?? {});
  let f = formatters.get(key);
  if (!f) {
    f = new Intl.NumberFormat(locale, options);
    formatters.set(key, f);
  }
  return f;
};

/** 189000 → "₫189.000" (vi-VN) / "₫189,000" (en-US). Tiền luôn là VND. */
export function formatVnd(value: number | null | undefined, locale = 'vi-VN'): string {
  return value === null || value === undefined ? '' : `₫${numberFormat(locale).format(value)}`;
}

/** Khoảng giá: "₫139.000 - ₫259.000", hoặc một giá nếu min = max. */
export function formatVndRange(min: number, max: number, locale = 'vi-VN'): string {
  return min === max ? formatVnd(min, locale) : `${formatVnd(min, locale)} - ${formatVnd(max, locale)}`;
}

/** 1234 → "1,2k" (vi) / "1.2k" (en) — kiểu hiển thị "Đã bán" của sàn TMĐT. */
export function formatCompactCount(value: number | null | undefined, locale = 'vi-VN'): string {
  if (!value) {
    return '0';
  }
  return value >= 1000 ? `${numberFormat(locale, { maximumFractionDigits: 1 }).format(value / 1000)}k` : String(value);
}

/** Impure để cập nhật khi đổi ngôn ngữ (chi phí thấp: chỉ định dạng số). */
@Pipe({ name: 'vnd', pure: false })
export class VndPipe implements PipeTransform {
  private readonly language = inject(LanguageService);

  transform(value: number | null | undefined): string {
    return formatVnd(value, this.language.intlLocale());
  }
}

@Pipe({ name: 'compactCount', pure: false })
export class CompactCountPipe implements PipeTransform {
  private readonly language = inject(LanguageService);

  transform(value: number | null | undefined): string {
    return formatCompactCount(value, this.language.intlLocale());
  }
}
