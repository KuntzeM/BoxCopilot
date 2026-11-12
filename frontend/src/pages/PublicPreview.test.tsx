import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import axios from '../services/axiosConfig';
import { fetchPublicPreview } from '../services/publicPreviewService';
import PublicPreview from './PublicPreview';

vi.mock('../services/publicPreviewService', () => ({ fetchPublicPreview: vi.fn() }));
vi.mock('../services/axiosConfig', () => ({ default: { get: vi.fn() } }));

const preview = {
  id: 3,
  boxNumber: 12,
  currentRoom: 'Cellar',
  targetRoom: 'Kitchen',
  items: [{ name: 'Teapot', imageUrl: '/api/v1/public/abc/items/1/image' }],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/public/abc']}>
      <Routes>
        <Route path="/public/:token" element={<PublicPreview />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PublicPreview', () => {
  beforeEach(() => {
    vi.mocked(fetchPublicPreview).mockReset();
    vi.mocked(axios.get).mockResolvedValue({ status: 401, data: {} });
    document.cookie = 'app-language=en';
  });

  afterEach(cleanup);

  it('shows a loading state with the app bar first, then the box', async () => {
    vi.mocked(fetchPublicPreview).mockResolvedValue(preview as never);

    renderPage();

    expect(screen.getByRole('status')).toBeTruthy();
    expect(screen.getByText('BoxCopilot')).toBeTruthy();
    expect(await screen.findByText('Cellar')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Show photo of Teapot' })).toBeTruthy();
  });

  it('answers HTTP 404 with the invalid-or-revoked message and no retry button', async () => {
    vi.mocked(fetchPublicPreview).mockRejectedValue({ response: { status: 404, data: { error: 'Not found' } } });

    renderPage();

    expect(await screen.findByText('Link invalid or revoked')).toBeTruthy();
    expect(screen.getByText('BoxCopilot')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull();
  });

  it.each([
    ['5xx', { response: { status: 503 } }],
    ['network error', new Error('Network Error')],
  ])('treats a %s as temporary and retries on demand', async (_label, failure) => {
    vi.mocked(fetchPublicPreview).mockRejectedValueOnce(failure).mockResolvedValueOnce(preview as never);

    renderPage();

    expect(await screen.findByText('Temporary problem')).toBeTruthy();
    expect(screen.getByText('BoxCopilot')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Cellar')).toBeTruthy();
    await waitFor(() => expect(fetchPublicPreview).toHaveBeenCalledTimes(2));
  });
});
