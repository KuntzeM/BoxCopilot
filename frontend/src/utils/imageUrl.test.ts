import { describe, expect, it } from 'vitest';
import { resolveImagePath } from './imageUrl';

const withBase = (p: string) => `https://api.example.com${p}`;

describe('resolveImagePath', () => {
  it('prefixes relative paths with the API base', () => {
    expect(resolveImagePath('/api/items/1/image', withBase)).toBe('https://api.example.com/api/items/1/image');
    expect(resolveImagePath('api/items/1/image', withBase)).toBe('https://api.example.com/api/items/1/image');
  });
  it('never returns a foreign absolute origin', () => {
    expect(resolveImagePath('https://evil.test/x.png?t=1', withBase)).toBe('https://api.example.com/x.png?t=1');
  });
  it('handles empty input', () => {
    expect(resolveImagePath(undefined, withBase)).toBeUndefined();
  });
});
