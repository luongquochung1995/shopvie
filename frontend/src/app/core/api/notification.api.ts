import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, AppNotification, NotificationPage } from './api.models';

@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/notifications`;

  list(page = 0, size = 10): Observable<NotificationPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<NotificationPage>>(this.base, { params }).pipe(map((r) => r.data));
  }

  markRead(id: number): Observable<AppNotification> {
    return this.http.post<ApiResponse<AppNotification>>(`${this.base}/${id}/read`, null).pipe(map((r) => r.data));
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/read-all`, null);
  }
}
