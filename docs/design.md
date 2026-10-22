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
