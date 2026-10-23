import { useEffect, useRef, useState } from 'react';
import { navigate } from '../../app/router';
import { CatalogShelf, Poster, Shelf } from '../../components/Poster';
import { Logo } from '../../components/Logo';
import { PlayIcon, SearchIcon } from '../../components/icons';
import { LoadingState } from '../../components/States';
import { Modal } from '../../components/Modal';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { parseDiscoverDeepLink } from '../../services/stremio/streams';
import { openCatalogTab } from '../catalog/DiscoverPage';
import type { ContinueWatchingPreview, MetaItemPreview } from '../../services/stremio/types';
import { recallStream } from '../../shared/streamMemory';

/** Shelves requested per step; more load as the user reaches the end (BoardRepository on Android). */
const RANGE_STEP = 6;

export function openDetails(item: { type: string; id: string }, videoId: string | null = null) {
  navigate({ name: 'detail', type: item.type, id: item.id, videoId });
}

export function HomePage() {
  const { core } = useCore();
  const board = useCoreState('board');
  const continueWatching = useCoreState('continue_watching_preview');
  const [rangeEnd, setRangeEnd] = useState(RANGE_STEP);
  const sentinel = useRef<HTMLDivElement>(null);
  const requested = useRef(false);
  const [menuItem, setMenuItem] = useState<ContinueItem | null>(null);

  // Load the board once; the core keeps it while the app runs, like the native apps.
  useEffect(() => {
    if (!board || requested.current) return;
    requested.current = true;
    if (!board.selected) core.loadBoard().then(() => core.loadBoardRange(0, RANGE_STEP));
  }, [board, core]);

  useEffect(() => {
    if (rangeEnd > RANGE_STEP) core.loadBoardRange(0, rangeEnd);
  }, [core, rangeEnd]);

  const catalogCount = board?.catalogs.length ?? 0;
  useEffect(() => {
    const node = sentinel.current;
    if (!node || rangeEnd >= catalogCount) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) setRangeEnd((end) => end + RANGE_STEP);
    }, { rootMargin: '600px 0px' });
    observer.observe(node);
    return () => observer.disconnect();
  }, [catalogCount, rangeEnd]);

  const heroItem = findHero(board?.catalogs.map((catalog) => catalog.content) ?? []);

  return (
    <main className="page">
      <header className="top-bar">
        <span className="home-logo">
          <Logo />
        </span>
        <span className="top-bar__spacer" />
        <button type="button" className="icon-button focusable" aria-label="Pesquisar" onClick={() => navigate({ name: 'search', query: '' })}>
          <SearchIcon />
        </button>
      </header>

      {heroItem && (
        <button type="button" className="hero focusable" onClick={() => openDetails(heroItem)}>
          {(heroItem.background || heroItem.poster) && <img src={heroItem.background ?? heroItem.poster!} alt="" />}
          <span className="hero__content">
            <span className="badge">Em destaque</span>
            <span className="hero__title">{heroItem.name}</span>
            <span className="meta-line">
              {[heroItem.releaseInfo, heroItem.type === 'movie' ? 'Filme' : heroItem.type === 'series' ? 'Série' : null].filter(Boolean).join('  •  ')}
            </span>
            <span className="cta">
              <PlayIcon width={18} height={18} /> Ver detalhes
            </span>
          </span>
        </button>
      )}

      {continueWatching && continueWatching.items.length > 0 && (
        <Shelf title="Continuar assistindo">
          {continueWatching.items.map((item) => (
            <Poster
              key={item._id}
              name={item.name}
              poster={item.poster}
              shape={item.posterShape}
              progress={item.progress}
              onSelect={() => resumeItem(item)}
              onLongPress={() => setMenuItem(item)}
            />
          ))}
        </Shelf>
      )}
      {menuItem && (
        <Modal title={menuItem.name} onClose={() => setMenuItem(null)}>
          <p className="settings__hint" style={{ margin: '0 0 0.5rem' }}>
            Excluir da lista apaga o ponto de retomada e mantém o item na biblioteca.
          </p>
          <div className="list">
            <button
              type="button"
              className="button button--primary focusable"
              onClick={() => {
                const item = menuItem;
                setMenuItem(null);
                // Details show the info and every available server.
                openDetails({ type: item.type, id: item._id }, videoIdOf(item));
              }}
            >
              Detalhes
            </button>
            <button
              type="button"
              className="button focusable"
              onClick={() => {
                const item = menuItem;
                setMenuItem(null);
                core.removeFromContinueWatching(item._id);
              }}
            >
              Excluir da lista
            </button>
            <button type="button" className="button focusable" onClick={() => setMenuItem(null)}>
              Cancelar
            </button>
          </div>
        </Modal>
      )}

      {!board && <LoadingState label="Carregando catálogos…" />}
      {board?.catalogs.map((catalog) => (
        <CatalogShelf
          key={`${catalog.addon.manifest.id}:${catalog.type}:${catalog.id}`}
          catalog={catalog}
          onOpen={(item) => openDetails(item)}
          onSeeAll={() => {
            const request = parseDiscoverDeepLink(catalog.deepLinks.discover);
            if (!request) return;
            core.loadDiscover(request);
            openCatalogTab(request);
          }}
        />
      ))}
      <div ref={sentinel} aria-hidden="true" style={{ height: 1 }} />
    </main>
  );
}

type ContinueItem = ContinueWatchingPreview['items'][number];

function videoIdOf(item: ContinueItem): string | null {
  return item.state?.videoId ?? item.state?.video_id ?? null;
}

/**
 * A tap resumes on the server used last time (for a new episode, the same server as before); the
 * server list only shows when nothing is remembered or that server no longer has the video.
 */
function resumeItem(item: ContinueItem) {
  const videoId = videoIdOf(item);
  const memory = recallStream(item.type, item._id, videoId ?? item._id);
  navigate({ name: 'detail', type: item.type, id: item._id, videoId, resume: memory !== null });
}

function findHero(contents: (import('../../services/stremio/types').Loadable<MetaItemPreview[], string> | null)[]): MetaItemPreview | null {
  for (const content of contents) {
    if (content?.type === 'Ready') {
      const withArt = content.content.find((item) => item.background);
      if (withArt) return withArt;
    }
  }
  return null;
}
