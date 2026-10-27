import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody, ApiErrorDetail } from './api.models';

/** Lỗi API đã chuẩn hóa từ format `{ success: false, error: {...} }` của backend. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: ApiErrorDetail[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }

  static from(err: unknown): ApiError {
    if (err instanceof ApiError) {
      return err;
    }
    if (err instanceof HttpErrorResponse) {
      const body = err.error as Partial<ApiErrorBody> | null;
      if (body && body.success === false && body.error) {
        return new ApiError(err.status, body.error.code, body.error.message, body.error.details ?? []);
      }
      return new ApiError(err.status, err.status === 0 ? 'NETWORK_ERROR' : 'HTTP_' + err.status, err.message);
    }
    return new ApiError(0, 'UNKNOWN', err instanceof Error ? err.message : String(err));
  }

  /** Lỗi validate của một field (nếu có). */
  fieldError(field: string): string | undefined {
    return this.details.find((d) => d.field === field)?.message;
  }
}

/** Mã lỗi đã có bản dịch trong i18n (`errors.<CODE>`). */
const TRANSLATED_CODES = new Set([
  'NETWORK_ERROR',
  'VALIDATION_ERROR',
  'UNAUTHORIZED',
  'FORBIDDEN',
  'EMAIL_ALREADY_EXISTS',
  'PHONE_ALREADY_EXISTS',
  'INVALID_CREDENTIALS',
  'ACCOUNT_LOCKED',
  'ADDRESS_LIMIT_REACHED',
  'PRODUCT_NOT_FOUND',
  'CATEGORY_NOT_FOUND',
  'UNSUPPORTED_MEDIA_TYPE',
  'FILE_TOO_LARGE',
  'STORAGE_UNAVAILABLE',
  'ADDRESS_NOT_FOUND',
  'VARIANT_NOT_FOUND',
  'PRODUCT_UNAVAILABLE',
  'OUT_OF_STOCK',
  'CART_ITEM_NOT_FOUND',
  'CART_QUANTITY_EXCEEDS_STOCK',
  'CART_LIMIT_REACHED',
  'NO_ITEMS_SELECTED',
  'PRICE_CHANGED',
  'PAYMENT_METHOD_NOT_SUPPORTED',
  'ORDER_NOT_FOUND',
  'INVALID_ORDER_STATUS_TRANSITION',
  'CONFLICT',
  'SHOP_NOT_FOUND',
  'SHOP_ALREADY_EXISTS',
  'SHOP_NOT_ACTIVE',
  'INVALID_SHOP_STATUS',
  'CATEGORY_NOT_LEAF',
  'PRODUCT_BANNED',
  'SKU_ALREADY_EXISTS',
]);

/** Key dịch thông báo lỗi cho người dùng theo mã lỗi API (dịch bằng Transloco ở nơi hiển thị). */
export function errorKey(err: unknown): string {
  const code = ApiError.from(err).code;
  return TRANSLATED_CODES.has(code) ? `errors.${code}` : 'errors.DEFAULT';
}
