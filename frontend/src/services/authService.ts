import axios from './axiosConfig';
import { MagicLoginPayload } from '../types/models';

async function postMagicLogin(payload: MagicLoginPayload): Promise<void> {
  // A fresh token per attempt. It is sent as a per-request header, not via the global axios default, because
  // the SPA's own /api/v1/me call concurrently overwrites that default with its own CSRF token.
  const csrfResponse = await axios.get<{ token: string }>('/api/v1/csrf');
  await axios.post('/api/v1/auth/magic-login', payload, {
    headers: { 'X-XSRF-TOKEN': csrfResponse.data.token },
  });
}

/**
 * Redeems a one-time magic-link token. The POST is CSRF-protected and the SPA is
 * cross-origin, so a fresh CSRF token is fetched and sent explicitly.
 * The CSRF filter rejects with 403 before the token is looked at (and thus consumed), so a 403 is
 * retried once with a new CSRF token: the XSRF-TOKEN cookie can be replaced by a concurrent request
 * (deferred CSRF token), which invalidates the first one.
 * Rejects on any other failure; the backend answers a generic 401 for every invalid token.
 */
export async function redeemMagicLink(token: string): Promise<void> {
  const payload: MagicLoginPayload = { token };
  try {
    await postMagicLogin(payload);
  } catch (err) {
    if ((err as { response?: { status?: number } })?.response?.status !== 403) {
      throw err;
    }
    await postMagicLogin(payload);
  }
}
