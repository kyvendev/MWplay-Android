import { useEffect, useRef } from 'react';
import { Poster, typeLabel } from '../../components/Poster';
import { EmptyState, LoadingState } from '../../components/States';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import type { LibrarySort } from '../../services/stremio/types';
import { openDetails } from '../home/HomePage';

const SORT_LABELS: Record<LibrarySort, string> = {
  lastwatched: 'Assistidos recentemente',
  name: 'Nome (A–Z)',
  namereverse: 'Nome (Z–A)',
  timeswatched: 'Mais assistidos',
  watched: 'Assistidos',
  notwatched: 'Não assistidos',
};

/** The user's Stremio library (synced by the core with the account), with type and sort filters. */
export function LibraryPage() {
  const { core } = useCore();
  const library = useCoreState('library');
  const requested = useRef(false);

  useEffect(() => {
    if (!library || requested.current) return;
    requested.current = true;
    if (!library.selected) core.loadLibrary(null);
  }, [core, library]);

  const selectedType = library?.selected?.request.type ?? null;
  const selectedSort = library?.selected?.request.sort ?? 'lastwatched';

  return (
    <main className="page">
      <h1 className="page-title">Biblioteca</h1>
      {library && (
        <div className="chips" role="toolbar" aria-label="Filtros da biblioteca">
          {library.selectable.types.map((type) => (
            <button
              key={type.type ?? 'all'}
              type="button"
              className="chip focusable"
              aria-pressed={type.selected}
              onClick={() => core.loadLibrary(type.type, selectedSort)}
            >
              {type.type ? typeLabel(type.type) : 'Todos'}
            </button>
          ))}
          <select
            className="select focusable"
            aria-label="Ordenar"
            value={selectedSort}
            onChange={(event) => core.loadLibrary(selectedType, event.target.value as LibrarySort)}
          >
            {library.selectable.sorts.map(({ sort }) => (
              <option key={sort} value={sort}>
                {SORT_LABELS[sort] ?? sort}
              </option>
            ))}
          </select>
        </div>
      )}
      {!library && <LoadingState />}
      {library && library.catalog.length === 0 && <EmptyState>Adicione filmes e séries pela tela de detalhes para montar sua biblioteca.</EmptyState>}
      <div className="poster-grid">
        {library?.catalog.map((item) => (
          <Poster key={item._id} name={item.name} poster={item.poster} progress={item.progress} onSelect={() => openDetails({ type: item.type, id: item._id })} />
        ))}
      </div>
      {library?.selectable.nextPage && (
        <div style={{ padding: '0 var(--gutter)' }}>
          <button type="button" className="button focusable" onClick={() => core.loadLibraryNextPage()}>
            Carregar mais
          </button>
        </div>
      )}
    </main>
  );
}
