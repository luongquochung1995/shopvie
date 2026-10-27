import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Page, SearchHit, SearchQuery, Suggestion } from './api.models';

/** Tìm kiếm full-text (Elasticsearch, gõ không dấu vẫn khớp). */
@Injectable({ providedIn: 'root' })
export class SearchApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/search`;

  search(query: SearchQuery): Observable<Page<SearchHit>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<ApiResponse<Page<SearchHit>>>(this.base, { params }).pipe(map((r) => r.data));
  }

  suggest(q: string): Observable<Suggestion[]> {
    return this.http
      .get<ApiResponse<Suggestion[]>>(`${this.base}/suggest`, { params: { q } })
      .pipe(map((r) => r.data));
  }
}
