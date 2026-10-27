import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Banner, CategoryNode, Page, ProductCard, ProductDetail, ProductQuery } from './api.models';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Cây danh mục ít thay đổi: dùng chung 1 request cho cả phiên. */
  private readonly categories$ = this.http
    .get<ApiResponse<CategoryNode[]>>(`${this.base}/categories`)
    .pipe(
      map((r) => r.data),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

  categories(): Observable<CategoryNode[]> {
    return this.categories$;
  }

  products(query: ProductQuery): Observable<Page<ProductCard>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<ApiResponse<Page<ProductCard>>>(`${this.base}/products`, { params }).pipe(map((r) => r.data));
  }

  /** Banner trang chủ đang hiển thị (admin quản lý). */
  banners(): Observable<Banner[]> {
    return this.http.get<ApiResponse<Banner[]>>(`${this.base}/banners`).pipe(map((r) => r.data));
  }

  product(slug: string): Observable<ProductDetail> {
    return this.http
      .get<ApiResponse<ProductDetail>>(`${this.base}/products/${encodeURIComponent(slug)}`)
      .pipe(map((r) => r.data));
  }
}

/** Tìm danh mục theo slug trong cây, trả kèm đường dẫn từ gốc. */
export function findCategoryPath(roots: CategoryNode[], slug: string): CategoryNode[] | null {
  for (const node of roots) {
    if (node.slug === slug) {
      return [node];
    }
    const sub = findCategoryPath(node.children, slug);
    if (sub) {
      return [node, ...sub];
    }
  }
  return null;
}
