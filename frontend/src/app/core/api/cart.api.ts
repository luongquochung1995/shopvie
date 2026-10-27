import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Cart } from './api.models';

@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/cart`;

  cart(): Observable<Cart> {
    return this.http.get<ApiResponse<Cart>>(this.base).pipe(map((r) => r.data));
  }

  count(): Observable<number> {
    return this.http.get<ApiResponse<{ count: number }>>(`${this.base}/count`).pipe(map((r) => r.data.count));
  }

  add(variantId: number, quantity: number): Observable<Cart> {
    return this.http.post<ApiResponse<Cart>>(`${this.base}/items`, { variantId, quantity }).pipe(map((r) => r.data));
  }

  update(itemId: number, patch: { quantity?: number; selected?: boolean }): Observable<Cart> {
    return this.http.patch<ApiResponse<Cart>>(`${this.base}/items/${itemId}`, patch).pipe(map((r) => r.data));
  }

  /** itemIds rỗng = toàn bộ giỏ. */
  select(itemIds: number[], selected: boolean): Observable<Cart> {
    return this.http.patch<ApiResponse<Cart>>(`${this.base}/selection`, { itemIds, selected }).pipe(map((r) => r.data));
  }

  remove(itemIds: number[]): Observable<Cart> {
    const params = new HttpParams().set('ids', itemIds.join(','));
    return this.http.delete<ApiResponse<Cart>>(`${this.base}/items`, { params }).pipe(map((r) => r.data));
  }
}
