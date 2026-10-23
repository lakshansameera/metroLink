// The JSON the backend sends. Field names match the Java view classes in lk.metrolink.journey.service.

export type Direction = 'IN' | 'OUT';
export type Tone = 'SUCCESS' | 'WARNING' | 'ERROR';
export type Outcome = 'JOURNEY_STARTED' | 'JOURNEY_COMPLETED' | 'PAYMENT_PENDING' | 'ALREADY_SETTLED' | 'REJECTED';
export type JourneyStatus = 'IN_PROGRESS' | 'PAYMENT_PENDING' | 'COMPLETED';
export type TicketStatus = 'ACTIVE' | 'USED' | 'EXPIRED' | 'CANCELLED';

export interface RouteStop {
  stopId: string;
  name: string;
  sequenceNo: number;
  distanceKm: number;
}

export interface Route {
  routeNo: string;
  name: string;
  stops: RouteStop[];
}

export interface Reference {
  stops: { stopId: string; name: string }[];
  routes: Route[];
}

export type Residency = 'RESIDENT' | 'NON_RESIDENT';

export interface Passenger {
  passengerId: number;
  name: string;
  email: string;
  residency: Residency;
  accountId: number;
  accountType: 'PERMANENT' | 'TEMPORARY';
  balance: number;
  visaExpiry: string | null;
}

export interface Ticket {
  ticketId: number;
  qrCode: string;
  passengerId: number;
  passengerName: string;
  routeNo: string;
  boardingStopId: string;
  boardingStopName: string;
  plannedDestinationStopId: string;
  plannedDestinationStopName: string;
  travelDate: string;
  quotedFare: number;
  status: TicketStatus;
}

export interface Journey {
  journeyId: number;
  ticketId: number;
  qrCode: string;
  passengerId: number;
  passengerName: string;
  routeNo: string;
  boardingStopId: string;
  boardingStopName: string;
  exitStopId: string | null;
  exitStopName: string | null;
  plannedDestinationStopId: string;
  tapInTime: string;
  tapOutTime: string | null;
  fareDue: number | null;
  fareCharged: number | null;
  status: JourneyStatus;
  overtravelled: boolean;
}

export interface Fare {
  due?: number;
  charged?: number;
  balance?: number;
  shortfall?: number;
  required?: number;
}

export interface ReaderDisplay {
  outcome: Outcome;
  tone: Tone;
  message: string;
  reason?: string;
  ticket?: Ticket;
  journey?: Journey;
  fare: Fare;
  overtravelled: boolean;
}

/** What the registration form sends. A resident sends no visa date. */
export interface RegisterInput {
  name: string;
  email: string;
  password: string;
  residency?: Residency;
  visaExpiry?: string;
}

/** The answer to a registration or a login. It never carries a password. */
export interface AuthResult {
  userId: number;
  message: string;
  customer: Passenger;
}
