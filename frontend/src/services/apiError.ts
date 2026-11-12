import Cookies from 'js-cookie';
import { getTranslation } from '../i18n';

/**
 * For a validation response (`fieldErrors`): localized generic text plus "field – message" pairs
 * (the field messages themselves are the backend's English fallback; forms validate client-side first).
 * Otherwise returns the backend-provided message of a failed request (`data.error` or `data.message`),
 * otherwise the localized fallback. Never exposes the raw axios `err.message`.
 */
export function extractApiError(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { error?: unknown; message?: unknown; fieldErrors?: unknown } } })
    ?.response?.data;
  const fields = Object.entries(data?.fieldErrors && typeof data.fieldErrors === 'object' ? data.fieldErrors : {}).filter(
    ([, msg]) => typeof msg === 'string' && msg.trim(),
  );
  if (fields.length) {
    const lang = Cookies.get('app-language') === 'en' ? 'en' : 'de';
    return `${getTranslation(lang, 'errors.validationFailed')}: ${fields.map(([f, m]) => `${f} – ${m}`).join('; ')}`;
  }
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
