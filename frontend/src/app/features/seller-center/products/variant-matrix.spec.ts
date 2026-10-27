import { combinations, comboKey, optionProblems, rowsFromVariants, syncRows, toVariantInputs, usableOptions, VariantRow } from './variant-matrix';

const COLOR = { name: 'Màu', values: ['Đỏ', 'Xanh'] };
const SIZE = { name: 'Size', values: ['M', 'L'] };

describe('variant matrix', () => {
  it('builds the cartesian product in group order', () => {
    expect(combinations([COLOR, SIZE])).toEqual([
      { Màu: 'Đỏ', Size: 'M' },
      { Màu: 'Đỏ', Size: 'L' },
      { Màu: 'Xanh', Size: 'M' },
      { Màu: 'Xanh', Size: 'L' },
    ]);
    expect(combinations([])).toEqual([{}]);
  });

  it('drops blank/duplicate values and unusable groups', () => {
    expect(usableOptions([{ name: ' Màu ', values: ['Đỏ', ' ', 'đỏ', 'Xanh '] }, { name: '', values: ['x'] }])).toEqual([
      { name: 'Màu', values: ['Đỏ', 'Xanh'] },
    ]);
  });

  it('keeps id, price, stock and sku of combinations that still exist', () => {
    const before = syncRows([COLOR], []);
    before[0] = { ...before[0], id: 7, price: 100_000, stock: 5, sku: 'AO-DO' };

    const after = syncRows([COLOR, SIZE], syncRows([{ name: 'Màu', values: ['Đỏ', 'Xanh'] }], before));
    // Thêm nhóm Size → khóa đổi, dòng cũ không còn khớp tổ hợp nào
    expect(after).toHaveLength(4);
    expect(after.every((r) => r.id === null)).toBe(true);

    const removeBlue = syncRows([{ name: 'Màu', values: ['Đỏ'] }], before);
    expect(removeBlue).toEqual([{ key: 'Đỏ', id: 7, optionValues: { Màu: 'Đỏ' }, price: 100_000, stock: 5, sku: 'AO-DO' }]);
  });

  it('uses the single SKU as defaults when options are enabled', () => {
    const single: VariantRow[] = [{ key: '', id: 3, optionValues: {}, price: 50_000, stock: 9, sku: 'X' }];

    const rows = syncRows([COLOR], single);

    expect(rows.map((r) => [r.price, r.stock, r.id, r.sku])).toEqual([
      [50_000, 9, null, ''],
      [50_000, 9, null, ''],
    ]);
  });

  it('maps existing variants and back to API input', () => {
    const rows = rowsFromVariants([COLOR, SIZE], [
      { id: 1, sku: 'A', optionValues: { Size: 'M', Màu: 'Đỏ' }, price: 1000, stock: 2, imageUrl: null },
    ]);
    expect(rows[0].key).toBe(comboKey([COLOR, SIZE], { Màu: 'Đỏ', Size: 'M' }));
    expect(toVariantInputs([{ ...rows[0], sku: '  ' }])).toEqual([
      { id: 1, optionValues: { Size: 'M', Màu: 'Đỏ' }, price: 1000, stock: 2, sku: null, imageUrl: null },
    ]);
  });

  it('reports problems per option group', () => {
    expect(
      optionProblems([
        { name: 'Màu', values: ['Đỏ'] },
        { name: 'màu', values: ['M'] },
      ]),
    ).toEqual([null, 'nameDuplicate']);
    expect(optionProblems([{ name: '', values: [] }])).toEqual(['nameRequired']);
    expect(optionProblems([{ name: 'Size', values: [] }])).toEqual(['valuesRequired']);
    expect(optionProblems([{ name: 'Size', values: ['M', 'm'] }])).toEqual(['valueDuplicate']);
  });
});
