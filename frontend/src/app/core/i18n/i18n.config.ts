import { Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';

export const LANGS = ['vi', 'en'] as const;
export type Lang = (typeof LANGS)[number];
export const DEFAULT_LANG: Lang = 'vi';
/** Cookie lưu ngôn ngữ người dùng chọn (đọc được cả khi SSR). */
export const LANG_COOKIE = 'lang';

/** Locale dùng cho Intl (định dạng số/tiền) theo ngôn ngữ giao diện. */
export const INTL_LOCALE: Record<Lang, string> = { vi: 'vi-VN', en: 'en-US' };

export function isLang(value: unknown): value is Lang {
  return typeof value === 'string' && (LANGS as readonly string[]).includes(value);
}

/**
 * Nạp file dịch bằng dynamic import: mỗi ngôn ngữ là một chunk riêng, chạy được cả SSR lẫn trình duyệt
 * mà không cần gọi HTTP.
 */
@Injectable({ providedIn: 'root' })
export class TranslocoJsonLoader implements TranslocoLoader {
  getTranslation(lang: string): Promise<Translation> {
    const loaders: Record<Lang, () => Promise<{ default: Translation }>> = {
      vi: () => import('../../../i18n/vi.json'),
      en: () => import('../../../i18n/en.json'),
    };
    return loaders[isLang(lang) ? lang : DEFAULT_LANG]().then((m) => m.default);
  }
}
