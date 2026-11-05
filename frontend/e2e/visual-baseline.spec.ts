import { existsSync, readFileSync } from 'node:fs';
import { basename, join } from 'node:path';
import { expect, test } from '@playwright/test';
import { applyVisualPolicy, expectVisual } from './support';

// Self-check of the visual-baseline helper (NFR-09.5, Sprint 5 test review MINOR-2). It runs in both projects on a
// trivial local page, points the helper at a folder without baselines and at its own skip list, so it never touches
// e2e/__snapshots__/ or the shared test-results/visual-baselines-missing.txt that CI reports.

test.beforeEach(async ({ page }) => {
  await page.setContent('<main><p data-testid="selfcheck-box">PolicyForge self-check</p></main>');
});

test('NFR-09.5: on a platform without baselines the helper skips only the visual assertion, records its name and the functional assertions after it still run', async ({
  page,
}, info) => {
  const snapshotFile = join(info.outputPath('no-baselines'), `selfcheck-box-${info.project.name}-${process.platform}.png`);
  const recordFile = info.outputPath('visual-baselines-missing.txt');
  const box = page.getByTestId('selfcheck-box');

  await applyVisualPolicy(box, 'selfcheck-box.png', {}, { snapshotFile, mode: 'auto', recordFile });

  expect(readFileSync(recordFile, 'utf8')).toBe(`${basename(snapshotFile)}\n`);
  expect(info.annotations).toContainEqual({ type: 'visual-baseline-missing', description: basename(snapshotFile) });
  expect(existsSync(snapshotFile)).toBe(false);
  await expect(box).toHaveText('PolicyForge self-check');
});

test('NFR-09.5: an unknown PF_VISUAL_BASELINES mode throws instead of skipping, and records nothing', async ({ page }, info) => {
  const snapshotFile = join(info.outputPath('no-baselines'), `selfcheck-box-${info.project.name}-${process.platform}.png`);
  const recordFile = info.outputPath('visual-baselines-missing.txt');
  const box = page.getByTestId('selfcheck-box');

  await expect(applyVisualPolicy(box, 'selfcheck-box.png', {}, { snapshotFile, mode: 'skip', recordFile })).rejects.toThrow(
    'PF_VISUAL_BASELINES="skip" is not a visual baseline mode',
  );
  expect(existsSync(recordFile)).toBe(false);

  const saved = process.env.PF_VISUAL_BASELINES;
  process.env.PF_VISUAL_BASELINES = 'asert';
  try {
    await expect(expectVisual(box, 'selfcheck-box.png')).rejects.toThrow(
      'PF_VISUAL_BASELINES="asert" is not a visual baseline mode',
    );
  } finally {
    if (saved === undefined) delete process.env.PF_VISUAL_BASELINES;
    else process.env.PF_VISUAL_BASELINES = saved;
  }
  expect(info.annotations.filter((a) => a.type === 'visual-baseline-missing')).toEqual([]);
});
