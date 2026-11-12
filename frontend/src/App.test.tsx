import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from './services/axiosConfig';
import App from './App';
import { PRIMARY } from './context/ThemeContext';

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

describe('App logout', () => {
  const realLocation = window.location;
  const assign = vi.fn();

  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    vi.mocked(axios.post).mockReset();
    assign.mockReset();
    Object.defineProperty(window, 'location', { configurable: true, value: { ...realLocation, assign } });
    document.cookie = 'app-language=en';
  });

  afterEach(() => {
    cleanup();
    Object.defineProperty(window, 'location', { configurable: true, value: realLocation });
  });

  it('navigates top-level to the redirectUrl the server returns', async () => {
    mockMe({});
    vi.mocked(axios.post).mockResolvedValue({ data: { redirectUrl: 'https://idp.example/logout?redirect_uri=x' } });
    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Logout' }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://idp.example/logout?redirect_uri=x'));
    expect(axios.post).toHaveBeenCalledWith('/api/auth/logout');
  });

  it('falls back to the login page when the logout call fails', async () => {
    mockMe({});
    vi.mocked(axios.post).mockRejectedValue(new Error('network'));
    vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Logout' }));
    await waitFor(() => expect(window.location.href).toBe('/'));
  });
});

function lum(hex: string) {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}
const ratio = (a: string, b: string) => (Math.max(lum(a), lum(b)) + 0.05) / (Math.min(lum(a), lum(b)) + 0.05);

describe('theme contrast (WCAG AA)', () => {
  it('light primary', () => {
    expect(ratio(PRIMARY.light.contrastText, PRIMARY.light.main)).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY.light.main, '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY.light.main, '#f5f5f5')).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY.light.contrastText, PRIMARY.light.dark)).toBeGreaterThanOrEqual(4.5);
  });
  it('dark primary', () => {
    expect(ratio(PRIMARY.dark.contrastText, PRIMARY.dark.main)).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY.dark.main, '#1e1e1e')).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY.dark.main, '#121212')).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY.dark.contrastText, PRIMARY.dark.light)).toBeGreaterThanOrEqual(4.5);
  });
});

describe('App shell', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    axios.defaults.headers.common = {};
    document.cookie = 'app-language=en';
  });
  afterEach(() => cleanup());

  it('renders the logo as a link, a main landmark, and sets lang/title', async () => {
    mockMe({});
    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );
    const logo = await screen.findByRole('link', { name: 'BoxCopilot' });
    expect(logo.getAttribute('href')).toBe('/app/boxes');
    expect(screen.getByRole('main')).toBeDefined();
    expect(document.documentElement.lang).toBe('en');
    expect(document.title).toContain('BoxCopilot');
  });

  it('redirects a non-admin from /app/admin to the boxes page', async () => {
    mockMe({ admin: false });
    render(
      <MemoryRouter initialEntries={['/app/admin']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByRole('button', { name: 'Logout' });
    expect(screen.queryByText('Administration')).toBeNull();
    expect(screen.getByRole('link', { name: 'BoxCopilot' })).toBeDefined();
  });
});
