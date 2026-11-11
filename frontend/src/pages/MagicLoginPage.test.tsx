import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../context/LanguageContext';
import { redeemMagicLink } from '../services/authService';
import MagicLoginPage from './MagicLoginPage';

vi.mock('../services/authService', () => ({ redeemMagicLink: vi.fn() }));

function renderPage() {
  return render(
    <StrictMode>
      <LanguageProvider>
        <MemoryRouter initialEntries={['/magic']}>
          <MagicLoginPage />
        </MemoryRouter>
      </LanguageProvider>
    </StrictMode>,
  );
}

describe('MagicLoginPage', () => {
  beforeEach(() => {
    vi.mocked(redeemMagicLink).mockReset();
    document.cookie = 'app-language=en';
  });

  afterEach(() => {
    cleanup();
    window.history.replaceState(null, '', '/');
  });

  it('shows a loading indicator while the token is being redeemed', () => {
    window.history.replaceState(null, '', '/magic#t=abc');
    vi.mocked(redeemMagicLink).mockReturnValue(new Promise(() => undefined));

    renderPage();

    expect(screen.getByRole('status')).toBeTruthy();
    expect(redeemMagicLink).toHaveBeenCalledTimes(1);
    expect(redeemMagicLink).toHaveBeenCalledWith('abc');
  });

  it('shows an error with a link back to the login when the token is missing', async () => {
    window.history.replaceState(null, '', '/magic');

    renderPage();

    expect(await screen.findByRole('alert')).toBeTruthy();
    const link = screen.getByRole('link', { name: 'Back to login' });
    expect(link.getAttribute('href')).toBe('/');
    expect(redeemMagicLink).not.toHaveBeenCalled();
  });

  it('shows an error when the backend rejects the token', async () => {
    window.history.replaceState(null, '', '/magic#t=expired');
    vi.mocked(redeemMagicLink).mockRejectedValue(new Error('401'));

    renderPage();

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(redeemMagicLink).toHaveBeenCalledTimes(1);
  });

  it('shows the too-many-attempts message on HTTP 429', async () => {
    window.history.replaceState(null, '', '/magic#t=abc');
    vi.mocked(redeemMagicLink).mockRejectedValue({ response: { status: 429, data: { retryAfterSeconds: 600 } } });

    renderPage();

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Too many failed login attempts');
    expect(alert.textContent).toContain('10 min');
  });
});
