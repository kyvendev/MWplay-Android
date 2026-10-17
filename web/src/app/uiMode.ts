import { useEffect, useState } from 'react';

export type UiPreference = 'auto' | 'standard' | 'tv';
export type UiMode = 'standard' | 'tv';

const STORAGE_KEY = 'mwplay.web.ui';

/** TV browsers identify themselves in the user agent (Tizen, webOS, Android TV, Fire TV, ...). */
const TV_USER_AGENT = /smart-?tv|tizen|web0s|webos|netcast|hbbtv|bravia|aft[a-z]|crkey|googletv|android tv|androidtv|opera tv|vidaa|philipstv|viera|roku/i;

export function detectTv(userAgent: string, search: string): boolean {
  const forced = new URLSearchParams(search).get('ui');
  if (forced === 'tv') return true;
  if (forced === 'standard') return false;
  return TV_USER_AGENT.test(userAgent);
}

export function readUiPreference(): UiPreference {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'tv' || value === 'standard' ? value : 'auto';
  } catch {
    return 'auto';
  }
}

export function writeUiPreference(preference: UiPreference) {
  try {
    if (preference === 'auto') localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, preference);
  } catch {
    // Not persisted in private mode; the choice still applies to this session.
  }
}

export function resolveUiMode(preference: UiPreference): UiMode {
  if (preference !== 'auto') return preference;
  return detectTv(navigator.userAgent, window.location.search) ? 'tv' : 'standard';
}

/** Applies the mode to <html data-ui> so CSS switches between touch/desktop and TV layouts. */
export function useUiMode() {
  const [preference, setPreference] = useState<UiPreference>(readUiPreference);
  const mode = resolveUiMode(preference);
  useEffect(() => {
    document.documentElement.dataset.ui = mode;
  }, [mode]);
  const update = (next: UiPreference) => {
    writeUiPreference(next);
    setPreference(next);
  };
  return { mode, preference, setPreference: update };
}
