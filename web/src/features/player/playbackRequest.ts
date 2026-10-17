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
}

let current: PlaybackRequest | null = null;

export function setPlaybackRequest(request: PlaybackRequest | null) {
  current = request;
}

export function getPlaybackRequest(): PlaybackRequest | null {
  return current;
}
