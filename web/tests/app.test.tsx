// UI flows against the fake core (test double) and fixtures; no network.
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import { getPlaybackRequest } from '../src/features/player/playbackRequest';
import { createFakeCore, type FakeCore } from './fakeCore';
import { boardFixture, ctxFixture, detailsFixture, searchFixture } from './fixtures/coreStates';

class NoopObserver {
  observe() {}
  disconnect() {}
  unobserve() {}
}

beforeEach(() => {
  window.location.hash = '#/';
  localStorage.clear();
  vi.stubGlobal('IntersectionObserver', NoopObserver);
  window.scrollTo = () => {};
  Element.prototype.scrollIntoView = () => {};
  window.matchMedia = ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function renderApp(fake: FakeCore) {
  return render(
    <CoreProvider core={fake.core}>
      <App />
    </CoreProvider>,
  );
}

const loadActions = (fake: FakeCore) =>
  fake.dispatched.map((entry) => entry.action as { action: string; args?: { model?: string; action?: string } }).map((action) => action.args?.model ?? action.args?.action ?? action.action);

describe('startup', () => {
  it('shows a clear error when the core cannot start in this browser', async () => {
    renderApp(createFakeCore({}, { failStart: 'WebAssembly.instantiate failed' }));
    expect((await screen.findByRole("alert")).textContent).toContain('não conseguiu iniciar');
  });

  it('asks for sign-in before showing catalogs, like the apps', async () => {
    const fake = createFakeCore({ ctx: ctxFixture(false) });
    renderApp(fake);
    expect(await screen.findByLabelText('E-mail')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'teste@example.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    await waitFor(() => expect(loadActions(fake)).toContain('Authenticate'));
    act(() => fake.emit({ name: 'CoreEvent', args: { event: 'Error', args: { error: { message: 'Wrong password' } } } }));
    expect((await screen.findByRole("alert")).textContent).toContain('E-mail ou senha incorretos');
  });
});

describe('home', () => {
  it('renders real catalog shapes with loading, error and ready states and loads the board once', async () => {
    const fake = createFakeCore({ ctx: ctxFixture(true), board: { selected: null, catalogs: [] }, continue_watching_preview: { items: [] } });
    renderApp(fake);
    await waitFor(() => expect(loadActions(fake)).toEqual(['CatalogsWithExtra', 'LoadRange']));
    act(() => fake.setState('board', boardFixture));
    expect(await screen.findByRole('button', { name: 'Unabomber' })).toBeTruthy();
    expect(screen.getByText('Popular · Séries')).toBeTruthy(); // still loading: skeletons
    expect(screen.getByText(/Não foi possível carregar este catálogo/)).toBeTruthy();
    expect(screen.getAllByRole('button', { name: /Spider-Man/ })[0]).toBeTruthy();
    // No duplicate board loads when the state updates.
    expect(loadActions(fake).filter((action) => action === 'CatalogsWithExtra')).toHaveLength(1);
  });

  it('opens details from a poster', async () => {
    const fake = createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: { items: [] }, meta_details: detailsFixture, streaming_server: { selected: { transportUrl: '' }, settings: { type: 'Err', content: 'down' }, baseUrl: null } });
    renderApp(fake);
    fireEvent.click(await screen.findByRole('button', { name: 'Unabomber' }));
    await waitFor(() => expect(window.location.hash).toBe('#/detail/movie/tt6933238'));
  });
});

describe('details and sources', () => {
  it('lists sources, blocks torrents without a streaming server and starts playback through the Player model', async () => {
    window.location.hash = '#/detail/movie/tt0012349';
    const fake = createFakeCore({
      ctx: ctxFixture(true),
      meta_details: detailsFixture,
      streaming_server: { selected: { transportUrl: '' }, settings: { type: 'Err', content: 'down' }, baseUrl: null },
    });
    renderApp(fake);
    expect(await screen.findByText('IMDb 8.2')).toBeTruthy();
    expect(screen.getByText('Comedy · Drama')).toBeTruthy();
    // Same meta already loaded: no reload.
    expect(loadActions(fake)).not.toContain('MetaDetails');

    const torrent = screen.getByText('Teste torrent').closest('button')!;
    expect((torrent as HTMLButtonElement).disabled).toBe(true);
    expect(torrent.textContent).toContain('Disponível apenas no app MW Play');
    // HTTP sources flagged notWebReady by their addon play fine in browsers: no warning, not blocked.
    const http = screen.getByText('Teste HLS').closest('button')!;
    expect((http as HTMLButtonElement).disabled).toBe(false);
    expect(http.textContent).not.toContain('Pode não reproduzir');
    // Playable sources are listed before blocked ones.
    const titles = screen.getAllByRole('button').map((button) => button.textContent ?? '');
    expect(titles.findIndex((text) => text.includes('Teste HLS'))).toBeLessThan(titles.findIndex((text) => text.includes('Teste torrent')));

    fireEvent.click(screen.getByText('Teste HLS').closest('button')!);
    await waitFor(() => expect(window.location.hash).toBe('#/player'));
    expect(getPlaybackRequest()).toMatchObject({
      stream: { url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8' },
      streamRequest: { base: 'https://example.invalid/http/manifest.json', path: detailsFixture.selected!.streamPath },
      metaRequest: { base: 'https://v3-cinemeta.strem.io/manifest.json', path: detailsFixture.selected!.metaPath },
      title: 'The Kid',
    });
  });
});

describe('search', () => {
  it('debounces typing into a single core search and renders results', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    window.location.hash = '#/search';
    const fake = createFakeCore({ ctx: ctxFixture(true), search: { selected: null, catalogs: [] } });
    renderApp(fake);
    const field = await screen.findByLabelText('Buscar');
    for (const value of ['m', 'ma', 'mat', 'matrix']) fireEvent.change(field, { target: { value } });
    await act(async () => {
      vi.advanceTimersByTime(600);
    });
    await waitFor(() => expect(loadActions(fake).filter((action) => action === 'CatalogsWithExtra')).toHaveLength(1));
    act(() => fake.setState('search', searchFixture));
    expect(await screen.findByRole('button', { name: 'The Matrix' })).toBeTruthy();
    vi.useRealTimers();
  });
});

describe('modals and back', () => {
  it("saves settings changes to the core profile", async () => {
    window.location.hash = '#/settings';
    const fake = createFakeCore({
      ctx: ctxFixture(true),
      streaming_server: { selected: { transportUrl: '' }, settings: { type: 'Err', content: 'down' }, baseUrl: null },
    });
    renderApp(fake);
    expect(await screen.findByText('Tamanho das legendas')).toBeTruthy();
    expect(screen.queryByText('Servidor de streaming')).toBeNull();
    // Settings changes go to the core profile (same semantics as Android).
    fireEvent.change(screen.getByLabelText('Tamanho das legendas'), { target: { value: '150' } });
    await waitFor(() => expect(loadActions(fake)).toContain('UpdateSettings'));
    const update = fake.dispatched.find((entry) => (entry.action as { args: { action: string } }).args.action === 'UpdateSettings')!;
    expect((update.action as { args: { args: { subtitlesSize: number } } }).args.args.subtitlesSize).toBe(150);
  });

  it('persists the interface mode preference', async () => {
    window.location.hash = '#/settings';
    const fake = createFakeCore({ ctx: ctxFixture(true), streaming_server: { selected: { transportUrl: '' }, settings: { type: 'Loading' }, baseUrl: null } });
    renderApp(fake);
    fireEvent.change(await screen.findByLabelText('Modo da interface'), { target: { value: 'tv' } });
    await waitFor(() => expect(document.documentElement.dataset.ui).toBe('tv'));
    expect(localStorage.getItem('mwplay.web.ui')).toBe('tv');
  });
});
