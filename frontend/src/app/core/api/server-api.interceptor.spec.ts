import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SERVER_API_ORIGIN, serverApiInterceptor } from './server-api.interceptor';

describe('serverApiInterceptor', () => {
  function setup(origin: string | null) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverApiInterceptor])),
        provideHttpClientTesting(),
        ...(origin ? [{ provide: SERVER_API_ORIGIN, useValue: origin }] : []),
      ],
    });
    return { http: TestBed.inject(HttpClient), ctrl: TestBed.inject(HttpTestingController) };
  }

  it('sends relative API URLs straight to the backend when rendering on the server', () => {
    const { http, ctrl } = setup('http://backend:8080/');
    http.get('/api/v1/products').subscribe();
    http.get('https://cdn.test/a.json').subscribe();
    ctrl.expectOne('http://backend:8080/api/v1/products');
    ctrl.expectOne('https://cdn.test/a.json');
    ctrl.verify();
  });

  it('leaves URLs untouched in the browser', () => {
    const { http, ctrl } = setup(null);
    http.get('/api/v1/products').subscribe();
    ctrl.expectOne('/api/v1/products');
    ctrl.verify();
  });
});
