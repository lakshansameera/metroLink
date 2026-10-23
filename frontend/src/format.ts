import type { JourneyStatus, TicketStatus } from './types';

const COLOMBO = 'Asia/Colombo';

export const lkr = (amount: number | null | undefined): string =>
  amount === null || amount === undefined ? 'none yet' : `LKR ${amount}`;

export function timeOf(iso: string | null): string {
  if (!iso) return 'none yet';
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: COLOMBO });
}

export function todayInColombo(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: COLOMBO });
}

/**
 * What each state means, in plain words, for a tooltip. The screen shows the state itself exactly as the server names
 * it (see StateBadge), and never a word of its own in its place. The Record types make a missing state a compile error.
 */
export const stateMeaning: {
  ticket: Record<TicketStatus, string>;
  journey: Record<JourneyStatus, string>;
} = {
  ticket: {
    ACTIVE: 'Can be used on its travel date. It stays ACTIVE while a journey is on board or its fare is owed.',
    USED: 'The journey was completed and the fare charged, so the ticket cannot be used again.',
    EXPIRED: 'Past its day, so the reader refuses it.',
    CANCELLED: 'Cancelled, so the reader refuses it.',
  },
  journey: {
    IN_PROGRESS: 'Tapped in. The fare is worked out and charged when the passenger taps out.',
    PAYMENT_PENDING: 'Tapped out, but the account could not pay the fare yet. It can be paid later.',
    COMPLETED: 'The fare was charged. The ticket is then USED.',
  },
};

export const outcomeLabel: Record<string, string> = {
  JOURNEY_STARTED: 'Journey started',
  JOURNEY_COMPLETED: 'Fare paid',
  PAYMENT_PENDING: 'Fare owed',
  ALREADY_SETTLED: 'Already paid',
  REJECTED: 'Not accepted',
};

/** 2026-11-02 becomes "2 Nov 2026". */
export function dayOf(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}
