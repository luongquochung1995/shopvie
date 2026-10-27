import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { AuthStore } from '../../auth/auth.store';
import { CartStore } from '../../cart/cart.store';
import { LanguageSwitcher } from '../language-switcher';

/**
 * Header dùng phần tử HTML + Tailwind thuần (không PrimeNG) để giữ bundle tải đầu nhỏ.
 */
@Component({
  selector: 'app-header',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, LanguageSwitcher],
  templateUrl: './header.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'menuOpen.set(false)',
  },
})
export class Header {
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly auth = inject(AuthStore);
  protected readonly cart = inject(CartStore);

  protected readonly keyword = signal('');
  protected readonly menuOpen = signal(false);

  protected search(): void {
    const q = this.keyword().trim();
    if (q) {
      this.router.navigate(['/search'], { queryParams: { q } });
    }
  }

  protected logout(): void {
    this.menuOpen.set(false);
    this.auth.logout().subscribe({ complete: () => this.router.navigateByUrl('/') });
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.menuOpen() && !this.host.nativeElement.querySelector('[data-user-menu]')?.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }
}
