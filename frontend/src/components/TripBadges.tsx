import { signedLkr, tripMeaning } from '../format';
import type { Trip } from '../types';

/**
 * The real trip next to the ticket, as small chips: Over-travel, Shorter trip, Over-charged. The server decides which
 * apply (TripComparison in the backend); this only shows them. A trip made exactly as ticketed shows no chip.
 */
export function TripBadges({ trip }: { trip: Trip }) {
  const chips: { key: string; className: string; label: string; title: string }[] = [];

  if (trip.kind === 'OVER_TRAVEL') {
    chips.push({ key: 'over', className: 'trip-over', label: 'Over-travel', title: tripMeaning.OVER_TRAVEL });
  } else if (trip.kind === 'SHORTER') {
    chips.push({ key: 'shorter', className: 'trip-shorter', label: 'Shorter trip', title: tripMeaning.SHORTER });
  } else if (trip.kind === null && trip.boardedOffTicket) {
    chips.push({
      key: 'board',
      className: 'trip-boarded',
      label: 'Boarded off ticket',
      title: 'Tapped in at a stop other than the ticket\'s. The fare is worked out when they tap out.',
    });
  }

  if (trip.overcharged && trip.fareDifference !== null) {
    chips.push({
      key: 'charged',
      className: 'trip-over',
      label: `Over-charged ${signedLkr(trip.fareDifference)}`,
      title: `The fare is more than the ticket's LKR ${trip.quotedFare}.`,
    });
  }

  if (chips.length === 0) return null;
  return (
    <span className="trip-chips">
      {chips.map((c) => (
        <span key={c.key} className={`trip-chip ${c.className}`} title={c.title}>
          {c.label}
        </span>
      ))}
    </span>
  );
}
