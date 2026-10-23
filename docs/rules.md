# Journey Execution: business rules

Each rule is one testable sentence. Alerts belong to the separate Monitor Credit Alerts functionality and are out of scope here. "Proven in" is the guide phase where its test is written.

## Boarding (scan in)

| ID | Rule | Proven in |
| --- | --- | --- |
| B1 | A QR token that matches no ticket is rejected (`UNKNOWN_TOKEN`). | 3d |
| B2 | A `USED`, `EXPIRED` or `CANCELLED` ticket is rejected. An active ticket for an earlier date is `TICKET_EXPIRED`; for a later date it is `WRONG_TRAVEL_DATE`. | 3a |
| B3 | A ticket that already has a journey cannot open a second one (`ALREADY_BOARDED`). | 3d |
| B4 | The reader's stop must be on the ticket's route and before the planned destination (`INVALID_BOARDING_STOP`). | 3d |
| B5 | The balance must cover the fare from this stop to the planned destination. If not, refuse with `INSUFFICIENT_CREDIT`. This check is advisory: nothing is reserved. | 3a, 3d |
| B6 | On success a journey opens as `IN_PROGRESS`. No money moves and the ticket stays `ACTIVE`. | 3d |

## Exit (scan out)

| ID | Rule | Proven in |
| --- | --- | --- |
| X1 | Scan out with no open journey is rejected (`MISSING_BOARDING_SCAN`). | 3d |
| X2 | The exit stop must be on the route and after the boarding stop (`INVALID_EXIT_STOP`). The journey stays open. | 3b, 3d |
| X3 | The fare is priced from the boarding stop to the stop where the passenger actually got off, not the planned one. | 3b |
| X4 | The fare uses the rule in force on the tap-in date. Distance fare is `base + rate x km`, rounded up to a whole rupee; flat fare is the flat amount. | 3b |
| X5 | Enough credit: debit once, journey `COMPLETED`, `fareCharged = fareDue`, ticket `USED`. | 3a, 3d |
| X6 | Not enough credit: nothing is debited, the journey becomes `PAYMENT_PENDING` with `fareDue` recorded and `fareCharged` empty. | 3d |
| X7 | Exiting past the planned stop is priced to the actual stop and the result is marked `overtravelled`. It is a flag, not a payment failure. | 3d |
| X8 | Scanning out again on a settled journey changes nothing (`ALREADY_SETTLED`). | 3d, 3e |

## Data integrity

| ID | Rule | Proven in |
| --- | --- | --- |
| D1 | A `PAYMENT_PENDING` journey can be settled later, once credit exists. That completes it. An `IN_PROGRESS` journey cannot be settled. | 3d |
| D2 | One journey, one debit. Each debit carries the reference `FARE-<journeyId>`, which is unique, so a retry cannot charge twice. | 3c, 3d |
| D3 | A balance never goes below zero. | 3a |
| D4 | A ticket has zero or one journey. | 3c |
| D5 | The writes of one scan succeed together or not at all. | 3c |
| D6 | Dates are compared in Asia/Colombo time, so a 00:30 scan counts as the new day. | 3a |

## Accounts

| ID | Rule | Proven in |
| --- | --- | --- |
| A1 | A resident gets a permanent account. A non-resident gets a temporary account. Residency decides, not nationality. | 3a |
| A2 | A temporary account is valid through its visa expiry date (that day included, Asia/Colombo time). After that nothing can be debited or added. | 3a |
| A3 | A permanent account never expires. | 3a |
| A4 | "Can pay" means the account is usable at scan time and has enough credit. Boarding (B5) and exit (X6) both use it. | 3d |

## Worked numbers (for the first tests)

Route R100 charges 20 + 4.5 per km from 1 July 2026, and 20 + 4.0 per km before that. Route R200 is a flat LKR 30.

| Trip | Calculation | Fare |
| --- | --- | --- |
| S1 to S5 (10.8 km) | 20 + 4.5 x 10.8 = 68.6, rounded up | LKR 69 |
| S1 to S4 (7.4 km) | 20 + 4.5 x 7.4 = 53.3, rounded up | LKR 54 |
| S2 to S5 (8.7 km) | 20 + 4.5 x 8.7 = 59.15, rounded up | LKR 60 |
| S1 to S5 on 30 June 2026 | 20 + 4.0 x 10.8 = 63.2, rounded up | LKR 64 |
| R200, any two stops | flat | LKR 30 |