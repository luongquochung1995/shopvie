import { isPlatformBrowser } from '@angular/common';
import { computed, inject, PLATFORM_ID } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withProps, withState } from '@ngrx/signals';
import { finalize, map, Observable, of, share, tap } from 'rxjs';
import { AuthApi, RegisterRequest } from '../api/auth.api';
import { AuthResponse, AuthUser, UserRole } from '../api/api.models';

/** Cờ "có phiên đăng nhập" (không chứa token) để biết có nên gọi /auth/refresh khi mở trang. */
const SESSION_FLAG = 'shopvi.hasSession';

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  /** true khi đã thử khôi phục phiên lúc khởi động (guard chờ cờ này). */
  initialized: boolean;
}

/**
 * Phiên đăng nhập: access token chỉ giữ trong bộ nhớ, refresh token nằm trong cookie httpOnly do backend quản lý.
 */
export const AuthStore = signalStore(
  { providedIn: 'root' },
  withState<AuthState>({ user: null, accessToken: null, initialized: false }),
  withProps(() => ({
    _api: inject(AuthApi),
    _isBrowser: isPlatformBrowser(inject(PLATFORM_ID)),
  })),
  withComputed(({ user }) => ({
    isLoggedIn: computed(() => user() !== null),
    role: computed<UserRole | null>(() => user()?.role ?? null),
  })),
  withMethods((store) => {
    let refreshInFlight: Observable<string> | null = null;
    const setSession = (res: AuthResponse) => {
      patchState(store, { user: res.user, accessToken: res.accessToken, initialized: true });
      if (store._isBrowser) {
        localStorage.setItem(SESSION_FLAG, '1');
      }
    };
    const clear = () => {
      patchState(store, { user: null, accessToken: null, initialized: true });
      if (store._isBrowser) {
        localStorage.removeItem(SESSION_FLAG);
      }
    };

    /** Làm mới access token; nhiều request 401 cùng lúc dùng chung một lần gọi /auth/refresh. */
    const refresh = (): Observable<string> => {
      if (!refreshInFlight) {
        refreshInFlight = store._api.refresh().pipe(
          tap({ next: setSession, error: clear }),
          map((res) => res.accessToken),
          finalize(() => (refreshInFlight = null)),
          share(),
        );
      }
      return refreshInFlight;
    };

    return {
      setSession,
      clear,
      refresh,
      login: (email: string, password: string) => store._api.login(email, password).pipe(tap(setSession)),
      register: (req: RegisterRequest) => store._api.register(req).pipe(tap(setSession)),
      logout: () =>
        store._api.logout().pipe(
          finalize(clear),
          map(() => undefined),
        ),
      updateUser: (patch: Partial<AuthUser>) => {
        const user = store.user();
        if (user) {
          patchState(store, { user: { ...user, ...patch } });
        }
      },
      /** Khôi phục phiên từ cookie refresh khi mở trang (chỉ trên trình duyệt). */
      init: (): Observable<void> => {
        if (!store._isBrowser || localStorage.getItem(SESSION_FLAG) !== '1') {
          patchState(store, { initialized: store._isBrowser });
          return of(undefined);
        }
        return new Observable<void>((subscriber) => {
          refresh().subscribe({
            complete: () => subscriber.complete(),
            error: () => subscriber.complete(),
          });
        });
      },
    };
  }),
);
