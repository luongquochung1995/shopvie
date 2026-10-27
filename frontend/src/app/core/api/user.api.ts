import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Address, AddressInput, ApiResponse, Profile, UploadedMedia } from './api.models';

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  me(): Observable<Profile> {
    return this.http.get<ApiResponse<Profile>>(`${this.base}/me`).pipe(map((r) => r.data));
  }

  updateMe(body: { fullName: string; phone: string | null; avatarUrl: string | null }): Observable<Profile> {
    return this.http.put<ApiResponse<Profile>>(`${this.base}/me`, body).pipe(map((r) => r.data));
  }

  addresses(): Observable<Address[]> {
    return this.http.get<ApiResponse<Address[]>>(`${this.base}/me/addresses`).pipe(map((r) => r.data));
  }

  createAddress(body: AddressInput): Observable<Address> {
    return this.http.post<ApiResponse<Address>>(`${this.base}/me/addresses`, body).pipe(map((r) => r.data));
  }

  updateAddress(id: number, body: AddressInput): Observable<Address> {
    return this.http.put<ApiResponse<Address>>(`${this.base}/me/addresses/${id}`, body).pipe(map((r) => r.data));
  }

  setDefaultAddress(id: number): Observable<Address> {
    return this.http
      .patch<ApiResponse<Address>>(`${this.base}/me/addresses/${id}/default`, null)
      .pipe(map((r) => r.data));
  }

  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/me/addresses/${id}`);
  }

  uploadImage(file: File): Observable<UploadedMedia> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ApiResponse<UploadedMedia>>(`${this.base}/media/upload`, form).pipe(map((r) => r.data));
  }
}
