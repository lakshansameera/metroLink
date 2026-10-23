import { journeyLabel, lkr, timeOf } from '../format';
import type { Journey } from '../types';

interface Props {
  journeys: Journey[];
  onSettle: (journeyId: number) => void;
  busy: boolean;
}

export function JourneyList({ journeys, onSettle, busy }: Props) {
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
                <span className="journey-status">{journeyLabel[j.status]}</span>
              </div>
              <p className="journey-trip">
                {j.boardingStopName} to {j.exitStopName ?? 'not exited yet'}
              </p>
              <p className="journey-meta">
                In at {timeOf(j.tapInTime)}, out at {timeOf(j.tapOutTime)}. Fare {lkr(j.fareDue)}, charged {lkr(j.fareCharged)}.
                {j.overtravelled ? ' Went past the planned stop.' : ''}
              </p>
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
