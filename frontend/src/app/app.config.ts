import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  provideRouter,
  TitleStrategy,
  withComponentInputBinding,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideTransloco } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { errorInterceptor } from './core/api/error.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthStore } from './core/auth/auth.store';
import { DEFAULT_LANG, LANGS, TranslocoJsonLoader } from './core/i18n/i18n.config';
import { LanguageService } from './core/i18n/language.service';
import { TranslatedTitleStrategy } from './core/i18n/translated-title.strategy';
import { RealtimeService } from './core/realtime/realtime.service';
import { ShopviPreset } from './core/theme/shopvi-preset';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
    provideClientHydration(withEventReplay()),
    provideTransloco({
      config: {
        availableLangs: [...LANGS],
        defaultLang: DEFAULT_LANG,
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
        missingHandler: { logMissingKey: true },
      },
      loader: TranslocoJsonLoader,
    }),
    // Nạp ngôn ngữ (cookie `lang`) trước khi render để SSR và hydrate cùng một ngôn ngữ
    provideAppInitializer(() => inject(LanguageService).init()),
    // errorInterceptor bọc ngoài cùng để authInterceptor nhận HttpErrorResponse gốc;
    // interceptor theo môi trường (mock backend) nằm trong cùng, ngay trước khi gửi request
    provideHttpClient(withFetch(), withInterceptors([errorInterceptor, authInterceptor, ...environment.interceptors])),
    // Khôi phục phiên đăng nhập từ cookie refresh (không chặn render; guard chờ AuthStore.initialized)
    provideAppInitializer(() => {
      inject(AuthStore).init().subscribe();
    }),
    { provide: RealtimeService, useClass: environment.realtime },
    MessageService,
    providePrimeNG({
      theme: {
        preset: ShopviPreset,
        options: {
          // Giao diện sáng; class này không được gắn nên dark mode tắt
          darkModeSelector: '.app-dark',
          cssLayer: { name: 'primeng', order: 'theme, base, primeng, components, utilities' },
        },
      },
    }),
  ],
};
