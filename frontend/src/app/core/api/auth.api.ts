import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, AuthResponse } from './api.models';

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string | null;
}

/** Các request /auth gửi kèm cookie httpOnly chứa refresh token (withCredentials, xem auth.interceptor). */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/auth`;

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.base}/login`, { email, password }).pipe(map((r) => r.data));
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.base}/register`, req).pipe(map((r) => r.data));
  }

  refresh(): Observable<AuthResponse> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.base}/refresh`, null).pipe(map((r) => r.data));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/logout`, null);
  }
}
