# MetroLink: Journey Execution

A bus reader for MetroLink. A passenger taps a QR ticket going in and going out. The server checks the ticket, works out the fare for the stops actually travelled, takes it from the passenger's account, and tells the reader what to show.

This repository holds two functions of the larger MetroLink system:

- **Journey Execution**: scan in, scan out, fare calculation, payment settlement.
- **Customer onboarding**: register a new customer and open their account, and log an existing customer in. It is the "New user - Registration" and "Existing user - Login" part of the Top Up Credit sequence diagram.

Alerts, top-ups and ticket issuing belong to other functions and are only stood in for by demo data.

- `backend`: Java 17, plain JDK HTTP server, Jackson, JUnit 4, Maven. No framework.
- `frontend`: React and Vite. It only displays what the server decides. Two screens: Reader and New customer.
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

It listens on port 4000. Set `PORT` to use another. Data lives in memory and starts fresh each run.

Frontend (needs Node 22 or later), in a second terminal:

```
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` to port 4000. Use the tabs under the title to switch between the Reader and the New customer screen.

## Try it

Demo passengers and tickets are created for today's date every time the server starts. "Start over with demo data" in the screen restores them at any time.

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

Open the **New customer** tab. Registrations are kept in memory, and "Start over with demo data" forgets them.

| What to do | What you should see |
|---|---|
| Press "Open my account" with an empty form | Every wrong field is marked at once: name, email, password and residency |
| Choose Resident, fill in the rest, open the account | "Account opened": a **permanent** account with LKR 0, and the customer number, kind and balance on the card. The new customer is in the Passengers list on the Reader tab |
| Choose Non-resident | A visa expiry date field appears. Leave it empty or pick a past date and you are told. A date of today or later gives a **temporary** account that works until that date |
| Register the same email again, even in capitals or with spaces | "This email is already registered" |
| Log in with the email and password you just used | The **Reader** tab opens with "Logged in as ..." and a Log out button. It shows only that customer's account, tickets and journeys, and every ticket shows its travel date. A new customer has no tickets yet |
| Log in with a wrong password, or with an email nobody has | The same message for both: "That email and password do not match an account." |
| Log in as `nimal@example.com` with `Demo@1234` | Works, and the Reader tab shows his six tickets. All five demo customers use the password `Demo@1234`. Log in as `sanduni@example.com` and you see her one ticket instead |
| Press Log out | Back to the New customer screen. The Reader tab shows everyone again, as it did before anyone logged in |
| On the Reader tab, add credit to the new customer in the Passengers list | The balance goes up, but there are no tickets: issuing tickets is another function |

The rules are O1 to O9 in `docs/rules.md`. Section 8 of `docs/design.md` shows how each message in the sequence diagram maps to code.

## API

Customer onboarding has two endpoints:

| Endpoint | Body | Answer |
|---|---|---|
| `POST /api/onboarding/register` | `{ "name", "email", "password", "residency": "RESIDENT" \| "NON_RESIDENT", "visaExpiry": "2026-12-31" }` (`visaExpiry` only for a non-resident) | 201 with `{ "userId", "message", "customer": { ... } }`. 400 `VALIDATION` with one entry per wrong field. 409 `EMAIL_TAKEN`. |
| `POST /api/onboarding/login` | `{ "email", "password" }` | 200 with the same shape. 401 `INVALID_LOGIN` for a wrong password or an unknown email. |

No answer ever contains the password or its hash.

For Journey Execution, one endpoint carries the whole function: `POST /api/reader/scan` with `{ "qrCode", "direction": "IN" | "OUT", "stopId", "scannedAt" (optional) }`. A refused ticket is a normal answer (HTTP 200, `outcome: "REJECTED"`), not an error. The full list is in `docs/design.md`.

## Code map

```
backend/src/main/java/lk/metrolink/journey
  domain/        Passenger, Account (permanent and temporary), QRTicket, Journey, Route, FareRule, states,
                 OnboardingRequest (what a registration may contain), PasswordHash
  fare/          FareCalculator and the distance and flat strategies
  repository/    interfaces, plus memory/ with the in-memory database and unit of work
  service/       JourneyService (the workflow), ReaderService (the front door), QueryService,
                 OnboardingService (register and log in)
  web/           HTTP server, routes, validation
  Container.java wires everything together
```
