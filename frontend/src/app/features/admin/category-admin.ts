import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { AdminApi } from '../../core/api/admin.api';
import { ApiError, errorKey } from '../../core/api/api-error';
import { CategoryInput, CategoryNode } from '../../core/api/api.models';
import { EmptyState } from '../../shared/ui/empty-state';

/** Cấp sâu nhất của cây danh mục (0 = gốc) — khớp Category.MAX_LEVEL ở backend. */
export const MAX_CATEGORY_LEVEL = 2;

interface Row {
  node: CategoryNode;
  depth: number;
}

/** Duỗi cây thành danh sách theo thứ tự hiển thị (cha trước con, theo position). */
export function flattenTree(nodes: CategoryNode[], depth = 0): Row[] {
  return [...nodes]
    .sort((a, b) => a.position - b.position || a.id - b.id)
    .flatMap((node) => [{ node, depth }, ...flattenTree(node.children, depth + 1)]);
}

type FormValue = Omit<CategoryInput, 'parentId'>;

/** Quản lý cây danh mục (tối đa 3 cấp): thêm gốc / thêm con, sửa tên, slug, thứ tự, icon; xóa danh mục rỗng. */
@Component({
  selector: 'app-category-admin',
  imports: [FormsModule, TranslocoDirective, ButtonDirective, Dialog, EmptyState],
  template: `
    <div *transloco="let t; prefix: 'admin.categories'" class="rounded-sm bg-white p-4 shadow-sm md:p-6">
      <div class="mb-4 flex items-center justify-between gap-3">
        <h1 class="text-lg font-medium">{{ t('title') }}</h1>
        <button pButton type="button" size="small" (click)="openCreate(null)"><i class="pi pi-plus" aria-hidden="true"></i> {{ t('createRoot') }}</button>
      </div>
      @if (!loading() && rows().length === 0) {
        <app-empty-state [title]="t('empty')" icon="pi-sitemap" />
      }
      <ul class="flex flex-col divide-y divide-gray-50 text-sm">
        @for (row of rows(); track row.node.id) {
          <li class="flex flex-wrap items-center gap-2 py-2" [style.padding-left.rem]="row.depth * 1.5" [attr.data-category]="row.node.id">
            @if (row.node.iconUrl) {
              <img [src]="row.node.iconUrl" alt="" class="size-7 shrink-0 rounded-sm object-cover" loading="lazy" />
            } @else {
              <i class="pi pi-folder w-7 shrink-0 text-center text-gray-400" aria-hidden="true"></i>
            }
            <div class="min-w-48 flex-1">
              <p class="truncate font-medium">{{ row.node.name }} <span class="text-xs font-normal text-gray-500">#{{ row.node.position }}</span></p>
              <p class="truncate text-xs text-gray-500">/c/{{ row.node.slug }}</p>
            </div>
            <div class="ml-9 flex shrink-0 gap-1 sm:ml-0">
              @if (row.node.level < maxLevel) {
                <button pButton type="button" size="small" [text]="true" (click)="openCreate(row.node)">{{ t('addChild') }}</button>
              }
              <button pButton type="button" size="small" [text]="true" (click)="openEdit(row.node)">{{ t('edit') }}</button>
              <button pButton type="button" size="small" [text]="true" severity="danger" (click)="remove(row.node)">{{ t('delete') }}</button>
            </div>
          </li>
        }
      </ul>

      <p-dialog [header]="dialogTitle()" [visible]="formOpen()" (visibleChange)="formOpen.set($event)" [modal]="true"
                [draggable]="false" [style]="{ width: '30rem' }" [breakpoints]="{ '640px': '95vw' }">
        <form class="grid grid-cols-2 gap-3 text-sm" (ngSubmit)="save()">
          @if (parent(); as p) {
            <p class="col-span-2 text-gray-500">{{ t('parent', { name: p.name }) }}</p>
          }
          <label class="col-span-2 flex flex-col gap-1">{{ t('field.name') }}
            <input name="name" required maxlength="100" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().name" (ngModelChange)="patch({ name: $event })" />
          </label>
          <label class="col-span-2 flex flex-col gap-1">{{ t('field.slug') }}
            <input name="slug" maxlength="120" class="rounded-sm border border-gray-300 px-2 py-1.5" [placeholder]="t('slugHint')"
                   [ngModel]="form().slug" (ngModelChange)="patch({ slug: $event || null })" />
          </label>
          <label class="flex flex-col gap-1">{{ t('field.position') }}
            <input name="position" type="number" min="0" max="1000" class="rounded-sm border border-gray-300 px-2 py-1.5"
                   [ngModel]="form().position" (ngModelChange)="patch({ position: $event })" />
          </label>
          <label class="col-span-2 flex flex-col gap-1">{{ t('field.iconUrl') }}
            <input name="iconUrl" type="url" maxlength="500" class="rounded-sm border border-gray-300 px-2 py-1.5" placeholder="https://"
                   [ngModel]="form().iconUrl" (ngModelChange)="patch({ iconUrl: $event || null })" />
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
export default class CategoryAdmin {
  private readonly api = inject(AdminApi);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly maxLevel = MAX_CATEGORY_LEVEL;
  private readonly tree = signal<CategoryNode[]>([]);
  protected readonly rows = computed(() => flattenTree(this.tree()));
  protected readonly loading = signal(true);
  protected readonly formOpen = signal(false);
  /** null = đang tạo mới. */
  protected readonly editing = signal<CategoryNode | null>(null);
  /** Danh mục cha khi tạo danh mục con. */
  protected readonly parent = signal<CategoryNode | null>(null);
  protected readonly form = signal<FormValue>(emptyForm());
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly dialogTitle = computed(() =>
    this.transloco.translate(`admin.categories.${this.editing() ? 'editTitle' : this.parent() ? 'addChild' : 'createRoot'}`),
  );

  constructor() {
    this.load();
  }

  protected patch(p: Partial<FormValue>): void {
    this.form.update((f) => ({ ...f, ...p }));
  }

  protected openCreate(parent: CategoryNode | null): void {
    this.editing.set(null);
    this.parent.set(parent);
    this.form.set({ ...emptyForm(), position: (parent ? parent.children : this.tree()).length });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(node: CategoryNode): void {
    this.editing.set(node);
    this.parent.set(null);
    this.form.set({ name: node.name, slug: node.slug, position: node.position, iconUrl: node.iconUrl });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected save(): void {
    const f = this.form();
    const body: FormValue = {
      name: f.name.trim(),
      slug: f.slug?.trim() || null,
      position: Number(f.position) || 0,
      iconUrl: f.iconUrl?.trim() || null,
    };
    const editing = this.editing();
    this.saving.set(true);
    this.formError.set(null);
    (editing ? this.api.updateCategory(editing.id, body) : this.api.createCategory({ ...body, parentId: this.parent()?.id ?? null })).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.messages.add({ severity: 'success', summary: this.transloco.translate('admin.categories.saved') });
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        const fields = ApiError.from(err).details.map((d) => d.field).join(', ');
        this.formError.set(this.transloco.translate(errorKey(err)) + (fields ? ` (${fields})` : ''));
      },
    });
  }

  protected remove(node: CategoryNode): void {
    if (!confirm(this.transloco.translate('admin.categories.confirmDelete', { name: node.name }))) {
      return;
    }
    this.api.deleteCategory(node.id).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: this.transloco.translate('admin.categories.deleted') });
        this.load();
      },
      error: (err) => this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) }),
    });
  }

  private load(): void {
    this.api.categoryTree().subscribe({
      next: (tree) => {
        this.tree.set(tree);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.messages.add({ severity: 'error', summary: this.transloco.translate(errorKey(err)) });
      },
    });
  }
}

function emptyForm(): FormValue {
  return { name: '', slug: null, position: 0, iconUrl: null };
}
