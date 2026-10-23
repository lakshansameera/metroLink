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
| X7 | A trip that goes outside the ticket is `OVER_TRAVEL`: the passenger got off after the planned stop, or tapped in before the ticket's boarding stop, or both. It is priced to the stops really used (X3) and the result is marked `overtravelled`. It is a flag, not a payment failure. | 3d |
| X8 | Scanning out again on a settled journey changes nothing (`ALREADY_SETTLED`). | 3d, 3e |
| X9 | Tapping in at a stop other than the ticket's, but before the planned destination (B4), is allowed. The journey records the stop really tapped at, the planned-fare check (B5) is made from that stop, the reader answers with a warning that names both stops, and the console log says so. Tapping in at the ticket's own stop adds nothing. | `BoardingTest`, `ReaderServiceTest`, `ConsoleTraceTest` |
| X10 | At the exit the real trip is put next to the ticket. `OVER_TRAVEL` (X7); `SHORTER` when it stays inside the ticket's stretch but at other stops (boarded later or got off earlier); `AS_TICKETED` when the stops are the ticket's. The fare owed is compared with the ticket's fare: `fareDifference` is owed minus quoted, and `overcharged` is true when it is above zero. Nothing is added to the fare for travelling further and the ticket's own quoted fare is never rewritten. A fare that waits in `PAYMENT_PENDING` is compared the same way when it is settled. | `TripComparisonTest`, `ExitTest`, `ReaderServiceTest`, `ApiTest`, `ConsoleTraceTest` |

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

## Customer onboarding (register and log in)

These rules come from the "New user - Registration" and "Existing user - Login" branches of the Top Up Credit sequence diagram. Here "Proven in" names the test class. Registering is the same step as `register(name, email, password)`, then `createUser`, then `createAccount(userId, initialCredit=0)`, then `registrationSuccess(userId)`.

| ID | Rule | Proven in |
| --- | --- | --- |
| O1 | Registration needs a name of 1 to 100 characters, an email that looks like one, a password of 8 to 128 characters, and a residency. Every problem is reported at once (`VALIDATION`, HTTP 400). | `OnboardingRequestTest`, `ApiTest` |
| O2 | An email can be registered once. Emails are compared without case and without surrounding spaces. A second registration is refused (`EMAIL_TAKEN`, HTTP 409). | `OnboardingTest`, `ConstraintsTest` |
| O3 | A resident gets a permanent account. A non-resident must give a visa expiry date that is today or later (Asia/Colombo) and gets a temporary account valid through that date. A resident must not give a visa date. Residency decides, not nationality (see A1, A2). | `OnboardingRequestTest`, `OnboardingTest` |
| O4 | A new account starts with LKR 0. | `OnboardingTest` |
| O5 | The customer, the password and the account are created together or not at all (see D5). | `OnboardingTest` |
| O6 | The password is never stored or returned. Only a salted PBKDF2 hash is kept. | `PasswordHashTest`, `OnboardingTest`, `ApiTest` |
| O7 | Logging in with the right email and password succeeds and returns the customer. | `OnboardingTest`, `ApiTest` |
| O8 | A wrong password and an unknown email give the same answer (`INVALID_LOGIN`, HTTP 401), so the answer never reveals which emails are registered. | `OnboardingTest`, `ApiTest` |
| O9 | A newly registered customer is in the passenger list straight away, with the right account kind and a balance of 0. | `OnboardingTest`, `ApiTest` |

## Ticket booking

A customer books a ticket for a trip. This replaces the demo tickets as the way tickets come to exist. "Proven in" names the test class. Booking never takes money: the fare is only quoted, and is charged at the exit (decision 3 in `docs/design.md`).

| ID | Rule | Proven in |
| --- | --- | --- |
| T1 | A booking needs a customer, a route, a boarding stop, a destination stop and a travel date. Every wrong field is reported at once (`VALIDATION`, HTTP 400). | `BookingRequestTest`, `BookingTest`, `ApiTest` |
| T2 | The route must exist, both stops must be on it, and the destination must come after the boarding stop. | `BookingRequestTest`, `BookingTest` |
| T3 | The travel date must be today or later in Asia/Colombo time (D6), and at most 30 days ahead. | `BookingRequestTest`, `BookingTest` |
| T4 | A temporary account cannot book a day after its visa expiry date, because the account is not valid then (A2). A permanent account has no such limit. | `BookingRequestTest`, `BookingTest` |
| T5 | A customer who does not exist cannot book (`NOT_FOUND`, HTTP 404). | `BookingTest`, `ApiTest` |
| T6 | The quoted fare is what the fare calculator gives for the booked stops on the travel date, so it is the same fare the exit would charge (X3, X4). A flat route quotes its flat amount. | `BookingTest` |
| T7 | Booking takes no money and changes no balance. The new ticket is `ACTIVE`, has no journey, and its issue time is the moment of booking. | `BookingTest` |
| T8 | Every ticket gets its own QR code that is hard to guess. A code that is already taken is never reused: the service draws another, and the store refuses a duplicate anyway. | `BookingTest`, `ConstraintsTest`, `QrCodesTest` |
| T9 | The ticket belongs to the customer who booked it and is in their ticket list at once. A booking is stored completely or not at all (D5). | `BookingTest` |
| T10 | The answer says whether the account could pay the fare right now (A4), so the screen can tell the customer to add credit. It never refuses the booking for that. | `BookingTest` |
| T11 | A quote checks the same rules as a booking and returns the fare, but creates nothing. | `BookingTest`, `ApiTest` |
| T12 | A booked ticket works at the reader exactly like any other: it can be tapped in on its travel date and refused on any other day (B2). | `BookingTest`, `ApiTest` |

## Console logs

The server writes what it is doing to the console (section 11 of `docs/design.md`). "Proven in" names the test class.

| ID | Rule | Proven in |
| --- | --- | --- |
| L1 | The log is off unless the application turns it on, so a test, or any code that uses the classes directly, prints nothing. With the level off, nothing is printed and nothing is formatted. | `LogTest`, `ConsoleTraceTest` |
| L2 | `METROLINK_LOG` chooses the level: `off`, `warn`, `info` or `debug`, in any case. The default is `info`. Any other value stops start-up and says which values are allowed. A level shows its own lines and those of the levels before it. | `AppSettingsTest`, `LogTest`, `ConsoleTraceTest` |
| L3 | A line is `time level request topic message`, on one line of plain printable ASCII, with the time in Colombo. A character that is not printable or not ASCII is replaced by `?`, so a request cannot forge a line, and a line is cut at 400 characters. The request is the number of the HTTP request that caused the line, or `-` when none did. | `LogTest`, `ConsoleTraceTest`, `WebLogTest` |
| L4 | The log never shows a password, a password hash, a request body or a whole email address. An email that a repository call carries is masked (`a***@example.com`), and a hash is shown as `a password hash (hidden)`. QR codes and ids are shown. | `ConsoleTraceTest`, `RepositoryTraceTest`, `WebLogTest` |
| L5 | A read, a fare quote and the browser's permission check are routine: their lines show only at `debug`. A request that changes something shows at `info`. A warning always shows. | `WebLogTest` |
| L6 | Every change of state names the old state, the new state and the State class that allowed it. A change that is not allowed, a failed login and an unexpected error are warnings (`WARN`). A refused scan, a validation error and a refused booking are not. | `StateLogTest`, `ConsoleTraceTest`, `WebLogTest` |
| L7 | A unit of work shows where it begins, and whether it commits or rolls back, and why. One inside another shows only at `debug`. A unit that only reads shows begin and commit only at `debug`. | `LoggedUnitOfWorkTest`, `ConsoleTraceTest` |
| L8 | Logging never changes what the program does: the same requests give the same answers with the log off and at `debug`; an exception goes through the logging unchanged; a message that cannot be formatted becomes a note and never fails the request; a failure inside the program is a warning and still answers HTTP 500. | `ConsoleTraceTest`, `LoggedUnitOfWorkTest`, `RepositoryTraceTest`, `LogTest`, `WebLogTest` |
| L9 | At `debug`, every repository call shows its arguments and what it returned. Below `debug`, none does. | `RepositoryTraceTest`, `ConsoleTraceTest` |

## States on the screen

The screen shows a ticket's state and a journey's state on each ticket, on each journey and on the reader face. "Proven in" names the test class. The look of the screen was also checked in a browser against the live server: 52 comparisons of what a card, a journey row or the reader face said with what the server reported, in each step below, with no difference.

| ID | Rule | Proven in |
| --- | --- | --- |
| V1 | The screen writes a state exactly as the server names it: `ACTIVE`, `USED`, `EXPIRED`, `CANCELLED` for a ticket and `IN_PROGRESS`, `PAYMENT_PENDING`, `COMPLETED` for a journey. It has no word of its own in their place. Its list of states (`frontend/src/types.ts`) is the server's list, and every state the server sends is in it. | `FrontEndStatesTest` |
| V2 | The database accepts exactly those states, so no row can hold a state the screen does not know. The test reads the two `CHECK` lists in `schema.sql`. | `FrontEndStatesTest` |
| V3 | A ticket and its journey show the pair the server moves them through. Tap in: `ACTIVE` and `IN_PROGRESS`. Tap out with the fare paid: `USED` and `COMPLETED`, together. Tap out with the fare unpaid: `ACTIVE` and `PAYMENT_PENDING`, until it is paid, and then `USED` and `COMPLETED`. A ticket is `USED` only when its journey is `COMPLETED`. | `FrontEndStatesTest`, `ExitTest`, `SettlementTest` |
| V4 | A refused tap changes no state. A ticket for another day stays `ACTIVE` (nothing expires it by itself) and the screen says the reader refuses it for its date. The answer to a refused tap carries the ticket only, so the reader face shows only the ticket's state then, as the console log does. | `FrontEndStatesTest` |
| V5 | The screen writes a kind of trip exactly as the server names it: `AS_TICKETED`, `SHORTER`, `OVER_TRAVEL`. Its list (`TRIP_KINDS` in `frontend/src/types.ts`) is the server's list, every kind the server sends is in it, and the reader face and the journey list show the same kind and the same difference for one journey. Over-travel and over-charged are chips beside the states, not new states: a journey is still `COMPLETED` or `PAYMENT_PENDING`. | `FrontEndStatesTest`, `ApiTest` |

## Worked numbers (for the first tests)

Route R100 charges 20 + 4.5 per km from 1 July 2026, and 20 + 4.0 per km before that. Route R200 is a flat LKR 30.

| Trip | Calculation | Fare |
| --- | --- | --- |
| S1 to S5 (10.8 km) | 20 + 4.5 x 10.8 = 68.6, rounded up | LKR 69 |
| S1 to S4 (7.4 km) | 20 + 4.5 x 7.4 = 53.3, rounded up | LKR 54 |
| S2 to S5 (8.7 km) | 20 + 4.5 x 8.7 = 59.15, rounded up | LKR 60 |
| S1 to S5 on 30 June 2026 | 20 + 4.0 x 10.8 = 63.2, rounded up | LKR 64 |
| R200, any two stops | flat | LKR 30 |