import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Observable } from 'rxjs';
import { errorKey } from '../../core/api/api-error';
import { Address, AddressInput } from '../../core/api/api.models';
import { UserApi } from '../../core/api/user.api';
import { EmptyState } from '../../shared/ui/empty-state';
import { AddressForm } from '../../shared/ui/address-form';
import { DivisionsService, formatAddressLine } from '../../shared/divisions.service';

@Component({
  selector: 'app-addresses',
  imports: [TranslocoDirective, ButtonDirective, Dialog, AddressForm, EmptyState],
  template: `
    <ng-container *transloco="let t; prefix: 'address'">
      <div class="mb-4 flex items-center justify-between gap-2 border-b border-gray-100 pb-4">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        <button pButton type="button" (click)="openCreate()"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('add') }}</button>
      </div>

      @if (loading()) {
        <p class="py-8 text-center text-gray-500">{{ t('loading') }}</p>
      } @else if (addresses().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-map-marker" />
      } @else {
        <ul class="divide-y divide-gray-100">
          @for (a of addresses(); track a.id) {
            <li class="flex flex-col gap-2 py-4 sm:flex-row sm:justify-between">
              <div class="min-w-0 text-sm">
                <p><span class="font-medium">{{ a.receiverName }}</span> <span class="mx-2 text-gray-300">|</span>
                  <span class="text-gray-500">{{ a.phone }}</span></p>
                <p class="text-gray-500">{{ lineOf(a) }}</p>
                @if (a.isDefault) {
                  <span class="mt-1 inline-block border border-brand-500 px-1.5 text-xs text-brand-500">{{ t('default') }}</span>
                }
              </div>
              <div class="flex shrink-0 flex-col items-start gap-2 sm:items-end">
                <div class="flex gap-3 text-sm text-blue-600">
                  <button type="button" (click)="openEdit(a)">{{ t('edit') }}</button>
                  @if (!a.isDefault) {
                    <button type="button" (click)="remove(a)">{{ t('delete') }}</button>
                  }
                </div>
                <button pButton type="button" size="small" [outlined]="true" severity="secondary"
                        [disabled]="a.isDefault || busy()" (click)="makeDefault(a)">{{ t('makeDefault') }}</button>
              </div>
            </li>
          }
        </ul>
      }

      <p-dialog [header]="t(editing() ? 'editTitle' : 'newTitle')" [visible]="dialogOpen()" (visibleChange)="dialogOpen.set($event)"
                [modal]="true" [draggable]="false" [style]="{ width: '36rem' }" [breakpoints]="{ '640px': '95vw' }">
        @if (dialogOpen()) {
          <app-address-form [address]="editing()" [saving]="busy()" (saved)="save($event)" (cancelled)="dialogOpen.set(false)" />
        }
      </p-dialog>
    </ng-container>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class Addresses {
  private readonly api = inject(UserApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  private readonly provinces = toSignal(inject(DivisionsService).provinces(), { initialValue: [] });

  protected readonly addresses = signal<Address[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<Address | null>(null);
  protected readonly lineOf = (a: Address) => formatAddressLine(this.provinces(), a);

  constructor() {
    this.reload();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(a: Address): void {
    this.editing.set(a);
    this.dialogOpen.set(true);
  }

  protected save(input: AddressInput): void {
    const current = this.editing();
    this.run(
      current ? this.api.updateAddress(current.id, input) : this.api.createAddress(input),
      current ? 'address.updated' : 'address.created',
      () => this.dialogOpen.set(false),
    );
  }

  protected makeDefault(a: Address): void {
    this.run(this.api.setDefaultAddress(a.id), 'address.defaultSet');
  }

  protected remove(a: Address): void {
    if (confirm(this.transloco.translate('address.confirmDelete', { name: a.receiverName }))) {
      this.run(this.api.deleteAddress(a.id), 'address.deleted');
    }
  }

  private run(request: Observable<unknown>, successKey: string, after?: () => void): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.toast('success', successKey);
        after?.();
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        this.toast('error', errorKey(err));
      },
    });
  }

  private reload(): void {
    this.api.addresses().subscribe({
      next: (list) => {
        this.addresses.set(list);
        this.loading.set(false);
        this.busy.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.busy.set(false);
        this.toast('error', errorKey(err));
      },
    });
  }

  private toast(severity: string, key: string): void {
    this.messages.add({ severity, summary: this.transloco.translate(key) });
  }
}
