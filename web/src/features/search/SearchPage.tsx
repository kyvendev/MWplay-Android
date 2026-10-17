import { useEffect, useRef, useState } from 'react';
import { navigate } from '../../app/router';
import { useBackHandler } from '../../app/back';
import { CatalogShelf } from '../../components/Poster';
import { BackIcon } from '../../components/icons';
import { EmptyState } from '../../components/States';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { openDetails } from '../home/HomePage';

const DEBOUNCE_MS = 450;
/** Search catalogs from every installed addon load in this range. */
const SEARCH_RANGE = 30;

export function SearchPage({ initialQuery }: { initialQuery: string }) {
  const { core } = useCore();
  const search = useCoreState('search');
  const [query, setQuery] = useState(initialQuery);
  const input = useRef<HTMLInputElement>(null);
  const lastSearched = useRef<string | null>(null);

  useBackHandler(() => {
    navigate({ name: 'home' });
    return true;
  });

  useEffect(() => {
    input.current?.focus();
  }, []);

  // Debounced: one core search per pause in typing, never a request per keystroke.
  useEffect(() => {
    const trimmed = query.trim();
    const timer = window.setTimeout(() => {
      if (!trimmed || trimmed === lastSearched.current) return;
      lastSearched.current = trimmed;
      navigate({ name: 'search', query: trimmed }, { replace: true });
      core.loadSearch(trimmed).then(() => core.loadSearchRange(0, SEARCH_RANGE));
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [core, query]);

  const searched = search?.selected?.extra.find(([name]) => name === 'search')?.[1] ?? null;
  const showResults = Boolean(searched) && searched === query.trim();
  const catalogs = showResults ? search?.catalogs ?? [] : [];
  const allSettled = catalogs.length > 0 && catalogs.every((catalog) => catalog.content && catalog.content.type !== 'Loading');
  const anyResults = catalogs.some((catalog) => catalog.content?.type === 'Ready' && catalog.content.content.length > 0);

  return (
    <main className="page">
      <form
        className="top-bar"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          input.current?.blur();
        }}
      >
        <button type="button" className="icon-button focusable" aria-label="Voltar" onClick={() => navigate({ name: 'home' })}>
          <BackIcon />
        </button>
        <input
          ref={input}
          className="field focusable"
          type="search"
          enterKeyHint="search"
          placeholder="Buscar filmes, séries, anime…"
          aria-label="Buscar"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </form>
      {!query.trim() && <EmptyState>Digite o nome de um filme ou série.</EmptyState>}
      {catalogs.map((catalog) => (
        <CatalogShelf key={`${catalog.addon.manifest.id}:${catalog.type}:${catalog.id}`} catalog={catalog} onOpen={(item) => openDetails(item)} />
      ))}
      {showResults && allSettled && !anyResults && <EmptyState>Nenhum resultado para “{query.trim()}”.</EmptyState>}
    </main>
  );
}
