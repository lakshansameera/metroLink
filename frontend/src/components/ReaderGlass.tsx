import { lkr, outcomeLabel, signedLkr } from '../format';
import type { ReaderDisplay } from '../types';
import { StateBadge } from './StateBadge';
import { TripBadges } from './TripBadges';

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

  const trip = result.journey?.trip;
  // The ticket's own fare is shown only when the trip was not as ticketed, so a normal trip shows no extra figures.
  const offTicket = trip !== undefined && trip.fareDifference !== null && (trip.kind !== 'AS_TICKETED' || trip.fareDifference !== 0);
  const figures: [string, string | undefined][] = [
    ['Trip needs', result.fare.required === undefined ? undefined : lkr(result.fare.required)],
    ['Fare', result.fare.due === undefined ? undefined : lkr(result.fare.due)],
    ['Ticket fare', offTicket ? lkr(trip!.quotedFare) : undefined],
    ['Against ticket', offTicket ? signedLkr(trip!.fareDifference!) : undefined],
    ['Charged', result.fare.charged === undefined ? undefined : lkr(result.fare.charged)],
    ['Still owed', result.fare.shortfall === undefined ? undefined : lkr(result.fare.shortfall)],
    ['Balance', result.fare.balance === undefined ? undefined : lkr(result.fare.balance)],
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
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}

      {result.ticket && (
        <p className="glass-ticket">
          <span>{result.ticket.qrCode}</span>
          <span>{result.ticket.passengerName}</span>
          <span className="glass-states">
            <StateBadge kind="Ticket" state={result.ticket.status} />
            {result.journey && <StateBadge kind="Journey" state={result.journey.status} />}
            {trip && <TripBadges trip={trip} />}
          </span>
        </p>
      )}
    </section>
  );
}
