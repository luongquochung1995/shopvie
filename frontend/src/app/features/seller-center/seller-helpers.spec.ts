import { CategoryNode } from '../../core/api/api.models';
import { isoDate } from './dashboard/dashboard';
import { niceMax } from './dashboard/revenue-chart';
import { leafCategoryGroups } from './products/product-form';

const node = (id: number, name: string, children: CategoryNode[] = []): CategoryNode => ({
  id, parentId: null, name, slug: `c${id}`, level: 0, position: 0, iconUrl: null, children,
});

describe('seller center helpers', () => {
  it('rounds the chart axis up to a nice number', () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(830_000)).toBe(1_000_000);
    expect(niceMax(1_200_000)).toBe(2_000_000);
    expect(niceMax(2_100_000)).toBe(2_500_000);
    expect(niceMax(4_000_000)).toBe(5_000_000);
  });

  it('groups only lowest-level categories under their root', () => {
    const tree = [
      node(1, 'Thời trang', [node(2, 'Áo'), node(3, 'Giày', [node(4, 'Sneaker'), node(5, 'Sandal')])]),
      node(6, 'Trống'),
    ];

    expect(leafCategoryGroups(tree)).toEqual([
      {
        label: 'Thời trang',
        items: [
          { label: 'Áo', value: 2 },
          { label: 'Giày › Sneaker', value: 4 },
          { label: 'Giày › Sandal', value: 5 },
        ],
      },
    ]);
  });

  it('formats local dates as yyyy-MM-dd', () => {
    expect(isoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
