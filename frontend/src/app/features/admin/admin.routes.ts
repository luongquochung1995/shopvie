import { Routes } from '@angular/router';

/** Khu quản trị (role ADMIN). P3: duyệt shop; các trang quản trị khác ở Phase 6. */
export default [
  { path: 'shops', loadComponent: () => import('./shop-approval'), title: 'title.adminShops' },
  { path: '', pathMatch: 'full', redirectTo: 'shops' },
] satisfies Routes;
