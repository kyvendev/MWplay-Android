import { useEffect, useMemo, useState } from 'react';
import { navigate } from '../../app/router';
import { useBackHandler } from '../../app/back';
import { BackIcon, CheckIcon, PlayIcon, PlusIcon } from '../../components/icons';
import { EmptyState, ErrorState, LoadingState, Spinner } from '../../components/States';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { episodeLabel, metaLinks, streamKind, streamLabel } from '../../services/stremio/streams';
import type { MetaItem, MetaItemPreview, Stream, StreamsGroup, Video } from '../../services/stremio/types';
import { setPlaybackRequest } from '../player/playbackRequest';

interface Props {
  type: string;
  id: string;
  videoId: string | null;
}

export function DetailsPage({ type, id, videoId }: Props) {
  const { core } = useCore();
  const details = useCoreState('meta_details');
  const server = useCoreState('streaming_server');

  useBackHandler(() => {
    window.history.length > 1 ? window.history.back() : navigate({ name: 'home' });
    return true;
  });

  // One load per title/episode; revisiting the same route reuses the core's state.
  useEffect(() => {
    const selected = details?.selected;
    if (!details) return;
    const sameMeta = selected?.metaPath.type === type && selected?.metaPath.id === id;
    const expectedStream = videoId ?? (type === 'movie' ? id : null);
    const sameStream = (selected?.streamPath?.id ?? null) === expectedStream;
    if (!sameMeta || !sameStream) core.loadMetaDetails(type, id, videoId);
  }, [core, type, id, videoId, details === null]);

  const metaContent = details?.metaItem?.content;
  const meta: MetaItem | null = metaContent?.type === 'Ready' ? metaContent.content : null;
  const isCurrent = details?.selected?.metaPath.id === id;

  if (!details || !isCurrent || !details.metaItem || metaContent?.type === 'Loading') {
    return (
      <main className="page">
        <DetailsTopBar />
        <LoadingState label="Carregando detalhes…" />
      </main>
    );
  }
  if (!meta) {
    return (
      <main className="page">
        <DetailsTopBar />
        <ErrorState message="Não foi possível carregar os detalhes deste título." onRetry={() => core.loadMetaDetails(type, id, videoId)} />
      </main>
    );
  }

  const links = metaLinks(meta);
  const isSeries = meta.videos.length > 0 && type !== 'movie';
  const torrentsAvailable = Boolean(server?.baseUrl) && server?.settings.type === 'Ready';

  const play = (stream: Stream, group: StreamsGroup) => {
    const streamPath = details.selected?.streamPath ?? null;
    const video = meta.videos.find((item) => item.id === streamPath?.id);
    setPlaybackRequest({
      stream,
      streamRequest: streamPath && group.addon.transportUrl ? { base: group.addon.transportUrl, path: streamPath } : null,
      metaRequest: details.metaItem?.addon.transportUrl && details.selected ? { base: details.metaItem.addon.transportUrl, path: details.selected.metaPath } : null,
      title: meta.name,
      subtitle: video ? [episodeLabel(video.season, video.episode), video.title].filter(Boolean).join(' — ') : null,
      backdrop: meta.background ?? meta.poster ?? null,
      returnTo: { type, id, videoId },
    });
    navigate({ name: 'player' });
  };

  return (
    <main className="details">
      {(meta.background || meta.poster) && (
        <div className="details__backdrop" aria-hidden="true">
          <img src={meta.background ?? meta.poster!} alt="" />
        </div>
      )}
      <div className="details__body">
        <DetailsTopBar />
        {meta.logo ? <img className="details__logo" src={meta.logo} alt={meta.name} /> : null}
        <h1 className={meta.logo ? 'visually-hidden' : 'details__title'}>{meta.name}</h1>
        <div className="meta-line">
          {links.imdbRating && <span className="imdb">IMDb {links.imdbRating}</span>}
          {[meta.releaseInfo, meta.runtime, type === 'movie' ? 'Filme' : type === 'series' ? 'Série' : null].filter(Boolean).join('  •  ')}
        </div>
        {links.genres.length > 0 && <div className="settings__hint">{links.genres.slice(0, 4).join('  ·  ')}</div>}
        {meta.description && <p className="details__description">{meta.description}</p>}
        <div className="details__actions">
          <LibraryButton meta={meta} />
        </div>
        {links.cast.length > 0 && <p className="settings__hint">Elenco: {links.cast.slice(0, 5).join(', ')}</p>}

        {isSeries && <Episodes videos={meta.videos} selectedVideoId={details.selected?.streamPath?.id ?? null} onSelect={(video) => navigate({ name: 'detail', type, id, videoId: video.id }, { replace: true })} />}

        {(!isSeries || details.selected?.streamPath) && (
          <Streams groups={details.streams} torrentsAvailable={torrentsAvailable} onPlay={play} episode={isSeries ? meta.videos.find((video) => video.id === details.selected?.streamPath?.id) ?? null : null} />
        )}
      </div>
    </main>
  );
}

function DetailsTopBar() {
  return (
    <div className="top-bar" style={{ padding: 0 }}>
      <button type="button" className="icon-button focusable" aria-label="Voltar" onClick={() => (window.history.length > 1 ? window.history.back() : navigate({ name: 'home' }))}>
        <BackIcon />
      </button>
    </div>
  );
}

function LibraryButton({ meta }: { meta: MetaItem }) {
  const { core } = useCore();
  const preview: MetaItemPreview = useMemo(() => {
    const { id, type, name, poster, background, logo, description, releaseInfo, runtime, released, posterShape, links } = meta;
    return { id, type, name, poster, background, logo, description, releaseInfo, runtime, released, posterShape, links };
  }, [meta]);
  return meta.inLibrary ? (
    <button type="button" className="button focusable" onClick={() => core.removeFromLibrary(meta.id)}>
      <CheckIcon /> Na minha lista
    </button>
  ) : (
    <button type="button" className="button focusable" onClick={() => core.addToLibrary(preview)}>
      <PlusIcon /> Minha lista
    </button>
  );
}

function Episodes({ videos, selectedVideoId, onSelect }: { videos: Video[]; selectedVideoId: string | null; onSelect: (video: Video) => void }) {
  const seasons = useMemo(() => {
    const values = Array.from(new Set(videos.map((video) => video.season ?? 0)));
    // Specials (season 0) go last, as in the apps.
    return values.sort((a, b) => (a === 0 ? 1 : b === 0 ? -1 : a - b));
  }, [videos]);
  const initialSeason = videos.find((video) => video.id === selectedVideoId)?.season ?? seasons[0] ?? 0;
  const [season, setSeason] = useState(initialSeason);
  const episodes = videos.filter((video) => (video.season ?? 0) === season).sort((a, b) => (a.episode ?? 0) - (b.episode ?? 0));
  return (
    <section aria-label="Episódios">
      <h2 className="section-label">Episódios</h2>
      {seasons.length > 1 && (
        <div className="chips" style={{ padding: '0.2rem 0 0.6rem' }} role="toolbar" aria-label="Temporadas">
          {seasons.map((value) => (
            <button key={value} type="button" className="chip focusable" aria-pressed={value === season} onClick={() => setSeason(value)}>
              {value === 0 ? 'Especiais' : `Temporada ${value}`}
            </button>
          ))}
        </div>
      )}
      <div className="list">
        {episodes.map((video) => (
          <button key={video.id} type="button" className="row-card focusable" aria-current={video.id === selectedVideoId ? 'true' : undefined} onClick={() => onSelect(video)}>
            <span className="row-card__thumb">{video.thumbnail ? <img src={video.thumbnail} alt="" loading="lazy" /> : `E${video.episode ?? ''}`}</span>
            <span className="row-card__text">
              <span className="row-card__title">
                {video.episode !== undefined ? `E${video.episode}. ` : ''}
                {video.title || 'Episódio'}
              </span>
              {video.released && <span className="row-card__detail">{new Date(video.released).toLocaleDateString('pt-BR')}</span>}
            </span>
            {video.watched && <CheckIcon width={18} height={18} aria-label="Assistido" />}
          </button>
        ))}
      </div>
    </section>
  );
}

function Streams({
  groups,
  torrentsAvailable,
  onPlay,
  episode,
}: {
  groups: StreamsGroup[];
  torrentsAvailable: boolean;
  onPlay: (stream: Stream, group: StreamsGroup) => void;
  episode: Video | null;
}) {
  const loading = groups.some((group) => group.content.type === 'Loading');
  const playableHere = (stream: Stream) => {
    const kind = streamKind(stream);
    return kind === 'http' || kind === 'external' || ((kind === 'torrent' || kind === 'youtube') && torrentsAvailable);
  };
  // Addon order is kept, but sources this browser can play come before the ones it cannot.
  const ready = groups
    .flatMap((group) => (group.content.type === 'Ready' ? group.content.content.map((stream) => ({ stream, group })) : []))
    .map((entry, index) => ({ ...entry, index, playable: playableHere(entry.stream) }))
    .sort((a, b) => Number(b.playable) - Number(a.playable) || a.index - b.index);
  return (
    <section aria-label="Fontes">
      <h2 className="section-label">{episode ? `Fontes · ${episodeLabel(episode.season, episode.episode) ?? episode.title}` : 'Fontes'}</h2>
      {loading && (
        <div className="settings__hint" style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', margin: '0.5rem 0' }}>
          <Spinner /> Buscando fontes nos seus addons…
        </div>
      )}
      {!loading && ready.length === 0 && <EmptyState>Nenhuma fonte encontrada nos addons instalados para este título.</EmptyState>}
      <div className="list">
        {ready.map(({ stream, group }, index) => {
          const kind = streamKind(stream);
          const label = streamLabel(stream);
          const blocked = kind === 'unsupported' || ((kind === 'torrent' || kind === 'youtube') && !torrentsAvailable);
          const note =
            kind === 'torrent' || kind === 'youtube'
              ? torrentsAvailable
                ? null
                : 'Requer o servidor de streaming (veja Configurações)'
              : kind === 'external'
                ? 'Abre fora do MW Play'
                : kind === 'unsupported'
                  ? 'Formato não suportado no navegador'
                  : stream.behaviorHints?.notWebReady
                    ? 'O addon indica que pode não reproduzir no navegador'
                    : null;
          return (
            <button
              key={`${group.addon.manifest.id}:${index}`}
              type="button"
              className="row-card focusable"
              disabled={blocked}
              style={blocked ? { opacity: 0.55 } : undefined}
              onClick={() => {
                if (kind === 'external') {
                  const href = stream.externalUrl ?? stream.playerFrameUrl;
                  if (href) window.open(href, '_blank', 'noopener,noreferrer');
                  return;
                }
                onPlay(stream, group);
              }}
            >
              <span className="play-dot" aria-hidden="true">
                <PlayIcon width={18} height={18} />
              </span>
              <span className="row-card__text">
                <span className="row-card__title">
                  <span className="tag">{group.addon.manifest.name}</span>
                  {label.title}
                </span>
                {label.detail && <span className="row-card__detail">{label.detail}</span>}
                {note && <span className="row-card__detail" style={{ color: 'var(--warning)' }}>{note}</span>}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
