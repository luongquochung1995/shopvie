import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { Banner } from '../../core/api/api.models';
import { CatalogApi } from '../../core/api/catalog.api';

interface Slide {
  /** Key dịch: home.banner.<key>.title / .subtitle */
  key: string;
  gradient: string;
}

/**
 * Banner trang chủ: ảnh do admin quản lý (`/banners`); chưa có banner nào thì dùng slide mẫu. Tự chuyển slide mỗi
 * 5 giây trên trình duyệt.
 */
@Component({
  selector: 'app-home-banner',
  imports: [RouterLink, TranslocoDirective],
  template: `
    <section *transloco="let t; prefix: 'home.banner'" class="grid gap-1 md:grid-cols-3" [attr.aria-label]="t('label')">
      <div class="relative overflow-hidden rounded-sm md:col-span-2" aria-roledescription="carousel">
        @for (b of banners(); track b.id; let i = $index) {
          <div class="aspect-[2/1] transition-opacity duration-700 md:aspect-auto md:h-60"
               [class.absolute]="i !== active()" [class.inset-0]="i !== active()"
               [class.opacity-0]="i !== active()" [attr.aria-hidden]="i !== active()">
            @if (b.linkUrl && b.linkUrl.startsWith('/')) {
              <a [routerLink]="b.linkUrl" class="block size-full" [attr.tabindex]="i === active() ? null : -1">
                <img [src]="b.imageUrl" [alt]="b.title" class="size-full object-cover" />
              </a>
            } @else if (b.linkUrl) {
              <a [href]="b.linkUrl" target="_blank" rel="noopener noreferrer" class="block size-full" [attr.tabindex]="i === active() ? null : -1">
                <img [src]="b.imageUrl" [alt]="b.title" class="size-full object-cover" />
              </a>
            } @else {
              <img [src]="b.imageUrl" [alt]="b.title" class="size-full object-cover" />
            }
          </div>
        }
        @for (slide of banners().length ? [] : slides; track slide.key; let i = $index) {
          <div class="flex aspect-[2/1] flex-col justify-center gap-2 px-6 text-white transition-opacity duration-700 md:aspect-auto md:h-60 md:px-10"
               [class]="slide.gradient"
               [class.absolute]="i !== active()" [class.inset-0]="i !== active()"
               [class.opacity-0]="i !== active()" [attr.aria-hidden]="i !== active()">
            <p class="text-2xl font-bold md:text-4xl">{{ t(slide.key + '.title') }}</p>
            <p class="text-sm opacity-90 md:text-lg">{{ t(slide.key + '.subtitle') }}</p>
          </div>
        }
        <div class="absolute inset-x-0 bottom-2 flex justify-center gap-2">
          @for (n of dots(); track n; let i = $index) {
            <button type="button" class="size-2.5 rounded-full border border-white/80"
                    [class.bg-white]="i === active()" [attr.aria-label]="t('slide', { index: i + 1 })"
                    [attr.aria-current]="i === active()" (click)="active.set(i)"></button>
          }
        </div>
      </div>
      <div class="hidden grid-rows-2 gap-1 md:grid">
        <div class="flex flex-col justify-center rounded-sm bg-gradient-to-r from-brand-600 to-brand-400 px-5 text-white">
          <p class="text-lg font-bold">{{ t('freeship.title') }}</p>
          <p class="text-sm opacity-90">{{ t('freeship.subtitle') }}</p>
        </div>
        <div class="flex flex-col justify-center rounded-sm bg-gradient-to-r from-fuchsia-600 to-pink-500 px-5 text-white">
          <p class="text-lg font-bold">{{ t('cashback.title') }}</p>
          <p class="text-sm opacity-90">{{ t('cashback.subtitle') }}</p>
        </div>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeBanner {
  protected readonly slides: Slide[] = [
    { key: 'sale', gradient: 'bg-gradient-to-r from-brand-700 to-brand-400' },
    { key: 'fashion', gradient: 'bg-gradient-to-r from-indigo-600 to-brand-500' },
    { key: 'tech', gradient: 'bg-gradient-to-r from-violet-800 to-fuchsia-500' },
  ];
  protected readonly active = signal(0);
  protected readonly banners = toSignal(inject(CatalogApi).banners().pipe(catchError(() => of<Banner[]>([]))), {
    initialValue: [] as Banner[],
  });
  private readonly count = computed(() => this.banners().length || this.slides.length);
  protected readonly dots = computed(() => Array.from({ length: this.count() }, (_, i) => i));

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const timer = setInterval(() => this.active.update((i) => (i + 1) % this.count()), 5000);
      destroyRef.onDestroy(() => clearInterval(timer));
    });
  }
}
