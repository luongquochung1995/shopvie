import { Routes } from '@angular/router';

export default [
  { path: 'login', loadComponent: () => import('./login'), title: 'title.login' },
  { path: 'register', loadComponent: () => import('./register'), title: 'title.register' },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
] satisfies Routes;
