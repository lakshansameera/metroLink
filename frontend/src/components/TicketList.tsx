import { useEffect, useRef } from 'react';
import { dayOf, todayInColombo } from '../format';
import type { Journey, Ticket } from '../types';
import { StateBadge } from './StateBadge';

interface Props {
  tickets: Ticket[];
  journeys: Journey[];
  selected: string;
  onSelect: (ticket: Ticket) => void;
}

/**
 * A hint about the travel date, for a ticket that is ACTIVE and has no journey. The states themselves are shown by
 * the badges, exactly as the server has them, so this says nothing about a state. A ticket for another day stays
 * ACTIVE on the server (nothing expires it by itself) and the reader refuses it, which is what the hint says.
 */
function dateNote(ticket: Ticket, journey: Journey | undefined, today: string): string {
  if (journey || ticket.status !== 'ACTIVE') return '';
  if (ticket.travelDate < today) return 'For an earlier day, so the reader refuses it';
  if (ticket.travelDate > today) return 'For a later day, so the reader refuses it';
  return 'Ready to travel';
}

export function TicketList({ tickets, journeys, selected, onSelect }: Props) {
  const today = todayInColombo();
  const byTicket = new Map(journeys.map((j) => [j.ticketId, j]));

  // A ticket just booked may sit below the fold of a long list, so bring the chosen one into view.
  const chosen = useRef<HTMLLIElement | null>(null);
  useEffect(() => {
    chosen.current?.scrollIntoView({ block: 'nearest' });
  }, [selected, tickets.length]);

  return (
    <section className="panel" aria-labelledby="tickets-title">
      <h2 id="tickets-title">Tickets</h2>
      {tickets.length === 0 && <p className="empty">No tickets yet.</p>}
      <ul className="tickets">
        {tickets.map((ticket) => {
          const journey = byTicket.get(ticket.ticketId);
          const isSelected = ticket.qrCode === selected;
          return (
            <li key={ticket.ticketId} ref={isSelected ? chosen : undefined}>
              <button type="button" className="ticket" aria-pressed={isSelected} onClick={() => onSelect(ticket)}>
                <span className="ticket-trip">
                  {ticket.boardingStopName} to {ticket.plannedDestinationStopName}
                </span>
                <span className="ticket-fare">LKR {ticket.quotedFare}</span>
                <span className="ticket-date">
                  Travel date <time dateTime={ticket.travelDate}>{dayOf(ticket.travelDate)}</time>
                </span>
                <span className="ticket-note">{dateNote(ticket, journey, today)}</span>
                <span className="ticket-states">
                  <StateBadge kind="Ticket" state={ticket.status} />
                  {journey && <StateBadge kind="Journey" state={journey.status} />}
                </span>
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
