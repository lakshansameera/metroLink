import { dayOf } from '../format';
import type { Passenger } from '../types';

interface Props {
  passengers: Passenger[];
  onTopUp: (passengerId: number, amount: number) => void;
  busy: boolean;
}

/** The logged-in customer's own account: the kind, the balance, and the test buttons that add credit. */
export function PassengerList({ passengers, onTopUp, busy }: Props) {
  return (
    <section className="panel" aria-labelledby="people-title">
      <h2 id="people-title">Your account</h2>
      <ul className="people">
        {passengers.map((p) => (
          <li key={p.passengerId}>
            <div className="person">
              <strong>{p.name}</strong>
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
