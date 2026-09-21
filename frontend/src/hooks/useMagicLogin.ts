import { useEffect, useRef, useState } from 'react';
import { redeemMagicLink } from '../services/authService';

export type MagicLoginStatus = 'processing' | 'error';

interface UseMagicLoginOptions {
  redeem?: (token: string) => Promise<void>;
  onSuccess?: () => void;
}

const redirectToApp = () => window.location.replace('/');

function readTokenFromFragment(hash: string): string | null {
  const token = new URLSearchParams(hash.replace(/^#/, '')).get('t');
  return token && token.length > 0 ? token : null;
}

/**
 * Redeems the magic-link token carried in the URL fragment (`#t=<token>`).
 * The fragment is removed from the address bar before anything else happens, and the
 * one-time token is submitted at most once even when effects run twice (StrictMode).
 */
export function useMagicLogin({
  redeem = redeemMagicLink,
  onSuccess = redirectToApp,
}: UseMagicLoginOptions = {}): MagicLoginStatus {
  const [status, setStatus] = useState<MagicLoginStatus>('processing');
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;

    const token = readTokenFromFragment(window.location.hash);
    window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);

    if (!token) {
      setStatus('error');
      return;
    }

    redeem(token).then(onSuccess, () => setStatus('error'));
  }, [redeem, onSuccess]);

  return status;
}
