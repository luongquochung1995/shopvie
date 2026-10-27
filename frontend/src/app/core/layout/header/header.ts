import { isPlatformBrowser } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, inject, PLATFORM_ID, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { Suggestion } from '../../api/api.models';
import { SearchApi } from '../../api/search.api';
import { AuthStore } from '../../auth/auth.store';
import { CartStore } from '../../cart/cart.store';
import { LanguageSwitcher } from '../language-switcher';
import { NotificationBell } from '../notification-bell';

/**
 * Header dùng phần tử HTML + Tailwind thuần (không PrimeNG) để giữ bundle tải đầu nhỏ.
 */
@Component({
  selector: 'app-header',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, LanguageSwitcher, NotificationBell],
  templateUrl: './header.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    // Ghim header khi cuộn trang
    class: 'sticky top-0 z-40 block',
    '(window:scroll)': 'onScroll()',
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'menuOpen.set(false); suggestOpen.set(false)',
  },
})
export class Header {
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly auth = inject(AuthStore);
  protected readonly cart = inject(CartStore);
  private readonly searchApi = inject(SearchApi);

  protected readonly keyword = signal('');
  protected readonly menuOpen = signal(false);
  /** Gợi ý khi gõ (từ 2 ký tự, chờ người dùng ngừng gõ 200ms). */
  protected readonly suggestions = toSignal(
    toObservable(this.keyword).pipe(
      map((q) => q.trim()),
      debounceTime(200),
      distinctUntilChanged(),
      switchMap((q) => (q.length < 2 ? of<Suggestion[]>([]) : this.searchApi.suggest(q).pipe(catchError(() => of<Suggestion[]>([]))))),
    ),
    { initialValue: [] as Suggestion[] },
  );
  protected readonly suggestOpen = signal(false);
  protected readonly activeSuggestion = signal(-1);
  protected readonly showSuggestions = computed(() => this.suggestOpen() && this.suggestions().length > 0);
  /** Đã cuộn xuống: thu gọn thanh trên của header. */
  protected readonly scrolled = signal(false);

  constructor() {
    // Chiều cao header ghim → biến CSS --app-header-h cho các phần tử sticky khác (đặt ngay dưới header)
    if (isPlatformBrowser(inject(PLATFORM_ID)) && typeof ResizeObserver !== 'undefined') {
      const root = document.documentElement;
      const observer = new ResizeObserver(([entry]) =>
        root.style.setProperty('--app-header-h', `${Math.round(entry.target.getBoundingClientRect().height)}px`),
      );
      observer.observe(this.host.nativeElement);
      inject(DestroyRef).onDestroy(() => observer.disconnect());
    }
  }

  protected search(): void {
    const active = this.suggestions()[this.activeSuggestion()];
    if (this.showSuggestions() && active) {
      this.openSuggestion(active);
      return;
    }
    const q = this.keyword().trim();
    this.suggestOpen.set(false);
    if (q) {
      this.router.navigate(['/search'], { queryParams: { q } });
    }
  }

  protected onKeywordChange(value: string): void {
    this.keyword.set(value);
    this.suggestOpen.set(true);
    this.activeSuggestion.set(-1);
  }

  /** Mũi tên lên/xuống chọn gợi ý; Enter (submit form) mở gợi ý đang chọn. */
  protected moveSuggestion(step: number, event: Event): void {
    const count = this.suggestions().length;
    if (!this.showSuggestions() || count === 0) {
      return;
    }
    event.preventDefault();
    // -1 = đang ở ô nhập; đi quá đầu/cuối danh sách thì vòng lại
    this.activeSuggestion.update((i) => {
      const next = i + step;
      return next >= count ? -1 : next < -1 ? count - 1 : next;
    });
  }

  protected openSuggestion(s: Suggestion): void {
    this.suggestOpen.set(false);
    this.activeSuggestion.set(-1);
    this.keyword.set(s.name);
    this.router.navigate(['/p', s.slug]);
  }

  protected logout(): void {
    this.menuOpen.set(false);
    this.auth.logout().subscribe({ complete: () => this.router.navigateByUrl('/') });
  }

  protected onScroll(): void {
    // Ngưỡng có độ trễ (hysteresis) để header không nhấp nháy quanh mốc khi thanh trên co lại
    const y = window.scrollY;
    if (!this.scrolled() && y > 40) {
      this.scrolled.set(true);
    } else if (this.scrolled() && y < 8) {
      this.scrolled.set(false);
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.menuOpen() && !this.host.nativeElement.querySelector('[data-user-menu]')?.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
    if (this.suggestOpen() && !this.host.nativeElement.querySelector('[role=search]')?.contains(event.target as Node)) {
      this.suggestOpen.set(false);
    }
  }
}
