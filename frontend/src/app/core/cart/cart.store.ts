import { isPlatformBrowser } from '@angular/common';
import { effect, inject, PLATFORM_ID, untracked } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { CartApi } from '../api/cart.api';
import { AuthStore } from '../auth/auth.store';

/**
 * Số món trong giỏ cho badge trên header. Tự làm mới khi đăng nhập/đăng xuất;
 * các trang sửa giỏ gọi {@link setCount} với giá trị trả về từ API.
 */
export const CartStore = signalStore(
  { providedIn: 'root' },
  withState({ count: 0 }),
  withMethods((store, api = inject(CartApi), auth = inject(AuthStore)) => ({
    setCount(count: number): void {
      patchState(store, { count });
    },
    refresh(): void {
      if (!auth.isLoggedIn()) {
        patchState(store, { count: 0 });
        return;
      }
      api.count().subscribe({ next: (count) => patchState(store, { count }), error: () => undefined });
    },
  })),
  withHooks({
    onInit(store, auth = inject(AuthStore), platformId = inject(PLATFORM_ID)) {
      if (!isPlatformBrowser(platformId)) {
        return;
      }
      effect(() => {
        auth.user();
        untracked(() => store.refresh());
      });
    },
  }),
);
