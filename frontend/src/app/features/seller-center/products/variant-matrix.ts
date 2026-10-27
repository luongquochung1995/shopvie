import { Variant, VariantInput, VariantOption } from '../../../core/api/api.models';

/** Tối đa 2 nhóm phân loại × 20 giá trị, tổng ≤ 200 tổ hợp (khớp backend). */
export const MAX_OPTION_GROUPS = 2;
export const MAX_OPTION_VALUES = 20;
export const MAX_VARIANTS = 200;

/** Một dòng trong bảng SKU của form sản phẩm. */
export interface VariantRow {
  /** Khóa tổ hợp, vd. "Đỏ␟M" ('' khi không có phân loại). */
  key: string;
  id: number | null;
  optionValues: Record<string, string>;
  price: number | null;
  stock: number | null;
  sku: string;
}

const SEP = '\u001f';

/** Nhóm phân loại hợp lệ để sinh tổ hợp: tên và giá trị đã trim, bỏ giá trị rỗng/trùng; bỏ nhóm không tên/không giá trị. */
export function usableOptions(options: VariantOption[]): VariantOption[] {
  return options
    .map((o) => ({ name: o.name.trim(), values: unique(o.values.map((v) => v.trim()).filter(Boolean)) }))
    .filter((o) => o.name && o.values.length > 0);
}

/** Tích Descartes theo thứ tự nhóm: Màu [Đỏ, Xanh] × Size [M, L] → Đỏ/M, Đỏ/L, Xanh/M, Xanh/L. */
export function combinations(options: VariantOption[]): Record<string, string>[] {
  return options.reduce<Record<string, string>[]>(
    (acc, option) => acc.flatMap((prefix) => option.values.map((value) => ({ ...prefix, [option.name]: value }))),
    [{}],
  );
}

export function comboKey(options: VariantOption[], values: Record<string, string>): string {
  return options.map((o) => values[o.name] ?? '').join(SEP);
}

/**
 * Sinh lại bảng SKU khi nhóm phân loại thay đổi, giữ nguyên dữ liệu (id, giá, kho, SKU) của các tổ hợp còn tồn tại.
 * Chuyển từ "không phân loại" sang có phân loại: giá/kho của SKU duy nhất được dùng làm mặc định cho các dòng mới.
 */
export function syncRows(options: VariantOption[], previous: VariantRow[]): VariantRow[] {
  const byKey = new Map(previous.map((r) => [r.key, r]));
  const single = previous.length === 1 && previous[0].key === '' ? previous[0] : null;
  return combinations(options).map((optionValues) => {
    const key = comboKey(options, optionValues);
    const old = byKey.get(key);
    if (old) {
      return { ...old, optionValues };
    }
    return { key, id: null, optionValues, price: single?.price ?? null, stock: single?.stock ?? null, sku: '' };
  });
}

/** Dòng SKU từ sản phẩm đang sửa (khóa theo thứ tự nhóm của sản phẩm). */
export function rowsFromVariants(options: VariantOption[], variants: Variant[]): VariantRow[] {
  return variants.map((v) => ({
    key: comboKey(options, v.optionValues),
    id: v.id,
    optionValues: { ...v.optionValues },
    price: v.price,
    stock: v.stock,
    sku: v.sku,
  }));
}

export function toVariantInputs(rows: VariantRow[]): VariantInput[] {
  return rows.map((r) => ({
    id: r.id,
    optionValues: r.optionValues,
    price: r.price ?? 0,
    stock: r.stock ?? 0,
    sku: r.sku.trim() || null,
    imageUrl: null,
  }));
}

export type OptionProblem = 'nameRequired' | 'nameDuplicate' | 'valuesRequired' | 'valueDuplicate' | 'tooManyValues';

/** Lỗi của từng nhóm phân loại (null = hợp lệ), dùng để hiển thị ngay dưới nhóm. */
export function optionProblems(options: VariantOption[]): (OptionProblem | null)[] {
  const names = options.map((o) => o.name.trim().toLowerCase());
  return options.map((o, i) => {
    const values = o.values.map((v) => v.trim().toLowerCase()).filter(Boolean);
    if (!names[i]) return 'nameRequired';
    if (names.indexOf(names[i]) !== i) return 'nameDuplicate';
    if (values.length === 0) return 'valuesRequired';
    if (new Set(values).size !== values.length) return 'valueDuplicate';
    if (values.length > MAX_OPTION_VALUES) return 'tooManyValues';
    return null;
  });
}

function unique(values: string[]): string[] {
  const seen = new Set<string>();
  return values.filter((v) => {
    const k = v.toLowerCase();
    return seen.has(k) ? false : (seen.add(k), true);
  });
}
