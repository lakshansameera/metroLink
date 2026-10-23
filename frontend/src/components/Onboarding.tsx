import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { api, ApiError } from '../api';
import { dayOf, lkr, todayInColombo } from '../format';
import type { AuthResult, Passenger, Residency } from '../types';
import { PassengerList } from './PassengerList';

interface Props {
  passengers: Passenger[];
  /** Reload the lists after a customer joins, so the new person shows up at once. */
  onChanged: () => Promise<void>;
  onTopUp: (passengerId: number, amount: number) => void;
  busy: boolean;
}

type Errors = Record<string, string>;

/** The first message for each field, so a field shows one clear sentence rather than a pile. */
function byField(error: ApiError): Errors {
  const found: Errors = {};
  for (const issue of error.details) {
    if (!(issue.path in found)) found[issue.path] = issue.message;
  }
  return found;
}

interface FieldProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

function Field({ id, label, hint, error, children }: FieldProps) {
  return (
    <div className={error ? 'field field-bad' : 'field'}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <p className="field-error" id={`${id}-note`}>
          {error}
        </p>
      ) : (
        hint && (
          <p className="field-hint" id={`${id}-note`}>
            {hint}
          </p>
        )
      )}
    </div>
  );
}

/**
 * Customer onboarding: register a new customer, or log in an existing one.
 * Every rule lives on the server. This screen only shows what the server says, field by field.
 */
export function Onboarding({ passengers, onChanged, onTopUp, busy }: Props) {
  const ids = useId();
  const id = (field: string) => `${ids}-${field}`;

  // Registration form
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [residency, setResidency] = useState<Residency | ''>('');
  const [visaExpiry, setVisaExpiry] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [registering, setRegistering] = useState(false);

  // Login form
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginErrors, setLoginErrors] = useState<Errors>({});
  const [loginError, setLoginError] = useState<string | null>(null);
  const [loggingIn, setLoggingIn] = useState(false);

  // What happened last, shown on the big face on the right
  const [outcome, setOutcome] = useState<{ kind: 'registered' | 'loggedIn'; result: AuthResult } | null>(null);
  const [justJoined, setJustJoined] = useState<number | null>(null);

  async function register(event: FormEvent) {
    event.preventDefault();
    setRegistering(true);
    setErrors({});
    setFormError(null);
    try {
      const result = await api.register({
        name,
        email,
        password,
        residency: residency || undefined,
        visaExpiry: residency === 'NON_RESIDENT' && visaExpiry ? visaExpiry : undefined,
      });
      setOutcome({ kind: 'registered', result });
      setJustJoined(result.userId);
      setLoginEmail(result.customer.email);
      setLoginPassword('');
      setName('');
      setEmail('');
      setPassword('');
      setShowPassword(false);
      setResidency('');
      setVisaExpiry('');
      try {
        await onChanged();
      } catch {
        // The customer exists; the list will catch up on the next reload.
      }
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION' && e.details.length > 0) {
        const found = byField(e);
        setErrors(found);
        if (found[''] !== undefined) setFormError(found['']);
      } else if (e instanceof ApiError && e.code === 'EMAIL_TAKEN') {
        setErrors({ email: 'This email is already registered. Log in instead, or use a different email.' });
      } else {
        setFormError(e instanceof ApiError ? e.message : 'Something went wrong.');
      }
    } finally {
      setRegistering(false);
    }
  }

  async function logIn(event: FormEvent) {
    event.preventDefault();
    setLoggingIn(true);
    setLoginErrors({});
    setLoginError(null);
    try {
      const result = await api.login(loginEmail, loginPassword);
      setOutcome({ kind: 'loggedIn', result });
      setLoginPassword('');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_LOGIN') {
        // One sentence for both a wrong password and an unknown email, as the server does (rule O8).
        setLoginError('That email and password do not match an account.');
      } else if (e instanceof ApiError && e.code === 'VALIDATION' && e.details.length > 0) {
        setLoginErrors(byField(e));
      } else {
        setLoginError(e instanceof ApiError ? e.message : 'Something went wrong.');
      }
    } finally {
      setLoggingIn(false);
    }
  }

  return (
    <main className="signup">
      <div className="area-register">
        <section className="panel" aria-labelledby="register-title">
          <h2 id="register-title">New customer</h2>
          <form onSubmit={register} noValidate aria-busy={registering}>
            <Field id={id('name')} label="Full name" error={errors.name}>
              <input
                id={id('name')}
                type="text"
                autoComplete="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                aria-invalid={errors.name ? true : undefined}
                aria-describedby={errors.name ? `${id('name')}-note` : undefined}
              />
            </Field>

            <Field id={id('email')} label="Email" error={errors.email}>
              <input
                id={id('email')}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                aria-invalid={errors.email ? true : undefined}
                aria-describedby={errors.email ? `${id('email')}-note` : undefined}
              />
            </Field>

            <Field id={id('password')} label="Password" hint="At least 8 characters." error={errors.password}>
              <div className="pw-row">
                <input
                  id={id('password')}
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-invalid={errors.password ? true : undefined}
                  aria-describedby={`${id('password')}-note`}
                />
                <button
                  type="button"
                  className="quiet"
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword((shown) => !shown)}
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </Field>

            <fieldset className="choices" aria-describedby={errors.residency ? `${id('residency')}-note` : undefined}>
              <legend>Where do you live?</legend>
              <label className={residency === 'RESIDENT' ? 'choice choice-on' : 'choice'}>
                <input
                  type="radio"
                  name={id('residency')}
                  value="RESIDENT"
                  checked={residency === 'RESIDENT'}
                  onChange={() => setResidency('RESIDENT')}
                />
                <span className="choice-title">Resident</span>
                <span className="choice-note">A permanent account with no end date.</span>
              </label>
              <label className={residency === 'NON_RESIDENT' ? 'choice choice-on' : 'choice'}>
                <input
                  type="radio"
                  name={id('residency')}
                  value="NON_RESIDENT"
                  checked={residency === 'NON_RESIDENT'}
                  onChange={() => setResidency('NON_RESIDENT')}
                />
                <span className="choice-title">Non-resident</span>
                <span className="choice-note">A temporary account that works until your visa ends.</span>
              </label>
            </fieldset>
            {errors.residency && (
              <p className="field-error choices-error" id={`${id('residency')}-note`}>
                {errors.residency}
              </p>
            )}

            {residency === 'NON_RESIDENT' && (
              <Field
                id={id('visa')}
                label="Visa expiry date"
                hint="The last day your visa is valid."
                error={errors.visaExpiry}
              >
                <input
                  id={id('visa')}
                  type="date"
                  min={todayInColombo()}
                  value={visaExpiry}
                  onChange={(e) => setVisaExpiry(e.target.value)}
                  aria-invalid={errors.visaExpiry ? true : undefined}
                  aria-describedby={`${id('visa')}-note`}
                />
              </Field>
            )}

            {formError && (
              <p className="form-error" role="alert">
                {formError}
              </p>
            )}

            <button type="submit" className="primary" disabled={registering}>
              {registering ? 'Opening your account' : 'Open my account'}
            </button>
          </form>
        </section>
      </div>

      <div className="area-welcome">
        <Welcome outcome={outcome} />

        <section className="panel login" aria-labelledby="login-title">
          <h2 id="login-title">Already a customer?</h2>
          <form onSubmit={logIn} noValidate aria-busy={loggingIn}>
            <Field id={id('login-email')} label="Email" error={loginErrors.email}>
              <input
                id={id('login-email')}
                type="email"
                autoComplete="username"
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                aria-invalid={loginErrors.email ? true : undefined}
                aria-describedby={loginErrors.email ? `${id('login-email')}-note` : undefined}
              />
            </Field>
            <Field id={id('login-password')} label="Password" error={loginErrors.password}>
              <input
                id={id('login-password')}
                type="password"
                autoComplete="current-password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                aria-invalid={loginErrors.password ? true : undefined}
                aria-describedby={loginErrors.password ? `${id('login-password')}-note` : undefined}
              />
            </Field>
            {loginError && (
              <p className="form-error" role="alert">
                {loginError}
              </p>
            )}
            <button type="submit" className="primary" disabled={loggingIn}>
              {loggingIn ? 'Checking' : 'Log in'}
            </button>
            <p className="hint">The five demo customers log in with the password Demo@1234, for example nimal@example.com.</p>
          </form>
        </section>
      </div>

      <div className="area-everyone">
        <PassengerList passengers={passengers} onTopUp={onTopUp} busy={busy} highlightId={justJoined} />
      </div>
    </main>
  );
}

/** The big face on the right: what the last registration or login produced. */
function Welcome({ outcome }: { outcome: { kind: 'registered' | 'loggedIn'; result: AuthResult } | null }) {
  if (!outcome) {
    return (
      <section className="glass glass-idle welcome" aria-live="polite">
        <h2>Welcome aboard</h2>
        <p className="glass-message">
          A resident gets a permanent account. A non-resident gets a temporary one that ends with their visa. Every new
          account starts with no credit.
        </p>
      </section>
    );
  }

  const { customer, message } = outcome.result;
  const temporary = customer.accountType === 'TEMPORARY';
  return (
    <section className="glass glass-success welcome" aria-live="polite">
      <h2>{outcome.kind === 'registered' ? 'Account opened' : 'Logged in'}</h2>
      <p className="glass-message">{message}</p>
      <dl className="figures">
        <div>
          <dt>Customer</dt>
          <dd>#{customer.passengerId}</dd>
        </div>
        <div>
          <dt>Account</dt>
          <dd>{temporary ? 'Temporary' : 'Permanent'}</dd>
        </div>
        <div>
          <dt>Balance</dt>
          <dd>{lkr(customer.balance)}</dd>
        </div>
        {temporary && customer.visaExpiry && (
          <div>
            <dt>Works until</dt>
            <dd>{dayOf(customer.visaExpiry)}</dd>
          </div>
        )}
      </dl>
      <p className="glass-ticket">
        <span>{customer.name}</span>
        <span>{customer.email}</span>
      </p>
    </section>
  );
}
