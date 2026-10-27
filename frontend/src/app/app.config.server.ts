import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { SERVER_API_ORIGIN } from './core/api/server-api.interceptor';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    // Backend trong mạng nội bộ (cùng biến với sitemap ở server.ts)
    { provide: SERVER_API_ORIGIN, useValue: process.env['API_ORIGIN'] ?? 'http://localhost:8080' },
  ]
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
