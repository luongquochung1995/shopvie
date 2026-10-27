import { formatCompactCount, formatVnd, formatVndRange } from './vnd.pipe';

describe('VND formatting', () => {
  it('formats with locale thousand separators', () => {
    expect(formatVnd(189000)).toBe('₫189.000');
    expect(formatVnd(24990000, 'en-US')).toBe('₫24,990,000');
    expect(formatVnd(null)).toBe('');
  });

  it('formats a price range, collapsing equal bounds', () => {
    expect(formatVndRange(139000, 259000)).toBe('₫139.000 - ₫259.000');
    expect(formatVndRange(99000, 99000, 'en-US')).toBe('₫99,000');
  });

  it('compacts sold counts per locale', () => {
    expect(formatCompactCount(999)).toBe('999');
    expect(formatCompactCount(1234)).toBe('1,2k');
    expect(formatCompactCount(1234, 'en-US')).toBe('1.2k');
    expect(formatCompactCount(2000)).toBe('2k');
    expect(formatCompactCount(0)).toBe('0');
  });
});
