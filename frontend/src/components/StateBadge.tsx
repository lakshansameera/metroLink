import { stateMeaning } from '../format';
import type { JourneyStatus, TicketStatus } from '../types';

type Props = { kind: 'Ticket'; state: TicketStatus } | { kind: 'Journey'; state: JourneyStatus };

/**
 * A ticket's or a journey's state, written exactly as the server names it, so what the screen says is what the
 * server's console log and its database say. The plain-words meaning is in the tooltip.
 */
export function StateBadge(props: Props) {
  const meaning = props.kind === 'Ticket' ? stateMeaning.ticket[props.state] : stateMeaning.journey[props.state];
  return (
    <span className={`state state-${props.state.toLowerCase()}`} title={meaning} data-kind={props.kind.toLowerCase()}>
      <span className="state-kind">{props.kind}</span>
      <span className="state-name">{props.state}</span>
    </span>
  );
}
