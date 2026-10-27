import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home/home'), title: 'title.home' },
  { path: 'c/:slug', loadComponent: () => import('./features/product-list/product-list') },
  { path: 'p/:slug', loadComponent: () => import('./features/product-detail/product-detail') },
  { path: 'search', loadComponent: () => import('./features/search/search'), title: 'title.search' },
  { path: 'cart', canActivate: [authGuard()], loadComponent: () => import('./features/cart/cart'), title: 'title.cart' },
  {
    path: 'checkout',
    canActivate: [authGuard()],
    children: [
      { path: '', loadComponent: () => import('./features/checkout/checkout'), title: 'title.checkout' },
      { path: 'success', loadComponent: () => import('./features/checkout/checkout-success'), title: 'title.checkoutSuccess' },
    ],
  },
  {
    path: 'payment',
    canActivate: [authGuard()],
    children: [
      { path: 'result', loadComponent: () => import('./features/payment/payment-result'), title: 'title.paymentResult' },
      { path: 'sandbox', loadComponent: () => import('./features/payment/payment-sandbox'), title: 'title.paymentSandbox' },
    ],
  },
  { path: 'auth', canActivate: [guestGuard], loadChildren: () => import('./features/auth/auth.routes') },
  { path: 'account', canActivate: [authGuard()], loadChildren: () => import('./features/account/account.routes') },
  {
    path: 'seller-center',
    canActivate: [authGuard()],
    loadChildren: () => import('./features/seller-center/seller-center.routes'),
  },
  { path: 'admin', canActivate: [authGuard('ADMIN')], loadChildren: () => import('./features/admin/admin.routes') },
  { path: '**', loadComponent: () => import('./features/not-found/not-found'), title: 'title.notFound' },
];
