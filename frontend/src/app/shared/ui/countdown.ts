import { isPlatformBrowser } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, PLATFORM_ID, signal } from '@angular/core';

/** Đếm ngược tới một thời điểm (mm:ss), vd. hạn thanh toán. Hiện 00:00 khi đã quá hạn. */
@Component({
  selector: 'app-countdown',
  template: `<time class="font-mono tabular-nums" [attr.datetime]="until()">{{ text() }}</time>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Countdown {
  readonly until = input.required<string>();

  private readonly now = signal(Date.now());
  protected readonly text = computed(() => {
    const left = Math.max(0, Math.floor((Date.parse(this.until()) - this.now()) / 1000));
    const pad = (n: number) => String(n).padStart(2, '0');
    const hours = Math.floor(left / 3600);
    const rest = `${pad(Math.floor((left % 3600) / 60))}:${pad(left % 60)}`;
    return hours > 0 ? `${hours}:${rest}` : rest;
  });

  constructor() {
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      const timer = setInterval(() => this.now.set(Date.now()), 1000);
      inject(DestroyRef).onDestroy(() => clearInterval(timer));
    }
  }
}
