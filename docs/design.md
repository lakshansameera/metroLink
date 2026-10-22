# Journey Execution: design

## 1. Domain model

Only the classes this sequence touches.

| Class | Holds | Point to notice |
|---|---|---|
| `Passenger` | id, name, email | Owns exactly one account. |
| `Account` | id, passenger id, balance in whole rupees | `hasSufficientCredit(amount)` and `deduct(amount)`. Never goes below zero. |
| `FareTransaction` | id, reference, account id, journey id, amount, balance after, time | One row per debit. The only link between money and a journey. |
| `QRTicket` | id, QR code, passenger id, route, boarding stop, planned destination, travel date, quoted fare, status | `validate(today)` and `markUsed()`. Status: ACTIVE, USED, EXPIRED, CANCELLED. |
| `Journey` | id, ticket id, route, boarding stop, exit stop, tap in, tap out, fare due, fare charged, status | Has the state machine below. |
| `Stop`, `Route`, `RouteStop` | the network | `RouteStop` gives a stop's order on a route and its distance from the origin. Read-only here. |
| `FareRule` | route, type (DISTANCE or FLAT), base fare, rate per km, flat amount, effective from, effective to | Pricing is data with dates, not a field on `Route`. |
| `Alert` | type, stage, audience, journey id, ticket id, message, created, resolved | A consequence of what happened, never an input to it. |

Relationships:

- A passenger has one account and many tickets.
- A ticket has zero or one journey (association, not composition: the ticket exists before the journey and survives it).
- An account has many fare transactions, and each transaction names the journey it paid for. There is no direct Account-to-Journey link.
- A route has many route stops (each pointing at one stop) and many fare rules.

## 2. Journey state machine

| From | Event | To | Side effects |
|---|---|---|---|
| (none) | Board accepted | IN_PROGRESS | Nothing is charged. |
| IN_PROGRESS | Exit scan, credit is enough | COMPLETED | One debit, ticket becomes USED. |
| IN_PROGRESS | Exit scan, credit is short | PAYMENT_PENDING | `fareDue` is set, `fareCharged` stays empty, one alert. |
| PAYMENT_PENDING | Settle, credit is enough | COMPLETED | One debit, ticket USED, alert resolved. |

Any other pair is an illegal transition and must fail loudly.

`fareDue` is what the system says is owed. `fareCharged` is what has actually been debited. They differ exactly while a journey is PAYMENT_PENDING.

## 3. Differences from my class diagram

(To fill in.)

## 4. The API contract

The whole sequence is one endpoint, `POST /api/reader/scan`. The others only feed the screen.

| Method and path | Purpose | Needed for |
|---|---|---|
| `POST /api/reader/scan` | A reader scans a ticket: direction IN or OUT, at a stop | The sequence itself |
| `POST /api/journeys/{id}/settle` | Retry payment on a PAYMENT_PENDING journey | Rule D1 |
| `GET /api/reference` | Stops, routes, route stops, fare rules | Dropdowns |
| `GET /api/passengers`, `/passengers/{id}/account` | Passengers with balances; one account with its transactions | Display |
| `GET /api/tickets`, `/journeys`, `/journeys/{id}`, `/alerts` | Lists for the screen | Display |
| `POST /api/test-data/credit`, `/tickets`, `/reset` | Stand-ins for Top Up and Generate Ticket; restore the seed | Demo |
| `GET /api/health` | Is the server up | Smoke check |

Request:

```json
{ "qrCode": "MLK-HAPPY01", "direction": "OUT", "stopId": "S5", "scannedAt": "2026-10-22T08:40:00+05:30" }
```

`scannedAt` is optional and defaults to now. It lets tests and the demo control the clock.

Response (shortened):

```json
{
  "outcome": "JOURNEY_COMPLETED",
  "tone": "SUCCESS",
  "message": "Fare LKR 69 charged. Balance LKR 431.",
  "journey": { "journeyId": 1, "status": "COMPLETED", "fareDue": 69, "fareCharged": 69, "overtravelled": false },
  "fare": { "due": 69, "charged": 69, "balance": 431 },
  "alerts": []
}
```

`outcome` is one of JOURNEY_STARTED, JOURNEY_COMPLETED, PAYMENT_PENDING, ALREADY_SETTLED or REJECTED (with a reason). `tone` (SUCCESS, WARNING, ERROR) tells the screen which colour to use, so the browser never interprets business codes.

Status codes:

| Code | When |
|---|---|
| 200 | Any business result, including a refused ticket (`outcome: REJECTED`). A rejection is an answer, not an error. |
| 400 | Malformed request. Body: `{ "error": "VALIDATION", "message": "...", "details": [...] }` |
| 404 | Missing resource |
| 409 | Illegal state change |
| 500 | Bugs only |

## 5. Build order

Built from the inside out, so every layer is tested before the next one exists.

| Phase | Build | Check |
|---|---|---|
| 3a | Account, QRTicket, Journey, dates | Plain unit tests |
| 3b | Network records, FareRule, fare strategies, FareCalculator | Unit tests using the worked fares in rules.md |
| 3c | Repository interfaces, in-memory store, unit of work | Rollback and constraint tests |
| 3d | Services and the board, alight, settle workflow | Service tests, no HTTP |
| 3e | JSON, routes, validation, error mapping | HTTP tests against a real server on a free port |
| 4 | The React screen | By hand first, then a scripted run |

The payoff: when a fare is wrong, I look in `fare`, never in the browser.

## 6. Six decisions

1. **Money is whole rupees in a `long`.** No `double` for money, ever.
2. **Storage sits behind interfaces.** The in-memory store can later be replaced by a database without touching a service.
3. **Charge on exit, never at boarding.** The boarding balance check is advisory.
4. **A debit is idempotent.** Its reference `FARE-<journeyId>` is unique, so a retried request cannot charge twice.
5. **The server decides, the browser displays.** No fare or validity logic in React.
6. **Rejections are answers.** The reader always gets a message it can show.
