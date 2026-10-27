import { Variant, VariantOption } from '../../core/api/api.models';

/** Lựa chọn hiện tại: tên nhóm phân loại → giá trị (vd. {"Màu sắc": "Đỏ"}). */
export type Selection = Record<string, string>;

function matches(variant: Variant, selection: Selection): boolean {
  return Object.entries(selection).every(([name, value]) => variant.optionValues[name] === value);
}

/** SKU tương ứng khi đã chọn đủ mọi nhóm phân loại; sản phẩm không có phân loại → SKU duy nhất. */
export function findVariant(options: VariantOption[], variants: Variant[], selection: Selection): Variant | undefined {
  if (options.some((o) => !selection[o.name])) {
    return options.length === 0 ? variants[0] : undefined;
  }
  return variants.find((v) => matches(v, selection));
}

/**
 * Một giá trị còn chọn được không: tồn tại SKU còn hàng khớp giá trị này
 * cùng các lựa chọn hiện có ở những nhóm khác.
 */
export function isValueAvailable(variants: Variant[], selection: Selection, optionName: string, value: string): boolean {
  const candidate: Selection = { ...selection, [optionName]: value };
  return variants.some((v) => v.stock > 0 && matches(v, candidate));
}

/** Bấm vào một giá trị: chọn, hoặc bỏ chọn nếu đang chọn. */
export function toggleValue(selection: Selection, optionName: string, value: string): Selection {
  const next = { ...selection };
  if (next[optionName] === value) {
    delete next[optionName];
  } else {
    next[optionName] = value;
  }
  return next;
}

/** Khoảng giá các SKU khớp lựa chọn (chưa chọn đủ vẫn thu hẹp được khoảng giá). */
export function priceRange(variants: Variant[], selection: Selection): { min: number; max: number } | null {
  const prices = variants.filter((v) => matches(v, selection)).map((v) => v.price);
  return prices.length ? { min: Math.min(...prices), max: Math.max(...prices) } : null;
}

/** Tổng tồn kho các SKU khớp lựa chọn. */
export function stockFor(variants: Variant[], selection: Selection): number {
  return variants.filter((v) => matches(v, selection)).reduce((sum, v) => sum + v.stock, 0);
}

/** Ảnh riêng của giá trị phân loại (ảnh của SKU đầu tiên khớp có imageUrl). */
export function imageFor(variants: Variant[], selection: Selection): string | null {
  return variants.find((v) => v.imageUrl && matches(v, selection))?.imageUrl ?? null;
}
