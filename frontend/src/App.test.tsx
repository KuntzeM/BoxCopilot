import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import axios from './services/axiosConfig';
import App from './App';
import { PRIMARY, SURFACE, STATUS } from './context/ThemeContext';

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

const openMenu = async () => fireEvent.click(await screen.findByRole('button', { name: 'Menu' }));

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

    await openMenu();
    expect(await screen.findByRole('menuitem', { name: 'Claude connection' })).toBeDefined();
  });

  it('hides the Claude connection menu entry when the user is not mcpAllowed', async () => {
    mockMe({ mcpAllowed: false });

    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );

    // Wait for the shell to render before asserting absence.
    await openMenu();
    await screen.findByRole('menuitem', { name: 'Logout' });
    expect(screen.queryByRole('menuitem', { name: 'Claude connection' })).toBeNull();
  });

  it('hides the Claude connection menu entry when mcpAllowed is missing from the response', async () => {
    mockMe({});

    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );

    await openMenu();
    await screen.findByRole('menuitem', { name: 'Logout' });
    expect(screen.queryByRole('menuitem', { name: 'Claude connection' })).toBeNull();
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
    await openMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Logout' }));
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
    await openMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Logout' }));
    await waitFor(() => expect(window.location.href).toBe('/'));
  });
});

function lum(hex: string) {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}
const ratio = (a: string, b: string) => (Math.max(lum(a), lum(b)) + 0.05) / (Math.min(lum(a), lum(b)) + 0.05);

describe('theme contrast (WCAG AA)', () => {
  it.each(['light', 'dark'] as const)('%s theme text and accent pairs', (m) => {
    const S = SURFACE[m];
    expect(ratio(PRIMARY[m].contrastText, PRIMARY[m].main)).toBeGreaterThanOrEqual(4.5);
    expect(ratio(PRIMARY[m].contrastText, PRIMARY[m].dark)).toBeGreaterThanOrEqual(4.5);
    for (const bg of [S.bg, S.paper, S.raised]) {
      expect(ratio(S.text, bg)).toBeGreaterThanOrEqual(4.5);
      expect(ratio(S.muted, bg)).toBeGreaterThanOrEqual(4.5);
      expect(ratio(PRIMARY[m].main, bg)).toBeGreaterThanOrEqual(4.5);
      expect(ratio(STATUS[m].done, bg)).toBeGreaterThanOrEqual(4.5);
      expect(ratio(STATUS[m].warn, bg)).toBeGreaterThanOrEqual(4.5);
      // Form-field / control borders: WCAG 1.4.11 non-text contrast
      expect(ratio(S.outline, bg)).toBeGreaterThanOrEqual(3);
    }
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
    expect(await screen.findByRole('link', { name: 'BoxCopilot' })).toBeDefined();
    await openMenu();
    await screen.findByRole('menuitem', { name: 'Logout' });
    expect(screen.queryByText('Administration')).toBeNull();
  });
});

describe('App navigation', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    axios.defaults.headers.common = {};
    document.cookie = 'app-language=en';
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('wide layout: left rail with the three actions, admin is hidden in the avatar menu', async () => {
    mockMe({ admin: true });
    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('link', { name: 'Boxes' })).toBeDefined();
    expect(screen.getByRole('button', { name: /new box/i })).toBeDefined();
    expect(screen.queryByRole('menuitem', { name: 'Admin' })).toBeNull();
    await openMenu();
    expect(await screen.findByRole('menuitem', { name: 'Admin' })).toBeDefined();
  });

  it('phone layout: thumb bar with the raised new-box action, no admin entry for normal users', async () => {
    vi.stubGlobal('matchMedia', (q: string) => ({
      matches: q.includes('max-width'),
      media: q,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
    }));
    mockMe({});
    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByRole('main');
    expect(screen.getByRole('navigation')).toBeDefined();
    expect(screen.getByRole('button', { name: /new box/i })).toBeDefined();
    await openMenu();
    await screen.findByRole('menuitem', { name: 'Logout' });
    expect(screen.queryByRole('menuitem', { name: 'Admin' })).toBeNull();
  });
});

describe('App live resize across the 900px breakpoint', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    axios.defaults.headers.common = {};
    document.cookie = 'app-language=en';
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('keeps rendering when the viewport flips wide -> narrow -> wide', async () => {
    let width = 1200;
    const listeners = new Set<() => void>();
    const matches = (q: string) => {
      const min = /min-width:\s*([\d.]+)px/.exec(q);
      const max = /max-width:\s*([\d.]+)px/.exec(q);
      return (!min || width >= +min[1]) && (!max || width <= +max[1]);
    };
    vi.stubGlobal('matchMedia', (q: string) => ({
      get matches() { return matches(q); },
      media: q,
      addEventListener: (_: string, l: () => void) => listeners.add(l),
      removeEventListener: (_: string, l: () => void) => listeners.delete(l),
      addListener: () => {},
      removeListener: () => {},
    }));
    const { fetchBoxes } = await import('./services/boxService');
    vi.mocked(fetchBoxes).mockResolvedValue([
      { id: 1, uuid: 'u1', boxNumber: '1', currentRoom: 'Kitchen', targetRoom: 'Hall', description: 'd', items: [] },
    ] as never);
    mockMe({});
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <MemoryRouter initialEntries={['/app/boxes']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByRole('main');
    for (const w of [600, 1200, 600, 1200]) {
      width = w;
      act(() => listeners.forEach((l) => l()));
      await screen.findByRole('main');
      expect(screen.getByRole('main')).toBeDefined();
    }
    expect(errors.mock.calls.flat().join('\n')).not.toMatch(/TypeError|Cannot read/);
    errors.mockRestore();
  });
});
