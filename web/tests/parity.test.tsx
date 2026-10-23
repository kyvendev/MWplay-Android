// Features ported from the Android apps: search history, TV tab, resume on the last server,
// Continuar assistindo menu and Portuguese labels. Fake core and fixtures only; no network.
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { resetCatalogTabs } from '../src/features/catalog/DiscoverPage';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import type { Discover, Stream } from '../src/services/stremio/types';
import { extraOptionName, genreName, preferredTvCatalog, shelfTitle, typeName } from '../src/shared/contentLabels';
import { addSearch, removeSearch } from '../src/shared/searchHistory';
import { pickRemembered, recallStream, rememberStream, selectionOf } from '../src/shared/streamMemory';
import { createFakeCore, type FakeCore } from './fakeCore';
import { boardFixture, ctxFixture } from './fixtures/coreStates';

class NoopObserver {
  observe() {}
  disconnect() {}
  unobserve() {}
}

beforeEach(() => {
  window.location.hash = '#/';
  localStorage.clear();
  resetCatalogTabs();
  vi.stubGlobal('IntersectionObserver', NoopObserver);
  vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 503 })));
  window.scrollTo = () => {};
  Element.prototype.scrollIntoView = () => {};
  window.matchMedia = ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const renderApp = (fake: FakeCore) =>
  render(
    <CoreProvider core={fake.core}>
      <App />
    </CoreProvider>,
  );

const actions = (fake: FakeCore) => fake.dispatched.map((entry) => entry.action as { action: string; args?: { action?: string; model?: string; args?: unknown } });

describe('labels', () => {
  it('translates types, catalogs and genres like the apps', () => {
    expect(typeName('movie')).toBe('Filmes');
    expect(typeName('tv')).toBe('TV');
    expect(shelfTitle('Popular', 'series')).toBe('Séries populares');
    expect(shelfTitle('New', 'anime')).toBe('Lançamentos · Anime');
    expect(genreName('Sci-Fi')).toBe('Ficção científica');
    expect(extraOptionName('genre', null)).toBe('Todos os gêneros');
    expect(shelfTitle('TNT HD (BC)', null)).toBe('TNT HD (BC)');
  });

  it('prefers the Best Cine TV catalog, otherwise the first one', () => {
    const catalogs = [
      { name: 'Canais', url: 'https://a.example/manifest.json', addon: 'Outro' },
      { name: 'Canais abertos', url: 'https://b.example/manifest.json', addon: 'Best Cine' },
    ];
    const pick = (list: typeof catalogs) => preferredTvCatalog(list, (c) => c.name, (c) => c.url, (c) => c.addon);
    expect(pick(catalogs)?.addon).toBe('Best Cine');
    expect(pick([catalogs[0]])?.addon).toBe('Outro');
  });
});

describe('search history', () => {
  it('keeps recent searches first, without duplicates or one-letter entries', () => {
    let history: string[] = [];
    history = addSearch(history, 'Matrix');
    history = addSearch(history, '  Breaking   Bad ');
    history = addSearch(history, 'matrix');
    history = addSearch(history, 'a');
    expect(history).toEqual(['matrix', 'Breaking Bad']);
    expect(removeSearch(history, 'matrix')).toEqual(['Breaking Bad']);
  });

  it('shows the history on the empty search screen and removes an entry with its X', async () => {
    localStorage.setItem('mwplay.web.searchHistory', JSON.stringify(['Matrix', 'Duna']));
    window.location.hash = '#/search';
    renderApp(createFakeCore({ ctx: ctxFixture(true), search: { selected: null, catalogs: [] } }));
    expect(await screen.findByText('Buscas recentes')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Remover "Matrix" do histórico' }));
    expect(screen.queryByText('Matrix')).toBeNull();
    expect(JSON.parse(localStorage.getItem('mwplay.web.searchHistory')!)).toEqual(['Duna']);
    fireEvent.click(screen.getByText('Duna'));
    expect((screen.getByLabelText('Buscar') as HTMLInputElement).value).toBe('Duna');
  });
});

describe('resume on the last server', () => {
  const stream = (url: string, name: string): Stream => ({ url, name, description: 'S01E02' });

  it('finds the same source again, and the same server for a new episode', () => {
    const server = 'https://addon.example/manifest.json';
    rememberStream('series', 'tt1', 'tt1:1:2', selectionOf(stream('https://cdn.example/ep2.mp4?token=abc', 'Best HD'), server));
    // The stream URL (and its token) is not stored.
    expect(localStorage.getItem('mwplay.web.lastStreams')).not.toContain('token=abc');

    const candidates = [
      { stream: stream('https://other.example/x.mp4', 'Outro'), server: 'https://other.example/manifest.json' },
      { stream: stream('https://cdn.example/ep2.mp4?token=abc', 'Best HD'), server },
    ];
    const exact = recallStream('series', 'tt1', 'tt1:1:2')!;
    expect(exact.exact).toBe(true);
    expect(pickRemembered(candidates, exact)).toBe(candidates[1]);

    const nextEpisode = recallStream('series', 'tt1', 'tt1:1:3')!;
    expect(nextEpisode.exact).toBe(false);
    const ep3 = [{ stream: stream('https://cdn.example/ep3.mp4', 'Best HD'), server }];
    expect(pickRemembered(ep3, nextEpisode)).toBe(ep3[0]);
    expect(pickRemembered([candidates[0]], nextEpisode)).toBeNull();
    expect(recallStream('movie', 'tt9', 'tt9')).toBeNull();
  });
});

describe('Continuar assistindo', () => {
  const continueWatching = {
    items: [{ _id: 'tt0903747', name: 'Breaking Bad', type: 'series', poster: null, progress: 0.4, notifications: 0, state: { videoId: 'tt0903747:1:2' } }],
  };

  it('a tap resumes when a server is remembered, and opens the details otherwise', async () => {
    renderApp(createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: continueWatching }));
    fireEvent.click(await screen.findByRole('button', { name: 'Breaking Bad' }), { detail: 1 });
    expect(window.location.hash).toBe('#/detail/series/tt0903747?video=tt0903747%3A1%3A2');

    cleanup();
    window.location.hash = '#/';
    rememberStream('series', 'tt0903747', 'tt0903747:1:2', selectionOf({ url: 'https://cdn.example/a.mp4' }, 'https://addon.example/manifest.json'));
    renderApp(createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: continueWatching }));
    fireEvent.click(await screen.findByRole('button', { name: 'Breaking Bad' }), { detail: 1 });
    expect(window.location.hash).toBe('#/detail/series/tt0903747?video=tt0903747%3A1%3A2&resume=1');
  });

  it('long press offers only "Detalhes" and "Excluir da lista", which rewinds through the core', async () => {
    const fake = createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: continueWatching });
    renderApp(fake);
    fireEvent.contextMenu(await screen.findByRole('button', { name: 'Breaking Bad' }));
    expect(screen.getByRole('button', { name: 'Detalhes' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Continuar assistindo' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Excluir da lista' }));
    await waitFor(() =>
      expect(actions(fake).filter((action) => action.action === 'Ctx').map((action) => [action.args?.action, action.args?.args])).toEqual([
        ['RewindLibraryItem', 'tt0903747'],
        ['DismissNotificationItem', 'tt0903747'],
      ]),
    );
  });

  it('holding OK on a TV remote opens the menu instead of resuming', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderApp(createFakeCore({ ctx: ctxFixture(true), board: boardFixture, continue_watching_preview: continueWatching }));
    const poster = await screen.findByRole('button', { name: 'Breaking Bad' });
    fireEvent.keyDown(poster, { key: 'Enter' });
    await act(async () => {
      vi.advanceTimersByTime(700);
    });
    fireEvent.keyUp(poster, { key: 'Enter' });
    expect(screen.getByRole('button', { name: 'Excluir da lista' })).toBeTruthy();
    expect(window.location.hash).toBe('#/');
    vi.useRealTimers();
  });
});

describe('TV tab', () => {
  const base = 'https://addon.example/manifest.json';
  const deepLink = (addon: string, type: string, catalog: string) => `#/discover/${encodeURIComponent(addon)}/${type}/${catalog}?`;
  const discoverState = (type: string, catalogId: string): Discover => ({
    selected: { request: { base, path: { resource: 'catalog', type, id: catalogId, extra: [] } } },
    selectable: {
      types: [
        { type: 'movie', selected: type === 'movie', deepLinks: { discover: deepLink(base, 'movie', 'top') } },
        { type: 'tv', selected: type === 'tv', deepLinks: { discover: deepLink(base, 'tv', 'canais') } },
      ],
      catalogs:
        type === 'tv'
          ? [
              { id: 'canais', name: 'Canais', type: 'tv', selected: catalogId === 'canais', addon: { manifest: { id: 'outro', name: 'Outro' }, transportUrl: base }, deepLinks: { discover: deepLink(base, 'tv', 'canais') } },
              { id: 'bc', name: 'Canais abertos', type: 'tv', selected: catalogId === 'bc', addon: { manifest: { id: 'bestcine', name: 'Best Cine' }, transportUrl: 'https://bc.example/manifest.json' }, deepLinks: { discover: deepLink('https://bc.example/manifest.json', 'tv', 'bc') } },
            ]
          : [{ id: 'top', name: 'Popular', type: 'movie', selected: true, addon: { manifest: { id: 'cinemeta', name: 'Cinemeta' }, transportUrl: base }, deepLinks: { discover: deepLink(base, 'movie', 'top') } }],
      extra: [],
      nextPage: false,
    },
    catalog: { content: { type: 'Ready', content: [] } },
  });
  const discoverLoads = (fake: FakeCore) =>
    actions(fake)
      .filter((action) => action.action === 'Load' && action.args?.model === 'CatalogWithFilters')
      .map((action) => (action.args?.args as { request: { base: string; path: { type: string; id: string } } }).request)
      .map((request) => `${request.path.type}/${request.path.id}@${request.base}`);

  it('opens the TV catalogs, switches to Best Cine once and keeps TV out of Discover', async () => {
    window.location.hash = '#/tv';
    const fake = createFakeCore({ ctx: ctxFixture(true), discover: discoverState('movie', 'top') });
    renderApp(fake);
    await waitFor(() => expect(discoverLoads(fake)).toEqual([`tv/canais@${base}`]));

    act(() => fake.setState('discover', discoverState('tv', 'canais')));
    await waitFor(() => expect(discoverLoads(fake).at(-1)).toBe('tv/bc@https://bc.example/manifest.json'));
    expect(screen.getByRole('heading', { name: 'TV' })).toBeTruthy();
    expect(screen.queryByRole('toolbar', { name: 'Tipo' })).toBeNull();

    // Discover never offers TV as a type.
    act(() => {
      window.location.hash = '#/discover';
      fake.setState('discover', discoverState('movie', 'top'));
    });
    const types = await screen.findByRole('toolbar', { name: 'Tipo' });
    expect(types.textContent).toBe('Filmes');
  });
});
