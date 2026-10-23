import type { ReactNode } from 'react';

interface FieldProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

/** A label, one input, and either the server's problem with it or a hint. Shared by every form. */
export function Field({ id, label, hint, error, children }: FieldProps) {
  return (
    <div className={error ? 'field field-bad' : 'field'}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <p className="field-error" id={`${id}-note`}>
          {error}
        </p>
      ) : (
        hint && (
          <p className="field-hint" id={`${id}-note`}>
            {hint}
          </p>
        )
      )}
    </div>
  );
}
