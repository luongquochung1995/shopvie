import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Paginator } from 'primeng/paginator';
import { ApiError, errorKey } from '../../core/api/api-error';
import { FlashSale, FlashSaleInput } from '../../core/api/api.models';
import { PromotionApi } from '../../core/api/promotion.api';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { VndPipe } from '../../shared/pipes/vnd.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { fromLocalInput, toLocalInput } from '../promotion/datetime-local';

const PAGE_SIZE = 20;

type ItemRow = FlashSaleInput['items'][number];

/** Admin tạo / hủy phiên flash sale (SKU theo id phân loại, giá sale, quota, giới hạn mỗi người). */
@Component({
  selector: 'app-flash-sale-admin',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Dialog, Paginator, DateTimePipe, VndPipe, EmptyState],
  template: `
    <div *transloco="let t; prefix: 'admin.flashSales'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <div class="mb-4 flex items-center justify-between gap-3">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        <button pButton type="button" size="small" (click)="openCreate()"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('create') }}</button>
      </div>
      @if (!loading() && sales().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-bolt" />
      }
      <ul class="flex flex-col gap-3">
        @for (s of sales(); track s.id) {
          <li class="rounded-sm border border-gray-100 p-3 text-sm" [attr.data-sale]="s.id">
            <div class="flex flex-wrap items-center gap-2">
              <b>{{ s.name }}</b>
              <span class="rounded-sm px-2 py-0.5 text-xs" [class]="s.status === 'CANCELLED' ? 'bg-red-50 text-red-700' : s.running ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'">
                {{ t(s.status === 'CANCELLED' ? 'cancelled' : s.running ? 'running' : 'notRunning') }}
              </span>
              <span class="text-xs text-gray-500">{{ s.startAt | dateTime }} → {{ s.endAt | dateTime }}</span>
              @if (s.status === 'ACTIVE' && !isEnded(s)) {
                <button pButton type="button" size="small" [text]="true" severity="danger" class="ml-auto" (click)="cancel(s)">{{ t('cancel') }}</button>
              }
            </div>
            <ul class="mt-2 grid gap-1 text-xs text-gray-600 sm:grid-cols-2">
              @for (i of s.items; track i.id) {
                <li>{{ i.productName }}@if (i.variantName) { ({{ i.variantName }}) } — {{ i.salePrice | vnd }}
                  <span class="text-gray-400 line-through">{{ i.originalPrice | vnd }}</span> · {{ t('soldOf', { sold: i.quota - i.remaining, quota: i.quota }) }}</li>
              }
            </ul>
          </li>
        }
      </ul>
      @if (totalItems() > pageSize) {
        <p-paginator styleClass="mt-2 !bg-transparent" [first]="page() * pageSize" [rows]="pageSize" [totalRecords]="totalItems()"
                     (onPageChange)="page.set($event.page ?? 0)" />
      }

      <p-dialog [header]="t('create')" [visible]="formOpen()" (visibleChange)="formOpen.set($event)" [modal]="true" [draggable]="false"
                [style]="{ width: '44rem' }" [breakpoints]="{ '720px': '95vw' }">
        <form class="flex flex-col gap-3 text-sm" (ngSubmit)="save()">
          <label class="flex flex-col gap-1">{{ t('name') }}
            <input name="name" required maxlength="100" class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </label>
          <div class="grid grid-cols-2 gap-3">
            <label class="flex flex-col gap-1">{{ t('startAt') }}
              <input name="startAt" type="datetime-local" required class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="startAt()" (ngModelChange)="startAt.set($event)" />
            </label>
            <label class="flex flex-col gap-1">{{ t('endAt') }}
              <input name="endAt" type="datetime-local" required class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="endAt()" (ngModelChange)="endAt.set($event)" />
            </label>
          </div>
          <table class="w-full text-left">
            <thead class="text-xs text-gray-500">
              <tr><th class="font-normal">{{ t('variantId') }}</th><th class="font-normal">{{ t('salePrice') }}</th><th class="font-normal">{{ t('quota') }}</th><th class="font-normal">{{ t('perUser') }}</th><th></th></tr>
            </thead>
            <tbody>
              @for (row of items(); track $index; let i = $index) {
                <tr>
                  <td class="pr-2"><input [name]="'variant' + i" type="number" required min="1" class="w-full rounded-sm border border-gray-300 px-2 py-1"
                                          [attr.aria-label]="t('variantId')" [ngModel]="row.variantId" (ngModelChange)="patchItem(i, { variantId: $event })" /></td>
                  <td class="pr-2"><input [name]="'price' + i" type="number" required min="1" class="w-full rounded-sm border border-gray-300 px-2 py-1"
                                          [attr.aria-label]="t('salePrice')" [ngModel]="row.salePrice" (ngModelChange)="patchItem(i, { salePrice: $event })" /></td>
                  <td class="pr-2"><input [name]="'quota' + i" type="number" required min="1" class="w-full rounded-sm border border-gray-300 px-2 py-1"
                                          [attr.aria-label]="t('quota')" [ngModel]="row.quota" (ngModelChange)="patchItem(i, { quota: $event })" /></td>
                  <td class="pr-2"><input [name]="'limit' + i" type="number" required min="1" max="10" class="w-full rounded-sm border border-gray-300 px-2 py-1"
                                          [attr.aria-label]="t('perUser')" [ngModel]="row.perUserLimit" (ngModelChange)="patchItem(i, { perUserLimit: $event })" /></td>
                  <td>
                    <button type="button" class="p-1 text-gray-500" [attr.aria-label]="t('removeItem')" [disabled]="items().length === 1"
                            (click)="removeItem(i)"><i class="pi pi-trash" aria-hidden="true"></i></button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <button type="button" class="self-start text-blue-600" (click)="addItem()"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('addItem') }}</button>
          @if (formError(); as err) {
            <p class="text-red-600" role="alert">{{ err }}</p>
          }
          <div class="flex justify-end gap-2">
            <button pButton type="button" [text]="true" severity="secondary" (click)="formOpen.set(false)">{{ t('close') }}</button>
            <button pButton type="submit" [disabled]="saving()">{{ t('save') }}</button>
          </div>
        </form>
      </p-dialog>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class FlashSaleAdmin {
  private readonly api = inject(PromotionApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly sales = signal<FlashSale[]>([]);
  protected readonly loading = signal(true);
  protected readonly page = signal(0);
  protected readonly totalItems = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly name = signal('');
  protected readonly startAt = signal('');
  protected readonly endAt = signal('');
  protected readonly items = signal<ItemRow[]>([]);

  constructor() {
    effect(() => this.load(this.page()));
  }

  protected isEnded(s: FlashSale): boolean {
    return Date.parse(s.endAt) <= Date.now();
  }

  protected openCreate(): void {
    const start = new Date(Date.now() + 3600_000);
    start.setMinutes(0, 0, 0);
    this.name.set('');
    this.startAt.set(toLocalInput(start));
    this.endAt.set(toLocalInput(new Date(start.getTime() + 2 * 3600_000)));
    this.items.set([newRow()]);
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected patchItem(index: number, patch: Partial<ItemRow>): void {
    this.items.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  protected addItem(): void {
    this.items.update((rows) => [...rows, newRow()]);
  }

  protected removeItem(index: number): void {
    this.items.update((rows) => rows.filter((_, i) => i !== index));
  }

  protected save(): void {
    const body: FlashSaleInput = {
      name: this.name().trim(),
      startAt: fromLocalInput(this.startAt()),
      endAt: fromLocalInput(this.endAt()),
      items: this.items().map((r) => ({
        variantId: Number(r.variantId),
        salePrice: Number(r.salePrice),
        quota: Number(r.quota),
        perUserLimit: Number(r.perUserLimit),
      })),
    };
    this.saving.set(true);
    this.formError.set(null);
    this.api.createFlashSale(body).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('admin.flashSales.created') });
        this.page() === 0 ? this.load(0) : this.page.set(0);
      },
      error: (err) => {
        this.saving.set(false);
        const e = ApiError.from(err);
        const details = e.details.map((d) => `${d.field}: ${d.message}`).join('; ');
        this.formError.set(this.transloco.translate(errorKey(err)) + (details ? ` — ${details}` : ''));
      },
    });
  }

  protected cancel(s: FlashSale): void {
    if (!confirm(this.transloco.translate('admin.flashSales.confirmCancel', { name: s.name }))) {
      return;
    }
    this.api.cancelFlashSale(s.id).subscribe({
      next: (updated) => this.sales.update((list) => list.map((x) => (x.id === updated.id ? updated : x))),
      error: (err) => this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) }),
    });
  }

  private load(page: number): void {
    this.loading.set(true);
    this.api.adminFlashSales(page, PAGE_SIZE).subscribe({
      next: (p) => {
        this.sales.set(p.items);
        this.totalItems.set(p.totalItems);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}

function newRow(): ItemRow {
  return { variantId: 0, salePrice: 0, quota: 100, perUserLimit: 1 };
}
