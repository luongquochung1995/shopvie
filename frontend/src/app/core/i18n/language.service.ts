import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, REQUEST } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { DEFAULT_LANG, INTL_LOCALE, isLang, Lang, LANG_COOKIE } from './i18n.config';

const ONE_YEAR_SECONDS = 365 * 24 * 60 * 60;

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);
  private readonly request = inject(REQUEST, { optional: true });
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  readonly lang = computed(() => (isLang(this.transloco.activeLang()) ? this.transloco.activeLang() : DEFAULT_LANG) as Lang);
  readonly intlLocale = computed(() => INTL_LOCALE[this.lang()]);

  /** Chạy lúc khởi động (app initializer): nạp xong file dịch rồi mới render để SSR không ra chuỗi rỗng. */
  async init(): Promise<void> {
    await this.use(this.detect(), false);
  }

  async use(lang: Lang, persist = true): Promise<void> {
    await firstValueFrom(this.transloco.load(lang));
    this.transloco.setActiveLang(lang);
    this.document.documentElement.lang = lang;
    if (persist && this.isBrowser) {
      this.document.cookie = `${LANG_COOKIE}=${lang}; path=/; max-age=${ONE_YEAR_SECONDS}; SameSite=Lax`;
    }
  }

  /** Cookie `lang` (trình duyệt: document.cookie, SSR: header Cookie của request), mặc định tiếng Việt. */
  private detect(): Lang {
    const cookies = this.isBrowser ? this.document.cookie : (this.request?.headers.get('cookie') ?? '');
    const value = cookies
      .split(';')
      .map((c) => c.trim().split('='))
      .find(([name]) => name === LANG_COOKIE)?.[1];
    return isLang(value) ? value : DEFAULT_LANG;
  }
}
