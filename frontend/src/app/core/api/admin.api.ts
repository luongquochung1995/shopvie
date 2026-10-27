import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminShop, ApiResponse, Page, ShopStatus } from './api.models';

/** API quản trị (/admin/**). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin`;

  shops(status: ShopStatus | null, page: number, size = 20): Observable<Page<AdminShop>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiResponse<Page<AdminShop>>>(`${this.base}/shops`, { params }).pipe(map((r) => r.data));
  }

  approveShop(id: number): Observable<AdminShop> {
    return this.http.post<ApiResponse<AdminShop>>(`${this.base}/shops/${id}/approve`, null).pipe(map((r) => r.data));
  }

  rejectShop(id: number, reason: string): Observable<AdminShop> {
    return this.http
      .post<ApiResponse<AdminShop>>(`${this.base}/shops/${id}/reject`, { reason })
      .pipe(map((r) => r.data));
  }
}
