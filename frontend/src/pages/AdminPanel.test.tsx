import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { LanguageProvider } from '../context/LanguageContext';
import { userService } from '../services/userService';
import { AuthProvider, Role, User } from '../types/models';
import AdminPanel from './AdminPanel';

vi.mock('../services/userService', () => ({
  userService: {
    getUsers: vi.fn(),
    updateUser: vi.fn(),
    setPassword: vi.fn(),
  },
}));

function buildUser(overrides: Partial<User> = {}): User {
  return {
    id: 7,
    username: 'alice',
    name: 'Alice',
    authProvider: AuthProvider.LOCAL,
    role: Role.USER,
    enabled: true,
    createdAt: '2026-01-01T10:00:00',
    hasPassword: true,
    mcpAllowed: false,
    ...overrides,
  };
}

async function openEditDialogFor(user: User) {
  vi.mocked(userService.getUsers).mockResolvedValue([user]);
  render(
    <LanguageProvider>
      <AdminPanel />
    </LanguageProvider>,
  );
  fireEvent.click(await screen.findByTitle('Edit User'));
  return screen.findByRole('switch', { name: 'MCP' });
}

describe('AdminPanel MCP switch', () => {
  beforeEach(() => {
    vi.mocked(userService.getUsers).mockReset();
    vi.mocked(userService.updateUser).mockReset();
    document.cookie = 'app-language=en';
  });

  afterEach(() => {
    cleanup();
  });

  it('reflects the stored mcpAllowed value of the user', async () => {
    const off = await openEditDialogFor(buildUser({ mcpAllowed: false }));
    expect((off as HTMLInputElement).checked).toBe(false);

    cleanup();

    const on = await openEditDialogFor(buildUser({ mcpAllowed: true }));
    expect((on as HTMLInputElement).checked).toBe(true);
  });

  it('sends mcpAllowed=true when the admin switches MCP on and saves', async () => {
    const user = buildUser({ mcpAllowed: false });
    vi.mocked(userService.updateUser).mockResolvedValue({ ...user, mcpAllowed: true });
    const toggle = await openEditDialogFor(user);

    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(userService.updateUser).toHaveBeenCalledTimes(1));
    expect(userService.updateUser).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ mcpAllowed: true, enabled: true, role: Role.USER }),
    );
  });

  it('sends mcpAllowed=false when the admin revokes MCP and saves', async () => {
    const user = buildUser({ mcpAllowed: true });
    vi.mocked(userService.updateUser).mockResolvedValue({ ...user, mcpAllowed: false });
    const toggle = await openEditDialogFor(user);

    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(userService.updateUser).toHaveBeenCalledTimes(1));
    expect(userService.updateUser).toHaveBeenCalledWith(7, expect.objectContaining({ mcpAllowed: false }));
  });

  it('keeps mcpAllowed=true when the admin only edits name and role', async () => {
    const user = buildUser({ mcpAllowed: true });
    vi.mocked(userService.updateUser).mockResolvedValue({ ...user, name: 'Alice B.', role: Role.ADMIN });
    await openEditDialogFor(user);

    const dialog = within(screen.getByRole('dialog'));
    fireEvent.change(dialog.getByLabelText('Name'), { target: { value: 'Alice B.' } });
    fireEvent.mouseDown(dialog.getByRole('combobox'));
    fireEvent.click(await screen.findByRole('option', { name: 'Admin' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(userService.updateUser).toHaveBeenCalledTimes(1));
    const payload = vi.mocked(userService.updateUser).mock.calls[0][1];
    expect(payload).toEqual(expect.objectContaining({ name: 'Alice B.', role: Role.ADMIN }));
    expect(payload.mcpAllowed).toBe(true);
  });
});

describe('AdminPanel hardening', () => {
  beforeEach(() => {
    vi.mocked(userService.getUsers).mockReset();
    vi.mocked(userService.updateUser).mockReset();
    vi.mocked(userService.setPassword).mockReset();
    document.cookie = 'app-language=en';
  });
  afterEach(cleanup);

  it('asks for confirmation before changing a role', async () => {
    const user = buildUser();
    vi.mocked(userService.getUsers).mockResolvedValue([user]);
    vi.mocked(userService.updateUser).mockResolvedValue({ ...user, role: Role.ADMIN });
    render(
      <LanguageProvider>
        <AdminPanel />
      </LanguageProvider>,
    );
    fireEvent.click(await screen.findByTitle('Make Admin'));
    expect(userService.updateUser).not.toHaveBeenCalled();
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(userService.updateUser).toHaveBeenCalledWith(7, { role: Role.ADMIN }));
  });

  it('has no unlock control and shows no lock state, even if the backend sends one', async () => {
    const locked = { ...buildUser(), lockedUntil: '2999-01-01T00:00:00', failedLoginAttempts: 5 } as User;
    vi.mocked(userService.getUsers).mockResolvedValue([locked]);
    render(
      <LanguageProvider>
        <AdminPanel />
      </LanguageProvider>,
    );
    await screen.findByTitle('Edit User');
    expect(screen.queryByTitle('Unlock')).toBeNull();
    expect(screen.queryByText('Locked')).toBeNull();
  });

  it('requires 12 characters for a new password and mentions ended sessions', async () => {
    vi.mocked(userService.getUsers).mockResolvedValue([buildUser()]);
    vi.mocked(userService.setPassword).mockResolvedValue();
    render(
      <LanguageProvider>
        <AdminPanel />
      </LanguageProvider>,
    );
    fireEvent.click(await screen.findByTitle('Set Password'));
    const dialog = within(screen.getByRole('dialog'));
    fireEvent.change(dialog.getByLabelText(/Password/), { target: { value: 'short' } });
    expect((dialog.getByRole('button', { name: 'Save' }) as HTMLButtonElement).disabled).toBe(true);
    expect(dialog.getByText('Password must be at least 12 characters.')).toBeTruthy();
    fireEvent.change(dialog.getByLabelText(/Password/), { target: { value: 'long-enough-pw1' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/sessions of this user were ended/)).toBeTruthy();
  });

  it('shows the backend error message, never the raw axios message', async () => {
    vi.mocked(userService.getUsers).mockRejectedValue({ message: 'Request failed with status code 500', response: { data: {} } });
    render(
      <LanguageProvider>
        <AdminPanel />
      </LanguageProvider>,
    );
    expect(await screen.findByText('Action failed')).toBeTruthy();
  });
});
