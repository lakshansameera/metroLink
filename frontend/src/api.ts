import type { AuthResult, Direction, Journey, Passenger, ReaderDisplay, Reference, RegisterInput, Ticket } from './types';

/** One wrong field in a rejected request. `path` is the field name, empty when the whole body is wrong. */
export interface FieldIssue {
  path: string;
  message: string;
}

/** An error the API explained, or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: FieldIssue[];

  constructor(status: number, code: string, message: string, details: FieldIssue[] = []) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: body === undefined ? undefined : { 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Start the backend on port 4000, then reload.');
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data.error ?? 'ERROR',
      data.message ?? `Request failed (${response.status})`,
      Array.isArray(data.details) ? data.details : [],
    );
  }
  return data as T;
}

export const api = {
  reference: () => request<Reference>('GET', '/reference'),
  passengers: () => request<Passenger[]>('GET', '/passengers'),
  tickets: () => request<Ticket[]>('GET', '/tickets'),
  journeys: () => request<Journey[]>('GET', '/journeys'),

  scan: (qrCode: string, direction: Direction, stopId: string) =>
    request<ReaderDisplay>('POST', '/reader/scan', { qrCode, direction, stopId }),
  settle: (journeyId: number) => request<ReaderDisplay>('POST', `/journeys/${journeyId}/settle`),

  // Customer onboarding: the registration and login branches of the Top Up Credit sequence.
  register: (input: RegisterInput) => request<AuthResult>('POST', '/onboarding/register', input),
  login: (email: string, password: string) => request<AuthResult>('POST', '/onboarding/login', { email, password }),

  // Test data. Topping up belongs to a different function; this only gives a demo passenger some money.
  addCredit: (passengerId: number, amount: number) =>
    request<Passenger>('POST', '/test-data/credit', { passengerId, amount }),
  reset: () => request<{ status: string }>('POST', '/test-data/reset'),
};
