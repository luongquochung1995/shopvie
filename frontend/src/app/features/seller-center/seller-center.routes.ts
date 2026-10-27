import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import SellerLayout from './seller-layout';

/** Chỉ tài khoản SELLER vào được các trang quản lý; người mua được chuyển tới trang đăng ký mở shop. */
const sellerOnly: CanActivateFn = () =>
  inject(AuthStore).role() === 'SELLER' ? true : inject(Router).createUrlTree(['/seller-center/register']);

/** Đã là seller thì không cần đăng ký nữa. */
const notSeller: CanActivateFn = () =>
  inject(AuthStore).role() === 'SELLER' ? inject(Router).createUrlTree(['/seller-center']) : true;

export default [
  {
    path: 'register',
    canActivate: [notSeller],
    loadComponent: () => import('./register-shop'),
    title: 'title.sellerRegister',
  },
  {
    path: '',
    component: SellerLayout,
    canActivate: [sellerOnly],
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./dashboard/dashboard'), title: 'title.sellerDashboard' },
      { path: 'products', loadComponent: () => import('./products/product-list'), title: 'title.sellerProducts' },
      { path: 'products/new', loadComponent: () => import('./products/product-form'), title: 'title.sellerProductNew' },
      { path: 'products/:id', loadComponent: () => import('./products/product-form'), title: 'title.sellerProductEdit' },
      { path: 'orders', loadComponent: () => import('./orders/order-list'), title: 'title.sellerOrders' },
      { path: 'orders/:code', loadComponent: () => import('./orders/order-detail'), title: 'title.sellerOrderDetail' },
      { path: 'vouchers', loadComponent: () => import('../promotion/voucher-manager'), data: { scope: 'seller' }, title: 'title.sellerVouchers' },
      { path: 'reviews', loadComponent: () => import('./reviews/review-list'), title: 'title.sellerReviews' },
      { path: 'shop', loadComponent: () => import('./shop-profile'), title: 'title.sellerShop' },
    ],
  },
] satisfies Routes;
