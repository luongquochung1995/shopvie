import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Chuyển người mua tới cổng thanh toán. URL cùng origin (trang cổng giả lập ở dev/mock, trang kết quả) đi bằng router
 * để không tải lại ứng dụng; URL ngoài (VNPay/MoMo, return URL của backend) tải trang đầy đủ.
 */
@Injectable({ providedIn: 'root' })
export class PaymentRedirect {
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  go(url: string): void {
    const origin = this.document.location.origin;
    const target = new URL(url, origin);
    if (target.origin === origin) {
      void this.router.navigateByUrl(target.pathname + target.search);
    } else {
      this.document.location.assign(target.href);
    }
  }
}
