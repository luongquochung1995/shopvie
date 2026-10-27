import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthStore } from './auth.store';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * - Gắn access token cho mọi request tới API (trừ /auth/**).
 * - Request /auth/** gửi kèm cookie (refresh token httpOnly).
 * - Gặp 401 khi đang có token: refresh 1 lần rồi gửi lại; refresh thất bại → xóa phiên, về trang đăng nhập.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const apiBase = environment.apiBaseUrl;
  if (!req.url.startsWith(apiBase)) {
    return next(req);
  }
  if (req.url.startsWith(`${apiBase}/auth/`)) {
    return next(req.clone({ withCredentials: true }));
  }

  const store = inject(AuthStore);
  const router = inject(Router);
  const token = store.accessToken();

  return next(withToken(req, token)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || !token) {
        return throwError(() => err);
      }
      return store.refresh().pipe(
        catchError(() => {
          void router.navigate(['/auth/login'], { queryParams: { returnUrl: router.url } });
          return throwError(() => err);
        }),
        switchMap((newToken) => next(withToken(req, newToken))),
      );
    }),
  );
};
