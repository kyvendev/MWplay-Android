// Remote session revocation, mirroring MainActivity.isSessionRevoked on Android.
// fetch is a test double: no request reaches the Stremio API.
import { StrictMode } from 'react';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import { checkSession, SESSION_CHECK_URL, SESSION_RECHECK_MS } from '../src/services/stremio/sessionGuard';
import { createFakeCore } from './fakeCore';
import { boardFixture, ctxFixture } from './fixtures/coreStates';

const respond = (status: number, body?: unknown) =>
  vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));

describe('checkSession', () => {
  it('posts the auth key to the same endpoint as the apps', async () => {
    const fetchImpl = respond(200, { result: { user: {} } });
    await checkSession('key-123', fetchImpl as unknown as typeof fetch);
    expect(fetchImpl).toHaveBeenCalledWith(SESSION_CHECK_URL, expect.objectContaining({ method: 'POST', body: JSON.stringify({ authKey: 'key-123' }) }));
    expect(SESSION_CHECK_URL).toBe('https://api.strem.io/api/getUser');
  });

  it('treats only definitive authentication failures as revoked', async () => {
    const check = (fetchImpl: unknown) => checkSession('k', fetchImpl as typeof fetch);
    expect(await check(respond(401))).toBe('revoked');
    expect(await check(respond(403))).toBe('revoked');
    expect(await check(respond(200, { error: { code: 1, message: 'Session does not exist' } }))).toBe('revoked');
    expect(await check(respond(200, { result: { user: { email: 'x' } } }))).toBe('valid');
    expect(await check(respond(200, { error: { code: 2 } }))).toBe('valid');
    expect(await check(respond(500))).toBe('unknown');
    expect(await check(vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))).toBe('unknown');
  });

  it('gives up after the timeout without signing out', async () => {
    const hanging = vi.fn((_url: string, init?: RequestInit) => new Promise<Response>((_resolve, reject) => init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))));
    expect(await checkSession('k', hanging as unknown as typeof fetch, 20)).toBe('unknown');
  });
});

describe('remote session guard', () => {
  class NoopObserver {
    observe() {}
    disconnect() {}
  }

  beforeEach(() => {
    window.location.hash = '#/';
    vi.stubGlobal('IntersectionObserver', NoopObserver);
    window.scrollTo = () => {};
    window.matchMedia = ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  const logouts = (fake: ReturnType<typeof createFakeCore>) =>
    fake.dispatched.filter((entry) => (entry.action as { args?: { action?: string } }).args?.action === 'Logout').length;

  const renderSignedIn = () => {
    const fake = createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: { items: [] } });
    render(
      <CoreProvider core={fake.core}>
        <App />
      </CoreProvider>,
    );
    return fake;
  };

  it('signs out through the core when the session was revoked elsewhere', async () => {
    vi.stubGlobal('fetch', respond(200, { error: { code: 1, message: 'Session does not exist' } }));
    const fake = renderSignedIn();
    await waitFor(() => expect(logouts(fake)).toBe(1));
    // The core clears the profile; the login screen comes back.
    act(() => fake.setState('ctx', ctxFixture(false)));
    expect(await screen.findByLabelText('E-mail')).toBeTruthy();
  });

  it('still signs out under StrictMode double effects', async () => {
    vi.stubGlobal('fetch', respond(401));
    const fake = createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: { items: [] } });
    render(
      <StrictMode>
        <CoreProvider core={fake.core}>
          <App />
        </CoreProvider>
      </StrictMode>,
    );
    await waitFor(() => expect(logouts(fake)).toBeGreaterThanOrEqual(1));
  });

  it('keeps the user signed in on network failures', async () => {
    const fetchImpl = vi.fn(async () => Promise.reject(new TypeError('Failed to fetch')));
    vi.stubGlobal('fetch', fetchImpl);
    const fake = renderSignedIn();
    await waitFor(() => expect(fetchImpl).toHaveBeenCalled());
    await act(async () => {});
    expect(logouts(fake)).toBe(0);
  });

  it('re-checks when the tab becomes visible again and periodically', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchImpl = respond(200, { result: {} });
    vi.stubGlobal('fetch', fetchImpl);
    renderSignedIn();
    await waitFor(() => expect(fetchImpl).toHaveBeenCalledTimes(1));
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await waitFor(() => expect(fetchImpl).toHaveBeenCalledTimes(2));
    await act(async () => {
      vi.advanceTimersByTime(SESSION_RECHECK_MS);
    });
    await waitFor(() => expect(fetchImpl).toHaveBeenCalledTimes(3));
  });
});
