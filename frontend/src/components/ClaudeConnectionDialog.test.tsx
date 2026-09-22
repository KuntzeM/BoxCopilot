import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { LanguageProvider } from '../context/LanguageContext';
import { mcpService } from '../services/mcpService';
import ClaudeConnectionDialog from './ClaudeConnectionDialog';

vi.mock('../services/mcpService', () => ({
  mcpService: {
    getClient: vi.fn(),
    regenerateClient: vi.fn(),
    revokeConnections: vi.fn(),
  },
  getMcpConnectorUrl: vi.fn(() => 'https://api.example.com/mcp'),
}));

function renderDialog(open = true) {
  const onClose = vi.fn();
  render(
    <LanguageProvider>
      <ClaudeConnectionDialog open={open} onClose={onClose} />
    </LanguageProvider>,
  );
  return { onClose };
}

describe('ClaudeConnectionDialog', () => {
  beforeEach(() => {
    vi.mocked(mcpService.getClient).mockReset();
    vi.mocked(mcpService.regenerateClient).mockReset();
    vi.mocked(mcpService.revokeConnections).mockReset();
    document.cookie = 'app-language=en';
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
  });

  afterEach(() => {
    cleanup();
  });

  it('loads and shows the client id when opened', async () => {
    vi.mocked(mcpService.getClient).mockResolvedValue({ clientId: 'client-abc' });

    renderDialog();

    await screen.findByDisplayValue('client-abc');
    expect(mcpService.getClient).toHaveBeenCalledTimes(1);
  });

  it('does not call the service when closed', () => {
    renderDialog(false);

    expect(mcpService.getClient).not.toHaveBeenCalled();
  });

  it('copies the client id to the clipboard and shows feedback', async () => {
    vi.mocked(mcpService.getClient).mockResolvedValue({ clientId: 'client-abc' });
    renderDialog();
    await screen.findByDisplayValue('client-abc');

    fireEvent.click(screen.getByRole('button', { name: 'Copy client ID' }));

    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith('client-abc'));
    await screen.findByText('Copied to clipboard');
  });

  it('shows an error state when loading the client id fails', async () => {
    vi.mocked(mcpService.getClient).mockRejectedValue(new Error('403'));

    renderDialog();

    await screen.findByText('The client ID could not be loaded.');
  });

  it('requires confirmation before regenerating and calls the service once', async () => {
    vi.mocked(mcpService.getClient).mockResolvedValue({ clientId: 'client-abc' });
    vi.mocked(mcpService.regenerateClient).mockResolvedValue({ clientId: 'client-new' });
    renderDialog();
    await screen.findByDisplayValue('client-abc');

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate' }));
    expect(mcpService.regenerateClient).not.toHaveBeenCalled();

    const confirmTitle = await screen.findByText('Regenerate client ID?');
    const confirmDialog = confirmTitle.closest('[role="dialog"]') as HTMLElement;
    fireEvent.click(within(confirmDialog).getByRole('button', { name: 'Regenerate' }));

    await waitFor(() => expect(mcpService.regenerateClient).toHaveBeenCalledTimes(1));
    await screen.findByDisplayValue('client-new');
  });

  it('requires confirmation before revoking all connections', async () => {
    vi.mocked(mcpService.getClient).mockResolvedValue({ clientId: 'client-abc' });
    vi.mocked(mcpService.revokeConnections).mockResolvedValue(undefined);
    renderDialog();
    await screen.findByDisplayValue('client-abc');

    fireEvent.click(screen.getByRole('button', { name: 'Revoke all connections' }));
    expect(mcpService.revokeConnections).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Revoke' }));

    await waitFor(() => expect(mcpService.revokeConnections).toHaveBeenCalledTimes(1));
    await screen.findByText('All connections have been revoked.');
  });
});
