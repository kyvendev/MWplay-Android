import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type MouseEvent as ReactMouseEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';
import type { CatalogResource, Loadable, MetaItemPreview, PosterShape } from '../services/stremio/types';
import { ChevronRightIcon } from './icons';
import { ErrorState } from './States';
import { shelfTitle, typeName } from '../shared/contentLabels';

interface PosterProps {
  name: string;
  poster?: string | null;
  shape?: PosterShape;
  progress?: number | null;
  onSelect: () => void;
  /** Hold (touch), right-click (mouse) or hold OK/Enter (TV remote, keyboard). */
  onLongPress?: () => void;
}

const LONG_PRESS_MS = 550;

/** Artwork tile; the title stays visible until (or instead of) the image, like the native posters. */
export function Poster({ name, poster, shape = 'poster', progress, onSelect, onLongPress }: PosterProps) {
  const [failed, setFailed] = useState(false);
  const showImage = Boolean(poster) && !failed;
  const press = useLongPress(onSelect, onLongPress);
  return (
    <button
      type="button"
      className={`poster focusable ${shape !== 'poster' ? `poster--${shape}` : ''} ${onLongPress ? 'poster--menu' : ''}`}
      aria-label={name}
      aria-haspopup={onLongPress ? 'dialog' : undefined}
      {...press}
    >
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
  const title = shelfTitle(catalog.name, catalog.type);
  if (content?.type === 'Err') {
    return (
      <section className="shelf" aria-label={title}>
        <div className="shelf__header">
          <h2 className="shelf__title">{title}</h2>
        </div>
        <ErrorState message="Não foi possível carregar este catálogo." />
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

/** Content type in Portuguese ("Filmes", "Séries", "TV"…). */
export function typeLabel(type: string): string {
  return typeName(type);
}

/**
 * Short press selects, long press opens the menu. Keys are handled here (not by the browser's
 * click on Enter) so a held remote OK never selects as well.
 */
function useLongPress(onSelect: () => void, onLongPress?: () => void) {
  const timer = useRef<number>();
  const fired = useRef(false);
  const keyHeld = useRef(false);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  if (!onLongPress) return { onClick: onSelect };
  const fire = () => {
    if (fired.current) return;
    fired.current = true;
    onLongPress();
  };
  const start = () => {
    fired.current = false;
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(fire, LONG_PRESS_MS);
  };
  const cancel = () => window.clearTimeout(timer.current);
  const isSelectKey = (key: string) => key === 'Enter' || key === ' ';
  return {
    onPointerDown: (event: ReactPointerEvent) => {
      if (event.button === 0) start();
    },
    onPointerUp: cancel,
    onPointerLeave: cancel,
    onPointerCancel: cancel,
    onContextMenu: (event: ReactMouseEvent) => {
      event.preventDefault();
      cancel();
      fire();
    },
    onKeyDown: (event: ReactKeyboardEvent) => {
      if (!isSelectKey(event.key)) return;
      event.preventDefault();
      if (!event.repeat && !keyHeld.current) {
        keyHeld.current = true;
        start();
      }
    },
    onKeyUp: (event: ReactKeyboardEvent) => {
      if (!isSelectKey(event.key) || !keyHeld.current) return;
      event.preventDefault();
      keyHeld.current = false;
      cancel();
      if (!fired.current) onSelect();
      fired.current = false;
    },
    onClick: (event: ReactMouseEvent) => {
      // Keyboard activation is handled above; a click after a long press is swallowed.
      if (event.detail === 0) return;
      if (fired.current) {
        fired.current = false;
        return;
      }
      onSelect();
    },
  };
}
