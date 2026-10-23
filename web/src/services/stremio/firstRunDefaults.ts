import { useEffect } from 'react';
import type { StremioCore } from './core';
import type { ProfileSettings } from './types';

/**
 * MW Play defaults for a brand-new browser installation, applied once through the core's own
 * profile settings (same values as the Android apps): interface and audio "por", subtitles "pob"
 * (Portuguese Brazil). The web build has no streaming server, so the server defaults (cache,
 * connections, seeding) do not apply here.
 *
 * The fresh-install decision is taken before the core starts and stored, so reloads, updates and
 * people who already used MW Play in this browser keep their settings.
 */
const STATE_KEY = 'mwplay.web.firstRunDefaults';
const CORE_STORAGE_PREFIX = 'mwplay.core.';

export const FIRST_RUN_PROFILE_DEFAULTS = {
  interfaceLanguage: 'por',
  audioLanguage: 'por',
  subtitlesLanguage: 'pob',
} as const satisfies Partial<ProfileSettings>;

type Store = Pick<Storage, 'getItem' | 'setItem' | 'key' | 'length'>;

function browserStorage(): Store | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** Must run before the core starts (it writes its own keys on start). */
export function decideFirstRunDefaults(storage: Store | null = browserStorage()) {
  try {
    if (!storage || storage.getItem(STATE_KEY) !== null) return;
    let existingUser = false;
    for (let i = 0; i < storage.length; i++) {
      if (storage.key(i)?.startsWith(CORE_STORAGE_PREFIX)) existingUser = true;
    }
    storage.setItem(STATE_KEY, existingUser ? 'done' : 'pending');
  } catch {
    // Storage unavailable: keep whatever the core has.
  }
}

export function firstRunDefaultsPending(storage: Store | null = browserStorage()): boolean {
  try {
    return storage?.getItem(STATE_KEY) === 'pending';
  } catch {
    return false;
  }
}

function markApplied(storage: Store | null = browserStorage()) {
  try {
    storage?.setItem(STATE_KEY, 'done');
  } catch {
    // Applied for this session anyway.
  }
}

/** Applies the defaults once, on the first signed-in profile of a fresh installation. */
export function useFirstRunDefaults(core: StremioCore, settings: ProfileSettings | null | undefined, signedIn: boolean) {
  useEffect(() => {
    if (!settings || !signedIn || !firstRunDefaultsPending()) return;
    markApplied();
    core.updateSettings({ ...settings, ...FIRST_RUN_PROFILE_DEFAULTS });
  }, [core, settings, signedIn]);
}
