import { CategoryNode } from './api.models';
import { findCategoryPath } from './catalog.api';

const node = (id: number, slug: string, children: CategoryNode[] = []): CategoryNode => ({
  id,
  parentId: null,
  name: slug,
  slug,
  level: 0,
  position: 0,
  iconUrl: null,
  children,
});

describe('findCategoryPath', () => {
  const roots = [node(1, 'thoi-trang', [node(2, 'ao', [node(3, 'ao-thun')])]), node(4, 'dien-thoai')];

  it('returns the path from root to the category', () => {
    expect(findCategoryPath(roots, 'ao-thun')?.map((c) => c.id)).toEqual([1, 2, 3]);
    expect(findCategoryPath(roots, 'dien-thoai')?.map((c) => c.id)).toEqual([4]);
  });

  it('returns null for unknown slug', () => {
    expect(findCategoryPath(roots, 'khong-co')).toBeNull();
  });
});
