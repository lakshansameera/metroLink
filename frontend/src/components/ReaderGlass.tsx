import { lkr, outcomeLabel } from '../format';
import type { ReaderDisplay } from '../types';

interface Props {
  result: ReaderDisplay | null;
  busy: boolean;
}

/** The face of the reader. Its whole colour is the answer: green went through, amber needs attention, red was refused. */
export function ReaderGlass({ result, busy }: Props) {
  if (!result) {
    return (
      <section className="glass glass-idle" aria-live="polite" aria-busy={busy}>
        <h2>Ready for a tap</h2>
        <p className="glass-message">Choose a ticket, pick the stop the reader is at, then tap in or tap out.</p>
      </section>
    );
  }

  const figures: [string, number | undefined][] = [
    ['Trip needs', result.fare.required],
    ['Fare', result.fare.due],
    ['Charged', result.fare.charged],
    ['Still owed', result.fare.shortfall],
    ['Balance', result.fare.balance],
  ];
  const shown = figures.filter(([, value]) => value !== undefined);

  return (
    <section className={`glass glass-${result.tone.toLowerCase()}`} aria-live="polite" aria-busy={busy}>
      <h2>{outcomeLabel[result.outcome]}</h2>
      <p className="glass-message">{result.message}</p>

      {shown.length > 0 && (
        <dl className="figures">
          {shown.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{lkr(value)}</dd>
            </div>
          ))}
        </dl>
      )}

      {result.ticket && (
        <p className="glass-ticket">
          <span>{result.ticket.qrCode}</span>
          <span>{result.ticket.passengerName}</span>
        </p>
      )}
    </section>
  );
}
