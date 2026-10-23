import { dayOf, ticketLabel, todayInColombo } from '../format';
import type { Journey, Ticket } from '../types';

interface Props {
  tickets: Ticket[];
  journeys: Journey[];
  selected: string;
  onSelect: (ticket: Ticket) => void;
}

/** What a ticket can do today, in a few words. */
function note(ticket: Ticket, journey: Journey | undefined, today: string): string {
  if (journey) return journey.status === 'COMPLETED' ? 'Journey finished' : journey.status === 'IN_PROGRESS' ? 'On board' : 'Fare owed';
  if (ticket.status !== 'ACTIVE') return ticketLabel[ticket.status];
  if (ticket.travelDate < today) return 'For an earlier day';
  if (ticket.travelDate > today) return 'For a later day';
  return 'Ready to travel';
}

export function TicketList({ tickets, journeys, selected, onSelect }: Props) {
  const today = todayInColombo();
  const byTicket = new Map(journeys.map((j) => [j.ticketId, j]));

  return (
    <section className="panel" aria-labelledby="tickets-title">
      <h2 id="tickets-title">Tickets</h2>
      {tickets.length === 0 && <p className="empty">No tickets yet.</p>}
      <ul className="tickets">
        {tickets.map((ticket) => {
          const journey = byTicket.get(ticket.ticketId);
          const isSelected = ticket.qrCode === selected;
          return (
            <li key={ticket.ticketId}>
              <button type="button" className="ticket" aria-pressed={isSelected} onClick={() => onSelect(ticket)}>
                <span className="ticket-trip">
                  {ticket.boardingStopName} to {ticket.plannedDestinationStopName}
                </span>
                <span className="ticket-fare">LKR {ticket.quotedFare}</span>
                <span className="ticket-date">
                  Travel date <time dateTime={ticket.travelDate}>{dayOf(ticket.travelDate)}</time>
                </span>
                <span className="ticket-note">{note(ticket, journey, today)}</span>
                <span className="ticket-who">
                  {ticket.passengerName}, {ticket.qrCode}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
