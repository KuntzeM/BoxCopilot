import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { LanguageProvider } from '../context/LanguageContext';
import * as itemService from '../services/itemService';
import { ItemForm, getItemFormRef } from './ItemForm';

vi.mock('../services/itemService', () => ({
  createItem: vi.fn(),
  updateItem: vi.fn(),
  uploadItemImage: vi.fn(),
  deleteItemImage: vi.fn(),
}));

function setup(props: Partial<React.ComponentProps<typeof ItemForm>> = {}) {
  const handlers = { onSuccess: vi.fn(), onError: vi.fn(), onAddItem: vi.fn(), onUpdateItem: vi.fn() };
  render(
    <LanguageProvider>
      <ItemForm boxId={1} {...handlers} {...props} />
    </LanguageProvider>,
  );
  return handlers;
}

async function openCreateDialog() {
  fireEvent.click(screen.getByRole('button', { name: 'Add New Item' }));
  return (await screen.findByLabelText('Item Name')) as HTMLInputElement;
}

function pickPhoto() {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  const file = new File(['x'], 'p.jpg', { type: 'image/jpeg' });
  fireEvent.change(input, { target: { files: [file] } });
  return screen.findByAltText('Preview');
}

describe('ItemForm', () => {
  beforeEach(() => {
    document.cookie = 'app-language=en';
    vi.mocked(itemService.createItem).mockReset().mockResolvedValue({ id: 11, name: 'Hammer' });
    vi.mocked(itemService.updateItem).mockReset().mockResolvedValue({ id: 5, name: 'Cup' });
    vi.mocked(itemService.uploadItemImage).mockReset().mockResolvedValue({ id: 11, name: 'Hammer' });
    vi.mocked(itemService.deleteItemImage).mockReset().mockResolvedValue({ id: 5, name: 'Cup' });
  });

  afterEach(() => cleanup());

  it('submits on Enter (form submit) and closes the dialog', async () => {
    const h = setup();
    const name = await openCreateDialog();
    fireEvent.change(name, { target: { value: 'Hammer' } });
    fireEvent.submit(name.closest('form')!);

    await waitFor(() => expect(itemService.createItem).toHaveBeenCalledWith({ name: 'Hammer', boxId: 1 }));
    await waitFor(() => expect(screen.queryByLabelText('Item Name')).toBeNull());
    expect(h.onSuccess).toHaveBeenCalledWith('Item added successfully');
    expect(h.onAddItem).toHaveBeenCalled();
  });

  it('"Save and next" keeps the dialog open, clears name and photo and refocuses the name field', async () => {
    const h = setup();
    const name = await openCreateDialog();
    fireEvent.change(name, { target: { value: 'Hammer' } });
    await pickPhoto();

    fireEvent.click(screen.getByRole('button', { name: 'Save and next' }));

    await waitFor(() => expect(h.onAddItem).toHaveBeenCalledTimes(1));
    expect(itemService.uploadItemImage).toHaveBeenCalledWith(11, expect.any(File));
    const again = screen.getByLabelText('Item Name') as HTMLInputElement;
    await waitFor(() => expect(again.value).toBe(''));
    expect(screen.queryByAltText('Preview')).toBeNull();
    await waitFor(() => expect(document.activeElement).toBe(again));
  });

  it('shows a failed photo upload as a warning with retry, not as success', async () => {
    vi.mocked(itemService.uploadItemImage).mockRejectedValueOnce(new Error('boom'));
    const h = setup();
    const name = await openCreateDialog();
    fireEvent.change(name, { target: { value: 'Hammer' } });
    await pickPhoto();
    fireEvent.submit(name.closest('form')!);

    const warning = await screen.findByText('Item saved, but the photo could not be processed.');
    expect(warning.closest('[role="alert"]')?.className).toMatch(/Warning/);
    expect(h.onSuccess).not.toHaveBeenCalled();
    expect(h.onAddItem).toHaveBeenCalledTimes(1); // list is refreshed, item exists

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(h.onSuccess).toHaveBeenCalledWith('Item added successfully'));
    expect(itemService.uploadItemImage).toHaveBeenCalledTimes(2);
    expect(itemService.createItem).toHaveBeenCalledTimes(1);
  });

  it('shows the backend error message, never the raw axios message', async () => {
    vi.mocked(itemService.createItem).mockRejectedValueOnce({
      message: 'Request failed with status code 500',
      response: { data: { error: 'Box is full' } },
    });
    const h = setup();
    const name = await openCreateDialog();
    fireEvent.change(name, { target: { value: 'Hammer' } });
    fireEvent.submit(name.closest('form')!);

    expect(await screen.findByText('Box is full')).toBeTruthy();
    expect(screen.queryByText(/status code/)).toBeNull();
    expect(h.onError).toHaveBeenCalledWith('Box is full');
  });

  it('removing a server photo while editing deletes it only on save', async () => {
    const h = setup({ hideFab: true });
    act(() => getItemFormRef()!.openEdit({ id: 5, name: 'Cup', imageUrl: '/api/v1/items/5/image' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Photo' }));
    expect(itemService.deleteItemImage).not.toHaveBeenCalled();
    expect(screen.queryByAltText('Preview')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(itemService.deleteItemImage).toHaveBeenCalledWith(5));
    expect(itemService.updateItem).toHaveBeenCalledWith(5, { name: 'Cup' });
    await waitFor(() => expect(h.onUpdateItem).toHaveBeenCalled());
  });

  it('cancelling after removing a server photo never deletes it', async () => {
    setup({ hideFab: true });
    act(() => getItemFormRef()!.openEdit({ id: 5, name: 'Cup', imageUrl: '/api/v1/items/5/image' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Remove Photo' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(itemService.deleteItemImage).not.toHaveBeenCalled();
  });
});
