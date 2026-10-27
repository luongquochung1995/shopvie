import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CheckoutPayment,
  CheckoutPreview,
  OrderDetail,
  OrderStatus,
  OrderSummary,
  Page,
  PaymentLink,
  PlaceOrderRequest,
  PlaceOrderResult,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Không truyền addressId → backend dùng địa chỉ mặc định. Mã voucher sai/không áp được → lỗi 4xx. */
  preview(addressId?: number | null, voucherCodes: string[] = []): Observable<CheckoutPreview> {
    return this.http
      .post<ApiResponse<CheckoutPreview>>(`${this.base}/checkout/preview`, {
        ...(addressId ? { addressId } : {}),
        ...(voucherCodes.length ? { voucherCodes } : {}),
      })
      .pipe(map((r) => r.data));
  }

  placeOrder(body: PlaceOrderRequest): Observable<PlaceOrderResult> {
    return this.http.post<ApiResponse<PlaceOrderResult>>(`${this.base}/checkout`, body).pipe(map((r) => r.data));
  }

  orders(status: OrderStatus | null, page: number, size = 10): Observable<Page<OrderSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiResponse<Page<OrderSummary>>>(`${this.base}/orders`, { params }).pipe(map((r) => r.data));
  }

  order(code: string): Observable<OrderDetail> {
    return this.http
      .get<ApiResponse<OrderDetail>>(`${this.base}/orders/${encodeURIComponent(code)}`)
      .pipe(map((r) => r.data));
  }

  /** Người mua xác nhận đã nhận hàng (DELIVERED → COMPLETED); sau đó đánh giá được. */
  complete(code: string): Observable<OrderDetail> {
    return this.http
      .post<ApiResponse<OrderDetail>>(`${this.base}/orders/${encodeURIComponent(code)}/complete`, null)
      .pipe(map((r) => r.data));
  }

  cancel(code: string, reason?: string): Observable<OrderDetail> {
    return this.http
      .post<ApiResponse<OrderDetail>>(`${this.base}/orders/${encodeURIComponent(code)}/cancel`, { reason: reason ?? null })
      .pipe(map((r) => r.data));
  }

  /** Trạng thái thanh toán của checkout (trang kết quả thanh toán hỏi lại tới khi có kết quả). */
  paymentStatus(checkoutId: string): Observable<CheckoutPayment> {
    return this.http
      .get<ApiResponse<CheckoutPayment>>(`${this.base}/checkout/${encodeURIComponent(checkoutId)}/payment`)
      .pipe(map((r) => r.data));
  }

  /** Thanh toán lại các đơn online chưa thanh toán của checkout. */
  retryPayment(checkoutId: string): Observable<PaymentLink> {
    return this.http
      .post<ApiResponse<PaymentLink>>(`${this.base}/checkout/${encodeURIComponent(checkoutId)}/payment`, null)
      .pipe(map((r) => r.data));
  }

  /** Cổng thanh toán giả lập (dev/test): trả URL return đã ký để trình duyệt chuyển tới. */
  completeSandboxPayment(reference: string, success: boolean): Observable<string> {
    return this.http
      .post<ApiResponse<{ redirectUrl: string }>>(`${this.base}/payments/sandbox/${encodeURIComponent(reference)}`, { success })
      .pipe(map((r) => r.data.redirectUrl));
  }
}
