import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { LanguageProvider } from '../context/LanguageContext';
import axios from '../services/axiosConfig';
import LoginPage from './LoginPage';

vi.mock('../services/axiosConfig', () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { headers: { common: {} as Record<string, string> } } },
}));

async function submitWith(postError: unknown) {
  vi.mocked(axios.get).mockResolvedValue({ data: { token: 'csrf' } });
  vi.mocked(axios.post).mockRejectedValue(postError);
  render(
    <LanguageProvider>
      <LoginPage onLoginSuccess={vi.fn()} />
    </LanguageProvider>,
  );
  fireEvent.change(screen.getByLabelText(/Username/), { target: { value: 'alice' } });
  fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: 'wrong' } });
  fireEvent.click(screen.getByRole('button', { name: 'Login' }));
  return screen.findByRole('alert');
}

describe('LoginPage errors', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
    vi.mocked(axios.post).mockReset();
    document.cookie = 'app-language=en';
  });
  afterEach(cleanup);

  it('shows a localized, rounded-up minute count on 429', async () => {
    const alert = await submitWith({
      response: { status: 429, data: { error: 'Too many failed login attempts', retryAfterSeconds: 125 } },
    });
    expect(alert.textContent).toBe('Too many failed login attempts. Please try again in 3 min.');
  });

  it('falls back to the Retry-After header on 429', async () => {
    const alert = await submitWith({ response: { status: 429, data: {}, headers: { 'retry-after': '30' } } });
    expect(alert.textContent).toContain('in 1 min');
  });

  it('shows the generic failure on 401 and the unavailable message otherwise', async () => {
    expect((await submitWith({ response: { status: 401 } })).textContent).toContain('Login failed');
    cleanup();
    expect((await submitWith({ response: { status: 500 } })).textContent).toContain('not possible');
  });

  it('toggles password visibility', async () => {
    render(
      <LanguageProvider>
        <LoginPage onLoginSuccess={vi.fn()} />
      </LanguageProvider>,
    );
    const input = screen.getByLabelText(/^Password/) as HTMLInputElement;
    expect(input.type).toBe('password');
    fireEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(input.type).toBe('text');
  });
});
