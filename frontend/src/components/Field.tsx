import type { ReactNode } from 'react';

interface Props {
  /** The id of the control inside, so the label and the error are tied to it. */
  id: string;
  label: string;
  fieldTestId: string;
  errorTestId: string;
  error?: string;
  children: ReactNode;
}

/** A labelled control. The error, when present, renders inside the same wrapper as its control. */
export function Field({ id, label, fieldTestId, errorTestId, error, children }: Props) {
  return (
    <div className="field" data-testid={fieldTestId}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <p className="field-error" id={`${id}-error`} role="alert" data-testid={errorTestId}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
