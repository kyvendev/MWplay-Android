// "Esportes" in the TV tab: the shared classification (same cases as SportsChannelsTest in the
// apps) and the page with the fake core. Fixtures only; no network.
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { resetCatalogTabs } from '../src/features/catalog/DiscoverPage';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import type { Discover, MetaItemPreview } from '../src/services/stremio/types';
import { buildCategoryRow, isSportsCategory, sportsChannels } from '../src/shared/sportsChannels';
import { createFakeCore, type FakeCore } from './fakeCore';
import { ctxFixture } from './fixtures/coreStates';

type Channel = { id: string; name: string; genres: string[] };
const sportv: Channel = { id: 'bc:sportv', name: 'SporTV', genres: ['Esportes'] };
const espn: Channel = { id: 'bc:espn', name: 'ESPN', genres: ['Futebol', 'Esportes'] };
const globo: Channel = { id: 'bc:globo', name: 'Globo', genres: ['Abertos'] };
const novela: Channel = { id: 'bc:esporte-novela', name: 'Esporte Novelas', genres: ['Novelas'] };
const band: Channel = { id: 'bc:band', name: 'Band', genres: [] };
const sports = (items: Channel[], listed: boolean, forced?: Set<string>, excluded?: Set<string>) =>
  sportsChannels(items, (item) => item.id, (item) => item.genres, listed, forced, excluded);

describe('classification', () => {
  it('sports channels appear, other channels do not, a sports word in the name is not enough', () => {
    expect(sports([sportv, globo, espn, novela, band], false)).toEqual([sportv, espn]);
  });

  it('compares whole categories ignoring accents and case', () => {
    expect(isSportsCategory('TÊNIS')).toBe(true);
    expect(isSportsCategory(' esportes  em geral ')).toBe(true);
    expect(isSportsCategory('Canal Esportivo')).toBe(false);
    expect(isSportsCategory(null)).toBe(false);
  });

  it('never duplicates channels', () => {
    expect(sports([sportv, espn, sportv], false)).toEqual([sportv, espn]);
  });

  it('fixes wrong classifications with the explicit lists', () => {
    expect(sports([sportv, globo], true, undefined, new Set(['bc:globo']))).toEqual([sportv]);
    expect(sports([globo, band], false, new Set(['bc:band']))).toEqual([band]);
  });

  it('keeps the source categories and adds Todos os canais and Esportes', () => {
    const options = [
      { value: null, selected: true, request: 'all' },
      { value: 'Abertos', selected: false, request: 'abertos' },
      { value: 'Esportes', selected: false, request: 'esportes' },
      { value: 'Futebol', selected: false, request: 'futebol' },
    ];
    const row = buildCategoryRow(options, false, (value) => value);
    expect(row.chips.map((chip) => chip.label)).toEqual(['Todos os canais', 'Esportes', 'Abertos']);
    const inSports = buildCategoryRow(options.map((option) => ({ ...option, selected: option.value === 'Futebol' })), false, (value) => value);
    expect(inSports.sportsOpen && inSports.listedUnderSports).toBe(true);
    expect(inSports.sportsSubcategories.map((chip) => chip.label)).toEqual(['Esportes', 'Futebol']);
    const noSports = buildCategoryRow([options[0], options[1]], true, (value) => value);
    expect(noSports.sportsOpen && noSports.sportsFromMetadata && !noSports.listedUnderSports).toBe(true);
  });
});

class NoopObserver {
  observe() {}
  disconnect() {}
  unobserve() {}
}

describe('TV tab with Esportes', () => {
  const base = 'https://bc.example/manifest.json';
  const link = (genre: string | null) => `#/discover/${encodeURIComponent(base)}/tv/canais?${genre ? `genre=${encodeURIComponent(genre)}` : ''}`;
  const meta = (channel: Channel): MetaItemPreview => ({
    id: channel.id,
    type: 'tv',
    name: channel.name,
    poster: null,
    links: channel.genres.map((name) => ({ name, category: 'Genres', url: '' })),
  });
  const state = (genre: string | null, items: Channel[], genres = ['Abertos', 'Esportes', 'Notícias']): Discover => ({
    selected: { request: { base, path: { resource: 'catalog', type: 'tv', id: 'canais', extra: genre ? [['genre', genre]] : [] } } },
    selectable: {
      types: [{ type: 'tv', selected: true, deepLinks: { discover: link(null) } }],
      catalogs: [{ id: 'canais', name: 'Canais', type: 'tv', selected: true, addon: { manifest: { id: 'bestcine', name: 'Best Cine' }, transportUrl: base }, deepLinks: { discover: link(null) } }],
      extra: [
        {
          name: 'genre',
          isRequired: false,
          options: [{ value: null, selected: genre === null, deepLinks: { discover: link(null) } }, ...genres.map((value) => ({ value, selected: genre === value, deepLinks: { discover: link(value) } }))],
        },
      ],
      nextPage: false,
    },
    catalog: { content: { type: 'Ready', content: items.map(meta) } },
  } as unknown as Discover);
  const loads = (fake: FakeCore) =>
    fake.dispatched
      .map((entry) => entry.action as { action: string; args?: { model?: string; args?: { request?: { path: { extra: [string, string][] } } } } })
      .filter((action) => action.action === 'Load' && action.args?.model === 'CatalogWithFilters')
      .map((action) => action.args!.args!.request!.path.extra.find(([name]) => name === 'genre')?.[1] ?? 'todos');

  beforeEach(() => {
    window.location.hash = '#/tv';
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

  const renderTv = (discover: Discover) => {
    const fake = createFakeCore({ ctx: ctxFixture(true), discover });
    render(
      <CoreProvider core={fake.core}>
        <App />
      </CoreProvider>,
    );
    return fake;
  };

  it('shows Todos os canais, Esportes and the other categories; Esportes loads the source sports category', async () => {
    const fake = renderTv(state(null, [sportv, globo, espn]));
    const toolbar = await screen.findByRole('toolbar', { name: 'Categorias' });
    expect(within(toolbar).getAllByRole('button').map((button) => button.textContent)).toEqual(['Todos os canais', 'Esportes', 'Abertos', 'Notícias']);
    expect(within(toolbar).getByRole('button', { name: 'Todos os canais' }).getAttribute('aria-pressed')).toBe('true');
    // The genre filter is not repeated as a second row.
    expect(screen.queryByRole('toolbar', { name: 'Gênero' })).toBeNull();
    expect(screen.getAllByRole('button', { name: /SporTV|Globo|ESPN/ })).toHaveLength(3);

    fireEvent.click(within(toolbar).getByRole('button', { name: 'Esportes' }));
    await waitFor(() => expect(loads(fake).at(-1)).toBe('Esportes'));
  });

  it('lists only sports channels, once each, and never the others', async () => {
    renderTv(state('Esportes', [sportv, espn, sportv, globo]));
    await screen.findByRole('toolbar', { name: 'Categorias' });
    // Listed by the source under Esportes: shown once each (the explicit lists can still exclude one).
    expect(screen.getAllByRole('button', { name: /SporTV/ })).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Esportes' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('without a sports category, Esportes filters every channel by its metadata', async () => {
    const fake = renderTv(state(null, [sportv, globo, novela, espn], ['Abertos']));
    const toolbar = await screen.findByRole('toolbar', { name: 'Categorias' });
    // Keyboard / remote: the chips are native buttons, so focus + Enter (click) selects.
    const chip = within(toolbar).getByRole('button', { name: 'Esportes' });
    chip.focus();
    expect(document.activeElement).toBe(chip);
    fireEvent.click(chip);
    await waitFor(() => expect(chip.getAttribute('aria-pressed')).toBe('true'));
    await waitFor(() => expect(loads(fake).at(-1)).toBe('todos'));
    expect(screen.queryByRole('button', { name: /Globo/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Esporte Novelas/ })).toBeNull();
    expect(screen.getAllByRole('button', { name: /SporTV|ESPN/ })).toHaveLength(2);

    act(() => fireEvent.click(within(toolbar).getByRole('button', { name: 'Todos os canais' })));
    await waitFor(() => expect(screen.getAllByRole('button', { name: /SporTV|Globo|ESPN|Novelas/ })).toHaveLength(4));
  });
});
