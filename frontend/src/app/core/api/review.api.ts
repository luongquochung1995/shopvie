import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Page, ProductReviews, Review, ReviewInput, SellerReview } from './api.models';

@Injectable({ providedIn: 'root' })
export class ReviewApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  productReviews(slug: string, page: number, rating?: number | null, withMedia = false, size = 10): Observable<ProductReviews> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (rating) {
      params = params.set('rating', rating);
    }
    if (withMedia) {
      params = params.set('withMedia', true);
    }
    return this.http
      .get<ApiResponse<ProductReviews>>(`${this.base}/products/${encodeURIComponent(slug)}/reviews`, { params })
      .pipe(map((r) => r.data));
  }

  /** Đánh giá người mua đã viết cho các món của đơn. */
  orderReviews(code: string): Observable<Review[]> {
    return this.http
      .get<ApiResponse<Review[]>>(`${this.base}/orders/${encodeURIComponent(code)}/reviews`)
      .pipe(map((r) => r.data));
  }

  create(code: string, itemId: number, body: ReviewInput): Observable<Review> {
    return this.http
      .post<ApiResponse<Review>>(`${this.base}/orders/${encodeURIComponent(code)}/items/${itemId}/review`, body)
      .pipe(map((r) => r.data));
  }

  sellerReviews(unrepliedOnly: boolean, page: number, size = 20): Observable<Page<SellerReview>> {
    const params = new HttpParams().set('page', page).set('size', size).set('unreplied', unrepliedOnly);
    return this.http
      .get<ApiResponse<Page<SellerReview>>>(`${this.base}/seller/reviews`, { params })
      .pipe(map((r) => r.data));
  }

  reply(id: number, content: string): Observable<SellerReview> {
    return this.http
      .post<ApiResponse<SellerReview>>(`${this.base}/seller/reviews/${id}/reply`, { content })
      .pipe(map((r) => r.data));
  }
}
