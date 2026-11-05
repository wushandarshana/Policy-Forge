import type { ManualDecision } from './api/types';

// U-10: manual reason codes are paired with decisions. Descriptions are the spec's catalogue
// (specs/underwriting_spec.md, "Underwriter workbench and override").
export interface ReasonOption {
  code: string;
  description: string;
}

export const APPROVE_REASONS: readonly ReasonOption[] = [
  { code: 'UW-MAN-001', description: 'Acceptable after review' },
  { code: 'UW-MAN-002', description: 'Additional evidence satisfactory' },
];

export const DECLINE_REASONS: readonly ReasonOption[] = [{ code: 'UW-MAN-010', description: 'Risk outside appetite' }];

export const OVERRIDE_REASONS: readonly ReasonOption[] = [
  { code: 'UW-OVR-001', description: 'Commercial exception approved' },
  { code: 'UW-OVR-002', description: 'Evidence rebuts decline reason' },
];

/** The codes the decision form may offer for a chosen decision. No override code is ever among them. */
export function manualReasonsFor(decision: ManualDecision | ''): readonly ReasonOption[] {
  if (decision === 'APPROVE') return APPROVE_REASONS;
  if (decision === 'DECLINE') return DECLINE_REASONS;
  return [];
}
