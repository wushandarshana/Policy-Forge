// NFR-09.5: which visual assertions run on this platform. A pure function with no Playwright imports, so a Vitest table
// test (src/visualBaselinePolicy.test.ts) proves every row. e2e/support.ts applies it to each visual assertion.
//
// PF_VISUAL_BASELINES selects the mode:
//   auto (default)  assert when the baseline exists; when it is absent, assert only if this platform already has at
//                   least one baseline (the missing one then fails the test); skip when the platform has none.
//   require         always assert.
//   update          always assert. Run it with --update-snapshots=missing, which writes only the missing files, so the
//                   run adds baselines for its own platform and never rewrites an existing one.
// Any other value is a configuration error, so a typo can never turn into a silent skip.

export const VISUAL_BASELINE_MODES = ['auto', 'require', 'update'] as const;

export type VisualBaselineDecision =
  | { action: 'assert' }
  | { action: 'skip' }
  | { action: 'error'; message: string };

export interface VisualBaselineInput {
  /** The raw value of PF_VISUAL_BASELINES, undefined when it is not set. */
  mode: string | undefined;
  /** The snapshot file for this test, project and platform exists. */
  baselineExists: boolean;
  /** At least one `-<platform>.png` file exists in the snapshot folder. */
  platformHasBaselines: boolean;
}

export function decideVisualBaseline(input: VisualBaselineInput): VisualBaselineDecision {
  const mode = input.mode === undefined ? 'auto' : input.mode;
  switch (mode) {
    case 'require':
    case 'update':
      return { action: 'assert' };
    case 'auto':
      if (input.baselineExists || input.platformHasBaselines) return { action: 'assert' };
      return { action: 'skip' };
    default:
      return {
        action: 'error',
        message: `PF_VISUAL_BASELINES=${JSON.stringify(mode)} is not a visual baseline mode, use one of: ${VISUAL_BASELINE_MODES.join(', ')}`,
      };
  }
}
