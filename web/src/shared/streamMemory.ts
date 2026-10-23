import type { Stream } from '../services/stremio/types';

/**
 * The server (addon source) used last time for each title/episode, kept in this browser like
 * LocalStreamSelection in the apps. Only identifying fields are stored, never the stream URL, so
 * tokens are not persisted; the stream itself is fetched again from the addon.
 */
const STORAGE_KEY = 'mwplay.web.lastStreams';
const MAX_ENTRIES = 200;
const LAST_SERVER = '__last_server__';

export interface StreamSelection {
  /** Addon transport URL: identifies the server. */
  server: string;
  name: string | null;
  description: string | null;
  /** Hash of the source (URL / infoHash) so the exact same source is found again. */
  sourceKey: string;
}

type Store = Record<string, StreamSelection & { at: number }>;

function sourceKeyOf(stream: Stream): string {
  const raw = stream.url ?? (stream.infoHash ? `${stream.infoHash}:${stream.fileIdx ?? ''}` : stream.ytId ?? stream.externalUrl ?? '');
  let hash = 0;
  for (let index = 0; index < raw.length; index++) hash = (Math.imul(31, hash) + raw.charCodeAt(index)) | 0;
  return `${raw.length}:${hash >>> 0}`;
}

export function selectionOf(stream: Stream, server: string): StreamSelection {
  return { server, name: stream.name ?? null, description: stream.description ?? null, sourceKey: sourceKeyOf(stream) };
}

const keyOf = (type: string, id: string, videoId: string) => `${type}|${id}|${videoId}`;

function read(): Store {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    return parsed && typeof parsed === 'object' ? (parsed as Store) : {};
  } catch {
    return {};
  }
}

function write(store: Store) {
  const entries = Object.entries(store).sort((a, b) => b[1].at - a[1].at).slice(0, MAX_ENTRIES);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(entries)));
  } catch {
    // Private mode: resume simply falls back to the server list.
  }
}

export function rememberStream(type: string, id: string, videoId: string, selection: StreamSelection) {
  const store = read();
  const at = Date.now();
  store[keyOf(type, id, videoId)] = { ...selection, at };
  store[keyOf(type, id, LAST_SERVER)] = { ...selection, at };
  write(store);
}

/** The exact selection for this episode, or (series) the server used for another episode. */
export function recallStream(type: string, id: string, videoId: string): { selection: StreamSelection; exact: boolean } | null {
  const store = read();
  const exact = store[keyOf(type, id, videoId)];
  if (exact) return { selection: exact, exact: true };
  const last = type !== 'movie' ? store[keyOf(type, id, LAST_SERVER)] : undefined;
  return last ? { selection: last, exact: false } : null;
}

/**
 * Picks the stream to resume with: the same source (or same server/name/description) for an
 * exact match; for a new episode, a stream from the same server preferring the same name.
 */
export function pickRemembered<T extends { stream: Stream; server: string }>(candidates: T[], memory: { selection: StreamSelection; exact: boolean }): T | null {
  const { selection, exact } = memory;
  const sameServer = candidates.filter((candidate) => candidate.server === selection.server);
  if (exact) {
    return (
      sameServer.find((candidate) => sourceKeyOf(candidate.stream) === selection.sourceKey) ??
      sameServer.find((candidate) => (candidate.stream.name ?? null) === selection.name && (candidate.stream.description ?? null) === selection.description) ??
      null
    );
  }
  return sameServer.find((candidate) => (candidate.stream.name ?? null) === selection.name) ?? sameServer[0] ?? null;
}
