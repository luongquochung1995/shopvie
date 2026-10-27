import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { translocoTesting } from '../../../../testing/transloco-testing';
import { AuthStore } from '../../auth/auth.store';
import { MockRealtimeService } from '../../../mock/mock-realtime';
import { RealtimeService } from '../../realtime/realtime.service';
import { Header } from './header';

describe('Header', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header, translocoTesting()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: RealtimeService, useClass: MockRealtimeService },
      ],
    }).compileComponents();
  });

  it('shows brand, search box, cart and login links for guests', async () => {
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('ShopVi');
    expect(el.querySelector('input[type="search"]')).toBeTruthy();
    expect(el.querySelector('a[aria-label="Giỏ hàng"]')?.getAttribute('href')).toBe('/cart');
    expect(el.querySelector('a[href="/auth/login"]')).toBeTruthy();
  });

  it('shows the user menu when logged in', async () => {
    TestBed.inject(AuthStore).setSession({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 1, email: 'a@b.vn', fullName: 'Nguyễn Văn A', role: 'BUYER' },
    });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    const toggle = el.querySelector<HTMLButtonElement>('button[aria-haspopup="menu"]')!;
    expect(toggle.getAttribute('aria-label')).toContain('Nguyễn Văn A');
    toggle.click();
    await fixture.whenStable();
    expect(el.querySelector('[role="menu"]')?.textContent).toContain('Đăng xuất');
    // Người mua thấy lối vào mở shop
    expect(el.querySelector('[role="menu"] a[href="/seller-center"]')?.textContent?.trim()).toBe('Mở shop bán hàng');
    expect(el.querySelector('a[href="/auth/login"]')).toBeNull();
  });

  it('shows the admin link for admins', async () => {
    TestBed.inject(AuthStore).setSession({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 1, email: 'admin@b.vn', fullName: 'Admin', role: 'ADMIN' },
    });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    el.querySelector<HTMLButtonElement>('button[aria-haspopup="menu"]')!.click();
    await fixture.whenStable();
    expect(el.querySelector('[role="menu"] a[href="/admin"]')).toBeTruthy();
    expect(el.querySelector('[role="menu"] a[href="/seller-center"]')).toBeNull();
  });

  it('switches language at runtime and remembers it in a cookie', async () => {
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('a[href="/auth/login"]')?.textContent?.trim()).toBe('Đăng Nhập');

    el.querySelector<HTMLButtonElement>('button[lang="en"]')!.click();
    await vi.waitFor(() => expect(TestBed.inject(TranslocoService).getActiveLang()).toBe('en'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('a[href="/auth/login"]')?.textContent?.trim()).toBe('Log In');
    expect(el.querySelector('a[href="/cart"]')?.getAttribute('aria-label')).toBe('Shopping cart');
    expect(document.cookie).toContain('lang=en');
    expect(document.documentElement.lang).toBe('en');
    document.cookie = 'lang=; path=/; max-age=0';
  });

  it('navigates to search page with trimmed keyword on submit', async () => {
    const fixture = TestBed.createComponent(Header);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = '  áo thun ';
    input.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    expect(navigate).toHaveBeenCalledWith(['/search'], { queryParams: { q: 'áo thun' } });
  });

  it('does not navigate on empty keyword', async () => {
    const fixture = TestBed.createComponent(Header);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    await fixture.whenStable();

    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    expect(navigate).not.toHaveBeenCalled();
  });
});
