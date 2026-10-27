import en from './en.json';
import vi from './vi.json';

type Tree = { [key: string]: string | Tree };

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  return Object.entries(tree).reduce<Record<string, string>>((acc, [k, v]) => {
    return typeof v === 'string' ? { ...acc, [prefix + k]: v } : { ...acc, ...flatten(v, `${prefix}${k}.`) };
  }, {});
}

const params = (text: string) => [...text.matchAll(/\{\{\s*(\w+)\s*\}\}/g)].map((m) => m[1]).sort();

describe('i18n files', () => {
  const viKeys = flatten(vi as Tree);
  const enKeys = flatten(en as Tree);

  it('vi and en have exactly the same keys', () => {
    expect(Object.keys(enKeys).sort()).toEqual(Object.keys(viKeys).sort());
  });

  it('every translation is non-empty', () => {
    const empty = [...Object.entries(viKeys), ...Object.entries(enKeys)].filter(([, v]) => !v.trim()).map(([k]) => k);
    expect(empty).toEqual([]);
  });

  it('interpolation params match between languages', () => {
    const mismatched = Object.keys(viKeys).filter(
      (k) => JSON.stringify(params(viKeys[k])) !== JSON.stringify(params(enKeys[k] ?? '')),
    );
    expect(mismatched).toEqual([]);
  });
});
