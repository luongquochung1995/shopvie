/** Chỉ chấp nhận đường dẫn nội bộ ("/..."), chặn open redirect kiểu "//evil.com" hoặc "https://...". */
export function safeReturnUrl(url: string | null | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\') ? url : '/';
}
