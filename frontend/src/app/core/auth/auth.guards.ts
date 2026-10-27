import { inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { CanActivateFn, Router } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { UserRole } from '../api/api.models';
import { AuthStore } from './auth.store';

/** Chờ AuthStore khôi phục phiên xong rồi mới quyết định. */
function whenInitialized() {
  const store = inject(AuthStore);
  return toObservable(store.initialized).pipe(
    filter(Boolean),
    take(1),
    map(() => store),
  );
}

/** Yêu cầu đăng nhập (và tùy chọn: thuộc một trong các role). Chưa đăng nhập → /auth/login?returnUrl=... */
export function authGuard(...roles: UserRole[]): CanActivateFn {
  return (_route, state) => {
    const router = inject(Router);
    return whenInitialized().pipe(
      map((store) => {
        if (!store.isLoggedIn()) {
          return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
        }
        const role = store.role();
        return roles.length === 0 || (role !== null && roles.includes(role)) ? true : router.createUrlTree(['/']);
      }),
    );
  };
}

/** Trang đăng nhập/đăng ký: đã đăng nhập thì về trang chủ. */
export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  return whenInitialized().pipe(map((store) => (store.isLoggedIn() ? router.createUrlTree(['/']) : true)));
};
