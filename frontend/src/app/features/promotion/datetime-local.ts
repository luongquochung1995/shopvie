/** Giá trị cho input datetime-local (giờ máy người dùng): "2026-10-27T09:30". */
export function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** datetime-local (giờ máy) → ISO UTC cho API. */
export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}
