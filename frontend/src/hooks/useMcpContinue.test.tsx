import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useMcpContinue } from './useMcpContinue';

const wrapper = StrictMode;

describe('useMcpContinue', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('navigates when authenticated and an MCP authorization is pending', () => {
    const navigate = vi.fn();

    renderHook(() => useMcpContinue(true, true, navigate));

    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it.each([false, undefined])('does not navigate when mcpPending is %s', (pending) => {
    const navigate = vi.fn();

    renderHook(() => useMcpContinue(true, pending, navigate));

    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not navigate when unauthenticated', () => {
    const navigate = vi.fn();

    renderHook(() => useMcpContinue(false, true, navigate));

    expect(navigate).not.toHaveBeenCalled();
  });

  it('navigates once authentication is established', () => {
    const navigate = vi.fn();

    const { rerender } = renderHook(({ auth }) => useMcpContinue(auth, true, navigate), {
      initialProps: { auth: false },
    });
    expect(navigate).not.toHaveBeenCalled();

    rerender({ auth: true });
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('navigates only once per page load, even with StrictMode double effects and rerenders', () => {
    const navigate = vi.fn();

    const { rerender } = renderHook(() => useMcpContinue(true, true, navigate), { wrapper });
    rerender();

    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('does not navigate again while the authorization is still pending after a returning page load', () => {
    const navigate = vi.fn();

    renderHook(() => useMcpContinue(true, true, navigate));
    renderHook(() => useMcpContinue(true, true, navigate));

    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('navigates again for a later authorization once a page load saw nothing pending', () => {
    const navigate = vi.fn();

    renderHook(() => useMcpContinue(true, true, navigate));
    renderHook(() => useMcpContinue(true, false, navigate));
    renderHook(() => useMcpContinue(true, true, navigate));

    expect(navigate).toHaveBeenCalledTimes(2);
  });

  it('still navigates once when sessionStorage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const navigate = vi.fn();

    const { rerender } = renderHook(() => useMcpContinue(true, true, navigate), { wrapper });
    rerender();

    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('navigates top-level to the backend continue URL by default', () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.com');

    renderHook(() => useMcpContinue(true, true));

    expect(assign).toHaveBeenCalledWith('https://api.example.com/api/v1/mcp/continue');
  });
});
