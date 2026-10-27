import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { AdminApi } from '../../core/api/admin.api';
import { ApiError, errorKey } from '../../core/api/api-error';
import { Banner, BannerInput } from '../../core/api/api.models';
import { DateTimePipe } from '../../shared/pipes/date-time.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { fromLocalInput, toLocalInput } from '../promotion/datetime-local';

/** Banner trang chủ: thêm / sửa / bật tắt / xóa, thứ tự hiển thị, khoảng thời gian (tùy chọn). */
@Component({
  selector: 'app-banner-admin',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Dialog, DateTimePipe, EmptyState],
  template: `
    <div *transloco="let t; prefix: 'admin.banners'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <div class="mb-4 flex items-center justify-between gap-3">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        <button pButton type="button" size="small" (click)="openForm(null)"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('create') }}</button>
      </div>
      @if (!loading() && banners().length === 0) {
        <app-empty-state [title]="t('empty')" [description]="t('emptyHint')" icon="pi-image" />
      }
      <ul class="flex flex-col gap-3">
        @for (b of banners(); track b.id) {
          <li class="flex flex-col gap-3 rounded-sm border border-gray-100 p-3 text-sm sm:flex-row sm:items-center" [attr.data-banner]="b.id">
            <img [src]="b.imageUrl" [alt]="b.title" class="aspect-[2/1] w-full shrink-0 rounded-sm object-cover sm:w-48" loading="lazy" />
            <div class="min-w-0 flex-1">
              <p class="font-medium">{{ b.title }} <span class="text-xs text-gray-500">#{{ b.position }}</span></p>
              @if (b.linkUrl) {
                <p class="truncate text-gray-500">{{ b.linkUrl }}</p>
              }
              <p class="text-xs text-gray-500">
                @if (b.startsAt || b.endsAt) {
                  {{ b.startsAt ? (b.startsAt | dateTime) : '…' }} → {{ b.endsAt ? (b.endsAt | dateTime) : '…' }}
                } @else {
                  {{ t('always') }}
                }
              </p>
            </div>
            <span class="shrink-0 rounded-sm px-2 py-0.5 text-xs" [class]="b.active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'">
              {{ t(b.active ? 'active' : 'inactive') }}</span>
            <div class="flex shrink-0 gap-1">
              <button pButton type="button" size="small" [text]="true" (click)="openForm(b)">{{ t('edit') }}</button>
              <button pButton type="button" size="small" [text]="true" severity="danger" (click)="remove(b)">{{ t('delete') }}</button>
            </div>
          </li>
        }
      </ul>

      <p-dialog [header]="t(editing() ? 'editTitle' : 'create')" [visible]="formOpen()" (visibleChange)="formOpen.set($event)" [modal]="true"
                [draggable]="false" [style]="{ width: '34rem' }" [breakpoints]="{ '640px': '95vw' }">
        <form class="grid grid-cols-2 gap-3 text-sm" (ngSubmit)="save()">
          <label class="col-span-2 flex flex-col gap-1">{{ t('field.title') }}
            <input name="title" required maxlength="100" class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="form().title" (ngModelChange)="patch({ title: $event })" />
          </label>
          <label class="col-span-2 flex flex-col gap-1">{{ t('field.imageUrl') }}
            <input name="imageUrl" type="url" required maxlength="500" class="rounded-sm border border-gray-300 px-2 py-1.5" placeholder="https://"
                   [ngModel]="form().imageUrl" (ngModelChange)="patch({ imageUrl: $event })" />
          </label>
          <label class="col-span-2 flex flex-col gap-1">{{ t('field.linkUrl') }}
            <input name="linkUrl" maxlength="500" class="rounded-sm border border-gray-300 px-2 py-1.5" placeholder="/c/thoi-trang-nam"
                   [ngModel]="form().linkUrl" (ngModelChange)="patch({ linkUrl: $event || null })" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.position') }}
            <input name="position" type="number" min="0" max="1000" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().position" (ngModelChange)="patch({ position: $event })" />
          </label>
          <label class="flex items-center gap-2 self-end pb-2">
            <input name="active" type="checkbox" [ngModel]="form().active" (ngModelChange)="patch({ active: $event })" />{{ t('field.active') }}
          </label>
          <label class="flex flex-col gap-1">{{ t('field.startsAt') }}
            <input name="startsAt" type="datetime-local" class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="startsAt()" (ngModelChange)="startsAt.set($event)" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.endsAt') }}
            <input name="endsAt" type="datetime-local" class="rounded-sm border border-gray-300 px-2 py-1.5" [ngModel]="endsAt()" (ngModelChange)="endsAt.set($event)" />
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
export default class BannerAdmin {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly banners = signal<Banner[]>([]);
  protected readonly loading = signal(true);
  protected readonly formOpen = signal(false);
  protected readonly editing = signal<Banner | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = signal<Omit<BannerInput, 'startsAt' | 'endsAt'>>(emptyForm());
  protected readonly startsAt = signal('');
  protected readonly endsAt = signal('');

  constructor() {
    this.load();
  }

  protected patch(p: Partial<BannerInput>): void {
    this.form.update((f) => ({ ...f, ...p }));
  }

  protected openForm(b: Banner | null): void {
    this.editing.set(b);
    this.form.set(b ? { title: b.title, imageUrl: b.imageUrl, linkUrl: b.linkUrl, position: b.position, active: b.active } : emptyForm());
    this.startsAt.set(b?.startsAt ? toLocalInput(new Date(b.startsAt)) : '');
    this.endsAt.set(b?.endsAt ? toLocalInput(new Date(b.endsAt)) : '');
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected save(): void {
    const body: BannerInput = {
      ...this.form(),
      title: this.form().title.trim(),
      imageUrl: this.form().imageUrl.trim(),
      position: Number(this.form().position) || 0,
      startsAt: this.startsAt() ? fromLocalInput(this.startsAt()) : null,
      endsAt: this.endsAt() ? fromLocalInput(this.endsAt()) : null,
    };
    const editing = this.editing();
    this.saving.set(true);
    this.formError.set(null);
    (editing ? this.api.updateBanner(editing.id, body) : this.api.createBanner(body)).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('admin.banners.saved') });
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        const e = ApiError.from(err);
        const fields = e.details.map((d) => d.field).join(', ');
        this.formError.set(this.transloco.translate(errorKey(err)) + (fields ? ` (${fields})` : ''));
      },
    });
  }

  protected remove(b: Banner): void {
    if (!confirm(this.transloco.translate('admin.banners.confirmDelete', { title: b.title }))) {
      return;
    }
    this.api.deleteBanner(b.id).subscribe({
      next: () => this.banners.update((list) => list.filter((x) => x.id !== b.id)),
      error: (err) => this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) }),
    });
  }

  private load(): void {
    this.api.banners().subscribe({
      next: (list) => {
        this.banners.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}

function emptyForm(): Omit<BannerInput, 'startsAt' | 'endsAt'> {
  return { title: '', imageUrl: '', linkUrl: null, position: 0, active: true };
}
