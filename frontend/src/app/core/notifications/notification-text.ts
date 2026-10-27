import { AppNotification } from '../api/api.models';
import { formatVnd } from '../../shared/pipes/vnd.pipe';

type Translate = (key: string, params?: Record<string, unknown>) => string;

/**
 * Nội dung thông báo theo loại + tham số (backend chỉ lưu loại và tham số để hiển thị đúng ngôn ngữ người dùng).
 * Mã đơn rút gọn 8 ký tự cuối cho dễ đọc.
 */
export function notificationText(n: AppNotification, t: Translate, locale = 'vi-VN'): string {
  const p = n.params;
  const params: Record<string, unknown> = {
    ...p,
    order: typeof p['orderCode'] === 'string' ? `#${p['orderCode'].slice(-8).toUpperCase()}` : '',
    total: typeof p['total'] === 'number' ? formatVnd(p['total'], locale) : '',
    status: typeof p['status'] === 'string' ? t(`orderStatus.${p['status']}`) : '',
  };
  const text = t(`notifications.type.${n.type}`, params);
  return p['reason'] ? `${text} ${t('notifications.reason', { reason: p['reason'] })}` : text;
}
