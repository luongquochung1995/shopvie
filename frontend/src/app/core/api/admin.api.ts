import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdminOverview,
  AdminProduct,
  AdminShop,
  AdminUser,
  ApiResponse,
  Banner,
  BannerInput,
  Page,
  ProductStatus,
  ShopStatus,
  UserRole,
  UserStatus,
} from './api.models';

/** API quản trị (/admin/**). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin`;

  // ----- Shop

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

  banShop(id: number, reason: string): Observable<AdminShop> {
    return this.http.post<ApiResponse<AdminShop>>(`${this.base}/shops/${id}/ban`, { reason }).pipe(map((r) => r.data));
  }

  unbanShop(id: number): Observable<AdminShop> {
    return this.http.post<ApiResponse<AdminShop>>(`${this.base}/shops/${id}/unban`, null).pipe(map((r) => r.data));
  }

  // ----- Tài khoản

  users(query: { q?: string; role?: UserRole | null; status?: UserStatus | null; page: number; size?: number }): Observable<Page<AdminUser>> {
    return this.http
      .get<ApiResponse<Page<AdminUser>>>(`${this.base}/users`, { params: toParams(query) })
      .pipe(map((r) => r.data));
  }

  lockUser(id: number, reason: string): Observable<AdminUser> {
    return this.http.post<ApiResponse<AdminUser>>(`${this.base}/users/${id}/lock`, { reason }).pipe(map((r) => r.data));
  }

  unlockUser(id: number): Observable<AdminUser> {
    return this.http.post<ApiResponse<AdminUser>>(`${this.base}/users/${id}/unlock`, null).pipe(map((r) => r.data));
  }

  // ----- Sản phẩm (hậu kiểm)

  products(query: { q?: string; status?: ProductStatus | null; page: number; size?: number }): Observable<Page<AdminProduct>> {
    return this.http
      .get<ApiResponse<Page<AdminProduct>>>(`${this.base}/products`, { params: toParams(query) })
      .pipe(map((r) => r.data));
  }

  banProduct(id: number, reason: string): Observable<AdminProduct> {
    return this.http
      .post<ApiResponse<AdminProduct>>(`${this.base}/products/${id}/ban`, { reason })
      .pipe(map((r) => r.data));
  }

  unbanProduct(id: number): Observable<AdminProduct> {
    return this.http.post<ApiResponse<AdminProduct>>(`${this.base}/products/${id}/unban`, null).pipe(map((r) => r.data));
  }

  // ----- Banner

  banners(): Observable<Banner[]> {
    return this.http.get<ApiResponse<Banner[]>>(`${this.base}/banners`).pipe(map((r) => r.data));
  }

  createBanner(body: BannerInput): Observable<Banner> {
    return this.http.post<ApiResponse<Banner>>(`${this.base}/banners`, body).pipe(map((r) => r.data));
  }

  updateBanner(id: number, body: BannerInput): Observable<Banner> {
    return this.http.put<ApiResponse<Banner>>(`${this.base}/banners/${id}`, body).pipe(map((r) => r.data));
  }

  deleteBanner(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/banners/${id}`);
  }

  // ----- Thống kê

  overview(days = 30): Observable<AdminOverview> {
    return this.http
      .get<ApiResponse<AdminOverview>>(`${this.base}/stats`, { params: { days } })
      .pipe(map((r) => r.data));
  }
}

function toParams(query: Record<string, string | number | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
