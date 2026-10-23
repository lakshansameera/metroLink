# Journey Execution: design

Alerts are not part of this function (they belong to Monitor Credit Alerts). Going past the planned stop is only a flag on the result.

## 1. Domain model

Only the classes this sequence touches.

| Class | Holds | Point to notice |
|---|---|---|
| `Passenger` | id, name, email, residency | Opens its own account (`openAccount`): the residency decides which kind (section 7). |
| `Residency` | `Resident`, or `NonResident` with a visa expiry date | Residency, not nationality, decides the account (rule A1). |
| `Account` | id, passenger id, balance in whole rupees | `canPay`, `deduct`, `credit` are final and shared. A `PermanentAccount` never expires; a `TemporaryAccount` is usable through its visa expiry date. There is no `setBalance`. |
| `FareTransaction` | id, reference, account id, journey id, amount, balance after, time | One row per debit. The only link between money and a journey. |
| `QRTicket` | id, QR code, passenger id, route, boarding stop, planned destination, travel date, quoted fare, state | `refusalReason(today)` and `markUsed()`. The state is an object (section 7): Active, Used, Expired, Cancelled. |
| `Journey` | id, ticket id, route, boarding stop, exit stop, tap in, tap out, fare due, fare charged, state | Has the state machine below. Asks its state instead of comparing status names (`isOpen`, `isSettled`, `isAwaitingPayment`). |
| `Stop`, `Route`, `RouteStop` | the network | `Route` owns its stops in order and answers `isBefore` and `distanceKm`, so nothing else needs the stop table to price a trip. Read-only here. |
| `FareRule` | route, type (DISTANCE or FLAT), base fare, rate per km, flat amount, effective from, effective to | Pricing is data with dates, not a field on `Route`. |

Relationships:

- A passenger has one account and many tickets. A resident gets a permanent account, a non-resident a temporary one.
- A ticket has zero or one journey (association, not composition: the ticket exists before the journey and survives it).
- An account has many fare transactions, and each transaction names the journey it paid for. There is no direct Account-to-Journey link.
- A route has many route stops (each pointing at one stop) and many fare rules.

## 2. Journey state machine

| From | Event | To | Side effects |
|---|---|---|---|
| (none) | Board accepted | IN_PROGRESS | Nothing is charged. |
| IN_PROGRESS | Exit scan, credit is enough | COMPLETED | One debit, ticket becomes USED. |
| IN_PROGRESS | Exit scan, credit is short or the account has expired | PAYMENT_PENDING | `fareDue` is set, `fareCharged` stays empty, nothing is debited. |
| PAYMENT_PENDING | Settle (or scan out again), credit is enough | COMPLETED | One debit, ticket USED. |

Any other pair is an illegal transition and must fail loudly.

`fareDue` is what the system says is owed. `fareCharged` is what has actually been debited. They differ exactly while a journey is PAYMENT_PENDING.

## 3. What changed while building

These are the places where the built design differs from the first version of this document.

| First design | Built | Why |
|---|---|---|
| One `Account` class | `Account` with `PermanentAccount` and `TemporaryAccount`, opened by the passenger's residency | Tourists have accounts that stop working on their visa date (rules A1 to A3). |
| An `Alert` class and alert endpoints | Removed | Alerts are a different function. Overtravel stays as a flag on the result. |
| Status values as text and enums | State classes for tickets and journeys | Each rule lives in one class, and a wrong move fails loudly. |
| `FareCalculator` looks up stops and rules itself | It is given the `Route` and the rules | The calculator needs no repository, so it is tested without a database. |
| Boarding only checked the balance | Boarding and exit both ask `Account.canPay` (usable and enough credit) | One definition of "can pay" (rule A4). |
| `POST /api/test-data/tickets` | Removed | Issuing tickets is another function; the demo data already has tickets for every case. |

## 4. The API contract

The whole function is one endpoint, `POST /api/reader/scan`. The others only feed the screen.

| Method and path | Purpose | Needed for |
|---|---|---|
| `POST /api/reader/scan` | A reader scans a ticket: direction IN or OUT, at a stop | The sequence itself |
| `POST /api/journeys/{id}/settle` | Retry payment on a PAYMENT_PENDING journey | Rule D1 |
| `GET /api/reference` | Stops, and routes with their stops in order | The line on the screen |
| `GET /api/passengers`, `/passengers/{id}/account` | Passengers with balances; one account with its transactions | Display |
| `GET /api/tickets`, `/journeys`, `/journeys/{id}` | Lists for the screen | Display |
| `POST /api/test-data/credit`, `/reset` | Stand-in for Top Up; restore the demo data | Demo |
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
  "ticket": { "qrCode": "MLK-HAPPY01", "status": "USED" },
  "journey": { "journeyId": 1, "status": "COMPLETED", "fareDue": 69, "fareCharged": 69, "overtravelled": false },
  "fare": { "due": 69, "charged": 69, "balance": 431 },
  "overtravelled": false
}
```

`outcome` is one of JOURNEY_STARTED, JOURNEY_COMPLETED, PAYMENT_PENDING, ALREADY_SETTLED or REJECTED. A rejection (or a payment waiting on an expired account) carries a `reason`. `tone` (SUCCESS, WARNING, ERROR) tells the screen which colour to use, so the browser never interprets business codes.

Status codes:

| Code | When |
|---|---|
| 200 | Any business result, including a refused ticket (`outcome: REJECTED`). A rejection is an answer, not an error. |
| 400 | Malformed request. Body: `{ "error": "VALIDATION", "message": "...", "details": [...] }` |
| 404 | Unknown stop, journey or passenger |
| 409 | Illegal state change, or the store refused the data (constraint violation) |
| 422 | The account has expired, so it cannot be changed |
| 500 | Bugs only |

## 5. Build order

Built from the inside out, so every layer is tested before the next one exists.

| Phase | Build | Check |
|---|---|---|
| 3a | Passenger, Residency, Account kinds, QRTicket, Journey, dates | Plain unit tests |
| 3b | Network classes, FareRule, fare strategies, FareCalculator | Unit tests using the worked fares in rules.md |
| 3c | Repository interfaces, in-memory store, unit of work, demo data | Rollback and constraint tests |
| 3d | Services and the board, alight, settle workflow | Service tests, no HTTP |
| 3e | JSON, routes, validation, error mapping | HTTP tests against a real server on a free port |
| 4 | The React screen | Used by hand against the running server |

The payoff: when a fare is wrong, I look in `fare`, never in the browser.

## 6. Eight decisions

1. **Money is whole rupees in a `long`.** No `double` for money, ever.
2. **Storage sits behind interfaces.** The in-memory store can later be replaced by a database without touching a service.
3. **Charge on exit, never at boarding.** The boarding balance check is advisory.
4. **A debit is idempotent.** Its reference `FARE-<journeyId>` is unique, so a retried request cannot charge twice.
5. **The server decides, the browser displays.** No fare or validity logic in React.
6. **Rejections are answers.** The reader always gets a message it can show.
7. **One scan, one unit of work.** If anything fails halfway, every change is undone.
8. **Dates are Colombo dates.** Rules receive the scan time as a parameter, so tests control the clock.

## 7. Design patterns

| Pattern | Where | Why it fits | Phase |
|---|---|---|---|
| **State** | `TicketState` with Active, Used, Expired, Cancelled; `JourneyState` with InProgress, PaymentPending, Completed | Behaviour depends on status and the status changes over time. Each rule lives in one class, and illegal moves fail loudly. | 3a |
| **Template Method** | `Account`: `canPay`, `deduct` and `credit` are final; each kind only decides `isUsableAt` | The steps are fixed, so no kind can skip the expiry check. | 3a |
| **Factory Method** | `Residency.openAccount`, called by `Passenger.openAccount` | The passenger never decides which kind of account to build; its residency does. | 3a |
| **Information Expert** | `Account` owns its balance (no setter); `Route` owns its stops and distances; `QRTicket` decides its own refusal | The object with the data makes the decision. | 3a, 3b |
| **Strategy** | `FareStrategy` with `DistanceFareStrategy` and `FlatFareStrategy`, chosen by `FareCalculator` from the `FareRule` type | The fare algorithm is chosen from outside by data and never changes during a calculation. | 3b |
| **Repository** | `repository` interfaces, `repository.memory` implementations | Services do not know how data is stored (decision 2). | 3c |
| **Unit of Work** | `UnitOfWork`, `InMemoryDatabase` | The writes of one scan succeed together or not at all (rule D5). | 3c |
| **Exception translation** | `DomainException` carries a code and an HTTP status; `NotFoundException`, `ConstraintViolationException` extend it; `ApiServer` turns them into responses | Services know nothing about HTTP. | 3e |
| **Service layer, Facade** | `JourneyService` for the workflow, `ReaderService` as the single entry for the reader screen | One place per workflow. The web layer calls one object. | 3d |
| **Static factory** | `Journey.start`, `FareRule.distance/flat`, `FareTransaction.forJourney`, `TicketCheck.ok/refused` | Names say what is being made; a refused ticket is an answer, not an exception (decision 6). | 3a to 3d |
| **Builder** | `ScanResult.Builder` | A scan result has many optional parts. | 3d |
| **Composition root** | `Container` | The only place that knows which store is in use. | 3e |

State and Strategy look the same in UML (an interface plus implementations) but differ in intent. In Strategy the caller picks the algorithm and it does not change. In State the object changes its own behaviour as events happen, and each state decides the next one.
