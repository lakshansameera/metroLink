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

export const journeyLabel: Record<string, string> = {
  IN_PROGRESS: 'On board',
  PAYMENT_PENDING: 'Fare owed',
  COMPLETED: 'Paid',
};

export const ticketLabel: Record<string, string> = {
  ACTIVE: 'Active',
  USED: 'Used',
  EXPIRED: 'Expired',
  CANCELLED: 'Cancelled',
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
