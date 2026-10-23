-- MetroLink database schema.
--
-- Plain SQL, kept to what H2 (used here), PostgreSQL and MySQL all accept: table-level constraints, no vendor
-- functions, no auto-increment. Every statement ends with a semicolon at the end of a line, and every table is
-- created only if it is missing, so running this again is harmless.

-- ---- the network: maintained by the Transport Manager, only read by Journey Execution

CREATE TABLE IF NOT EXISTS stops (
    stop_id   VARCHAR(10)  NOT NULL,
    name      VARCHAR(100) NOT NULL,
    latitude  DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    CONSTRAINT pk_stops PRIMARY KEY (stop_id)
);

CREATE TABLE IF NOT EXISTS routes (
    route_no VARCHAR(10)  NOT NULL,
    name     VARCHAR(150) NOT NULL,
    CONSTRAINT pk_routes PRIMARY KEY (route_no)
);

CREATE TABLE IF NOT EXISTS route_stops (
    route_no    VARCHAR(10)  NOT NULL,
    sequence_no INTEGER      NOT NULL,
    stop_id     VARCHAR(10)  NOT NULL,
    distance_km DECIMAL(8,3) NOT NULL,
    CONSTRAINT pk_route_stops PRIMARY KEY (route_no, sequence_no),
    CONSTRAINT uq_route_stops_stop UNIQUE (route_no, stop_id),
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_no) REFERENCES routes (route_no),
    CONSTRAINT fk_route_stops_stop FOREIGN KEY (stop_id) REFERENCES stops (stop_id),
    CONSTRAINT ck_route_stops_distance CHECK (distance_km >= 0)
);

CREATE TABLE IF NOT EXISTS fare_rules (
    fare_rule_id   BIGINT        NOT NULL,
    route_no       VARCHAR(10)   NOT NULL,
    fare_type      VARCHAR(10)   NOT NULL,
    base_fare      BIGINT        NOT NULL,
    rate_per_km    DECIMAL(10,4) NOT NULL,
    flat_amount    BIGINT        NOT NULL,
    effective_from DATE          NOT NULL,
    effective_to   DATE,
    CONSTRAINT pk_fare_rules PRIMARY KEY (fare_rule_id),
    CONSTRAINT fk_fare_rules_route FOREIGN KEY (route_no) REFERENCES routes (route_no),
    CONSTRAINT ck_fare_rules_type CHECK (fare_type IN ('DISTANCE', 'FLAT')),
    CONSTRAINT ck_fare_rules_amounts CHECK (base_fare >= 0 AND rate_per_km >= 0 AND flat_amount >= 0),
    CONSTRAINT ck_fare_rules_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- ---- customers: written by onboarding

-- email is kept as typed. email_key is the same email trimmed and in lower case, and is the unique one (rule O2).
-- A resident has no visa date and a non-resident must have one: the passenger's residency decides the kind of account.
CREATE TABLE IF NOT EXISTS passengers (
    passenger_id BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    email        VARCHAR(254) NOT NULL,
    email_key    VARCHAR(254) NOT NULL,
    residency    VARCHAR(12)  NOT NULL,
    visa_expiry  DATE,
    CONSTRAINT pk_passengers PRIMARY KEY (passenger_id),
    CONSTRAINT uq_passengers_email UNIQUE (email_key),
    CONSTRAINT ck_passengers_residency CHECK (residency IN ('RESIDENT', 'NON_RESIDENT')),
    CONSTRAINT ck_passengers_visa CHECK (
        (residency = 'RESIDENT' AND visa_expiry IS NULL) OR (residency = 'NON_RESIDENT' AND visa_expiry IS NOT NULL))
);

-- Kept apart from passengers, so a passenger row never travels with a password hash attached (rule O6).
CREATE TABLE IF NOT EXISTS credentials (
    passenger_id  BIGINT       NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT pk_credentials PRIMARY KEY (passenger_id),
    CONSTRAINT fk_credentials_passenger FOREIGN KEY (passenger_id) REFERENCES passengers (passenger_id)
);

-- One account per passenger. It has no kind of its own: a resident's is permanent, a non-resident's is temporary and
-- works until the passenger's visa_expiry.
CREATE TABLE IF NOT EXISTS accounts (
    account_id   BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    balance      BIGINT NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (account_id),
    CONSTRAINT uq_accounts_passenger UNIQUE (passenger_id),
    CONSTRAINT fk_accounts_passenger FOREIGN KEY (passenger_id) REFERENCES passengers (passenger_id),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0)
);

-- ---- journey execution

CREATE TABLE IF NOT EXISTS tickets (
    ticket_id                   BIGINT       NOT NULL,
    qr_code                     VARCHAR(64)  NOT NULL,
    passenger_id                BIGINT       NOT NULL,
    route_no                    VARCHAR(10)  NOT NULL,
    boarding_stop_id            VARCHAR(10)  NOT NULL,
    planned_destination_stop_id VARCHAR(10)  NOT NULL,
    travel_date                 DATE         NOT NULL,
    quoted_fare                 BIGINT       NOT NULL,
    issued_at                   TIMESTAMP(6) NOT NULL,
    status                      VARCHAR(10)  NOT NULL,
    CONSTRAINT pk_tickets PRIMARY KEY (ticket_id),
    CONSTRAINT uq_tickets_qr_code UNIQUE (qr_code),
    CONSTRAINT fk_tickets_passenger FOREIGN KEY (passenger_id) REFERENCES passengers (passenger_id),
    CONSTRAINT fk_tickets_route FOREIGN KEY (route_no) REFERENCES routes (route_no),
    CONSTRAINT fk_tickets_boarding FOREIGN KEY (boarding_stop_id) REFERENCES stops (stop_id),
    CONSTRAINT fk_tickets_destination FOREIGN KEY (planned_destination_stop_id) REFERENCES stops (stop_id),
    CONSTRAINT ck_tickets_fare CHECK (quoted_fare >= 0),
    CONSTRAINT ck_tickets_status CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'CANCELLED'))
);

-- A ticket has at most one journey (rule D4).
CREATE TABLE IF NOT EXISTS journeys (
    journey_id       BIGINT       NOT NULL,
    ticket_id        BIGINT       NOT NULL,
    route_no         VARCHAR(10)  NOT NULL,
    boarding_stop_id VARCHAR(10)  NOT NULL,
    tap_in_time      TIMESTAMP(6) NOT NULL,
    exit_stop_id     VARCHAR(10),
    tap_out_time     TIMESTAMP(6),
    fare_due         BIGINT,
    fare_charged     BIGINT,
    status           VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_journeys PRIMARY KEY (journey_id),
    CONSTRAINT uq_journeys_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_journeys_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (ticket_id),
    CONSTRAINT fk_journeys_route FOREIGN KEY (route_no) REFERENCES routes (route_no),
    CONSTRAINT fk_journeys_boarding FOREIGN KEY (boarding_stop_id) REFERENCES stops (stop_id),
    CONSTRAINT fk_journeys_exit FOREIGN KEY (exit_stop_id) REFERENCES stops (stop_id),
    CONSTRAINT ck_journeys_fares CHECK ((fare_due IS NULL OR fare_due >= 0) AND (fare_charged IS NULL OR fare_charged >= 0)),
    CONSTRAINT ck_journeys_status CHECK (status IN ('IN_PROGRESS', 'PAYMENT_PENDING', 'COMPLETED'))
);

-- A journey is charged at most once (rule D2), and the reference makes a repeat charge recognisable.
CREATE TABLE IF NOT EXISTS fare_transactions (
    transaction_id BIGINT       NOT NULL,
    reference      VARCHAR(40)  NOT NULL,
    account_id     BIGINT       NOT NULL,
    journey_id     BIGINT       NOT NULL,
    amount         BIGINT       NOT NULL,
    balance_after  BIGINT       NOT NULL,
    created_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_fare_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT uq_fare_transactions_reference UNIQUE (reference),
    CONSTRAINT uq_fare_transactions_journey UNIQUE (journey_id),
    CONSTRAINT fk_fare_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_fare_transactions_journey FOREIGN KEY (journey_id) REFERENCES journeys (journey_id),
    CONSTRAINT ck_fare_transactions_amount CHECK (amount >= 0 AND balance_after >= 0)
);

-- The next free id for each table. It changes inside the same transaction as the insert, so an id handed out by a
-- change that is rolled back is handed out again.
CREATE TABLE IF NOT EXISTS id_counters (
    name       VARCHAR(30) NOT NULL,
    next_value BIGINT      NOT NULL,
    CONSTRAINT pk_id_counters PRIMARY KEY (name)
);
