import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { de } from './locales/de';
import { en } from './locales/en';

function flatten(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([k, v]) =>
    v && typeof v === 'object' ? flatten(v as Record<string, unknown>, `${prefix}${k}.`) : [`${prefix}${k}`]
  );
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = join(dir, e.name);
    if (e.isDirectory()) return e.name === 'locales' ? [] : sourceFiles(p);
    return /\.tsx?$/.test(e.name) && !/\.test\./.test(e.name) ? [p] : [];
  });
}

describe('locales', () => {
  const deKeys = flatten(de).sort();
  const enKeys = flatten(en).sort();

  it('de and en have identical key sets', () => {
    expect(enKeys).toEqual(deKeys);
  });

  it('every literal t(...) key used in the source exists', () => {
    const used = new Set<string>();
    for (const f of sourceFiles(join(process.cwd(), 'src'))) {
      for (const m of readFileSync(f, 'utf8').matchAll(/\bt\(\s*['"`]([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)['"`]/g)) {
        used.add(m[1]);
      }
    }
    const known = new Set(deKeys);
    expect([...used].filter((k) => !known.has(k)).sort()).toEqual([]);
  });
});
