import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../context/LanguageContext';
import { fetchBoxes } from '../services/boxService';
import { useBoxListLogic } from './useBoxListLogic';

vi.mock('../services/boxService', () => ({
  fetchBoxes: vi.fn(),
  createBox: vi.fn(),
  deleteBox: vi.fn(),
}));
vi.mock('../services/itemService', () => ({ fetchItemsByBoxUuid: vi.fn(), searchItems: vi.fn() }));

const wrapper = ({ children }: { children: ReactNode }) => (
  <LanguageProvider>
    <MemoryRouter>{children}</MemoryRouter>
  </LanguageProvider>
);

describe('useBoxListLogic loading states', () => {
  beforeEach(() => {
    vi.mocked(fetchBoxes).mockReset();
    document.cookie = 'app-language=en';
  });

  afterEach(cleanup);

  it('starts loading, then exposes an empty list without an error', async () => {
    vi.mocked(fetchBoxes).mockResolvedValue([]);

    const { result } = renderHook(() => useBoxListLogic(), { wrapper });

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.loadError).toBe(false);
    expect(result.current.allBoxes).toEqual([]);
  });

  it('sets loadError when loading fails and clears it after a successful retry', async () => {
    vi.mocked(fetchBoxes).mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce([
      { id: 1, uuid: 'u1', boxNumber: 1 } as never,
    ]);

    const { result } = renderHook(() => useBoxListLogic(), { wrapper });

    await waitFor(() => expect(result.current.loadError).toBe(true));
    expect(result.current.isLoading).toBe(false);
    expect(result.current.snackbar.open).toBe(false);

    await act(async () => {
      await result.current.loadData();
    });

    expect(result.current.loadError).toBe(false);
    expect(result.current.allBoxes).toHaveLength(1);
  });
});
