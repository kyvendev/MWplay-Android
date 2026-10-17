import type { ExtraValue, MetaItemPreview, ResourceRequest, Stream } from './types';

/** How a stream can be handled in a browser, decided from the addon's stream source. */
export type StreamKind =
  | 'http' // direct URL, played by <video> (or hls.js)
  | 'torrent' // needs the streaming server to turn it into HTTP
  | 'youtube' // converted by the core through the streaming server
  | 'external' // meant to open in another app or site
  | 'unsupported';

export function streamKind(stream: Stream): StreamKind {
  if (stream.url) return /^https?:\/\//i.test(stream.url) ? 'http' : 'unsupported';
  if (stream.infoHash) return 'torrent';
  if (stream.ytId) return 'youtube';
  if (stream.externalUrl || stream.playerFrameUrl) return 'external';
  return 'unsupported';
}

/** Title and description lines shown for a source, matching the native stream list. */
export function streamLabel(stream: Stream): { title: string; detail: string | null } {
  const title = (stream.name ?? '').trim() || (stream.behaviorHints?.filename ?? '').trim() || 'Fonte';
  const detail = (stream.description ?? '').trim() || null;
  return { title, detail };
}

export type PlaybackFormat = 'hls' | 'dash' | 'file';

/** Container family from the playable URL (query strings and fragments ignored). */
export function playbackFormat(url: string): PlaybackFormat {
  const path = url.split(/[?#]/)[0].toLowerCase();
  if (path.endsWith('.m3u8') || /\/(hls|playlist)\.m3u8/.test(path)) return 'hls';
  if (path.endsWith('.mpd')) return 'dash';
  return 'file';
}

/**
 * Parses a core discover deep link (`stremio:///discover/{base}/{type}/{catalogId}?{extra}` or the
 * same path behind `#/`) back into the catalog request the core generated it from.
 */
export function parseDiscoverDeepLink(link: string): ResourceRequest | null {
  const marker = '/discover/';
  const start = link.indexOf(marker);
  if (start < 0) return null;
  const [path, query = ''] = link.slice(start + marker.length).split('?');
  const parts = path.split('/');
  if (parts.length < 3) return null;
  const [base, type, id] = parts.map((part) => decodeURIComponent(part));
  if (!base || !type || !id) return null;
  const extra: ExtraValue[] = [];
  new URLSearchParams(query).forEach((value, name) => extra.push([name, value]));
  return { base, path: { resource: 'catalog', type, id, extra } };
}

/** Genres, cast and IMDb rating live in the meta item's links (stremio-core link categories). */
export function metaLinks(meta: MetaItemPreview) {
  const links = meta.links ?? [];
  const byCategory = (category: string) => links.filter((link) => link.category === category).map((link) => link.name);
  return {
    genres: byCategory('Genres'),
    cast: byCategory('Cast'),
    directors: byCategory('Directors'),
    imdbRating: links.find((link) => link.category === 'imdb')?.name ?? null,
  };
}

/** Browsers only render WebVTT tracks; addon subtitles are commonly SRT. */
export function srtToVtt(srt: string): string {
  const body = srt
    .replace(/^﻿/, '')
    .replace(/\r\n?/g, '\n')
    .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2')
    .trim();
  return body.startsWith('WEBVTT') ? `${body}\n` : `WEBVTT\n\n${body}\n`;
}

/** Season/episode label used across details, player and media metadata. */
export function episodeLabel(season?: number, episode?: number): string | null {
  if (season === undefined || episode === undefined) return null;
  return season === 0 ? `Especial · Episódio ${episode}` : `Temporada ${season} · Episódio ${episode}`;
}
