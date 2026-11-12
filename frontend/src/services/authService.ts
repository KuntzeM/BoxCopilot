import axios from './axiosConfig';
import { MagicLoginPayload } from '../types/models';

/**
 * Redeems a one-time magic-link token. The POST is CSRF-protected and the SPA is
 * cross-origin, so a fresh CSRF token is fetched and sent explicitly.
 * Rejects on any failure; the backend answers a generic 401 for every invalid token.
 */
export async function redeemMagicLink(token: string): Promise<void> {
  const csrfResponse = await axios.get<{ token: string }>('/api/v1/csrf');
  axios.defaults.headers.common['X-XSRF-TOKEN'] = csrfResponse.data.token;

  const payload: MagicLoginPayload = { token };
  await axios.post('/api/v1/auth/magic-login', payload);
}
