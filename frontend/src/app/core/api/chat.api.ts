import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, ChatMessage, Conversation, MessagePage, Page } from './api.models';

@Injectable({ providedIn: 'root' })
export class ChatApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/chat`;

  /** Mở (hoặc lấy lại) hội thoại với shop. */
  open(shopId: number): Observable<Conversation> {
    return this.http.post<ApiResponse<Conversation>>(`${this.base}/conversations`, { shopId }).pipe(map((r) => r.data));
  }

  conversations(page = 0, size = 30): Observable<Page<Conversation>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<Page<Conversation>>>(`${this.base}/conversations`, { params })
      .pipe(map((r) => r.data));
  }

  messages(conversationId: number, before?: number | null, size = 30): Observable<MessagePage> {
    let params = new HttpParams().set('size', size);
    if (before) {
      params = params.set('before', before);
    }
    return this.http
      .get<ApiResponse<MessagePage>>(`${this.base}/conversations/${conversationId}/messages`, { params })
      .pipe(map((r) => r.data));
  }

  send(conversationId: number, content: string): Observable<ChatMessage> {
    return this.http
      .post<ApiResponse<ChatMessage>>(`${this.base}/conversations/${conversationId}/messages`, { content })
      .pipe(map((r) => r.data));
  }

  markRead(conversationId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/conversations/${conversationId}/read`, null);
  }

  unreadCount(): Observable<number> {
    return this.http.get<ApiResponse<{ count: number }>>(`${this.base}/unread-count`).pipe(map((r) => r.data.count));
  }
}
