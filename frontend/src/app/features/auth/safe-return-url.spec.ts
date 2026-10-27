import { safeReturnUrl } from './safe-return-url';

describe('safeReturnUrl', () => {
  it('keeps internal paths', () => {
    expect(safeReturnUrl('/account/addresses?x=1')).toBe('/account/addresses?x=1');
  });

  it.each(['https://evil.com', '//evil.com', '/\\evil.com', 'javascript:alert(1)', '', undefined, null])(
    'rejects %s',
    (url) => {
      expect(safeReturnUrl(url)).toBe('/');
    },
  );
});
