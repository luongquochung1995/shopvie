import { HttpBackend, HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

export interface Ward {
  code: string;
  name: string;
}

export interface Province {
  code: string;
  name: string;
  wards: Ward[];
}

/**
 * Địa giới hành chính 2 cấp (tỉnh/thành → xã/phường) áp dụng từ 01/07/2025.
 * Nguồn: provinces.open-api.vn (v2), đóng gói tĩnh tại /data/vn-divisions.json.
 */
@Injectable({ providedIn: 'root' })
export class DivisionsService {
  // HttpBackend: bỏ qua interceptor API (file tĩnh, không cần token)
  private readonly http = new HttpClient(inject(HttpBackend));

  private readonly provinces$ = this.http
    .get<Province[]>('data/vn-divisions.json')
    .pipe(shareReplay({ bufferSize: 1, refCount: false }));

  provinces(): Observable<Province[]> {
    return this.provinces$;
  }
}

/** "12 Lê Lợi, Phường Sài Gòn, Thành phố Hồ Chí Minh" */
export function formatAddressLine(
  provinces: Province[],
  a: { detail: string; provinceCode: string; wardCode: string },
): string {
  const province = provinces.find((p) => p.code === a.provinceCode);
  const ward = province?.wards.find((w) => w.code === a.wardCode);
  return [a.detail, ward?.name, province?.name].filter(Boolean).join(', ');
}
