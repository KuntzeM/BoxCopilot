import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useMagicLogin } from './useMagicLogin';

const wrapper = StrictMode;

describe('useMagicLogin', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/magic#t=secret-token');
  });

  afterEach(() => {
    window.history.replaceState(null, '', '/');
  });

  it('strips the fragment from the address bar', async () => {
    const redeem = vi.fn().mockResolvedValue(undefined);

    renderHook(() => useMagicLogin({ redeem, onSuccess: vi.fn() }), { wrapper });

    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/magic');
    await waitFor(() => expect(redeem).toHaveBeenCalled());
  });

  it('submits the token exactly once, even with StrictMode double effects', async () => {
    const redeem = vi.fn().mockResolvedValue(undefined);
    const onSuccess = vi.fn();

    renderHook(() => useMagicLogin({ redeem, onSuccess }), { wrapper });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(redeem).toHaveBeenCalledTimes(1);
    expect(redeem).toHaveBeenCalledWith('secret-token');
  });

  it('reports processing until the redeem call settles, then calls onSuccess', async () => {
    let resolve: () => void = () => undefined;
    const redeem = vi.fn().mockReturnValue(new Promise<void>((r) => (resolve = r)));
    const onSuccess = vi.fn();

    const { result } = renderHook(() => useMagicLogin({ redeem, onSuccess }), { wrapper });

    expect(result.current.status).toBe('processing');
    expect(onSuccess).not.toHaveBeenCalled();

    resolve();
    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(result.current.status).toBe('processing');
  });

  it('shows the error state when the redeem call fails', async () => {
    const redeem = vi.fn().mockRejectedValue(new Error('401'));
    const onSuccess = vi.fn();

    const { result } = renderHook(() => useMagicLogin({ redeem, onSuccess }), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(onSuccess).not.toHaveBeenCalled();
    expect(redeem).toHaveBeenCalledTimes(1);
  });

  it('reports rateLimited with the retry minutes on HTTP 429', async () => {
    const redeem = vi.fn().mockRejectedValue({ response: { status: 429, data: { retryAfterSeconds: 120 } } });

    const { result } = renderHook(() => useMagicLogin({ redeem, onSuccess: vi.fn() }), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('rateLimited'));
    expect(result.current.retryMinutes).toBe(2);
  });

  it.each(['/magic', '/magic#', '/magic#t=', '/magic#x=abc'])(
    'shows the error state without any request when the token is missing (%s)',
    async (url) => {
      window.history.replaceState(null, '', url);
      const redeem = vi.fn();

      const { result } = renderHook(() => useMagicLogin({ redeem, onSuccess: vi.fn() }), { wrapper });

      await waitFor(() => expect(result.current.status).toBe('error'));
      expect(redeem).not.toHaveBeenCalled();
      expect(window.location.hash).toBe('');
    },
  );
});
