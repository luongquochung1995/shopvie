import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CheckoutPreview,
  OrderDetail,
  OrderStatus,
  OrderSummary,
  Page,
  PlaceOrderRequest,
  PlaceOrderResult,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Không truyền addressId → backend dùng địa chỉ mặc định. */
  preview(addressId?: number | null): Observable<CheckoutPreview> {
    return this.http
      .post<ApiResponse<CheckoutPreview>>(`${this.base}/checkout/preview`, addressId ? { addressId } : {})
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

  cancel(code: string, reason?: string): Observable<OrderDetail> {
    return this.http
      .post<ApiResponse<OrderDetail>>(`${this.base}/orders/${encodeURIComponent(code)}/cancel`, { reason: reason ?? null })
      .pipe(map((r) => r.data));
  }
}
