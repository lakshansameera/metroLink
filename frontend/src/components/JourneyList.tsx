import { lkr, signedLkr, timeOf } from '../format';
import type { Journey, Ticket } from '../types';
import { StateBadge } from './StateBadge';
import { TripBadges } from './TripBadges';

interface Props {
  journeys: Journey[];
  tickets: Ticket[];
  onSettle: (journeyId: number) => void;
  busy: boolean;
}

export function JourneyList({ journeys, tickets, onSettle, busy }: Props) {
  const ticketOf = new Map(tickets.map((t) => [t.ticketId, t]));
  return (
    <section className="panel" aria-labelledby="journeys-title">
      <h2 id="journeys-title">Journeys</h2>
      {journeys.length === 0 ? (
        <p className="empty">No journeys yet. Tap in with a ticket to start one.</p>
      ) : (
        <ul className="journeys">
          {journeys.map((j) => (
            <li key={j.journeyId} className={`journey journey-${j.status.toLowerCase()}`}>
              <div className="journey-head">
                <strong>{j.passengerName}</strong>
                <span className="journey-states">
                  <StateBadge kind="Journey" state={j.status} />
                  {ticketOf.get(j.ticketId) && <StateBadge kind="Ticket" state={ticketOf.get(j.ticketId)!.status} />}
                  <TripBadges trip={j.trip} />
                </span>
              </div>
              <p className="journey-trip">
                {j.boardingStopName} to {j.exitStopName ?? 'not exited yet'}
              </p>
              <p className="journey-meta">
                In at {timeOf(j.tapInTime)}, out at {timeOf(j.tapOutTime)}. Fare {lkr(j.fareDue)}, charged {lkr(j.fareCharged)}.
              </p>
              {ticketOf.get(j.ticketId) && (j.trip.boardedOffTicket || j.trip.exitedOffTicket || j.trip.fareDifference) ? (
                <p className="journey-compare">
                  Ticket: {ticketOf.get(j.ticketId)!.boardingStopName} to {ticketOf.get(j.ticketId)!.plannedDestinationStopName},{' '}
                  {lkr(j.trip.quotedFare)}. Travelled: {j.boardingStopName} to {j.exitStopName ?? 'not exited yet'}
                  {j.trip.fareDifference !== null ? `, ${lkr(j.fareDue)} (${signedLkr(j.trip.fareDifference)} against the ticket)` : ''}.
                </p>
              ) : null}
              {j.status === 'PAYMENT_PENDING' && (
                <button type="button" disabled={busy} onClick={() => onSettle(j.journeyId)}>
                  Pay fare now
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
