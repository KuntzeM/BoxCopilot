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

  it('fetches a CSRF token, sets the header and posts the token in the body', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { token: 'csrf-123' } });
    vi.mocked(axios.post).mockImplementation(async () => {
      expect(axios.defaults.headers.common['X-XSRF-TOKEN']).toBe('csrf-123');
      return { data: { success: true } };
    });

    await redeemMagicLink('magic-abc');

    expect(axios.get).toHaveBeenCalledWith('/api/v1/csrf');
    expect(axios.post).toHaveBeenCalledWith('/api/v1/auth/magic-login', { token: 'magic-abc' });
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
