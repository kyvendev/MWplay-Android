import { useEffect, useRef } from 'react';
import { Poster, PosterSkeleton } from '../../components/Poster';
import { EmptyState, ErrorState } from '../../components/States';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { parseDiscoverDeepLink } from '../../services/stremio/streams';
import type { Discover, ResourceRequest } from '../../services/stremio/types';
import { catalogName, extraOptionName, isTvType, preferredTvCatalog, typeName } from '../../shared/contentLabels';
import { navigate } from '../../app/router';
import { openDetails } from '../home/HomePage';

type Mode = 'discover' | 'tv';

/**
 * Discover and TV share the core's discover model (like the apps): each tab remembers the
 * catalog it showed last, and the TV tab opens on the preferred provider's TV catalog once.
 */
const lastRequest: Record<Mode, ResourceRequest | null> = { discover: null, tv: null };
let tvCatalogChosen = false;

/** Opens a catalog (e.g. "Ver tudo" on Início) on the tab it belongs to. */
export function openCatalogTab(request: ResourceRequest) {
  const mode: Mode = isTvType(request.path.type) ? 'tv' : 'discover';
  lastRequest[mode] = request;
  navigate({ name: mode });
}

/** Test hook: forget the remembered catalogs. */
export function resetCatalogTabs() {
  lastRequest.discover = null;
  lastRequest.tv = null;
  tvCatalogChosen = false;
}

/** Addon catalogs with type, catalog and genre filters, as on the native Discover screen. */
export function DiscoverPage() {
  return <CatalogBrowser mode="discover" />;
}

/** TV channels from the addons' "tv" catalogs (Best Cine first). */
export function TvPage() {
  return <CatalogBrowser mode="tv" />;
}

function CatalogBrowser({ mode }: { mode: Mode }) {
  const { core } = useCore();
  const discover = useCoreState('discover');
  const sentinel = useRef<HTMLDivElement>(null);
  const lastLoad = useRef<string | null>(null);
  const isTv = mode === 'tv';

  const selectedRequest = discover?.selected?.request ?? null;
  const onThisTab = selectedRequest !== null && isTvType(selectedRequest.path.type) === isTv;
  const types = discover?.selectable.types ?? [];
  const tvType = types.find((type) => isTvType(type.type));

  const load = (request: ResourceRequest | null) => {
    const key = JSON.stringify(request);
    if (lastLoad.current === key) return;
    lastLoad.current = key;
    core.loadDiscover(request);
  };

  const select = (deepLink: string) => {
    const request = parseDiscoverDeepLink(deepLink);
    if (!request) return;
    lastRequest[mode] = request;
    load(request);
  };

  // Make the shared model show this tab's catalogs.
  useEffect(() => {
    if (!discover || onThisTab) return;
    const remembered = lastRequest[mode];
    if (remembered) return load(remembered);
    if (types.length === 0) {
      // Nothing loaded yet: let the core pick a default so the addons' types become known.
      if (!discover.selected) load(null);
      return;
    }
    if (isTv) {
      if (tvType) load(parseDiscoverDeepLink(tvType.deepLinks.discover));
      return;
    }
    const preferred = types.find((type) => type.type === 'movie') ?? types.find((type) => !isTvType(type.type));
    if (preferred) load(parseDiscoverDeepLink(preferred.deepLinks.discover));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [discover === null, onThisTab, types.length, tvType?.deepLinks.discover, mode]);

  // First visit to TV: switch to the preferred provider's catalog (Best Cine), once.
  useEffect(() => {
    if (!isTv || !onThisTab || !discover || tvCatalogChosen || lastRequest.tv) return;
    const catalogs = discover.selectable.catalogs.filter((catalog) => isTvType(catalog.type));
    if (catalogs.length === 0) return;
    tvCatalogChosen = true;
    const preferred = preferredTvCatalog(catalogs, (catalog) => catalog.name, (catalog) => catalog.addon.transportUrl ?? '', (catalog) => catalog.addon.manifest.name);
    if (preferred && !preferred.selected) select(preferred.deepLinks.discover);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isTv, onThisTab, discover?.selectable.catalogs.length]);

  const content = onThisTab ? discover?.catalog?.content ?? null : null;
  const items = content?.type === 'Ready' ? content.content : [];
  const nextPage = onThisTab && (discover?.selectable.nextPage ?? false);

  useEffect(() => {
    const node = sentinel.current;
    if (!node || !nextPage || content?.type !== 'Ready') return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) core.loadDiscoverNextPage();
    }, { rootMargin: '800px 0px' });
    observer.observe(node);
    return () => observer.disconnect();
  }, [content, core, nextPage]);

  const selectedType = discover?.selectable.types.find((type) => type.selected)?.type;
  const catalogs = discover?.selectable.catalogs.filter((catalog) => !selectedType || catalog.type === selectedType) ?? [];
  const genre = discover?.selectable.extra.find((extra) => extra.name === 'genre');
  const noTvCatalogs = isTv && discover !== null && types.length > 0 && !tvType;

  return (
    <main className="page">
      <h1 className="page-title">{isTv ? 'TV' : 'Descobrir'}</h1>
      {discover && onThisTab && <Filters discover={discover} isTv={isTv} catalogs={catalogs} genre={genre} onSelect={select} />}

      {noTvCatalogs && <EmptyState>Nenhum canal de TV disponível. Os canais aparecem aqui quando um addon da sua conta oferece catálogos de TV.</EmptyState>}
      {content?.type === 'Err' && <ErrorState message="Este catálogo não respondeu. Escolha outro ou tente novamente." onRetry={() => core.loadDiscover(discover?.selected?.request ?? null)} />}
      {content?.type === 'Ready' && items.length === 0 && <EmptyState>Nenhum item neste catálogo.</EmptyState>}
      <div className="poster-grid">
        {items.map((item) => (
          <Poster key={`${item.type}:${item.id}`} name={item.name} poster={item.poster} onSelect={() => openDetails(item)} />
        ))}
        {!noTvCatalogs && (!discover || !content || content.type === 'Loading') && Array.from({ length: 12 }, (_, index) => <PosterSkeleton key={index} />)}
      </div>
      <div ref={sentinel} aria-hidden="true" style={{ height: 1 }} />
    </main>
  );
}

function Filters({
  discover,
  isTv,
  catalogs,
  genre,
  onSelect,
}: {
  discover: Discover;
  isTv: boolean;
  catalogs: Discover['selectable']['catalogs'];
  genre: Discover['selectable']['extra'][number] | undefined;
  onSelect: (deepLink: string) => void;
}) {
  // TV has its own tab: Discover lists every other type; the TV tab needs no type filter.
  const types = isTv ? [] : discover.selectable.types.filter((type) => !isTvType(type.type));
  return (
    <>
      {types.length > 0 && (
        <div className="chips" role="toolbar" aria-label="Tipo">
          {types.map((type) => (
            <button key={type.type} type="button" className="chip focusable" aria-pressed={type.selected} onClick={() => onSelect(type.deepLinks.discover)}>
              {typeName(type.type)}
            </button>
          ))}
        </div>
      )}
      {catalogs.length > 1 && (
        <div className="chips" role="toolbar" aria-label="Catálogo">
          {catalogs.map((catalog) => (
            <button
              key={`${catalog.addon.manifest.id}:${catalog.id}`}
              type="button"
              className="chip focusable"
              aria-pressed={catalog.selected}
              onClick={() => onSelect(catalog.deepLinks.discover)}
            >
              {catalogLabel(catalog, catalogs)}
            </button>
          ))}
        </div>
      )}
      {genre && genre.options.length > 1 && (
        <div className="chips" role="toolbar" aria-label="Gênero">
          {genre.options.map((option) => (
            <button key={option.value ?? 'all'} type="button" className="chip focusable" aria-pressed={option.selected} onClick={() => onSelect(option.deepLinks.discover)}>
              {extraOptionName('genre', option.value)}
            </button>
          ))}
        </div>
      )}
    </>
  );
}

/** Catalogs sharing a name are numbered instead of naming their source. */
function catalogLabel(catalog: { id: string; name: string; addon: { manifest: { id: string } } }, all: { id: string; name: string; addon: { manifest: { id: string } } }[]): string {
  const same = all.filter((other) => other.name === catalog.name);
  if (same.length < 2) return catalogName(catalog.name);
  const index = same.findIndex((other) => other.id === catalog.id && other.addon.manifest.id === catalog.addon.manifest.id);
  return `${catalogName(catalog.name)} ${index + 1}`;
}
