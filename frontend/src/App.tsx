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

  const ticket = useMemo(() => tickets.find((t) => t.qrCode === token), [tickets, token]);
  const journey = useMemo(() => journeys.find((j) => j.ticketId === ticket?.ticketId), [journeys, ticket]);
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
    const j = journeys.find((x) => x.ticketId === t.ticketId);
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
  const reset = () =>
    run(async () => {
      await api.reset();
      setStartOver((n) => n + 1);
      setResult(null);
      setToken('');
      setStopId('S1');
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

      {error && (
        <p className="banner" role="alert">
          {error}
        </p>
      )}

      {!reference ? (
        <p className="loading">{error ? '' : 'Loading the network.'}</p>
      ) : view === 'onboarding' ? (
        <Onboarding key={startOver} passengers={passengers} onChanged={refresh} onTopUp={topUp} busy={busy} />
      ) : (
        <main className="shell">
          <div className="area-tickets">
            <TicketList tickets={tickets} journeys={journeys} selected={token} onSelect={pick} />
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
            <PassengerList passengers={passengers} onTopUp={topUp} busy={busy} />
          </div>
          <div className="area-journeys">
            <JourneyList journeys={journeys} onSettle={settle} busy={busy} />
          </div>
        </main>
      )}
    </>
  );
}
