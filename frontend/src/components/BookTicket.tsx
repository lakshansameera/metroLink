import { useEffect, useId, useState, type FormEvent } from 'react';
import { api, ApiError } from '../api';
import { dayOf, lkr, todayInColombo } from '../format';
import type { BookingInput, BookingResult, Reference, TicketQuote } from '../types';
import { byField, type Errors } from '../errors';
import { Field } from './Field';

interface Props {
  reference: Reference;
  /** The logged-in customer. The server keeps no session, so the booking says who it is for. */
  passengerId: number;
  /** A ticket was booked. The app reloads its lists and selects the new ticket. */
  onBooked: (result: BookingResult) => void;
}

/** The server's answer to a fare check: the fare, or the reasons it gave. */
type Answer = { quote: TicketQuote } | { problems: Errors };

/** An answer and the trip it was for, so an answer about an earlier trip is never shown for the current one. */
interface Asked {
  trip: string;
  answer: Answer;
}

/**
 * Book a ticket: pick a route, two stops and a day, see the fare, book.
 * The screen decides nothing. The fare, the date window, the visa limit and the stop order all come from the
 * server, which also names the field behind each problem.
 */
export function BookTicket({ reference, passengerId, onBooked }: Props) {
  const ids = useId();
  const id = (field: string) => `${ids}-${field}`;

  const [routeNo, setRouteNo] = useState('');
  const [boardingStopId, setBoardingStopId] = useState('');
  const [destinationStopId, setDestinationStopId] = useState('');
  const [travelDate, setTravelDate] = useState(todayInColombo());

  const [asked, setAsked] = useState<Asked | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [booking, setBooking] = useState(false);
  const [booked, setBooked] = useState<BookingResult | null>(null);

  const route = reference.routes.find((r) => r.routeNo === routeNo);
  const complete = routeNo !== '' && boardingStopId !== '' && destinationStopId !== '' && travelDate !== '';

  /** Names the trip being asked about; empty until every part is chosen. */
  const trip = complete ? [passengerId, routeNo, boardingStopId, destinationStopId, travelDate].join('|') : '';

  // Ask for the fare as soon as the trip is complete, and again whenever any part of it changes.
  useEffect(() => {
    if (!complete) return;
    let stale = false;
    const input: BookingInput = { passengerId, routeNo, boardingStopId, destinationStopId, travelDate };
    api
      .quote(input)
      .then((quote) => {
        if (!stale) setAsked({ trip, answer: { quote } });
      })
      .catch((e: unknown) => {
        if (stale) return;
        const problems =
          e instanceof ApiError && e.code === 'VALIDATION' && e.details.length > 0
            ? byField(e)
            : { '': e instanceof ApiError ? e.message : 'Could not get the fare.' };
        setAsked({ trip, answer: { problems } });
      });
    return () => {
      stale = true;
    };
  }, [complete, trip, passengerId, routeNo, boardingStopId, destinationStopId, travelDate]);

  // What the server said about the trip as it is now. An older answer shows nothing.
  const answer = asked !== null && trip !== '' && asked.trip === trip ? asked.answer : null;

  /** Editing anything makes the last answers stale. */
  function edited() {
    setErrors({});
    setFormError(null);
    setBooked(null);
  }

  function chooseRoute(next: string) {
    edited();
    setRouteNo(next);
    setBoardingStopId('');
    setDestinationStopId('');
  }

  async function book(event: FormEvent) {
    event.preventDefault();
    setBooking(true);
    setErrors({});
    setFormError(null);
    setBooked(null);
    try {
      const result = await api.book({ passengerId, routeNo, boardingStopId, destinationStopId, travelDate });
      setBooked(result);
      setRouteNo('');
      setBoardingStopId('');
      setDestinationStopId('');
      onBooked(result);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION' && e.details.length > 0) {
        const found = byField(e);
        setErrors(found);
        if (found[''] !== undefined) setFormError(found['']);
      } else {
        setFormError(e instanceof ApiError ? e.message : 'Something went wrong.');
      }
    } finally {
      setBooking(false);
    }
  }

  // Problems from the last submit win; otherwise show what the live fare check found.
  const shown: Errors = Object.keys(errors).length > 0 ? errors : answer && 'problems' in answer ? answer.problems : {};
  const quote = answer && 'quote' in answer ? answer.quote : null;

  const stopOptions = route?.stops.map((s) => (
    <option key={s.stopId} value={s.stopId}>
      {s.name}
    </option>
  ));

  return (
    <section className="panel book" aria-labelledby="book-title">
      <h2 id="book-title">Book a ticket</h2>

      {booked && (
        <p className="booked" role="status">
          {booked.message}
        </p>
      )}

      <form onSubmit={book} noValidate aria-busy={booking}>
        <Field id={id('route')} label="Route" error={shown.routeNo}>
          <select
            id={id('route')}
            value={routeNo}
            onChange={(e) => chooseRoute(e.target.value)}
            aria-invalid={shown.routeNo ? true : undefined}
            aria-describedby={shown.routeNo ? `${id('route')}-note` : undefined}
          >
            <option value="">Choose a route</option>
            {reference.routes.map((r) => (
              <option key={r.routeNo} value={r.routeNo}>
                {r.routeNo} · {r.name}
              </option>
            ))}
          </select>
        </Field>

        <div className="field-row">
          <Field id={id('from')} label="Get on at" error={shown.boardingStopId}>
            <select
              id={id('from')}
              value={boardingStopId}
              disabled={!route}
              onChange={(e) => {
                edited();
                setBoardingStopId(e.target.value);
              }}
              aria-invalid={shown.boardingStopId ? true : undefined}
              aria-describedby={shown.boardingStopId ? `${id('from')}-note` : undefined}
            >
              <option value="">{route ? 'Choose a stop' : 'Choose a route first'}</option>
              {stopOptions}
            </select>
          </Field>

          <Field id={id('to')} label="Get off at" error={shown.destinationStopId}>
            <select
              id={id('to')}
              value={destinationStopId}
              disabled={!route}
              onChange={(e) => {
                edited();
                setDestinationStopId(e.target.value);
              }}
              aria-invalid={shown.destinationStopId ? true : undefined}
              aria-describedby={shown.destinationStopId ? `${id('to')}-note` : undefined}
            >
              <option value="">{route ? 'Choose a stop' : 'Choose a route first'}</option>
              {stopOptions}
            </select>
          </Field>
        </div>

        <Field id={id('date')} label="Travel date" hint="The day you will travel." error={shown.travelDate}>
          <input
            id={id('date')}
            type="date"
            min={todayInColombo()}
            value={travelDate}
            onChange={(e) => {
              edited();
              setTravelDate(e.target.value);
            }}
            aria-invalid={shown.travelDate ? true : undefined}
            aria-describedby={`${id('date')}-note`}
          />
        </Field>

        {quote && (
          <div className="quote" aria-live="polite">
            <p className="quote-fare">
              {quote.fareType === 'FLAT' ? 'Flat fare ' : 'Fare '}
              <strong>{lkr(quote.fare)}</strong>
              {quote.fareType === 'DISTANCE' && <span> for {quote.distanceKm.toFixed(1)} km</span>}
            </p>
            <p className="quote-note">
              {dayOf(travelDate)}. Charged when you get off, not now.
              {quote.canPayNow
                ? ` Your balance is ${lkr(quote.balance)}.`
                : ` Your balance is ${lkr(quote.balance)}, so add credit before you tap in.`}
            </p>
          </div>
        )}

        {(formError ?? shown['']) && (
          <p className="form-error" role="alert">
            {formError ?? shown['']}
          </p>
        )}

        <button type="submit" className="primary" disabled={booking || !complete}>
          {booking ? 'Booking' : 'Book ticket'}
        </button>
      </form>
    </section>
  );
}
