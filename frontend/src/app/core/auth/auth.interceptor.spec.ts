import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../api/api.models';
import { errorInterceptor } from '../api/error.interceptor';
import { ApiError } from '../api/api-error';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';

const API = environment.apiBaseUrl;
const session = (token: string): AuthResponse => ({
  accessToken: token,
  tokenType: 'Bearer',
  expiresIn: 900,
  user: { id: 1, email: 'a@b.vn', fullName: 'A', role: 'BUYER' },
});

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let store: InstanceType<typeof AuthStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => backend.verify());

  it('adds bearer token to API calls but not to other hosts', () => {
    store.setSession(session('t1'));

    http.get(`${API}/me`).subscribe();
    http.get('https://example.com/x').subscribe();

    expect(backend.expectOne(`${API}/me`).request.headers.get('Authorization')).toBe('Bearer t1');
    expect(backend.expectOne('https://example.com/x').request.headers.has('Authorization')).toBe(false);
  });

  it('sends cookies (withCredentials) only for /auth calls, without bearer token', () => {
    store.setSession(session('t1'));

    http.post(`${API}/auth/refresh`, null).subscribe();

    const req = backend.expectOne(`${API}/auth/refresh`).request;
    expect(req.withCredentials).toBe(true);
    expect(req.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on concurrent 401s and retries with the new token', () => {
    store.setSession(session('old'));
    const results: unknown[] = [];

    http.get(`${API}/me`).subscribe((r) => results.push(r));
    http.get(`${API}/me/addresses`).subscribe((r) => results.push(r));
    backend.expectOne(`${API}/me`).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${API}/me/addresses`).flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectOne(`${API}/auth/refresh`).flush({ success: true, data: session('new'), message: null });

    const retried = backend.match((r) => r.url.startsWith(`${API}/me`));
    expect(retried).toHaveLength(2);
    retried.forEach((r) => {
      expect(r.request.headers.get('Authorization')).toBe('Bearer new');
      r.flush({ ok: true });
    });
    expect(results).toHaveLength(2);
    expect(store.accessToken()).toBe('new');
  });

  it('clears the session and goes to login when refresh fails', () => {
    store.setSession(session('old'));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let error: unknown;

    http.get(`${API}/me`).subscribe({ error: (e) => (error = e) });
    backend.expectOne(`${API}/me`).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${API}/auth/refresh`).flush(
      { success: false, error: { code: 'INVALID_REFRESH_TOKEN', message: 'x', details: [] } },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(store.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/auth/login'], expect.anything());
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
  });

  it('does not try to refresh when there is no session', () => {
    let error: unknown;

    http.get(`${API}/me`).subscribe({ error: (e) => (error = e) });
    backend
      .expectOne(`${API}/me`)
      .flush({ success: false, error: { code: 'UNAUTHORIZED', message: 'x', details: [] } }, { status: 401, statusText: '' });

    backend.expectNone(`${API}/auth/refresh`);
    expect((error as ApiError).code).toBe('UNAUTHORIZED');
  });
});
