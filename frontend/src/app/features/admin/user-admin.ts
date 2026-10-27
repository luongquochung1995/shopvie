import { ChangeDetectionStrategy, Component, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { AdminApi } from '../../core/api/admin.api';
import { errorKey } from '../../core/api/api-error';
import { AdminUser, UserRole, UserStatus } from '../../core/api/api.models';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { ModerationDialog } from './moderation-dialog';

const PAGE_SIZE = 20;

/** Quản lý tài khoản: tìm theo email/tên/SĐT, lọc vai trò/trạng thái, khóa (kèm lý do) / mở khóa. */
@Component({
  selector: 'app-user-admin',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Paginator, DateTimePipe, EmptyState, ModerationDialog],
  template: `
    <div *transloco="let t; prefix: 'admin.users'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <h1 class="mb-4 text-lg font-medium">{{ t('title') }}</h1>
      <form class="mb-4 flex flex-wrap gap-2 text-sm" (ngSubmit)="search()">
        <input type="search" name="q" class="min-w-48 flex-1 rounded-sm border border-gray-300 px-2 py-1.5" [placeholder]="t('searchPlaceholder')"
               [attr.aria-label]="t('searchPlaceholder')" [ngModel]="keyword()" (ngModelChange)="keyword.set($event)" />
        <select name="role" class="rounded-sm border border-gray-300 px-2 py-1.5" [attr.aria-label]="t('roleFilter')"
                [ngModel]="role()" (ngModelChange)="role.set($event || null); search()">
          <option [ngValue]="null">{{ t('allRoles') }}</option>
          @for (r of roles; track r) {
            <option [ngValue]="r">{{ t('role.' + r) }}</option>
          }
        </select>
        <select name="status" class="rounded-sm border border-gray-300 px-2 py-1.5" [attr.aria-label]="t('statusFilter')"
                [ngModel]="status()" (ngModelChange)="status.set($event || null); search()">
          <option [ngValue]="null">{{ t('allStatuses') }}</option>
          <option ngValue="ACTIVE">{{ t('status.ACTIVE') }}</option>
          <option ngValue="LOCKED">{{ t('status.LOCKED') }}</option>
        </select>
        <button pButton type="submit" size="small">{{ t('search') }}</button>
      </form>

      @if (!loading() && users().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-users" />
      } @else {
        <div class="overflow-x-auto">
          <table class="w-full min-w-[44rem] text-left text-sm">
            <thead class="bg-gray-50 text-gray-500">
              <tr>
                <th class="px-3 py-2 font-normal">{{ t('col.user') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.role') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.status') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.createdAt') }}</th>
                <th class="px-3 py-2"><span class="sr-only">{{ t('col.actions') }}</span></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              @for (u of users(); track u.id) {
                <tr [attr.data-user]="u.email">
                  <td class="px-3 py-2"><p class="font-medium">{{ u.fullName }}</p><p class="text-gray-500">{{ u.email }}@if (u.phone) { · {{ u.phone }} }</p></td>
                  <td class="px-3 py-2">{{ t('role.' + u.role) }}</td>
                  <td class="px-3 py-2">
                    <span class="rounded-sm px-2 py-0.5 text-xs" [class]="u.status === 'LOCKED' ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'">
                      {{ t('status.' + u.status) }}</span>
                    @if (u.lockReason) {
                      <p class="mt-1 text-xs text-red-700">{{ u.lockReason }}</p>
                    }
                  </td>
                  <td class="px-3 py-2 text-xs">{{ u.createdAt | dateTime }}</td>
                  <td class="px-3 py-2 text-right">
                    @if (u.role !== 'ADMIN') {
                      @if (u.status === 'ACTIVE') {
                        <button pButton type="button" size="small" [text]="true" severity="danger" (click)="openLock(u)">{{ t('lock') }}</button>
                      } @else {
                        <button pButton type="button" size="small" [text]="true" [disabled]="busyId() === u.id" (click)="unlock(u)">{{ t('unlock') }}</button>
                      }
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (totalItems() > pageSize) {
          <p-paginator styleClass="mt-2 !bg-transparent" [first]="page() * pageSize" [rows]="pageSize" [totalRecords]="totalItems()"
                       (onPageChange)="onPage($event)" />
        }
      }
      <app-moderation-dialog #dialog [header]="t('lockTitle', { name: locking()?.fullName ?? '' })" [confirmLabel]="t('lock')"
                             [visible]="locking() !== null" [busy]="busyId() !== null" (cancelled)="locking.set(null)" (confirmed)="lock($event)" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class UserAdmin {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly dialog = viewChild.required<ModerationDialog>('dialog');

  protected readonly roles: UserRole[] = ['BUYER', 'SELLER', 'ADMIN'];
  protected readonly pageSize = PAGE_SIZE;
  protected readonly keyword = signal('');
  protected readonly role = signal<UserRole | null>(null);
  protected readonly status = signal<UserStatus | null>(null);
  protected readonly page = signal(0);
  protected readonly users = signal<AdminUser[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly loading = signal(true);
  protected readonly busyId = signal<number | null>(null);
  protected readonly locking = signal<AdminUser | null>(null);
  /** Bộ lọc đã áp (chỉ đổi khi bấm tìm / đổi select). */
  private readonly applied = signal({ q: '', role: null as UserRole | null, status: null as UserStatus | null });

  constructor() {
    effect(() => this.load(this.applied(), this.page()));
  }

  protected search(): void {
    this.page.set(0);
    this.applied.set({ q: this.keyword().trim(), role: this.role(), status: this.status() });
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  protected openLock(u: AdminUser): void {
    this.dialog().reset();
    this.locking.set(u);
  }

  protected lock(reason: string): void {
    const u = this.locking();
    if (!u) {
      return;
    }
    this.busyId.set(u.id);
    this.api.lockUser(u.id, reason).subscribe({
      next: (updated) => this.replace(updated, 'admin.users.locked'),
      error: (err) => this.fail(err),
    });
  }

  protected unlock(u: AdminUser): void {
    this.busyId.set(u.id);
    this.api.unlockUser(u.id).subscribe({
      next: (updated) => this.replace(updated, 'admin.users.unlocked'),
      error: (err) => this.fail(err),
    });
  }

  private replace(updated: AdminUser, key: string): void {
    this.busyId.set(null);
    this.locking.set(null);
    this.users.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
    this.messages.add({ severity: 'success', summary: this.transloco.translate(key, { name: updated.fullName }) });
  }

  private fail(err: unknown): void {
    this.busyId.set(null);
    this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
  }

  private load(f: { q: string; role: UserRole | null; status: UserStatus | null }, page: number): void {
    this.loading.set(true);
    this.api.users({ ...f, page, size: PAGE_SIZE }).subscribe({
      next: (p) => {
        this.users.set(p.items);
        this.totalItems.set(p.totalItems);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.fail(err);
      },
    });
  }
}
