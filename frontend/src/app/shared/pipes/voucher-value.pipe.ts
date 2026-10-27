import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Voucher } from '../../core/api/api.models';
import { LanguageService } from '../../core/i18n/language.service';
import { formatVnd } from './vnd.pipe';

/** Mô tả mức giảm của voucher: "Giảm 10% tối đa ₫30.000", "Giảm ₫20.000", "Miễn phí vận chuyển tới ₫30.000". */
@Pipe({ name: 'voucherValue', pure: false })
export class VoucherValuePipe implements PipeTransform {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  transform(v: Pick<Voucher, 'type' | 'value' | 'maxDiscount'>): string {
    const locale = this.language.intlLocale();
    switch (v.type) {
      case 'PERCENT':
        return this.transloco.translate(v.maxDiscount ? 'voucher.percentMax' : 'voucher.percent', {
          value: v.value,
          max: formatVnd(v.maxDiscount, locale),
        });
      case 'FREESHIP':
        return this.transloco.translate('voucher.freeship', { amount: formatVnd(v.value, locale) });
      default:
        return this.transloco.translate('voucher.fixed', { amount: formatVnd(v.value, locale) });
    }
  }
}
