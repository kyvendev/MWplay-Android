import { useState, type ReactNode } from 'react';
import type { CatalogResource, Loadable, MetaItemPreview, PosterShape } from '../services/stremio/types';
import { ChevronRightIcon } from './icons';
import { ErrorState } from './States';

interface PosterProps {
  name: string;
  poster?: string | null;
  shape?: PosterShape;
  progress?: number | null;
  onSelect: () => void;
}

/** Artwork tile; the title stays visible until (or instead of) the image, like the native posters. */
export function Poster({ name, poster, shape = 'poster', progress, onSelect }: PosterProps) {
  const [failed, setFailed] = useState(false);
  const showImage = Boolean(poster) && !failed;
  return (
    <button type="button" className={`poster focusable ${shape !== 'poster' ? `poster--${shape}` : ''}`} onClick={onSelect} aria-label={name}>
      <span className="poster__fallback" aria-hidden="true">
        {name}
      </span>
      {showImage && <img src={poster!} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />}
      {typeof progress === 'number' && progress > 0 && (
        <span className="poster__progress" aria-hidden="true">
          <span style={{ width: `${Math.min(100, Math.max(0, progress * 100))}%` }} />
        </span>
      )}
    </button>
  );
}

export function PosterSkeleton({ shape = 'poster' }: { shape?: PosterShape }) {
  return <div className={`poster skeleton ${shape !== 'poster' ? `poster--${shape}` : ''}`} aria-hidden="true" />;
}

interface ShelfProps {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}

export function Shelf({ title, action, children }: ShelfProps) {
  return (
    <section className="shelf" aria-label={title}>
      <div className="shelf__header">
        <h2 className="shelf__title">{title}</h2>
        {action}
      </div>
      <div className="shelf__row">{children}</div>
    </section>
  );
}

/** One addon catalog row with loading skeletons, empty and error states. */
export function CatalogShelf({
  catalog,
  onOpen,
  onSeeAll,
}: {
  catalog: CatalogResource;
  onOpen: (item: MetaItemPreview) => void;
  onSeeAll?: () => void;
}) {
  const content: Loadable<MetaItemPreview[], string> | null = catalog.content;
  const title = catalog.type ? `${catalog.name} · ${typeLabel(catalog.type)}` : catalog.name;
  if (content?.type === 'Err') {
    return (
      <section className="shelf" aria-label={title}>
        <div className="shelf__header">
          <h2 className="shelf__title">{title}</h2>
        </div>
        <ErrorState message={`Não foi possível carregar este catálogo (${catalog.addon.manifest.name}).`} />
      </section>
    );
  }
  if (content?.type === 'Ready' && content.content.length === 0) return null;
  return (
    <Shelf
      title={title}
      action={
        onSeeAll && content?.type === 'Ready' ? (
          <button type="button" className="pill-button focusable" onClick={onSeeAll}>
            Ver tudo <ChevronRightIcon width={16} height={16} />
          </button>
        ) : undefined
      }
    >
      {content?.type === 'Ready'
        ? content.content.map((item) => (
            <Poster key={`${item.type}:${item.id}`} name={item.name} poster={item.poster} shape={item.posterShape} onSelect={() => onOpen(item)} />
          ))
        : Array.from({ length: 8 }, (_, index) => <PosterSkeleton key={index} />)}
    </Shelf>
  );
}

export function typeLabel(type: string): string {
  switch (type) {
    case 'movie':
      return 'Filmes';
    case 'series':
      return 'Séries';
    case 'channel':
      return 'Canais';
    case 'tv':
      return 'TV';
    case 'anime':
      return 'Anime';
    default:
      return type;
  }
}
