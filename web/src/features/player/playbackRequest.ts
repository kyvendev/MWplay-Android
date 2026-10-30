import type { ResourceRequest, Stream } from '../../services/stremio/types';

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
