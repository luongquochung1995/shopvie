import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, FlashSale, FlashSaleInput, FlashSaleReservation, Page, Voucher, VoucherInput } from './api.models';

/** Voucher (người mua / seller / admin) và flash sale. */
@Injectable({ providedIn: 'root' })
export class PromotionApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  // ----- Voucher

  /** Voucher người mua còn dùng được: voucher sàn + voucher của các shop trong giỏ. */
  availableVouchers(shopIds: number[]): Observable<Voucher[]> {
    return this.http
      .get<ApiResponse<Voucher[]>>(`${this.base}/vouchers/available`, { params: { shopIds: shopIds.join(',') } })
      .pipe(map((r) => r.data));
  }

  /** scope seller = voucher của shop mình, admin = voucher sàn. */
  vouchers(scope: 'seller' | 'admin', page: number, size = 20): Observable<Page<Voucher>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<Page<Voucher>>>(`${this.base}/${scope}/vouchers`, { params })
      .pipe(map((r) => r.data));
  }

  createVoucher(scope: 'seller' | 'admin', body: VoucherInput): Observable<Voucher> {
    return this.http.post<ApiResponse<Voucher>>(`${this.base}/${scope}/vouchers`, body).pipe(map((r) => r.data));
  }

  disableVoucher(scope: 'seller' | 'admin', id: number): Observable<Voucher> {
    return this.http
      .post<ApiResponse<Voucher>>(`${this.base}/${scope}/vouchers/${id}/disable`, null)
      .pipe(map((r) => r.data));
  }

  // ----- Flash sale

  currentFlashSales(): Observable<FlashSale[]> {
    return this.http.get<ApiResponse<FlashSale[]>>(`${this.base}/flash-sales/current`).pipe(map((r) => r.data));
  }

  /** Giữ suất (202); đơn được tạo bất đồng bộ — hỏi {@link reservation} tới khi CONFIRMED/FAILED. */
  purchaseFlashSale(itemId: number, quantity = 1, addressId?: number | null): Observable<FlashSaleReservation> {
    return this.http
      .post<ApiResponse<FlashSaleReservation>>(`${this.base}/flash-sales/items/${itemId}/purchase`, {
        quantity,
        ...(addressId ? { addressId } : {}),
      })
      .pipe(map((r) => r.data));
  }

  reservation(id: string): Observable<FlashSaleReservation> {
    return this.http
      .get<ApiResponse<FlashSaleReservation>>(`${this.base}/flash-sales/reservations/${encodeURIComponent(id)}`)
      .pipe(map((r) => r.data));
  }

  adminFlashSales(page: number, size = 20): Observable<Page<FlashSale>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<Page<FlashSale>>>(`${this.base}/admin/flash-sales`, { params })
      .pipe(map((r) => r.data));
  }

  createFlashSale(body: FlashSaleInput): Observable<FlashSale> {
    return this.http.post<ApiResponse<FlashSale>>(`${this.base}/admin/flash-sales`, body).pipe(map((r) => r.data));
  }

  cancelFlashSale(id: number): Observable<FlashSale> {
    return this.http
      .post<ApiResponse<FlashSale>>(`${this.base}/admin/flash-sales/${id}/cancel`, null)
      .pipe(map((r) => r.data));
  }
}
