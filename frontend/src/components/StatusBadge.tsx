interface Props {
  status: string;
  testId?: string;
}

/** The application status, exactly as the API sends it. */
export function StatusBadge({ status, testId = 'status-badge' }: Props) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`} data-testid={testId} data-status={status}>
      {status}
    </span>
  );
}
