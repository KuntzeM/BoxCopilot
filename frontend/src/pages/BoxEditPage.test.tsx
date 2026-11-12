import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LanguageProvider } from '../context/LanguageContext';
import * as boxService from '../services/boxService';
import * as itemService from '../services/itemService';
import BoxEditPage from './BoxEditPage';

vi.mock('../services/boxService', () => ({
  fetchBoxes: vi.fn(),
  updateBox: vi.fn(),
  rotateShare: vi.fn(),
}));
vi.mock('../services/itemService', () => ({
  fetchItemsByBoxUuid: vi.fn(),
  updateItem: vi.fn(),
  deleteItem: vi.fn(),
  moveItems: vi.fn(),
}));
vi.mock('../components/EnhancedItemsTable', () => ({ default: () => <div>items-table</div> }));
vi.mock('../components/ItemForm', () => ({ ItemForm: () => null }));

const box = { id: 3, uuid: 'old-uuid', boxNumber: 7 };

function renderPage() {
  render(
    <LanguageProvider>
      <MemoryRouter initialEntries={['/app/boxes/3/edit']}>
        <Routes>
          <Route path="/app/boxes/:id/edit" element={<BoxEditPage />} />
        </Routes>
      </MemoryRouter>
    </LanguageProvider>,
  );
}

const linkInput = () => screen.getByLabelText('Public Link') as HTMLInputElement;

describe('BoxEditPage', () => {
  beforeEach(() => {
    document.cookie = 'app-language=en';
    vi.mocked(boxService.fetchBoxes).mockReset().mockResolvedValue([box]);
    vi.mocked(boxService.rotateShare).mockReset().mockResolvedValue({ ...box, uuid: 'new-uuid' });
    vi.mocked(itemService.fetchItemsByBoxUuid).mockReset().mockResolvedValue([]);
  });

  afterEach(() => cleanup());

  it('shows an empty state with print and back actions when the box has no items', async () => {
    renderPage();

    expect(await screen.findByText('No items in this box.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Print label' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Back' })).toBeTruthy();
  });

  it('rotates the share link only after the warning dialog is confirmed, then uses the new uuid', async () => {
    renderPage();
    await screen.findByText('No items in this box.');
    expect(linkInput().value).toContain('/public/old-uuid');

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate link' }));
    expect(await screen.findByText(/printed QR labels .* will stop working/)).toBeTruthy();
    expect(boxService.rotateShare).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByText(/printed QR labels/)).toBeNull());
    expect(boxService.rotateShare).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate link' }));
    const buttons = await screen.findAllByRole('button', { name: 'Regenerate link' });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => expect(boxService.rotateShare).toHaveBeenCalledWith(3));
    await waitFor(() => expect(linkInput().value).toContain('/public/new-uuid'));
  });

  it('reports a failed rotation with the backend message and keeps the old link', async () => {
    vi.mocked(boxService.rotateShare).mockRejectedValue({ response: { data: { error: 'Not allowed' } } });
    renderPage();
    await screen.findByText('No items in this box.');

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate link' }));
    const buttons = await screen.findAllByRole('button', { name: 'Regenerate link' });
    fireEvent.click(buttons[buttons.length - 1]);

    expect(await screen.findByText('Not allowed')).toBeTruthy();
    expect(linkInput().value).toContain('/public/old-uuid');
  });
});
