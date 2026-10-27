import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  DashboardGranularity,
  MyShop,
  OrderStatus,
  OrderStatusCounts,
  Page,
  PickupAddress,
  ProductInput,
  SellerAction,
  SellerDashboard,
  SellerOrderDetail,
  SellerOrderSummary,
  SellerProductDetail,
  SellerProductQuery,
  SellerProductRow,
  ShopInfoInput,
} from './api.models';

/** API của Seller Center (/seller/**). */
@Injectable({ providedIn: 'root' })
export class SellerApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/seller`;

  // ----- Shop

  /** Mở shop (người mua → SELLER). Cần làm mới access token sau đó để nhận role mới. */
  registerShop(body: ShopInfoInput): Observable<MyShop> {
    return this.http.post<ApiResponse<MyShop>>(`${this.base}/shop/register`, body).pipe(map((r) => r.data));
  }

  myShop(): Observable<MyShop> {
    return this.http.get<ApiResponse<MyShop>>(`${this.base}/shop`).pipe(map((r) => r.data));
  }

  updateShop(body: ShopInfoInput): Observable<MyShop> {
    return this.http.put<ApiResponse<MyShop>>(`${this.base}/shop`, body).pipe(map((r) => r.data));
  }

  updatePickupAddress(body: PickupAddress): Observable<MyShop> {
    return this.http.put<ApiResponse<MyShop>>(`${this.base}/shop/pickup-address`, body).pipe(map((r) => r.data));
  }

  // ----- Sản phẩm

  products(query: SellerProductQuery): Observable<Page<SellerProductRow>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.q?.trim()) {
      params = params.set('q', query.q.trim());
    }
    return this.http
      .get<ApiResponse<Page<SellerProductRow>>>(`${this.base}/products`, { params })
      .pipe(map((r) => r.data));
  }

  product(id: number): Observable<SellerProductDetail> {
    return this.http.get<ApiResponse<SellerProductDetail>>(`${this.base}/products/${id}`).pipe(map((r) => r.data));
  }

  createProduct(body: ProductInput): Observable<SellerProductDetail> {
    return this.http.post<ApiResponse<SellerProductDetail>>(`${this.base}/products`, body).pipe(map((r) => r.data));
  }

  updateProduct(id: number, body: ProductInput): Observable<SellerProductDetail> {
    return this.http
      .put<ApiResponse<SellerProductDetail>>(`${this.base}/products/${id}`, body)
      .pipe(map((r) => r.data));
  }

  setVisibility(id: number, visible: boolean): Observable<SellerProductDetail> {
    return this.http
      .patch<ApiResponse<SellerProductDetail>>(`${this.base}/products/${id}/visibility`, { visible })
      .pipe(map((r) => r.data));
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/products/${id}`);
  }

  // ----- Đơn hàng

  orders(status: OrderStatus | null, page: number, size = 10): Observable<Page<SellerOrderSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http
      .get<ApiResponse<Page<SellerOrderSummary>>>(`${this.base}/orders`, { params })
      .pipe(map((r) => r.data));
  }

  orderCounts(): Observable<OrderStatusCounts> {
    return this.http.get<ApiResponse<OrderStatusCounts>>(`${this.base}/orders/counts`).pipe(map((r) => r.data));
  }

  order(code: string): Observable<SellerOrderDetail> {
    return this.http
      .get<ApiResponse<SellerOrderDetail>>(`${this.base}/orders/${encodeURIComponent(code)}`)
      .pipe(map((r) => r.data));
  }

  /** Thực hiện thao tác với đơn; CANCEL bắt buộc có lý do. */
  performOrderAction(code: string, action: SellerAction, text?: string | null): Observable<SellerOrderDetail> {
    const body = action === 'CANCEL' ? { reason: text ?? '' } : { note: text || null };
    return this.http
      .post<ApiResponse<SellerOrderDetail>>(
        `${this.base}/orders/${encodeURIComponent(code)}/${action.toLowerCase()}`,
        body,
      )
      .pipe(map((r) => r.data));
  }

  dashboard(granularity: DashboardGranularity, from?: string, to?: string): Observable<SellerDashboard> {
    let params = new HttpParams().set('granularity', granularity);
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<ApiResponse<SellerDashboard>>(`${this.base}/dashboard`, { params }).pipe(map((r) => r.data));
  }
}
