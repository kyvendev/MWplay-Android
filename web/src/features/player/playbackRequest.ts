import { streamKind } from '../../services/stremio/streams';
import type { MetaDetails, ResourcePath, ResourceRequest, Stream } from '../../services/stremio/types';

/**
 * What the details screen hands to the player: the chosen stream plus the requests the core needs
 * to track progress and resolve the next episode (CoreStream on Android).
 */
export interface PlaybackRequest {
  stream: Stream;
  streamRequest: ResourceRequest | null;
  metaRequest: ResourceRequest | null;
  title: string;
  subtitle: string | null;
  backdrop: string | null;
  /** Details route to return to when the player closes. */
  returnTo: { type: string; id: string; videoId: string | null };
  /** The chosen stream as a source-switch candidate; without it the switch is off. */
  candidate?: PlaybackCandidate;
  /** Every stream of the title, for the automatic switch when a source does not start. */
  alternatives?: PlaybackCandidate[];
  /** Torrents play here (a streaming server is running). */
  torrentsAvailable?: boolean;
}

/** One stream of the title as the automatic source switch sees it. */
export interface PlaybackCandidate {
  key: string;
  /** Addon name, matched against the registered order (Torrentio, FrostStream, BestCine). */
  addon: string;
  stream: Stream;
  streamRequest: ResourceRequest | null;
  /** This browser can play it (HTTP, or torrents with a streaming server). */
  playable: boolean;
  /** Position in the addon's own list. */
  order: number;
}

let current: PlaybackRequest | null = null;

export function setPlaybackRequest(request: PlaybackRequest | null) {
  current = request;
}

export function getPlaybackRequest(): PlaybackRequest | null {
  return current;
}

/** Plays inside the web player: HTTP streams, or torrents/YouTube with a streaming server. */
export function playableInBrowser(stream: Stream, torrentsAvailable: boolean): boolean {
  const kind = streamKind(stream);
  return kind === 'http' || ((kind === 'torrent' || kind === 'youtube') && torrentsAvailable);
}

/**
 * Every ready stream of the details screen's video as switch candidates. Empty when the core
 * shows another video (never another episode's streams).
 */
export function candidatesFrom(details: MetaDetails | null, streamPath: ResourcePath | null, torrentsAvailable: boolean): PlaybackCandidate[] {
  if (!details || !streamPath) return [];
  const shown = details.selected?.streamPath;
  if (!shown || shown.id !== streamPath.id || shown.type !== streamPath.type) return [];
  return details.streams.flatMap((group) =>
    group.content.type === 'Ready'
      ? group.content.content.map((stream, order) => ({
          key: `${group.addon.transportUrl ?? group.addon.manifest.id}|${order}`,
          addon: group.addon.manifest.name,
          stream,
          streamRequest: group.addon.transportUrl ? { base: group.addon.transportUrl, path: streamPath } : null,
          playable: playableInBrowser(stream, torrentsAvailable),
          order,
        }))
      : [],
  );
}
