import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { map } from 'rxjs';
import { ApiError, errorKey } from '../../../core/api/api-error';
import { CategoryNode, ProductInput, SellerProductDetail, VariantOption } from '../../../core/api/api.models';
import { CatalogApi } from '../../../core/api/catalog.api';
import { SellerApi } from '../../../core/api/seller.api';
import { UserApi } from '../../../core/api/user.api';
import { SellerShopStore } from '../seller-shop.store';
import {
  MAX_OPTION_GROUPS,
  MAX_OPTION_VALUES,
  MAX_VARIANTS,
  optionProblems,
  rowsFromVariants,
  syncRows,
  toVariantInputs,
  usableOptions,
  VariantRow,
} from './variant-matrix';

const MAX_IMAGES = 9;
const MAX_ATTRIBUTES = 20;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MIN_PRICE = 1_000;
const MAX_PRICE = 999_999_999;
const MAX_STOCK = 999_999;

interface CategoryGroup {
  label: string;
  items: { label: string; value: number }[];
}

/** Danh mục cấp thấp nhất, nhóm theo danh mục gốc (sản phẩm chỉ được gắn vào danh mục lá). */
export function leafCategoryGroups(roots: CategoryNode[]): CategoryGroup[] {
  const leaves = (node: CategoryNode, path: string[]): { label: string; value: number }[] =>
    node.children.length === 0
      ? [{ label: path.join(' › '), value: node.id }]
      : node.children.flatMap((c) => leaves(c, [...path, c.name]));
  return roots
    .map((root) => ({ label: root.name, items: root.children.flatMap((c) => leaves(c, [c.name])) }))
    .filter((g) => g.items.length > 0);
}

/** Thêm / sửa sản phẩm kèm phân loại hàng (tối đa 2 nhóm) và bảng giá/kho/SKU sinh tự động theo tổ hợp. */
@Component({
  selector: 'app-seller-product-form',
  imports: [RouterLink, FormsModule, TranslocoDirective, TranslocoPipe, ButtonDirective, InputText, Select, Textarea, ToggleSwitch],
  templateUrl: './product-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ProductForm {
  private readonly api = inject(SellerApi);
  private readonly userApi = inject(UserApi);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly transloco = inject(TranslocoService);
  protected readonly shop = inject(SellerShopStore);

  /** Route param :id (không có = thêm mới). */
  readonly id = input<string>();
  protected readonly productId = computed(() => (this.id() ? Number(this.id()) : null));

  protected readonly categoryGroups = toSignal(inject(CatalogApi).categories().pipe(map(leafCategoryGroups)), {
    initialValue: [] as CategoryGroup[],
  });

  protected readonly limits = { MAX_IMAGES, MAX_OPTION_GROUPS, MAX_OPTION_VALUES, MAX_VARIANTS, MIN_PRICE, MAX_PRICE, MAX_STOCK };

  // ----- Trạng thái form
  protected readonly loaded = signal<SellerProductDetail | null>(null);
  protected readonly loadErrorKey = signal<string | null>(null);
  protected readonly name = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly description = signal('');
  protected readonly images = signal<string[]>([]);
  protected readonly imageUrlDraft = signal('');
  protected readonly uploading = signal(0);
  protected readonly attributes = signal<{ key: string; value: string }[]>([]);
  protected readonly hasVariants = signal(false);
  protected readonly options = signal<VariantOption[]>([]);
  protected readonly valueDrafts = signal<string[]>([]);
  protected readonly rows = signal<VariantRow[]>(syncRows([], []));
  protected readonly bulkPrice = signal<number | null>(null);
  protected readonly bulkStock = signal<number | null>(null);
  protected readonly submitted = signal(false);
  protected readonly saving = signal(false);

  // ----- Kiểm tra hợp lệ (hiển thị sau lần bấm Lưu đầu tiên)
  protected readonly nameError = computed(() => {
    const n = this.name().trim().length;
    return n < 3 || n > 255;
  });
  protected readonly categoryError = computed(() => this.categoryId() === null);
  protected readonly imagesError = computed(() => this.images().length === 0);
  protected readonly problems = computed(() => (this.hasVariants() ? optionProblems(this.options()) : []));
  protected readonly optionGroupsError = computed(() => this.hasVariants() && usableOptions(this.options()).length === 0);
  protected readonly tooManyVariants = computed(() => this.rows().length > MAX_VARIANTS);
  protected readonly priceError = (price: number | null) =>
    price === null || !Number.isInteger(price) || price < MIN_PRICE || price > MAX_PRICE;
  protected readonly stockError = (stock: number | null) =>
    stock === null || !Number.isInteger(stock) || stock < 0 || stock > MAX_STOCK;
  protected readonly rowsError = computed(() => this.rows().some((r) => this.priceError(r.price) || this.stockError(r.stock)));
  protected readonly valid = computed(
    () =>
      !this.nameError() &&
      !this.categoryError() &&
      !this.imagesError() &&
      this.problems().every((p) => p === null) &&
      !this.optionGroupsError() &&
      !this.tooManyVariants() &&
      !this.rowsError(),
  );

  constructor() {
    effect(() => {
      const id = this.productId();
      if (id !== null) {
        untracked(() => this.load(id));
      }
    });
  }

  // ----- Ảnh

  protected onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []).slice(0, MAX_IMAGES - this.images().length);
    input.value = '';
    for (const file of files) {
      if (file.size > MAX_IMAGE_BYTES) {
        this.toast('warn', 'errors.FILE_TOO_LARGE');
        continue;
      }
      this.uploading.update((n) => n + 1);
      this.userApi.uploadImage(file).subscribe({
        next: (media) => {
          this.uploading.update((n) => n - 1);
          this.images.update((list) => (list.length < MAX_IMAGES ? [...list, media.url] : list));
        },
        error: (err) => {
          this.uploading.update((n) => n - 1);
          this.toast('error', errorKey(err));
        },
      });
    }
  }

  /** Thêm ảnh bằng URL có sẵn (hữu ích khi chưa có kho lưu trữ ảnh ở môi trường dev). */
  protected addImageUrl(): void {
    const url = this.imageUrlDraft().trim();
    if (!/^https?:\/\/\S+$/.test(url)) {
      this.toast('warn', 'seller.productForm.imageUrlInvalid');
      return;
    }
    if (this.images().length < MAX_IMAGES && !this.images().includes(url)) {
      this.images.update((list) => [...list, url]);
    }
    this.imageUrlDraft.set('');
  }

  protected removeImage(index: number): void {
    this.images.update((list) => list.filter((_, i) => i !== index));
  }

  protected makeCover(index: number): void {
    this.images.update((list) => [list[index], ...list.filter((_, i) => i !== index)]);
  }

  // ----- Thuộc tính

  protected addAttribute(): void {
    if (this.attributes().length < MAX_ATTRIBUTES) {
      this.attributes.update((list) => [...list, { key: '', value: '' }]);
    }
  }

  protected updateAttribute(index: number, field: 'key' | 'value', value: string): void {
    this.attributes.update((list) => list.map((a, i) => (i === index ? { ...a, [field]: value } : a)));
  }

  protected removeAttribute(index: number): void {
    this.attributes.update((list) => list.filter((_, i) => i !== index));
  }

  // ----- Phân loại hàng

  protected toggleVariants(enabled: boolean): void {
    this.hasVariants.set(enabled);
    if (enabled && this.options().length === 0) {
      this.options.set([{ name: '', values: [] }]);
      this.valueDrafts.set(['']);
    }
    if (!enabled) {
      // Tắt phân loại: giữ giá/kho của SKU đầu tiên cho SKU duy nhất
      const first = this.rows()[0];
      this.rows.set([{ key: '', id: null, optionValues: {}, price: first?.price ?? null, stock: first?.stock ?? null, sku: '' }]);
      return;
    }
    this.syncRows();
  }

  protected addOptionGroup(): void {
    if (this.options().length < MAX_OPTION_GROUPS) {
      this.options.update((list) => [...list, { name: '', values: [] }]);
      this.valueDrafts.update((list) => [...list, '']);
    }
  }

  protected removeOptionGroup(index: number): void {
    this.options.update((list) => list.filter((_, i) => i !== index));
    this.valueDrafts.update((list) => list.filter((_, i) => i !== index));
    if (this.options().length === 0) {
      this.toggleVariants(false);
      return;
    }
    this.syncRows();
  }

  protected renameOptionGroup(index: number, name: string): void {
    this.options.update((list) => list.map((o, i) => (i === index ? { ...o, name } : o)));
    this.syncRows();
  }

  protected setValueDraft(index: number, value: string): void {
    this.valueDrafts.update((list) => list.map((v, i) => (i === index ? value : v)));
  }

  /** Thêm giá trị từ ô nhập (cho phép nhiều giá trị cách nhau bởi dấu phẩy). */
  protected addValues(index: number, event?: Event): void {
    event?.preventDefault();
    const incoming = (this.valueDrafts()[index] ?? '').split(',').map((v) => v.trim()).filter(Boolean);
    if (incoming.length === 0) {
      return;
    }
    this.options.update((list) =>
      list.map((o, i) => {
        if (i !== index) return o;
        const existing = new Set(o.values.map((v) => v.toLowerCase()));
        const added = incoming.filter((v) => !existing.has(v.toLowerCase()) && (existing.add(v.toLowerCase()), true));
        return { ...o, values: [...o.values, ...added].slice(0, MAX_OPTION_VALUES) };
      }),
    );
    this.setValueDraft(index, '');
    this.syncRows();
  }

  protected removeValue(groupIndex: number, valueIndex: number): void {
    this.options.update((list) =>
      list.map((o, i) => (i === groupIndex ? { ...o, values: o.values.filter((_, j) => j !== valueIndex) } : o)),
    );
    this.syncRows();
  }

  /** "150.000" / "150,000" / "150000" → 150000; ô trống → null. */
  protected parseAmount(value: unknown): number | null {
    const digits = String(value ?? '').replace(/\D/g, '').slice(0, 12);
    return digits ? Number(digits) : null;
  }

  protected updateRow(index: number, field: 'price' | 'stock' | 'sku', value: unknown): void {
    const parsed = field === 'sku' ? String(value ?? '') : this.parseAmount(value);
    this.rows.update((list) => list.map((r, i) => (i === index ? { ...r, [field]: parsed } : r)));
  }

  protected applyBulk(): void {
    const price = this.bulkPrice();
    const stock = this.bulkStock();
    this.rows.update((list) =>
      list.map((r) => ({ ...r, price: price ?? r.price, stock: stock ?? r.stock })),
    );
  }

  protected rowLabel(row: VariantRow): string {
    return Object.values(row.optionValues).join(' / ');
  }

  private syncRows(): void {
    this.rows.set(syncRows(usableOptions(this.options()), this.rows()));
  }

  // ----- Lưu

  protected save(visible: boolean): void {
    this.submitted.set(true);
    if (!this.valid() || this.uploading() > 0) {
      this.toast('warn', 'seller.productForm.fixErrors');
      return;
    }
    const body: ProductInput = {
      categoryId: this.categoryId()!,
      name: this.name().trim(),
      description: this.description().trim() || null,
      attributes: Object.fromEntries(
        this.attributes()
          .map((a) => [a.key.trim(), a.value.trim()])
          .filter(([k, v]) => k && v),
      ),
      images: this.images(),
      variantOptions: this.hasVariants() ? usableOptions(this.options()) : [],
      variants: toVariantInputs(this.rows()),
    };
    const id = this.productId();
    this.saving.set(true);
    const request = id === null ? this.api.createProduct({ ...body, visible }) : this.api.updateProduct(id, body);
    request.subscribe({
      next: (product) => {
        this.saving.set(false);
        if (id === null) {
          this.toast('success', visible ? 'seller.productForm.created' : 'seller.productForm.createdHidden');
          this.router.navigate(['/seller-center/products']);
        } else {
          this.toast('success', 'seller.productForm.updated');
          this.fill(product);
        }
      },
      error: (err) => {
        this.saving.set(false);
        const e = ApiError.from(err);
        this.messages.add({
          severity: 'error',
          summary: this.transloco.translate(errorKey(err)),
          detail: e.code === 'SKU_ALREADY_EXISTS' ? e.details[0]?.message : undefined,
        });
      },
    });
  }

  private load(id: number): void {
    this.api.product(id).subscribe({
      next: (p) => this.fill(p),
      error: (err) => this.loadErrorKey.set(ApiError.from(err).status === 404 ? 'errors.PRODUCT_NOT_FOUND' : errorKey(err)),
    });
  }

  private fill(p: SellerProductDetail): void {
    this.loaded.set(p);
    this.name.set(p.name);
    this.categoryId.set(p.categoryId);
    this.description.set(p.description ?? '');
    this.images.set([...p.images]);
    this.attributes.set(Object.entries(p.attributes ?? {}).map(([key, value]) => ({ key, value: String(value) })));
    this.hasVariants.set(p.variantOptions.length > 0);
    this.options.set(p.variantOptions.map((o) => ({ name: o.name, values: [...o.values] })));
    this.valueDrafts.set(p.variantOptions.map(() => ''));
    // Sắp SKU theo thứ tự tổ hợp hiện tại để bảng hiển thị ổn định
    const byKey = new Map(rowsFromVariants(p.variantOptions, p.variants).map((r) => [r.key, r]));
    this.rows.set(syncRows(p.variantOptions, [...byKey.values()]));
    this.submitted.set(false);
  }

  private toast(severity: string, key: string): void {
    this.messages.add({ severity, summary: this.transloco.translate(key) });
  }
}
