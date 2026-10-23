import type { ApiError } from './api';

export type Errors = Record<string, string>;

/** The first message for each field, so a field shows one clear sentence rather than a pile. */
export function byField(error: ApiError): Errors {
  const found: Errors = {};
  for (const issue of error.details) {
    if (!(issue.path in found)) found[issue.path] = issue.message;
  }
  return found;
}
