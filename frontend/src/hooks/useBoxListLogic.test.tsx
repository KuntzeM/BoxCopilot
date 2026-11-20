import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../context/LanguageContext';
import { fetchBoxes, updateBox } from '../services/boxService';
import { useBoxListLogic } from './useBoxListLogic';

vi.mock('../services/boxService', () => ({
  fetchBoxes: vi.fn(),
  createBox: vi.fn(),
  deleteBox: vi.fn(),
  updateBox: vi.fn().mockResolvedValue({}),
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

describe('useBoxListLogic status filter', () => {
  beforeEach(() => {
    vi.mocked(fetchBoxes).mockReset();
    document.cookie = 'app-language=en';
  });

  afterEach(cleanup);

  it('defaults to all and filters moved / not moved boxes, reset restores all', async () => {
    vi.mocked(fetchBoxes).mockResolvedValue([
      { id: 1, uuid: 'u1', boxNumber: 1, isMovedToTarget: true } as never,
      { id: 2, uuid: 'u2', boxNumber: 2, isMovedToTarget: false } as never,
      { id: 3, uuid: 'u3', boxNumber: 3 } as never,
    ]);

    const { result } = renderHook(() => useBoxListLogic(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.statusFilter).toBe('all');
    expect(result.current.filteredBoxes).toHaveLength(3);

    act(() => result.current.setStatusFilter('moved'));
    await waitFor(() => expect(result.current.filteredBoxes.map((b) => b.id)).toEqual([1]));

    act(() => result.current.setStatusFilter('open'));
    await waitFor(() => expect(result.current.filteredBoxes.map((b) => b.id)).toEqual([3, 2]));

    act(() => result.current.handleResetFilter());
    expect(result.current.statusFilter).toBe('all');
    await waitFor(() => expect(result.current.filteredBoxes).toHaveLength(3));
  });
});

describe('useBoxListLogic drawer toggle moved', () => {
  afterEach(cleanup);

  it('flips isMovedToTarget via updateBox and reloads', async () => {
    vi.mocked(fetchBoxes).mockReset().mockResolvedValue([
      { id: 1, uuid: 'u1', boxNumber: 1, isMovedToTarget: false } as never,
    ]);
    const { result } = renderHook(() => useBoxListLogic(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    act(() => result.current.handleOpenBoxActions(result.current.allBoxes[0]));
    await act(async () => { await result.current.handleDrawerToggleMoved(); });
    expect(updateBox).toHaveBeenCalledWith(1, { isMovedToTarget: true });
    expect(fetchBoxes).toHaveBeenCalledTimes(2);
  });
});
