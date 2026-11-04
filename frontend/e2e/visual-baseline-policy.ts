// NFR-09.5 (red stub): the decision function is not written yet, so every visual assertion is asserted.
export type VisualBaselineDecision =
  | { action: 'assert' }
  | { action: 'skip' }
  | { action: 'error'; message: string };

export interface VisualBaselineInput {
  mode: string | undefined;
  baselineExists: boolean;
  platformHasBaselines: boolean;
}

export function decideVisualBaseline(_input: VisualBaselineInput): VisualBaselineDecision {
  return { action: 'assert' };
}
