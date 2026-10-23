import type { Journey, Reference, Ticket } from '../types';

interface Props {
  reference: Reference;
  ticket: Ticket | undefined;
  journey: Journey | undefined;
  stopId: string;
  onStop: (stopId: string) => void;
}

/**
 * The line, drawn the way a station sign draws it. Tapping a stop says where the reader is.
 * Stops that are not on the ticket's route stay tappable, so a wrong stop can be tried on purpose.
 */
export function RouteLine({ reference, ticket, journey, stopId, onStop }: Props) {
  // The main line has every stop. Shorter routes use some of them.
  const line = reference.routes.reduce((longest, route) => (route.stops.length > longest.stops.length ? route : longest));
  const onRoute = new Set(reference.routes.find((r) => r.routeNo === ticket?.routeNo)?.stops.map((s) => s.stopId));

  function marks(id: string): string[] {
    const tags: string[] = [];
    if (ticket && id === ticket.boardingStopId) tags.push('Ticket starts here');
    if (ticket && id === ticket.plannedDestinationStopId) tags.push('Ticket ends here');
    if (journey?.exitStopId === id) tags.push('Got off here');
    return tags;
  }

  return (
    <section className="panel" aria-labelledby="line-title">
      <h2 id="line-title">Where is the reader?</h2>
      <ol className="line">
        {line.stops.map((stop) => {
          const isHere = stop.stopId === stopId;
          const offRoute = ticket !== undefined && !onRoute.has(stop.stopId);
          return (
            <li key={stop.stopId} className={offRoute ? 'off-route' : undefined}>
              <button type="button" className="stop" aria-pressed={isHere} onClick={() => onStop(stop.stopId)}>
                <span className="stop-dot" aria-hidden="true" />
                <span className="stop-name">{stop.name}</span>
                <span className="stop-km">{stop.distanceKm} km</span>
                {offRoute && <span className="stop-mark">Not on {ticket.routeNo}</span>}
                {marks(stop.stopId).map((tag) => (
                  <span key={tag} className="stop-mark">
                    {tag}
                  </span>
                ))}
              </button>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
