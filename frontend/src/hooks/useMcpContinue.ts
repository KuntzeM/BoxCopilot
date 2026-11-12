import { useEffect, useRef } from 'react';
import { getMcpContinueUrl } from '../services/mcpService';

const ATTEMPTED_KEY = 'mcpContinueAttempted';

function readAttempted(): boolean {
  try {
    return window.sessionStorage.getItem(ATTEMPTED_KEY) === '1';
  } catch {
    return false;
  }
}

function writeAttempted(attempted: boolean): void {
  try {
    if (attempted) {
      window.sessionStorage.setItem(ATTEMPTED_KEY, '1');
    } else {
      window.sessionStorage.removeItem(ATTEMPTED_KEY);
    }
  } catch {
    // Storage is only a loop guard across page loads; the in-memory guard still applies.
  }
}

function navigateToContinue(): void {
  window.location.assign(getMcpContinueUrl());
}

/**
 * Resumes a pending MCP authorization after login by a top-level navigation to the backend.
 * Navigates at most once per page load and, via sessionStorage, not again while the backend
 * keeps reporting the authorization as pending after returning to the SPA.
 */
export function useMcpContinue(
  isAuthenticated: boolean,
  mcpPending: boolean | undefined,
  navigate: () => void = navigateToContinue,
): void {
  const handled = useRef(false);

  useEffect(() => {
    if (!isAuthenticated || handled.current) {
      return;
    }
    if (mcpPending !== true) {
      writeAttempted(false);
      return;
    }
    handled.current = true;
    if (readAttempted()) {
      return;
    }
    writeAttempted(true);
    navigate();
  }, [isAuthenticated, mcpPending, navigate]);
}
