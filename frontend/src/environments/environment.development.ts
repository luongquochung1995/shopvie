import { HttpInterceptorFn } from '@angular/common/http';

export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  interceptors: [] as HttpInterceptorFn[],
};
