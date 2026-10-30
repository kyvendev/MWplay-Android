import { useCallback, useEffect, useState } from 'react';

/**
 * Skip intro, same rules as the apps (player/intro/IntroSkip.kt, IntroTimestampRepository.kt):
 * - IMDb episodes (`tt0944947:1:1`) ask TheIntroDB, then SkipDB (with the video duration);
 *   Kitsu/MyAnimeList anime (`kitsu:12:5`) ask AniSkip. One source at a time, only until one
 *   gives a valid answer for the playing video; a source that is down is skipped.
 * - Answers are validated (same episode echoed, plausible for the duration) and cached.
 * - Without data the button never shows and nothing is skipped. Movies have no episode.
 */

export type IntroSource = 'introdb' | 'aniskip' | 'skipdb';

export interface IntroInterval {
  startMs: number;
  endMs: number;
  source: IntroSource;
  /** Length of the release the timestamps were measured on (AniSkip). */
  episodeLengthMs?: number;
  /** The source matched the playing duration itself (SkipDB "exact"/"shifted"). */
  durationMatched?: boolean;
}

/** On by default; manual (button) mode by default. */
export interface IntroSkipSettings {
  enabled: boolean;
  autoSkip: boolean;
}

export const DEFAULT_INTRO_SETTINGS: IntroSkipSettings = { enabled: true, autoSkip: false };

export const MIN_INTRO_MS = 5_000;
export const MAX_INTRO_MS = 4 * 60_000;
export const EPISODE_LENGTH_TOLERANCE_MS = 10_000;
export const MIN_TAIL_MS = 60_000;
export const END_MARGIN_MS = 1_000;
export const SKIPDB_MIN_CONFIDENCE = 0.5;

export interface EpisodeRef {
  type: string;
  itemId: string;
  videoId: string;
}

/** Null for movies and items without a distinct episode id. */
export function episodeRef(type: string | null | undefined, itemId: string | null | undefined, videoId: string | null | undefined): EpisodeRef | null {
  if (!type || !itemId || !videoId || type.toLowerCase() === 'movie' || videoId === itemId) return null;
  return { type, itemId, videoId };
}

const episodeKey = (ref: EpisodeRef) => `${ref.type}|${ref.itemId}|${ref.videoId}`;

/** (IMDb id, season, episode) for `tt…:S:E` ids of the show being played; season 0 = specials. */
export function imdbEpisode(ref: EpisodeRef): { imdbId: string; season: number; episode: number } | null {
  const parts = ref.videoId.split(':');
  if (parts.length !== 3 || !/^tt\d{5,10}$/.test(parts[0]) || parts[0] !== ref.itemId) return null;
  if (!/^\d+$/.test(parts[1]) || !/^\d+$/.test(parts[2])) return null;
  const season = Number(parts[1]);
  const episode = Number(parts[2]);
  if (episode <= 0) return null;
  return { imdbId: parts[0], season, episode };
}

/** (provider, show id, episode) for `kitsu:12:5` / `mal:21:5`, null otherwise. */
export function animeIds(ref: EpisodeRef): { provider: 'kitsu' | 'mal'; showId: string; episode: number } | null {
  const parts = ref.videoId.split(':');
  if (parts.length < 3) return null;
  const provider = parts[0].toLowerCase();
  if (provider !== 'kitsu' && provider !== 'mal') return null;
  const episode = Number(parts[parts.length - 1]);
  if (!Number.isInteger(episode) || episode <= 0 || !/^\d+$/.test(parts[1])) return null;
  return { provider, showId: parts[1], episode };
}

/** True when the interval makes sense for a video of `durationMs`; unknown duration = unreliable. */
export function isReliable(interval: IntroInterval, durationMs: number): boolean {
  const length = interval.endMs - interval.startMs;
  if (interval.startMs < 0 || length < MIN_INTRO_MS || length > MAX_INTRO_MS) return false;
  if (durationMs <= 0 || interval.endMs > durationMs - MIN_TAIL_MS) return false;
  // The opening sits in the first part of an episode, never in its second half.
  if (interval.startMs > durationMs / 2) return false;
  if (interval.episodeLengthMs !== undefined && Math.abs(interval.episodeLengthMs - durationMs) > EPISODE_LENGTH_TOLERANCE_MS) return false;
  if (interval.source === 'aniskip' && interval.episodeLengthMs === undefined) return false;
  return true;
}

/** The reliable candidate whose measured release is closest to the playing video. */
export function selectInterval(candidates: IntroInterval[], durationMs: number): IntroInterval | null {
  const distance = (item: IntroInterval) => (item.episodeLengthMs === undefined ? 0 : Math.abs(item.episodeLengthMs - durationMs));
  return candidates.filter((item) => isReliable(item, durationMs)).sort((a, b) => distance(a) - distance(b))[0] ?? null;
}

export type IntroDecision = { kind: 'none' } | { kind: 'button'; skipToMs: number } | { kind: 'auto'; skipToMs: number };

/**
 * Per-episode state, driven by the player's existing time updates. After a skip nothing shows
 * until the position leaves the opening (late updates never bring the button back or jump
 * twice); seeking back into it shows the button again; automatic skipping happens once.
 */
export class IntroSkipTracker {
  private key: string | null = null;
  private skippedInside = false;
  private autoSkipUsed = false;

  bind(ref: EpisodeRef | null) {
    const key = ref ? episodeKey(ref) : null;
    if (key === this.key) return;
    this.key = key;
    this.skippedInside = false;
    this.autoSkipUsed = false;
  }

  decide(positionMs: number, durationMs: number, interval: IntroInterval | null, settings: IntroSkipSettings): IntroDecision {
    if (!settings.enabled || !interval || !isReliable(interval, durationMs)) return { kind: 'none' };
    const inside = positionMs >= interval.startMs && positionMs < interval.endMs - END_MARGIN_MS;
    if (!inside) {
      this.skippedInside = false;
      return { kind: 'none' };
    }
    if (this.skippedInside) return { kind: 'none' };
    if (settings.autoSkip && !this.autoSkipUsed) return { kind: 'auto', skipToMs: interval.endMs };
    return { kind: 'button', skipToMs: interval.endMs };
  }

  /** False when the skip was already done (double tap, repeated event). */
  markSkipped(automatic: boolean): boolean {
    if (this.skippedInside) return false;
    this.skippedInside = true;
    if (automatic) this.autoSkipUsed = true;
    return true;
  }
}

// region storage --------------------------------------------------------------------------------

const SETTINGS_KEY = 'mwplay.web.introSkip';
const LEGACY_MANUAL_KEY = 'mwplay.web.introMarks';
const KITSU_KEY = 'mwplay.web.kitsuToMal';
const CACHE_KEY = 'mwplay.web.introLookup';
const DURATION_BUCKET_MS = 10_000;
const HIT_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const MISS_TTL_MS = 12 * 60 * 60 * 1000;

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? { ...fallback, ...(JSON.parse(raw) as T) } : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Private mode: works for this visit only.
  }
}

export function readIntroSettings(): IntroSkipSettings {
  const stored = readJson<Partial<IntroSkipSettings>>(SETTINGS_KEY, {});
  return { enabled: stored.enabled ?? DEFAULT_INTRO_SETTINGS.enabled, autoSkip: stored.autoSkip ?? DEFAULT_INTRO_SETTINGS.autoSkip };
}

const settingsListeners = new Set<(settings: IntroSkipSettings) => void>();

export function writeIntroSettings(settings: IntroSkipSettings) {
  writeJson(SETTINGS_KEY, { enabled: settings.enabled, autoSkip: settings.autoSkip });
  settingsListeners.forEach((listener) => listener(settings));
}

export function useIntroSettings(): [IntroSkipSettings, (settings: IntroSkipSettings) => void] {
  const [settings, setSettings] = useState(readIntroSettings);
  useEffect(() => {
    settingsListeners.add(setSettings);
    return () => {
      settingsListeners.delete(setSettings);
    };
  }, []);
  return [settings, useCallback((value: IntroSkipSettings) => writeIntroSettings(value), [])];
}

/** The manual "Marcar abertura" marks were replaced by the databases below. */
export function dropLegacyManualMarks() {
  try {
    localStorage.removeItem(LEGACY_MANUAL_KEY);
  } catch {
    // Storage unavailable: nothing to drop.
  }
}

type CacheEntry = { at: number; interval: IntroInterval | null };

function readCached(key: string, now: number): CacheEntry | null {
  const entry = readJson<Record<string, CacheEntry>>(CACHE_KEY, {})[key];
  if (!entry) return null;
  const ttl = entry.interval ? HIT_TTL_MS : MISS_TTL_MS;
  return now - entry.at < ttl ? entry : null;
}

function writeCached(key: string, interval: IntroInterval | null, now: number) {
  const cache = readJson<Record<string, CacheEntry>>(CACHE_KEY, {});
  // Keep the cache small: drop expired entries on every write.
  for (const [entryKey, entry] of Object.entries(cache)) if (now - entry.at >= HIT_TTL_MS) delete cache[entryKey];
  cache[key] = { at: now, interval };
  writeJson(CACHE_KEY, cache);
}

// region sources --------------------------------------------------------------------------------

/** What one source said about the episode. */
export type SourceAnswer = { kind: 'found'; interval: IntroInterval } | { kind: 'confirmed-none' } | { kind: 'no-data' };

const NO_DATA: SourceAnswer = { kind: 'no-data' };

type Json = Record<string, unknown>;

const echoes = (json: Json, imdbId: string, season: number, episode: number) =>
  json.imdb_id === imdbId && json.season === season && json.episode === episode;

/** TheIntroDB `/segments`: `intro` {start_ms, end_ms} or null; `no_intro` = confirmed none. */
export function parseIntroDb(body: unknown, imdbId: string, season: number, episode: number): SourceAnswer {
  const json = body as Json | null;
  if (!json || typeof json !== 'object' || !echoes(json, imdbId, season, episode)) return NO_DATA;
  const intro = json.intro as { start_ms?: unknown; end_ms?: unknown } | null | undefined;
  if (!intro) return json.no_intro === true ? { kind: 'confirmed-none' } : NO_DATA;
  const { start_ms: start, end_ms: end } = intro;
  if (typeof start !== 'number' || typeof end !== 'number' || start < 0 || end <= start) return NO_DATA;
  return { kind: 'found', interval: { startMs: start, endMs: end, source: 'introdb' } };
}

/** SkipDB `/api/segments`: only "exact"/"shifted" matches against the duration we sent. */
export function parseSkipDb(body: unknown, imdbId: string, season: number, episode: number): SourceAnswer {
  const json = body as Json | null;
  if (!json || typeof json !== 'object' || !echoes(json, imdbId, season, episode)) return NO_DATA;
  const intro = (json.segments as Json | undefined)?.intro as { start_ms?: unknown; end_ms?: unknown; match?: unknown; confidence?: unknown } | null | undefined;
  if (!intro || (intro.match !== 'exact' && intro.match !== 'shifted')) return NO_DATA;
  if (typeof intro.confidence !== 'number' || intro.confidence < SKIPDB_MIN_CONFIDENCE) return NO_DATA;
  const { start_ms: start, end_ms: end } = intro;
  if (typeof start !== 'number' || typeof end !== 'number' || end <= start) return NO_DATA;
  return { kind: 'found', interval: { startMs: Math.round(start), endMs: Math.round(end), source: 'skipdb', durationMatched: true } };
}

/** Opening ("op") intervals from an AniSkip v2 response, in milliseconds. */
export function parseAniSkip(body: unknown): IntroInterval[] {
  const json = body as { found?: boolean; results?: { skipType?: string; interval?: { startTime?: number; endTime?: number }; episodeLength?: number }[] } | null;
  if (!json?.found || !Array.isArray(json.results)) return [];
  return json.results.flatMap((result) => {
    const start = result.interval?.startTime;
    const end = result.interval?.endTime;
    const length = result.episodeLength;
    if (result.skipType !== 'op' || typeof start !== 'number' || typeof end !== 'number' || typeof length !== 'number') return [];
    return [{ startMs: Math.round(start * 1000), endMs: Math.round(end * 1000), source: 'aniskip' as const, episodeLengthMs: Math.round(length * 1000) }];
  });
}

/** MyAnimeList id from Kitsu's mappings endpoint, null when the show has none. */
export function parseKitsuMalId(body: unknown): string | null {
  const data = (body as { data?: { attributes?: { externalSite?: string; externalId?: string } }[] } | null)?.data;
  const id = Array.isArray(data) ? data.find((item) => item.attributes?.externalSite === 'myanimelist/anime')?.attributes?.externalId : undefined;
  return id && /^\d+$/.test(id) ? id : null;
}

class SourceUnavailable extends Error {}

/** JSON for 200, null for 404 ("no data"); anything else means the source is unavailable. */
async function getJson(url: string, fetchImpl: typeof fetch): Promise<unknown | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6_000);
  try {
    const response = await fetchImpl(url, { headers: { Accept: 'application/json' }, signal: controller.signal });
    if (response.status === 404) return null;
    if (!response.ok) throw new SourceUnavailable(`HTTP ${response.status}`);
    return await response.json();
  } catch (error) {
    throw error instanceof SourceUnavailable ? error : new SourceUnavailable(String(error));
  } finally {
    clearTimeout(timer);
  }
}

async function malIdForKitsu(kitsuId: string, fetchImpl: typeof fetch): Promise<string | null> {
  const cache = readJson<Record<string, string>>(KITSU_KEY, {});
  if (kitsuId in cache) return cache[kitsuId] || null;
  const body = await getJson(`https://kitsu.io/api/edge/anime/${encodeURIComponent(kitsuId)}/mappings?filter%5BexternalSite%5D=myanimelist%2Fanime`, fetchImpl);
  const malId = body === null ? null : parseKitsuMalId(body);
  writeJson(KITSU_KEY, { ...cache, [kitsuId]: malId ?? '' });
  return malId;
}

const validFor = (answer: SourceAnswer, durationMs: number): SourceAnswer =>
  answer.kind === 'found' && !isReliable(answer.interval, durationMs) ? NO_DATA : answer;

async function query(source: IntroSource, ref: EpisodeRef, durationMs: number, fetchImpl: typeof fetch): Promise<SourceAnswer> {
  if (source === 'aniskip') {
    const ids = animeIds(ref)!;
    const malId = ids.provider === 'mal' ? ids.showId : await malIdForKitsu(ids.showId, fetchImpl);
    if (!malId) return NO_DATA;
    const body = await getJson(`https://api.aniskip.com/v2/skip-times/${malId}/${ids.episode}?types%5B%5D=op&episodeLength=0`, fetchImpl);
    const interval = body === null ? null : selectInterval(parseAniSkip(body), durationMs);
    return interval ? { kind: 'found', interval } : NO_DATA;
  }
  const { imdbId, season, episode } = imdbEpisode(ref)!;
  if (source === 'introdb') {
    // Same-origin relay (api/introdb.js): TheIntroDB does not allow other sites in CORS.
    const body = await getJson(`/api/introdb?imdb_id=${imdbId}&season=${season}&episode=${episode}`, fetchImpl);
    return body === null ? NO_DATA : validFor(parseIntroDb(body, imdbId, season, episode), durationMs);
  }
  const seconds = Math.floor(durationMs / 1000);
  const body = await getJson(`https://api.skipdb.tv/api/segments?imdb_id=${imdbId}&season=${season}&episode=${episode}&type=intro&duration=${seconds}`, fetchImpl);
  return body === null ? NO_DATA : validFor(parseSkipDb(body, imdbId, season, episode), durationMs);
}

function sourcesFor(ref: EpisodeRef): IntroSource[] {
  if (animeIds(ref)) return ['aniskip'];
  if (imdbEpisode(ref)) return ['introdb', 'skipdb'];
  return [];
}

const memory = new Map<string, IntroInterval | null>();

/** Test helper: forget the in-memory answers (localStorage is cleared by the tests). */
export function resetIntroMemory() {
  memory.clear();
}

/**
 * The opening of `ref` for a video of `durationMs`, or null when no source has valid data.
 * Never throws; call it once the duration is known (playback is never held for it).
 */
export async function findIntro(ref: EpisodeRef, durationMs: number, fetchImpl: typeof fetch = fetch, now: number = Date.now()): Promise<IntroInterval | null> {
  if (durationMs <= 0) return null;
  const cacheKey = `${episodeKey(ref)}|${Math.floor(durationMs / DURATION_BUCKET_MS)}`;
  if (memory.has(cacheKey)) return memory.get(cacheKey) ?? null;
  const cached = readCached(cacheKey, now);
  if (cached) {
    memory.set(cacheKey, cached.interval);
    return cached.interval;
  }
  let anySourceFailed = false;
  let result: IntroInterval | null = null;
  for (const source of sourcesFor(ref)) {
    let answer: SourceAnswer;
    try {
      answer = await query(source, ref, durationMs, fetchImpl);
    } catch {
      anySourceFailed = true;
      continue;
    }
    if (answer.kind === 'found') {
      result = answer.interval;
      break;
    }
    // The community confirmed there is no opening: nothing to skip, stop here.
    if (answer.kind === 'confirmed-none') break;
  }
  memory.set(cacheKey, result);
  // A down source may have had the answer: only remember a miss when every source replied.
  if (result || !anySourceFailed) writeCached(cacheKey, result, now);
  return result;
}
