// Opening animation and first-install defaults (web port of the Android behaviour).
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { OrbitalIntro, shouldShowIntro } from '../src/features/intro/OrbitalIntro';
import { skipIntro, stepIntro, TIMELINE, type IntroClock } from '../src/features/intro/orbitalRenderer';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import { decideFirstRunDefaults, firstRunDefaultsPending } from '../src/services/stremio/firstRunDefaults';
import { createFakeCore, type FakeCore } from './fakeCore';
import { ctxFixture } from './fixtures/coreStates';

class NoopObserver {
  observe() {}
  disconnect() {}
  unobserve() {}
}

beforeEach(() => {
  window.location.hash = '#/';
  localStorage.clear();
  sessionStorage.clear();
  vi.stubGlobal('IntersectionObserver', NoopObserver);
  vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 503 })));
  window.scrollTo = () => {};
  // jsdom has no 2D canvas; the intro just skips drawing.
  HTMLCanvasElement.prototype.getContext = (() => null) as typeof HTMLCanvasElement.prototype.getContext;
  Element.prototype.scrollIntoView = () => {};
  window.matchMedia = ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const newClock = (): IntroClock => ({ time: 0, elapsed: 0, exitAt: null, skipRequested: false });
/** Runs the clock at 60 fps for `seconds`; returns whether it finished. */
function run(clock: IntroClock, seconds: number, ready: () => boolean, reducedMotion = false) {
  for (let i = 0; i < Math.round(seconds * 60); i++) if (stepIntro(clock, 1 / 60, ready(), reducedMotion)) return true;
  return false;
}

describe('opening timeline', () => {
  it('leaves right after the logo when the app is ready, without cutting it short', () => {
    const clock = newClock();
    expect(run(clock, 5.0, () => true)).toBe(false);
    expect(run(clock, 1.0, () => true)).toBe(true);
  });

  it('holds the logo while startup is still running, then leaves', () => {
    const clock = newClock();
    let ready = false;
    expect(run(clock, 9, () => ready)).toBe(false);
    ready = true;
    expect(run(clock, 0.6, () => ready)).toBe(true);
  });

  it('never covers the app for longer than the hard cap', () => {
    expect(run(newClock(), TIMELINE.HARD_CAP + 0.6, () => false)).toBe(true);
  });

  it('skip exits early when ready and jumps to the logo otherwise', () => {
    const ready = newClock();
    run(ready, 1, () => true);
    skipIntro(ready);
    expect(run(ready, 0.6, () => true)).toBe(true);

    const notReady = newClock();
    run(notReady, 1, () => false);
    skipIntro(notReady);
    expect(notReady.time).toBeGreaterThanOrEqual(TIMELINE.LOGO_IN);
  });

  it('reduced motion shows the logo only', () => {
    const clock = newClock();
    clock.time = TIMELINE.LOGO_IN;
    expect(run(clock, 0.5, () => true, true)).toBe(false);
    expect(clock.time).toBe(TIMELINE.LOGO_IN);
    expect(run(clock, 0.6, () => true, true)).toBe(true);
  });

  it('plays once per tab session', () => {
    expect(shouldShowIntro()).toBe(true);
    expect(shouldShowIntro()).toBe(false);
  });
});

describe('opening on screen', () => {
  it('swallows arrows so focus cannot move underneath, but leaves Back alone', () => {
    const underneath = vi.fn();
    window.addEventListener('keydown', underneath);
    render(<OrbitalIntro ready={false} onFinished={() => {}} />);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(underneath).not.toHaveBeenCalled();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(underneath).toHaveBeenCalledTimes(1);
    window.removeEventListener('keydown', underneath);
  });

  it('is not shown unless requested (reloads, tests, in-app navigation)', async () => {
    render(
      <CoreProvider core={createFakeCore({ ctx: ctxFixture(false) }).core}>
        <App />
      </CoreProvider>,
    );
    await screen.findByRole('button', { name: /entrar/i });
    expect(document.querySelector('.orbital-intro')).toBeNull();
  });
});

describe('first-install defaults', () => {
  const settingsUpdates = (fake: FakeCore) =>
    fake.dispatched
      .map((entry) => entry.action as { args?: { action?: string; args?: Record<string, unknown> } })
      .filter((action) => action.args?.action === 'UpdateSettings')
      .map((action) => action.args!.args!);

  function renderSignedIn() {
    const fake = createFakeCore({ ctx: ctxFixture(true) });
    const view = render(
      <CoreProvider core={fake.core}>
        <App />
      </CoreProvider>,
    );
    return { fake, view };
  }

  it('a fresh browser gets Portuguese (Brazil) defaults once, on the signed-in profile', async () => {
    decideFirstRunDefaults();
    expect(firstRunDefaultsPending()).toBe(true);
    const { fake } = renderSignedIn();
    await waitFor(() => expect(settingsUpdates(fake)).toHaveLength(1));
    const update = settingsUpdates(fake)[0];
    expect(update).toMatchObject({ interfaceLanguage: 'por', audioLanguage: 'por', subtitlesLanguage: 'pob' });
    // Other preferences are kept.
    expect(update.subtitlesSize).toBe(ctxFixture(true).profile.settings.subtitlesSize);
    expect(firstRunDefaultsPending()).toBe(false);
  });

  it('reopening never overwrites later changes', async () => {
    decideFirstRunDefaults();
    const first = renderSignedIn();
    await waitFor(() => expect(settingsUpdates(first.fake)).toHaveLength(1));
    first.view.unmount();
    decideFirstRunDefaults();
    const { fake } = renderSignedIn();
    await screen.findAllByRole('navigation');
    expect(settingsUpdates(fake)).toHaveLength(0);
  });

  it('existing users (core data already in this browser) keep their settings', async () => {
    localStorage.setItem('mwplay.core.profile', '{}');
    decideFirstRunDefaults();
    expect(firstRunDefaultsPending()).toBe(false);
    const { fake } = renderSignedIn();
    await screen.findAllByRole('navigation');
    expect(settingsUpdates(fake)).toHaveLength(0);
  });

  it('a corrupted state value is treated as already handled', () => {
    localStorage.setItem('mwplay.web.firstRunDefaults', 'garbage');
    decideFirstRunDefaults();
    expect(firstRunDefaultsPending()).toBe(false);
  });
});
