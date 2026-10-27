import { Routes } from '@angular/router';
import AdminLayout from './admin-layout';

/** Khu quản trị (role ADMIN, lazy): tổng quan, duyệt/khóa shop, hậu kiểm sản phẩm, tài khoản, voucher sàn, flash sale, banner. */
export default [
  {
    path: '',
    component: AdminLayout,
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./admin-dashboard'), title: 'title.adminDashboard' },
      { path: 'shops', loadComponent: () => import('./shop-approval'), title: 'title.adminShops' },
      { path: 'products', loadComponent: () => import('./product-admin'), title: 'title.adminProducts' },
      { path: 'users', loadComponent: () => import('./user-admin'), title: 'title.adminUsers' },
      { path: 'vouchers', loadComponent: () => import('../promotion/voucher-manager'), data: { scope: 'admin' }, title: 'title.adminVouchers' },
      { path: 'flash-sales', loadComponent: () => import('./flash-sale-admin'), title: 'title.adminFlashSales' },
      { path: 'banners', loadComponent: () => import('./banner-admin'), title: 'title.adminBanners' },
    ],
  },
] satisfies Routes;
