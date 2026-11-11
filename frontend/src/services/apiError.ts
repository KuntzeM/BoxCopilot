/**
 * Returns the backend-provided message of a failed request (`data.error` or `data.message`),
 * otherwise the localized fallback. Never exposes the raw axios `err.message`.
 */
export function extractApiError(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { error?: unknown; message?: unknown } } })?.response?.data;
  for (const value of [data?.error, data?.message]) {
    if (typeof value === 'string' && value.trim()) return value;
  }
  return fallback;
}

/** Seconds from the 429 body (`retryAfterSeconds`), falling back to the `Retry-After` header. */
export function extractRetryAfterSeconds(err: unknown): number | null {
  const response = (err as { response?: { data?: { retryAfterSeconds?: unknown }; headers?: Record<string, unknown> } })
    ?.response;
  const raw = response?.data?.retryAfterSeconds ?? response?.headers?.['retry-after'];
  const seconds = Number(raw);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
}
