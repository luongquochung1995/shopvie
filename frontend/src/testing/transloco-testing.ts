import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import en from '../i18n/en.json';
import vi from '../i18n/vi.json';

/** Transloco cho unit test với file dịch thật (mặc định tiếng Việt, nạp sẵn cả 2 ngôn ngữ). */
export function translocoTesting(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { vi, en },
    translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi', reRenderOnLangChange: true },
    preloadLangs: true,
    ...options,
  });
}
