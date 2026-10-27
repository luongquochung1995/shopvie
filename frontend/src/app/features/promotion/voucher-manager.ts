import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { ApiError, errorKey } from '../../core/api/api-error';
import { Voucher, VoucherInput, VoucherType } from '../../core/api/api.models';
import { PromotionApi } from '../../core/api/promotion.api';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { VoucherValuePipe } from '../../shared/pipes/voucher-value.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { fromLocalInput, toLocalInput } from './datetime-local';

const PAGE_SIZE = 20;
const TYPES: VoucherType[] = ['FIXED', 'PERCENT', 'FREESHIP'];

/** Danh sách + tạo/ngừng voucher. scope seller = voucher shop, admin = voucher sàn (truyền qua route data). */
@Component({
  selector: 'app-voucher-manager',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Dialog, Paginator, DateTimePipe, VndPipe, VoucherValuePipe, EmptyState],
  template: `
    <div *transloco="let t; prefix: 'voucherAdmin'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <div class="mb-4 flex items-center justify-between gap-3">
        <h1 class="text-lg font-medium">{{ t(scope() === 'admin' ? 'platformTitle' : 'shopTitle') }}</h1>
        <button pButton type="button" size="small" (click)="openCreate()"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('create') }}</button>
      </div>

      @if (!loading() && vouchers().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-ticket" />
      } @else {
        <div class="overflow-x-auto">
          <table class="w-full min-w-[48rem] text-left text-sm">
            <thead class="bg-gray-50 text-gray-500">
              <tr>
                <th class="px-3 py-2 font-normal">{{ t('col.voucher') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.value') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.usage') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.period') }}</th>
                <th class="px-3 py-2 font-normal">{{ t('col.status') }}</th>
                <th class="px-3 py-2"><span class="sr-only">{{ t('col.actions') }}</span></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              @for (v of vouchers(); track v.id) {
                <tr [attr.data-voucher]="v.code">
                  <td class="px-3 py-2"><p class="font-mono font-medium">{{ v.code }}</p><p class="text-gray-500">{{ v.name }}</p></td>
                  <td class="px-3 py-2">
                    <p>{{ v | voucherValue }}</p>
                    <p class="text-xs text-gray-500">{{ t('minOrder', { amount: (v.minOrderValue | vnd) }) }}</p>
                  </td>
                  <td class="px-3 py-2">{{ v.usedCount }}/{{ v.usageLimit }}<p class="text-xs text-gray-500">{{ t('perUser', { count: v.perUserLimit }) }}</p></td>
                  <td class="px-3 py-2 text-xs">{{ v.startAt | dateTime }}<br />{{ v.endAt | dateTime }}</td>
                  <td class="px-3 py-2">
                    <span class="rounded-sm px-2 py-0.5 text-xs" [class]="statusClass(v)">{{ t('status.' + statusOf(v)) }}</span>
                  </td>
                  <td class="px-3 py-2 text-right">
                    @if (v.status === 'ACTIVE' && statusOf(v) !== 'ENDED') {
                      <button pButton type="button" size="small" [text]="true" severity="danger" (click)="disable(v)">{{ t('disable') }}</button>
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

      <p-dialog [header]="t('create')" [visible]="formOpen()" (visibleChange)="formOpen.set($event)" [modal]="true" [draggable]="false"
                [style]="{ width: '36rem' }" [breakpoints]="{ '640px': '95vw' }">
        <form class="grid grid-cols-2 gap-3 text-sm" (ngSubmit)="save()">
          <label class="col-span-2 flex flex-col gap-1 sm:col-span-1">{{ t('field.code') }}
            <input name="code" required maxlength="20" pattern="[A-Za-z0-9]{4,20}" class="rounded-sm border border-gray-300 px-2 py-1.5 uppercase"
                   [ngModel]="form().code" (ngModelChange)="patch({ code: $event })" />
            <span class="text-xs text-gray-500">{{ t('codeHint') }}</span>
          </label>
          <label class="col-span-2 flex flex-col gap-1 sm:col-span-1">{{ t('field.name') }}
            <input name="name" required maxlength="100" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().name" (ngModelChange)="patch({ name: $event })" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.type') }}
            <select name="type" class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="form().type" (ngModelChange)="patch({ type: $event })">
              @for (type of types; track type) {
                <option [value]="type">{{ t('type.' + type) }}</option>
              }
            </select>
          </label>
          <label class="flex flex-col gap-1">{{ t(form().type === 'PERCENT' ? 'field.percent' : 'field.amount') }}
            <input name="value" type="number" required min="1" [max]="form().type === 'PERCENT' ? 100 : 1000000000"
                   class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="form().value" (ngModelChange)="patch({ value: $event })" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.minOrder') }}
            <input name="minOrderValue" type="number" min="0" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().minOrderValue" (ngModelChange)="patch({ minOrderValue: $event })" />
          </label>
          @if (form().type === 'PERCENT') {
            <label class="flex flex-col gap-1">{{ t('field.maxDiscount') }}
              <input name="maxDiscount" type="number" min="1" class="rounded-sm border border-gray-300 px-2 py-1.5"
                     [ngModel]="form().maxDiscount" (ngModelChange)="patch({ maxDiscount: $event || null })" />
            </label>
          } @else {
            <span></span>
          }
          <label class="flex flex-col gap-1">{{ t('field.usageLimit') }}
            <input name="usageLimit" type="number" required min="1" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().usageLimit" (ngModelChange)="patch({ usageLimit: $event })" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.perUserLimit') }}
            <input name="perUserLimit" type="number" required min="1" max="100" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().perUserLimit" (ngModelChange)="patch({ perUserLimit: $event })" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.startAt') }}
            <input name="startAt" type="datetime-local" required class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="startAt()" (ngModelChange)="startAt.set($event)" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.endAt') }}
            <input name="endAt" type="datetime-local" required class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="endAt()" (ngModelChange)="endAt.set($event)" />
          </label>
          @if (formError(); as err) {
            <p class="col-span-2 text-red-600" role="alert">{{ err }}</p>
          }
          <div class="col-span-2 flex justify-end gap-2">
            <button pButton type="button" [text]="true" severity="secondary" (click)="formOpen.set(false)">{{ t('cancel') }}</button>
            <button pButton type="submit" [disabled]="saving()">{{ t('save') }}</button>
          </div>
        </form>
      </p-dialog>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class VoucherManager {
  private readonly api = inject(PromotionApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  /** Route data. */
  readonly scope = input<'seller' | 'admin'>('seller');

  protected readonly types = TYPES;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly vouchers = signal<Voucher[]>([]);
  protected readonly loading = signal(true);
  protected readonly page = signal(0);
  protected readonly totalItems = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = signal<Omit<VoucherInput, 'startAt' | 'endAt'>>(emptyForm());
  protected readonly startAt = signal('');
  protected readonly endAt = signal('');

  constructor() {
    effect(() => this.load(this.scope(), this.page()));
  }

  protected statusOf(v: Voucher): 'DISABLED' | 'UPCOMING' | 'RUNNING' | 'ENDED' {
    const now = Date.now();
    return v.status === 'DISABLED' ? 'DISABLED' : now < Date.parse(v.startAt) ? 'UPCOMING' : now >= Date.parse(v.endAt) ? 'ENDED' : 'RUNNING';
  }

  protected statusClass(v: Voucher): string {
    return { RUNNING: 'bg-green-50 text-green-700', UPCOMING: 'bg-blue-50 text-blue-700', ENDED: 'bg-gray-100 text-gray-500', DISABLED: 'bg-red-50 text-red-700' }[this.statusOf(v)];
  }

  protected patch(p: Partial<VoucherInput>): void {
    this.form.update((f) => ({ ...f, ...p }));
  }

  protected openCreate(): void {
    const now = new Date();
    this.form.set(emptyForm());
    this.startAt.set(toLocalInput(now));
    this.endAt.set(toLocalInput(new Date(now.getTime() + 7 * 24 * 3600_000)));
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected save(): void {
    const f = this.form();
    const body: VoucherInput = {
      ...f,
      code: f.code.trim().toUpperCase(),
      name: f.name.trim(),
      value: Number(f.value),
      minOrderValue: Number(f.minOrderValue) || 0,
      maxDiscount: f.type === 'PERCENT' && f.maxDiscount ? Number(f.maxDiscount) : null,
      usageLimit: Number(f.usageLimit),
      perUserLimit: Number(f.perUserLimit),
      startAt: fromLocalInput(this.startAt()),
      endAt: fromLocalInput(this.endAt()),
    };
    this.saving.set(true);
    this.formError.set(null);
    this.api.createVoucher(this.scope(), body).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('voucherAdmin.created') });
        this.page() === 0 ? this.load(this.scope(), 0) : this.page.set(0);
      },
      error: (err) => {
        this.saving.set(false);
        const e = ApiError.from(err);
        const fields = e.details.map((d) => d.field).join(', ');
        this.formError.set(this.transloco.translate(errorKey(err)) + (e.code === 'VALIDATION_ERROR' && fields ? ` (${fields})` : ''));
      },
    });
  }

  protected disable(v: Voucher): void {
    if (!confirm(this.transloco.translate('voucherAdmin.confirmDisable', { code: v.code }))) {
      return;
    }
    this.api.disableVoucher(this.scope(), v.id).subscribe({
      next: (updated) => this.vouchers.update((list) => list.map((x) => (x.id === updated.id ? updated : x))),
      error: (err) => this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) }),
    });
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  private load(scope: 'seller' | 'admin', page: number): void {
    this.loading.set(true);
    this.api.vouchers(scope, page, PAGE_SIZE).subscribe({
      next: (p) => {
        this.totalItems.set(p.totalItems);
        this.vouchers.set(p.items);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}

function emptyForm(): Omit<VoucherInput, 'startAt' | 'endAt'> {
  return { code: '', name: '', type: 'FIXED', value: 10000, minOrderValue: 0, maxDiscount: null, usageLimit: 100, perUserLimit: 1 };
}
