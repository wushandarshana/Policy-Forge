import { describe, expect, it } from 'vitest';
import { decideVisualBaseline } from '../e2e/visual-baseline-policy';
import type { VisualBaselineDecision } from '../e2e/visual-baseline-policy';

// NFR-09.5: the visual-baseline policy of the E2E suite (plan C7). It is a pure function, so every row of the decision
// table is proved here without a browser. e2e/support.ts applies it to each visual assertion.
const ASSERT: VisualBaselineDecision = { action: 'assert' };
const SKIP: VisualBaselineDecision = { action: 'skip' };

describe('visual baseline policy', () => {
  describe.each(['auto', undefined] as const)('mode %s (auto is the default)', (mode) => {
    it('NFR-09.5: the baseline is present on a platform that has baselines, so the visual assertion runs', () => {
      expect(decideVisualBaseline({ mode, baselineExists: true, platformHasBaselines: true })).toEqual(ASSERT);
    });

    it('NFR-09.5: the baseline is absent but the platform has other baselines, so the assertion runs and fails as missing', () => {
      expect(decideVisualBaseline({ mode, baselineExists: false, platformHasBaselines: true })).toEqual(ASSERT);
    });

    it('NFR-09.5: the baseline is absent and the platform has no baselines at all, so only the visual assertion is skipped', () => {
      expect(decideVisualBaseline({ mode, baselineExists: false, platformHasBaselines: false })).toEqual(SKIP);
    });

    it('NFR-09.5: a present baseline is always asserted, even when the platform count says there are none', () => {
      expect(decideVisualBaseline({ mode, baselineExists: true, platformHasBaselines: false })).toEqual(ASSERT);
    });
  });

  describe.each(['require', 'update'] as const)('mode %s', (mode) => {
    const cases = [
      { baselineExists: true, platformHasBaselines: true },
      { baselineExists: false, platformHasBaselines: true },
      { baselineExists: false, platformHasBaselines: false },
      { baselineExists: true, platformHasBaselines: false },
    ];
    it.each(cases)(
      'NFR-09.5: always asserts, never skips ($baselineExists baseline, $platformHasBaselines platform baselines)',
      ({ baselineExists, platformHasBaselines }) => {
        expect(decideVisualBaseline({ mode, baselineExists, platformHasBaselines })).toEqual(ASSERT);
      },
    );
  });

  it.each(['AUTO', 'Require', 'none', 'skip', 'missing', 'true', '', ' auto'])(
    'NFR-09.5: the unknown mode %j is a configuration error that names the variable and the allowed values',
    (mode) => {
      const decision = decideVisualBaseline({ mode, baselineExists: false, platformHasBaselines: false });
      expect(decision.action).toBe('error');
      if (decision.action === 'error') {
        expect(decision.message).toContain('PF_VISUAL_BASELINES');
        expect(decision.message).toContain(JSON.stringify(mode));
        expect(decision.message).toContain('auto, require, update');
      }
    },
  );

  it('NFR-09.5: an unknown mode is an error whatever the baselines are, so a typo never turns into a silent skip', () => {
    for (const baselineExists of [true, false]) {
      for (const platformHasBaselines of [true, false]) {
        expect(decideVisualBaseline({ mode: 'bogus', baselineExists, platformHasBaselines }).action).toBe('error');
      }
    }
  });
});
