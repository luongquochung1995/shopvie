import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';

/** Chuẩn hóa mọi lỗi HTTP thành {@link ApiError} để component xử lý thống nhất. */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: unknown) => throwError(() => (err instanceof HttpErrorResponse ? ApiError.from(err) : err))),
  );
