import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, ApiError } from './api';
import { JourneyList } from './components/JourneyList';
import { Onboarding } from './components/Onboarding';
import { PassengerList } from './components/PassengerList';
import { ReaderGlass } from './components/ReaderGlass';
import { RouteLine } from './components/RouteLine';
import { TicketList } from './components/TicketList';
import type { Direction, Journey, Passenger, ReaderDisplay, Reference, Ticket } from './types';

type View = 'reader' | 'onboarding';

const heading: Record<View, { title: string; line: string }> = {
  reader: { title: 'MetroLink reader', line: 'Tap in, tap out, and see what the reader decides.' },
  onboarding: { title: 'MetroLink customers', line: 'Open an account, or log in to the one you have.' },
};

export function App() {
  const [view, setView] = useState<View>('reader');
  /** Changes on every reset, so the sign-up screen starts empty again. */
  const [startOver, setStartOver] = useState(0);
  const [reference, setReference] = useState<Reference | null>(null);
  const [passengers, setPassengers] = useState<Passenger[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [journeys, setJourneys] = useState<Journey[]>([]);
  /** Who logged in, or null. The server keeps no session, so this only decides what the Reader screen shows. */
  const [customerId, setCustomerId] = useState<number | null>(null);

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

  // A logged-in customer sees only their own account, tickets and journeys. With nobody logged in, the Reader shows
  // everyone, as a station demo.
  const customer = passengers.find((p) => p.passengerId === customerId);
  const shownPassengers = useMemo(
    () => (customerId === null ? passengers : passengers.filter((p) => p.passengerId === customerId)),
    [passengers, customerId],
  );
  const shownTickets = useMemo(
    () => (customerId === null ? tickets : tickets.filter((t) => t.passengerId === customerId)),
    [tickets, customerId],
  );
  const shownJourneys = useMemo(
    () => (customerId === null ? journeys : journeys.filter((j) => j.passengerId === customerId)),
    [journeys, customerId],
  );

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

  const settle = (journeyId: number) => run(async () => setResult(await api.settle(journeyId)));
  const topUp = (passengerId: number, amount: number) => run(async () => void (await api.addCredit(passengerId, amount)));

  /** Clears whatever the Reader was showing, so one customer's ticket is never left selected for the next. */
  function clearReader() {
    setResult(null);
    setToken('');
    setStopId('S1');
  }

  /** The Reader screen opens, showing only this customer's things. */
  function logIn(loggedIn: Passenger) {
    clearReader();
    setCustomerId(loggedIn.passengerId);
    setView('reader');
    void run(async () => {}); // reload, so the balance and tickets are current
  }

  function logOut() {
    clearReader();
    setCustomerId(null);
    setStartOver((n) => n + 1);
    setView('onboarding');
  }

  const reset = () =>
    run(async () => {
      await api.reset();
      setStartOver((n) => n + 1);
      // Customers who registered are gone after a reset, so nobody stays logged in.
      setCustomerId(null);
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
        <nav className="views" aria-label="Screens">
          <button type="button" aria-current={view === 'reader' ? 'page' : undefined} onClick={() => setView('reader')}>
            Reader
          </button>
          <button
            type="button"
            aria-current={view === 'onboarding' ? 'page' : undefined}
            onClick={() => setView('onboarding')}
          >
            New customer
          </button>
        </nav>
      </header>

      {customer && view === 'reader' && (
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

      {!reference ? (
        <p className="loading">{error ? '' : 'Loading the network.'}</p>
      ) : view === 'onboarding' ? (
        <Onboarding key={startOver} onChanged={refresh} onLoggedIn={logIn} />
      ) : (
        <main className="shell">
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
            <PassengerList
              passengers={shownPassengers}
              onTopUp={topUp}
              busy={busy}
              title={customer ? 'Your account' : 'Passengers'}
            />
          </div>
          <div className="area-journeys">
            <JourneyList journeys={shownJourneys} onSettle={settle} busy={busy} />
          </div>
        </main>
      )}
    </>
  );
}
