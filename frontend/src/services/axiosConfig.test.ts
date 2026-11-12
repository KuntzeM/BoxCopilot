import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios, { sessionExpired } from './axiosConfig';

function respondWith(status: number) {
  return (config: any) =>
    Promise.reject({ config, response: { status, data: {}, headers: {}, config } });
}

describe('401 response interceptor', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.spyOn(sessionExpired, 'reload').mockImplementation(() => {});
  });

  it('reloads on a 401 from a regular API call, only once within the guard window', async () => {
    await expect(axios.get('/api/v1/boxes', { adapter: respondWith(401) })).rejects.toBeTruthy();
    await expect(axios.get('/api/v1/boxes', { adapter: respondWith(401) })).rejects.toBeTruthy();
    expect(sessionExpired.reload).toHaveBeenCalledTimes(1);
  });

  it.each(['/api/v1/me', '/api/v1/csrf', '/api/auth/login', '/api/v1/public/abc', '/api/v1/auth/magic-login'])(
    'does not reload on a 401 from %s',
    async (url) => {
      await expect(axios.get(url, { adapter: respondWith(401) })).rejects.toBeTruthy();
      expect(sessionExpired.reload).not.toHaveBeenCalled();
    }
  );

  it('does not reload on other errors', async () => {
    await expect(axios.get('/api/v1/boxes', { adapter: respondWith(500) })).rejects.toBeTruthy();
    expect(sessionExpired.reload).not.toHaveBeenCalled();
  });
});
