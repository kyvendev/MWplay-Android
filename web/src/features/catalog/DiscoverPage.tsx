import { useEffect, useRef } from 'react';
import { Poster, PosterSkeleton, typeLabel } from '../../components/Poster';
import { EmptyState, ErrorState } from '../../components/States';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { parseDiscoverDeepLink } from '../../services/stremio/streams';
import { openDetails } from '../home/HomePage';

/** Addon catalogs with type, catalog and genre filters, as on the native Discover screen. */
export function DiscoverPage() {
  const { core } = useCore();
  const discover = useCoreState('discover');
  const requested = useRef(false);
  const sentinel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!discover || requested.current) return;
    requested.current = true;
    if (!discover.selected) core.loadDiscover(null);
  }, [core, discover]);

  const select = (deepLink: string) => {
    const request = parseDiscoverDeepLink(deepLink);
    if (request) core.loadDiscover(request);
  };

  const content = discover?.catalog?.content ?? null;
  const items = content?.type === 'Ready' ? content.content : [];
  const nextPage = discover?.selectable.nextPage ?? false;

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

  return (
    <main className="page">
      <h1 className="page-title">Descobrir</h1>
      {discover && (
        <>
          <div className="chips" role="toolbar" aria-label="Tipo">
            {discover.selectable.types.map((type) => (
              <button key={type.type} type="button" className="chip focusable" aria-pressed={type.selected} onClick={() => select(type.deepLinks.discover)}>
                {typeLabel(type.type)}
              </button>
            ))}
          </div>
          {catalogs.length > 1 && (
            <div className="chips" role="toolbar" aria-label="Catálogo">
              {catalogs.map((catalog) => (
                <button
                  key={`${catalog.addon.manifest.id}:${catalog.id}`}
                  type="button"
                  className="chip focusable"
                  aria-pressed={catalog.selected}
                  onClick={() => select(catalog.deepLinks.discover)}
                >
                  {catalogLabel(catalog, catalogs)}
                </button>
              ))}
            </div>
          )}
          {genre && genre.options.length > 1 && (
            <div className="chips" role="toolbar" aria-label="Gênero">
              {genre.options.map((option) => (
                <button key={option.value ?? 'all'} type="button" className="chip focusable" aria-pressed={option.selected} onClick={() => select(option.deepLinks.discover)}>
                  {option.value ?? 'Todos'}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      {content?.type === 'Err' && <ErrorState message="Este catálogo não respondeu. Escolha outro ou tente novamente." onRetry={() => core.loadDiscover(discover?.selected?.request ?? null)} />}
      {content?.type === 'Ready' && items.length === 0 && <EmptyState>Nenhum item neste catálogo.</EmptyState>}
      <div className="poster-grid">
        {items.map((item) => (
          <Poster key={`${item.type}:${item.id}`} name={item.name} poster={item.poster} onSelect={() => openDetails(item)} />
        ))}
        {(!discover || !content || content.type === 'Loading') && Array.from({ length: 12 }, (_, index) => <PosterSkeleton key={index} />)}
      </div>
      <div ref={sentinel} aria-hidden="true" style={{ height: 1 }} />
    </main>
  );
}

/** Catalogs sharing a name are numbered instead of naming their source. */
function catalogLabel(catalog: { id: string; name: string; addon: { manifest: { id: string } } }, all: { id: string; name: string; addon: { manifest: { id: string } } }[]): string {
  const same = all.filter((other) => other.name === catalog.name);
  if (same.length < 2) return catalog.name;
  const index = same.findIndex((other) => other.id === catalog.id && other.addon.manifest.id === catalog.addon.manifest.id);
  return `${catalog.name} ${index + 1}`;
}
