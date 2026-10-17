import { useEffect } from 'react';
import { useCore, useCoreState } from './CoreProvider';

/** Same account endpoint the Android apps validate against (MainActivity.isSessionRevoked). */
export const SESSION_CHECK_URL = 'https://api.strem.io/api/getUser';
const CHECK_TIMEOUT_MS = 5_000;
/** Browser and TV tabs stay open for hours, so the session is also re-checked while visible. */
export const SESSION_RECHECK_MS = 5 * 60_000;

export type SessionStatus = 'valid' | 'revoked' | 'unknown';

/**
 * Only definitive authentication failures count as revoked (HTTP 401/403, or HTTP 200 with
 * `error.code == 1`), exactly like the apps. Network errors, timeouts and other server errors are
 * `unknown` and never sign the user out.
 */
export async function checkSession(authKey: string, fetchImpl: typeof fetch = fetch, timeoutMs = CHECK_TIMEOUT_MS): Promise<SessionStatus> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(SESSION_CHECK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ authKey }),
      signal: controller.signal,
    });
    if (response.status === 401 || response.status === 403) return 'revoked';
    if (response.status !== 200) return 'unknown';
    const body = (await response.json().catch(() => null)) as { error?: { code?: number } } | null;
    return body?.error?.code === 1 ? 'revoked' : 'valid';
  } catch {
    return 'unknown';
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Signs the web app out when the session was ended elsewhere (for example "log out of all devices"
 * on the Stremio website). Checks on start, when the tab becomes visible again, when the network
 * comes back and periodically while visible; the logout goes through the core like on Android.
 */
export function useRemoteSessionGuard() {
  const { core } = useCore();
  const ctx = useCoreState('ctx');
  const authKey = ctx?.profile.auth?.key ?? null;

  useEffect(() => {
    if (!authKey) return;
    // Per subscription, so a re-run of the effect never inherits a cancelled check.
    let active = true;
    let inFlight = false;
    const validate = async (whenHidden = false) => {
      if (inFlight || (!whenHidden && document.visibilityState === 'hidden')) return;
      inFlight = true;
      try {
        if ((await checkSession(authKey)) === 'revoked' && active) await core.logout();
      } finally {
        inFlight = false;
      }
    };
    // The first check runs even for a tab opened in the background; later ones only when visible.
    validate(true);
    const onVisible = () => document.visibilityState === 'visible' && validate();
    const onOnline = () => validate();
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('online', onOnline);
    const interval = window.setInterval(() => validate(), SESSION_RECHECK_MS);
    return () => {
      active = false;
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('online', onOnline);
      window.clearInterval(interval);
    };
  }, [authKey, core]);
}
