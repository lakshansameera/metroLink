# MetroLink: Journey Execution

A bus reader for MetroLink. A passenger taps a QR ticket going in and going out. The server checks the ticket, works out the fare for the stops actually travelled, takes it from the passenger's account, and tells the reader what to show.

This repository holds two functions of the larger MetroLink system:

- **Journey Execution**: scan in, scan out, fare calculation, payment settlement.
- **Customer onboarding**: register a new customer and open their account, and log an existing customer in. It is the "New user - Registration" and "Existing user - Login" part of the Top Up Credit sequence diagram.
- **Ticket booking**: a logged-in customer picks a route, two stops and a day, sees the fare, and books. The ticket is the same kind the reader already accepts.

Alerts and top-ups belong to other functions. Top-up is only stood in for by the "Add 50" and "Add 100" buttons.

- `backend`: Java 17, plain JDK HTTP server, plain JDBC, an embedded H2 database, Jackson, JUnit 4, Maven. No framework.
- `frontend`: React and Vite. It only displays what the server decides. Two screens: sign-up and log-in first, then the Reader (with the booking panel) once a customer has logged in.
- `docs/rules.md`: every business rule as one testable sentence.
- `docs/design.md`: the model, the state machines, the API and the design patterns.

## Run it

Backend (needs Java 17 and Maven):

```
cd backend
mvn test                       # all tests
mvn package                    # builds target/metrolink-journey-execution.jar
java -jar target/metrolink-journey-execution.jar
```

It listens on port 4000. Set `PORT` to use another. Data is kept in a database file, so it is still there after a restart (see "Database" below). Nothing needs installing: the database runs inside the server.

Frontend (needs Node 22 or later), in a second terminal:

```
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` to port 4000. The first screen is sign-up and log-in. The Reader screen opens only after a customer logs in, and Log out brings the first screen back.

## Try it

Demo passengers and tickets are created the first time the server starts with an empty database, dated for that day. After that they stay as they are, including any journeys you made. "Start over with demo data" in the screen empties the database and loads them again, dated for the day the server started. On a later day the old tickets are for the wrong day, so press it once to get tickets for that day.

The Reader shows only the logged-in customer's tickets, so log in as the owner of the ticket you want. Every demo customer's password is `Demo@1234`.

| Ticket | Owner, log in as |
|---|---|
| `MLK-HAPPY01`, `MLK-OVERTR1`, `MLK-FLAT001`, `MLK-OLD0001`, `MLK-FUTURE1`, `MLK-CANCEL01` | Nimal Perera, `nimal@example.com` |
| `MLK-PENDING` | Sanduni Silva, `sanduni@example.com` |
| `MLK-NOFUNDS` | Kasun Fernando, `kasun@example.com` |
| `MLK-TOURIST1` | Emma Walker, `emma@example.com` |
| `MLK-VISAEXP1` | Hiro Tanaka, `hiro@example.com` |

| Ticket | What to try | What you should see |
|---|---|---|
| `MLK-HAPPY01` | Tap in at Colombo Fort, tap out at Kiribathgoda | Fare LKR 69 charged |
| `MLK-HAPPY01` | Tap out at Peliyagoda instead | Fare LKR 54: the stop actually used is priced, not the planned one |
| `MLK-OVERTR1` | Planned for Peliyagoda; tap out at Kiribathgoda | LKR 69 charged, and the journey is marked as gone past the planned stop |
| `MLK-FLAT001` | Tap in and out on the circular route | Flat LKR 30 |
| `MLK-PENDING` | Tap in, then tap out at Kiribathgoda (balance LKR 45) | Fare owed, LKR 24 short. Add credit, then "Pay fare now" |
| `MLK-NOFUNDS` | Tap in | Refused: not enough credit |
| `MLK-OLD0001`, `MLK-FUTURE1`, `MLK-CANCEL01` | Tap in | Refused: expired, wrong day, cancelled |
| `MLK-TOURIST1` | Tap in and out | Works: a tourist's temporary account is valid until the visa date |
| `MLK-VISAEXP1` | Tap in | Refused: the tourist's visa has expired, so the account cannot be used |
| any | Tap out twice | The second tap changes nothing |

## Try customer onboarding

Start on the first screen, which has the sign-up form and the log-in form. Registrations are kept in the database, so a customer who registered is still there after the server restarts. "Start over with demo data" removes them.

| What to do | What you should see |
|---|---|
| Press "Open my account" with an empty form | Every wrong field is marked at once: name, email, password and residency |
| Choose Resident, fill in the rest, open the account | "Account opened": a **permanent** account with LKR 0, and the customer number, kind and balance on the card. The log-in form is filled in with the new email |
| Choose Non-resident | A visa expiry date field appears. Leave it empty or pick a past date and you are told. A date of today or later gives a **temporary** account that works until that date |
| Register the same email again, even in capitals or with spaces | "This email is already registered" |
| Log in with the email and password you just used | The **Reader** screen opens, with "Logged in as ..." and a Log out button. It shows only that customer's account, tickets and journeys, and every ticket shows its travel date. A new customer has no tickets until they book one |
| Log in with a wrong password, or with an email nobody has | The same message for both: "That email and password do not match an account." |
| Log in as `nimal@example.com` with `Demo@1234` | Works, and the Reader shows his six tickets. All five demo customers use the password `Demo@1234`. Log in as `sanduni@example.com` and you see her one ticket instead |
| Press Log out | Back to the first screen with empty forms. The Reader is not reachable again until someone logs in |
| Log in as the new customer, and press Add 50 under "Your account" | The balance goes up. Booking a ticket does not need credit, but tapping in does |

The rules are O1 to O9 in `docs/rules.md`. Section 8 of `docs/design.md` shows how each message in the sequence diagram maps to code.

## Try ticket booking

Log in first (the "Book a ticket" panel is at the top of the Reader). A brand-new customer is the clearest test, because their ticket list starts empty.

| What to do | What you should see |
|---|---|
| Choose route R100, "Get on at" Colombo Fort, "Get off at" Kiribathgoda | The fare appears before you book: **LKR 69 for 10.8 km**, "Charged when you get off, not now", and your balance. With no credit the note says to add credit before you tap in |
| Choose a "Get off at" stop that is before the boarding stop | The server's message appears under that field: "The destination must come after the boarding stop". No fare is shown |
| Set the travel date to yesterday, or to 31 days from today | "Travel date must be today or later" / "Travel date can be at most 30 days ahead". Today and 30 days ahead are accepted |
| Press "Book ticket" | A green confirmation, the ticket appears in "Tickets" already selected, and the form empties. The balance has **not** changed |
| Press "Tap in" straight away with no credit | Refused: not enough credit to board. Press "Add 100", then tap in and tap out at Kiribathgoda: **LKR 69 is charged, the same as the quote** |
| Book a ticket for tomorrow and tap in | Refused: "This ticket is for a different day" |
| Choose route R200 | A flat fare: LKR 30 for any two stops |
| Log in as `emma@example.com` (visa ends 10 days after the day the data was created) and try to book a day after her visa ends | Refused, naming the date her account works through. Hiro (`hiro@example.com`) has no valid days at all |
| Log in as someone else | They see only their own tickets |

The rules are T1 to T12 in `docs/rules.md`. Section 10 of `docs/design.md` explains the design.

## Database

The server keeps its data in an [H2](https://www.h2database.com) database, an SQL database that runs inside the Java program, so there is nothing to install. By default the data is the file `data/metrolink.mv.db`, created next to where you start the server (`backend/data/` if you start it from `backend`). The `data` folder is ignored by Git.

| Setting | Meaning | Default |
|---|---|---|
| `METROLINK_DB` | A JDBC URL, or `memory` to keep everything in memory and forget it on exit | `jdbc:h2:file:./data/metrolink;AUTO_SERVER=TRUE;WRITE_DELAY=0` |
| `METROLINK_DB_USER` | Database user | `sa` |
| `METROLINK_DB_PASSWORD` | Database password | empty |

Setting one for a single run, in PowerShell and in bash:

```
$env:METROLINK_DB = "memory"; java -jar target/metrolink-journey-execution.jar        # PowerShell: nothing saved
METROLINK_DB=memory java -jar target/metrolink-journey-execution.jar                   # bash: nothing saved
```

- **First start.** The tables are created from `backend/src/main/resources/lk/metrolink/journey/repository/jdbc/schema.sql`, then the network and demo data are loaded. Later starts leave the data exactly as it is.
- **Looking inside.** Because of `AUTO_SERVER=TRUE`, a tool can open the same database while the server runs. With the [H2 console](https://www.h2database.com/html/tutorial.html#console_settings) or DBeaver, use the same URL (give the full path to the file), user `sa` and an empty password. Tables: `passengers`, `credentials`, `accounts`, `tickets`, `journeys`, `fare_transactions`, `stops`, `routes`, `route_stops`, `fare_rules`, `id_counters`.
- **Stopping.** Each change is written to disk before the server answers (`WRITE_DELAY=0`), so a customer who was told "account opened" is still there even if the server is killed instead of stopped, for example by an IDE's stop button. If you give your own H2 URL, keep that option.
- **Start over.** "Start over with demo data" deletes every row and loads the demo data again. To start completely fresh, stop the server and delete the `data` folder.
- **Another database.** `schema.sql` uses plain SQL, so it should also run on PostgreSQL or MySQL: add that database's driver to the `pom.xml` and give its URL in `METROLINK_DB`. Only H2 has been tested.

The tests run every store-dependent test twice, once on the in-memory store and once on H2, so both behave the same way.

## Console logs

The server writes what it is doing to the console, so you can watch the states change and see which class does what. It uses no logging library. Start it and tap a ticket in the Reader, and the console shows this (a real run, shortened):

```
22:57:46.305 INFO  #6    WEB      POST /api/reader/scan
22:57:46.311 INFO  #6    UOW      begin: one unit of work, so every change below is kept together or not at all
22:57:46.312 INFO  #6    SERVICE  ReaderService.scan (the Facade): IN tap for ticket MLK-HAPPY01 at stop S1, handed to JourneyService
22:57:46.315 INFO  #6    STATE    Ticket #1 MLK-HAPPY01 is ACTIVE: ActiveState.refusalReason(travel date 2026-10-23, today 2026-10-23) -> usable
22:57:46.324 INFO  #6    STRATEGY FareCalculator: route R100 S1 to S5 is 10.8 km; fare rule #2 in force on 2026-10-23 is DISTANCE -> DistanceFareStrategy
22:57:46.326 INFO  #6    STRATEGY DistanceFareStrategy: base LKR 20 + LKR 4.5 per km x 10.8 km = LKR 68.60, rounded up to LKR 69
22:57:46.328 INFO  #6    ACCOUNT  Account #1 (PermanentAccount): isUsableAt -> true, balance LKR 500 against LKR 69 -> can pay
22:57:46.330 INFO  #6    FACTORY  Journey.start: new journey for ticket #1 at stop S1, starts in the IN_PROGRESS state
22:57:46.336 INFO  #6    UOW      commit: changes kept (24 ms)
22:57:46.337 INFO  #6    WEB      -> 200 in 32 ms
```

Tap out, and you see `STATE  Journey #1: IN_PROGRESS -> COMPLETED (InProgressState.complete: LKR 69 charged)` and `STATE  Ticket #1 MLK-HAPPY01: ACTIVE -> USED (ActiveState.markUsed)`. A line is `time level request topic message`. The request is the number of the HTTP request that caused the line, so lines of taps that overlap can be told apart. It is `-` for lines written outside a request, such as start-up.

| Topic | What it shows | Pattern |
|---|---|---|
| `STATE` | A ticket or a journey moving from one state to the next, with the State class that allowed it. A move that is not allowed is a `WARN`. | State |
| `STRATEGY` | Which fare strategy priced the trip, and the sum it did | Strategy |
| `FACTORY` | How an account (permanent or temporary), a ticket or a journey comes to exist | Factory |
| `UOW` | Where a unit of work begins and whether it commits or rolls back | Unit of Work (a Decorator) |
| `SERVICE` | What each service decided | Facade |
| `ACCOUNT` | Whether the account can pay, and money moving in or out | |
| `WEB` | Each request, its status and how long it took | |
| `REPO` | Every repository call and what it returned (debug only) | Proxy |
| `APP` | Start-up | |

**Choose how much is shown** with `METROLINK_LOG`. The default is `info`.

| Value | Shows |
|---|---|
| `off` | Nothing |
| `warn` | Only what should be noticed: a state change that is not allowed, a failed login, an unexpected error (HTTP 500) |
| `info` | The behaviour of the classes, as above, for every request that changes something. This is the default. |
| `debug` | Everything in `info`, plus reads, fare quotes, every repository call and each nested unit of work |

```
$env:METROLINK_LOG = "debug"; java -jar target/metrolink-journey-execution.jar        # PowerShell
METROLINK_LOG=debug java -jar target/metrolink-journey-execution.jar                   # bash
```

Any other value stops the server at start-up and says which values are allowed. The screen reloads its lists all the time, so reads, and the fare quote shown while a customer chooses stops, appear only at `debug`; at `info` the console shows only the requests that change something.

- **Nothing private is printed.** No password, no password hash, no request body, and no whole email address (an email in a repository call shows as `a***@example.com`). QR codes and ids are printed, because they are what you follow.
- **One line, plain ASCII.** Anything a client sent, such as a QR code with a line break in it, is cleaned before it is printed, so a request cannot forge a log line. A very long line is cut.
- **Logging never changes what the program does.** With the level `off` and with `debug` the same requests give the same answers, and a message that cannot be formatted is replaced by a note instead of failing the request. The tests do not print anything, because the log is off unless the application turns it on.
- **Console only.** There is no log file. Redirect the output if you want one: `java -jar ... > metrolink.log`.

The rules are L1 to L9 in `docs/rules.md`. Section 11 of `docs/design.md` explains the design.

## API

Customer onboarding has two endpoints:

| Endpoint | Body | Answer |
|---|---|---|
| `POST /api/onboarding/register` | `{ "name", "email", "password", "residency": "RESIDENT" \| "NON_RESIDENT", "visaExpiry": "2026-12-31" }` (`visaExpiry` only for a non-resident) | 201 with `{ "userId", "message", "customer": { ... } }`. 400 `VALIDATION` with one entry per wrong field. 409 `EMAIL_TAKEN`. |
| `POST /api/onboarding/login` | `{ "email", "password" }` | 200 with the same shape. 401 `INVALID_LOGIN` for a wrong password or an unknown email. |

No answer ever contains the password or its hash.

Ticket booking has two endpoints. They take the same body:

| Endpoint | Body | Answer |
|---|---|---|
| `POST /api/tickets/quote` | `{ "passengerId": 6, "routeNo": "R100", "boardingStopId": "S1", "destinationStopId": "S5", "travelDate": "2026-11-02" }` | 200 with `{ "fare", "distanceKm", "fareType": "DISTANCE" \| "FLAT", "balance", "canPayNow" }`. Creates nothing. |
| `POST /api/tickets` | the same | 201 with `{ "ticket": { ... }, "message", "balance", "canPayNow" }`. No money moves. |

Both answer 400 `VALIDATION` with one entry in `details` per wrong field (`routeNo`, `boardingStopId`, `destinationStopId`, `travelDate`, or `passengerId` when it is missing or not a number), and 404 for a customer who does not exist.

For Journey Execution, one endpoint carries the whole function: `POST /api/reader/scan` with `{ "qrCode", "direction": "IN" | "OUT", "stopId", "scannedAt" (optional) }`. A refused ticket is a normal answer (HTTP 200, `outcome: "REJECTED"`), not an error. The full list is in `docs/design.md`.

## Code map

```
backend/src/main/java/lk/metrolink/journey
  domain/        Passenger, Account (permanent and temporary), QRTicket, Journey, Route, FareRule, states,
                 OnboardingRequest (what a registration may contain), PasswordHash,
                 BookingRequest (what a booking may contain)
  fare/          FareCalculator and the distance and flat strategies
  repository/    interfaces and Store, plus memory/ (in-memory store) and jdbc/ (SQL store, schema.sql)
  service/       JourneyService (the workflow), ReaderService (the front door), QueryService,
                 OnboardingService (register and log in), BookingService (quote and book), QrCodes
  web/           HTTP server, routes, validation
  log/           the console log: Log, LoggedUnitOfWork (Decorator), RepositoryTrace (Proxy)
  Container.java wires everything together
```
