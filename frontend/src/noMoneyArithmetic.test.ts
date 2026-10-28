import { describe, expect, it } from 'vitest';

// NFR-01 (frontend/CLAUDE.md): money arrives as decimal strings and is displayed as is. This guard scans the
// production sources so a later change cannot quietly introduce a JS number conversion.
const sources = import.meta.glob(['./**/*.ts', './**/*.tsx', '!./**/*.test.ts', '!./**/*.test.tsx', '!./test/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

function stripCommentsAndStrings(code: string): string {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')
    .replace(/'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\]|\\.)*`/g, '""');
}

describe('money handling', () => {
  it('NFR-01: no Number(), parseFloat or unary plus in production sources', () => {
    const files = Object.keys(sources);
    expect(files.length).toBeGreaterThan(5);
    for (const file of files) {
      const code = stripCommentsAndStrings(sources[file]);
      expect(code, `${file} uses Number()`).not.toMatch(/\bNumber\s*\(/);
      expect(code, `${file} uses parseFloat`).not.toMatch(/\bparseFloat\b/);
      expect(code, `${file} uses unary plus`).not.toMatch(/(^|[=(,:?&|!])\s*\+\s*[A-Za-z_(]/m);
    }
  });
});
