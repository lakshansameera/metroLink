import { dayOf } from '../format';
import type { Passenger } from '../types';

interface Props {
  passengers: Passenger[];
  onTopUp: (passengerId: number, amount: number) => void;
  busy: boolean;
  /** A customer who has just joined, drawn with a marker so the new row is easy to find. */
  highlightId?: number | null;
}

export function PassengerList({ passengers, onTopUp, busy, highlightId = null }: Props) {
  return (
    <section className="panel" aria-labelledby="people-title">
      <h2 id="people-title">Passengers</h2>
      <ul className="people">
        {passengers.map((p) => (
          <li key={p.passengerId} className={p.passengerId === highlightId ? 'just-joined' : undefined}>
            <div className="person">
              <strong>
                {p.name}
                {p.passengerId === highlightId && <span className="new-badge">New</span>}
              </strong>
              <span className="person-account">
                {p.accountType === 'PERMANENT' || !p.visaExpiry ? 'Permanent account' : `Temporary account, visa to ${dayOf(p.visaExpiry)}`}
              </span>
            </div>
            <span className="balance">LKR {p.balance}</span>
            <button type="button" className="quiet" disabled={busy} onClick={() => onTopUp(p.passengerId, 50)}>
              Add 50
            </button>
            <button type="button" className="quiet" disabled={busy} onClick={() => onTopUp(p.passengerId, 100)}>
              Add 100
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
