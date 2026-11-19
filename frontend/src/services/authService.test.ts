import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios from './axiosConfig';
import { redeemMagicLink } from './authService';

vi.mock('./axiosConfig', () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { headers: { common: {} as Record<string, string> } } },
}));

describe('redeemMagicLink', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    vi.mocked(axios.post).mockReset();
    axios.defaults.headers.common = {};
  });

  it('fetches a CSRF token and sends it as a per-request header with the token in the body', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { token: 'csrf-123' } });
    vi.mocked(axios.post).mockResolvedValue({ data: { success: true } });

    await redeemMagicLink('magic-abc');

    expect(axios.get).toHaveBeenCalledWith('/api/v1/csrf');
    expect(axios.post).toHaveBeenCalledWith(
      '/api/v1/auth/magic-login',
      { token: 'magic-abc' },
      { headers: { 'X-XSRF-TOKEN': 'csrf-123' } }
    );
  });

  it('is not affected by a concurrent overwrite of the global CSRF header', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { token: 'csrf-123' } });
    vi.mocked(axios.post).mockImplementation(async () => {
      axios.defaults.headers.common['X-XSRF-TOKEN'] = 'from-me-call';
      return { data: {} };
    });

    await redeemMagicLink('magic-abc');

    expect(vi.mocked(axios.post).mock.calls[0][2]).toEqual({ headers: { 'X-XSRF-TOKEN': 'csrf-123' } });
  });

  it('retries once with a fresh CSRF token when the post is rejected with 403', async () => {
    vi.mocked(axios.get)
      .mockResolvedValueOnce({ data: { token: 'stale' } })
      .mockResolvedValueOnce({ data: { token: 'fresh' } });
    vi.mocked(axios.post)
      .mockRejectedValueOnce({ response: { status: 403 } })
      .mockResolvedValueOnce({ data: {} });

    await redeemMagicLink('magic-abc');

    expect(axios.get).toHaveBeenCalledTimes(2);
    expect(axios.post).toHaveBeenCalledTimes(2);
    expect(vi.mocked(axios.post).mock.calls[1]).toEqual([
      '/api/v1/auth/magic-login',
      { token: 'magic-abc' },
      { headers: { 'X-XSRF-TOKEN': 'fresh' } },
    ]);
  });

  it('gives up after a second 403', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { token: 't' } });
    const forbidden = { response: { status: 403 } };
    vi.mocked(axios.post).mockRejectedValue(forbidden);

    await expect(redeemMagicLink('magic-abc')).rejects.toBe(forbidden);
    expect(axios.post).toHaveBeenCalledTimes(2);
  });

  it.each([401, 429])('does not retry on %i', async (status) => {
    vi.mocked(axios.get).mockResolvedValue({ data: { token: 't' } });
    vi.mocked(axios.post).mockRejectedValue({ response: { status } });

    await expect(redeemMagicLink('magic-abc')).rejects.toBeDefined();
    expect(axios.post).toHaveBeenCalledTimes(1);
    expect(axios.get).toHaveBeenCalledTimes(1);
  });

  it('does not post when the CSRF request fails', async () => {
    vi.mocked(axios.get).mockRejectedValue(new Error('network'));

    await expect(redeemMagicLink('magic-abc')).rejects.toThrow('network');
    expect(axios.post).not.toHaveBeenCalled();
  });

  it('propagates a rejected login', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { token: 'csrf-123' } });
    vi.mocked(axios.post).mockRejectedValue(new Error('401'));

    await expect(redeemMagicLink('magic-abc')).rejects.toThrow('401');
  });
});
