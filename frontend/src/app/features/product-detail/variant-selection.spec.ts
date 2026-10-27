import { Variant, VariantOption } from '../../core/api/api.models';
import { findVariant, imageFor, isValueAvailable, priceRange, stockFor, toggleValue } from './variant-selection';

const options: VariantOption[] = [
  { name: 'Màu sắc', values: ['Đỏ', 'Xanh'] },
  { name: 'Kích cỡ', values: ['M', 'L'] },
];

const v = (id: number, color: string, size: string, price: number, stock: number, imageUrl: string | null = null): Variant => ({
  id,
  sku: `SKU-${id}`,
  optionValues: { 'Màu sắc': color, 'Kích cỡ': size },
  price,
  stock,
  imageUrl,
});

const variants = [
  v(1, 'Đỏ', 'M', 100_000, 5, 'red.jpg'),
  v(2, 'Đỏ', 'L', 120_000, 0, 'red.jpg'),
  v(3, 'Xanh', 'M', 110_000, 3),
  v(4, 'Xanh', 'L', 130_000, 2),
];

describe('variant selection', () => {
  it('finds the SKU only when every option group is selected', () => {
    expect(findVariant(options, variants, { 'Màu sắc': 'Đỏ' })).toBeUndefined();
    expect(findVariant(options, variants, { 'Màu sắc': 'Xanh', 'Kích cỡ': 'L' })?.id).toBe(4);
  });

  it('returns the single SKU of a product without options', () => {
    const single = [{ ...v(9, '', '', 50_000, 1), optionValues: {} }];
    expect(findVariant([], single, {})?.id).toBe(9);
  });

  it('marks a value unavailable when the matching SKU is out of stock', () => {
    expect(isValueAvailable(variants, { 'Màu sắc': 'Đỏ' }, 'Kích cỡ', 'L')).toBe(false);
    expect(isValueAvailable(variants, { 'Màu sắc': 'Đỏ' }, 'Kích cỡ', 'M')).toBe(true);
    // Chưa chọn màu: size L vẫn còn (Xanh/L)
    expect(isValueAvailable(variants, {}, 'Kích cỡ', 'L')).toBe(true);
  });

  it('allows switching a value within the same group regardless of current choice', () => {
    expect(isValueAvailable(variants, { 'Màu sắc': 'Đỏ', 'Kích cỡ': 'M' }, 'Màu sắc', 'Xanh')).toBe(true);
  });

  it('toggles a value on and off', () => {
    const selected = toggleValue({}, 'Màu sắc', 'Đỏ');
    expect(selected).toEqual({ 'Màu sắc': 'Đỏ' });
    expect(toggleValue(selected, 'Màu sắc', 'Đỏ')).toEqual({});
    expect(toggleValue(selected, 'Màu sắc', 'Xanh')).toEqual({ 'Màu sắc': 'Xanh' });
  });

  it('narrows price range and stock by partial selection', () => {
    expect(priceRange(variants, {})).toEqual({ min: 100_000, max: 130_000 });
    expect(priceRange(variants, { 'Màu sắc': 'Xanh' })).toEqual({ min: 110_000, max: 130_000 });
    expect(stockFor(variants, {})).toBe(10);
    expect(stockFor(variants, { 'Kích cỡ': 'M' })).toBe(8);
  });

  it('picks the variant image for the selected value', () => {
    expect(imageFor(variants, { 'Màu sắc': 'Đỏ' })).toBe('red.jpg');
    expect(imageFor(variants, { 'Màu sắc': 'Xanh' })).toBeNull();
  });
});
