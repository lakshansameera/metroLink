import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, ApiError } from './api';
import { BookTicket } from './components/BookTicket';
import { JourneyList } from './components/JourneyList';
import { Onboarding } from './components/Onboarding';
import { PassengerList } from './components/PassengerList';
import { ReaderGlass } from './components/ReaderGlass';
import { RouteLine } from './components/RouteLine';
import { TicketList } from './components/TicketList';
import type { BookingResult, Direction, Journey, Passenger, ReaderDisplay, Reference, Ticket } from './types';

type View = 'reader' | 'onboarding';

const heading: Record<View, { title: string; line: string }> = {
  reader: { title: 'MetroLink reader', line: 'Tap in, tap out, and see what the reader decides.' },
  onboarding: { title: 'MetroLink customers', line: 'Open an account, or log in to the one you have.' },
};

export function App() {
  /** Changes on every reset, so the sign-up screen starts empty again. */
  const [startOver, setStartOver] = useState(0);
  const [reference, setReference] = useState<Reference | null>(null);
  const [passengers, setPassengers] = useState<Passenger[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [journeys, setJourneys] = useState<Journey[]>([]);
  /** Who logged in, or null. The server keeps no session, so this only decides which screen shows and whose things. */
  const [loggedIn, setLoggedIn] = useState<Passenger | null>(null);

  const [token, setToken] = useState('');
  const [stopId, setStopId] = useState('S1');
  const [result, setResult] = useState<ReaderDisplay | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const [p, t, j] = await Promise.all([api.passengers(), api.tickets(), api.journeys()]);
    setPassengers(p);
    setTickets(t);
    setJourneys(j);
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [network, p, t, j] = await Promise.all([api.reference(), api.passengers(), api.tickets(), api.journeys()]);
        if (cancelled) return;
        setReference(network);
        setPassengers(p);
        setTickets(t);
        setJourneys(j);
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Could not load the data.');
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  // Nobody sees the Reader until they log in. Then it shows only that customer's account, tickets and journeys.
  const view: View = loggedIn ? 'reader' : 'onboarding';
  const customerId = loggedIn?.passengerId ?? null;
  const customer = passengers.find((p) => p.passengerId === customerId) ?? loggedIn;
  const shownPassengers = useMemo(() => passengers.filter((p) => p.passengerId === customerId), [passengers, customerId]);
  const shownTickets = useMemo(() => tickets.filter((t) => t.passengerId === customerId), [tickets, customerId]);
  const shownJourneys = useMemo(() => journeys.filter((j) => j.passengerId === customerId), [journeys, customerId]);

  const ticket = useMemo(() => shownTickets.find((t) => t.qrCode === token), [shownTickets, token]);
  const journey = useMemo(() => shownJourneys.find((j) => j.ticketId === ticket?.ticketId), [shownJourneys, ticket]);
  const stopName = reference?.stops.find((s) => s.stopId === stopId)?.name ?? stopId;

  /** Runs an action, then reloads what the server holds, and shows any error the server explained. */
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  }

  function pick(t: Ticket) {
    setToken(t.qrCode);
    setResult(null);
    const j = shownJourneys.find((x) => x.ticketId === t.ticketId);
    // A passenger already on the train taps out at the planned stop next. Anyone else taps in where the ticket starts.
    setStopId(j && j.status !== 'COMPLETED' ? t.plannedDestinationStopId : t.boardingStopId);
  }

  const tap = (direction: Direction) =>
    run(async () => {
      const shown = await api.scan(token, direction, stopId);
      setResult(shown);
      if (shown.outcome === 'JOURNEY_STARTED' && shown.ticket) setStopId(shown.ticket.plannedDestinationStopId);
    });

  /** A ticket was just booked: reload, then have the Reader ready to use it. */
  function booked(result: BookingResult) {
    setToken(result.ticket.qrCode);
    setResult(null);
    setStopId(result.ticket.boardingStopId);
    void run(async () => {});
  }

  const settle = (journeyId: number) => run(async () => setResult(await api.settle(journeyId)));
  const topUp = (passengerId: number, amount: number) => run(async () => void (await api.addCredit(passengerId, amount)));

  /** Clears whatever the Reader was showing, so one customer's ticket is never left selected for the next. */
  function clearReader() {
    setResult(null);
    setToken('');
    setStopId('S1');
  }

  /** The Reader screen opens, showing only this customer's things. */
  function logIn(customerWhoLoggedIn: Passenger) {
    clearReader();
    setLoggedIn(customerWhoLoggedIn);
    void run(async () => {}); // reload, so the balance and tickets are current
  }

  /** Back to the first screen, with empty forms. */
  function logOut() {
    clearReader();
    setLoggedIn(null);
    setStartOver((n) => n + 1);
  }

  const reset = () =>
    run(async () => {
      await api.reset();
      setStartOver((n) => n + 1);
      // Customers who registered are gone after a reset, so nobody stays logged in.
      setLoggedIn(null);
      clearReader();
    });

  return (
    <>
      <header className="masthead">
        <h1>{heading[view].title}</h1>
        <p>{heading[view].line}</p>
        <button type="button" className="quiet" disabled={busy} onClick={reset}>
          Start over with demo data
        </button>
      </header>

      {customer && (
        <div className="whoami-wrap">
          <section className="whoami" aria-label="Logged in customer">
            <p>
              Logged in as <strong>{customer.name}</strong>
              <span className="whoami-email">{customer.email}</span>
            </p>
            <button type="button" className="quiet" onClick={logOut}>
              Log out
            </button>
          </section>
        </div>
      )}

      {error && (
        <p className="banner" role="alert">
          {error}
        </p>
      )}

      {view === 'onboarding' ? (
        <Onboarding key={startOver} onChanged={refresh} onLoggedIn={logIn} />
      ) : !reference ? (
        <p className="loading">{error ? '' : 'Loading the network.'}</p>
      ) : (
        <main className="shell">
          <div className="area-book">
            <BookTicket reference={reference} passengerId={loggedIn!.passengerId} onBooked={booked} />
          </div>

          <div className="area-tickets">
            <TicketList tickets={shownTickets} journeys={shownJourneys} selected={token} onSelect={pick} />
          </div>

          <div className="area-line">
            <RouteLine reference={reference} ticket={ticket} journey={journey} stopId={stopId} onStop={setStopId} />
            <div className="taps">
              <button type="button" className="tap tap-in" disabled={busy || !token} onClick={() => tap('IN')}>
                Tap in at {stopName}
              </button>
              <button type="button" className="tap tap-out" disabled={busy || !token} onClick={() => tap('OUT')}>
                Tap out at {stopName}
              </button>
            </div>
            {!token && <p className="hint">Choose a ticket first.</p>}
          </div>

          <div className="area-glass">
            <ReaderGlass result={result} busy={busy} />
          </div>

          <div className="area-people">
            <PassengerList passengers={shownPassengers} onTopUp={topUp} busy={busy} />
          </div>
          <div className="area-journeys">
            <JourneyList journeys={shownJourneys} tickets={shownTickets} onSettle={settle} busy={busy} />
          </div>
        </main>
      )}
    </>
  );
}
