import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from './services/axiosConfig';
import App from './App';

vi.mock('./services/axiosConfig', () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { headers: { common: {} as Record<string, string> } } },
}));

vi.mock('./services/boxService', () => ({
  fetchBoxes: vi.fn().mockResolvedValue([]),
  createBox: vi.fn(),
  deleteBox: vi.fn(),
  updateBox: vi.fn(),
  listBoxes: vi.fn().mockResolvedValue([]),
}));

vi.mock('./services/itemService', () => ({
  fetchItemsByBoxUuid: vi.fn().mockResolvedValue([]),
  searchItems: vi.fn().mockResolvedValue([]),
}));

function mockMe(overrides: Record<string, unknown> = {}) {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
    if (url === '/api/v1/me') {
      return {
        data: {
          id: 1,
          username: 'alice',
          name: 'Alice',
          authenticated: true,
          admin: false,
          ...overrides,
        },
      };
    }
    throw new Error(`unexpected GET ${url}`);
  });
}

describe('App menu - Claude connection entry', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    axios.defaults.headers.common = {};
    window.sessionStorage.clear();
    document.cookie = 'app-language=en';
  });

  afterEach(() => {
    cleanup();
  });

  it('shows the Claude connection menu entry when the user is mcpAllowed', async () => {
    mockMe({ mcpAllowed: true });

    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('button', { name: 'Claude connection' })).toBeDefined();
  });

  it('hides the Claude connection menu entry when the user is not mcpAllowed', async () => {
    mockMe({ mcpAllowed: false });

    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );

    // Wait for the shell to render before asserting absence.
    await screen.findByRole('button', { name: 'Logout' });
    expect(screen.queryByRole('button', { name: 'Claude connection' })).toBeNull();
  });

  it('hides the Claude connection menu entry when mcpAllowed is missing from the response', async () => {
    mockMe({});

    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );

    await screen.findByRole('button', { name: 'Logout' });
    expect(screen.queryByRole('button', { name: 'Claude connection' })).toBeNull();
  });
});
