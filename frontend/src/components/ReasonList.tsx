import type { Reason } from '../api';

/** Every reason with its description, in the order the API returned them. */
export function ReasonList({ reasons }: { reasons: Reason[] }) {
  return (
    <ul className="reasons" data-testid="reason-list">
      {reasons.map((reason) => (
        <li key={reason.code} className="reason" data-testid={`reason-item-${reason.code}`}>
          <span className="reason-code">{reason.code}</span>
          <span className="reason-description">{reason.description}</span>
        </li>
      ))}
    </ul>
  );
}
