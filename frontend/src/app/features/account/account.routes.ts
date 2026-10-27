import { Routes } from '@angular/router';
import AccountLayout from './account-layout';

export default [
  {
    path: '',
    component: AccountLayout,
    children: [
      { path: 'profile', loadComponent: () => import('./profile'), title: 'title.profile' },
      { path: 'addresses', loadComponent: () => import('./addresses'), title: 'title.addresses' },
      { path: 'orders', loadComponent: () => import('./orders'), title: 'title.orders' },
      { path: 'orders/:code', loadComponent: () => import('./order-detail'), title: 'title.orderDetail' },
      { path: '', pathMatch: 'full', redirectTo: 'profile' },
    ],
  },
] satisfies Routes;
