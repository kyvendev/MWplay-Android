import { useCallback, useEffect, useState } from 'react';

/**
 * Skip intro, same rules as the apps (player/intro/IntroSkip.kt):
 * - intervals come from marks saved by the user (episode or whole series) or from AniSkip
 *   (api.aniskip.com, public community data) for Kitsu/MyAnimeList anime ids;
 * - AniSkip data is used only when its measured episode length matches the playing video;
 * - without data the button never shows and nothing is skipped. Movies have no episode.
 */

export type IntroSource = 'manual-episode' | 'manual-series' | 'aniskip';

export interface IntroInterval {
  startMs: number;
  endMs: number;
  source: IntroSource;
  /** Length of the release the timestamps were measured on (AniSkip). */
  episodeLengthMs?: number;
}

export interface IntroSkipSettings {
  enabled: boolean;
  showButton: boolean;
  autoSkip: boolean;
}

export const DEFAULT_INTRO_SETTINGS: IntroSkipSettings = { enabled: true, showButton: true, autoSkip: false };

export const MIN_INTRO_MS = 5_000;
export const MAX_INTRO_MS = 4 * 60_000;
export const EPISODE_LENGTH_TOLERANCE_MS = 10_000;
export const END_MARGIN_MS = 1_000;

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
const seriesKey = (ref: EpisodeRef) => `${ref.type}|${ref.itemId}`;

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

/** True when the interval makes sense for a video of `durationMs` (0 = unknown). */
export function isReliable(interval: IntroInterval, durationMs: number): boolean {
  const length = interval.endMs - interval.startMs;
  if (interval.startMs < 0 || length < MIN_INTRO_MS || length > MAX_INTRO_MS) return false;
  if (durationMs > 0 && interval.endMs > durationMs) return false;
  if (interval.source === 'aniskip') {
    if (interval.episodeLengthMs === undefined || durationMs <= 0) return false;
    if (Math.abs(interval.episodeLengthMs - durationMs) > EPISODE_LENGTH_TOLERANCE_MS) return false;
  }
  return true;
}

/** Manual marks first; among AniSkip results, the release closest to the playing video. */
export function selectInterval(candidates: IntroInterval[], durationMs: number): IntroInterval | null {
  const reliable = candidates.filter((candidate) => isReliable(candidate, durationMs));
  const manual = reliable.find((candidate) => candidate.source !== 'aniskip');
  if (manual) return manual;
  const automatic = reliable.filter((candidate) => candidate.source === 'aniskip');
  automatic.sort((a, b) => Math.abs((a.episodeLengthMs ?? 0) - durationMs) - Math.abs((b.episodeLengthMs ?? 0) - durationMs));
  return automatic[0] ?? null;
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
    return settings.showButton ? { kind: 'button', skipToMs: interval.endMs } : { kind: 'none' };
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
const MANUAL_KEY = 'mwplay.web.introMarks';
const KITSU_KEY = 'mwplay.web.kitsuToMal';

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
  return readJson(SETTINGS_KEY, DEFAULT_INTRO_SETTINGS);
}

const settingsListeners = new Set<(settings: IntroSkipSettings) => void>();

export function writeIntroSettings(settings: IntroSkipSettings) {
  writeJson(SETTINGS_KEY, settings);
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

type Marks = Record<string, [number, number]>;

export function manualIntervals(ref: EpisodeRef): IntroInterval[] {
  const marks = readJson<Marks>(MANUAL_KEY, {});
  const episode = marks[`e:${episodeKey(ref)}`];
  const series = marks[`s:${seriesKey(ref)}`];
  return [
    ...(episode ? [{ startMs: episode[0], endMs: episode[1], source: 'manual-episode' as const }] : []),
    ...(series ? [{ startMs: series[0], endMs: series[1], source: 'manual-series' as const }] : []),
  ];
}

export function saveManualInterval(ref: EpisodeRef, startMs: number, endMs: number, wholeSeries: boolean) {
  const marks = readJson<Marks>(MANUAL_KEY, {});
  marks[wholeSeries ? `s:${seriesKey(ref)}` : `e:${episodeKey(ref)}`] = [Math.round(startMs), Math.round(endMs)];
  writeJson(MANUAL_KEY, marks);
}

/** Removes the episode mark and the series mark. */
export function clearManualIntervals(ref: EpisodeRef) {
  const marks = readJson<Marks>(MANUAL_KEY, {});
  delete marks[`e:${episodeKey(ref)}`];
  delete marks[`s:${seriesKey(ref)}`];
  writeJson(MANUAL_KEY, marks);
}

// region AniSkip --------------------------------------------------------------------------------

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

async function getJson(url: string, fetchImpl: typeof fetch): Promise<unknown | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6_000);
  try {
    const response = await fetchImpl(url, { headers: { Accept: 'application/json' }, signal: controller.signal });
    return response.ok ? await response.json() : null;
  } finally {
    clearTimeout(timer);
  }
}

async function malIdForKitsu(kitsuId: string, fetchImpl: typeof fetch): Promise<string | null> {
  const cache = readJson<Record<string, string>>(KITSU_KEY, {});
  if (kitsuId in cache) return cache[kitsuId] || null;
  const body = await getJson(`https://kitsu.io/api/edge/anime/${encodeURIComponent(kitsuId)}/mappings?filter%5BexternalSite%5D=myanimelist%2Fanime`, fetchImpl);
  if (body === null) return null; // Network problem: try again next time.
  const malId = parseKitsuMalId(body);
  writeJson(KITSU_KEY, { ...cache, [kitsuId]: malId ?? '' });
  return malId;
}

/** Every candidate for the episode: manual marks first, then AniSkip. Never throws. */
export async function introCandidates(ref: EpisodeRef, fetchImpl: typeof fetch = fetch): Promise<IntroInterval[]> {
  const manual = manualIntervals(ref);
  const ids = animeIds(ref);
  if (!ids) return manual;
  try {
    const malId = ids.provider === 'mal' ? ids.showId : await malIdForKitsu(ids.showId, fetchImpl);
    if (!malId) return manual;
    const body = await getJson(`https://api.aniskip.com/v2/skip-times/${malId}/${ids.episode}?types%5B%5D=op&episodeLength=0`, fetchImpl);
    return [...manual, ...parseAniSkip(body)];
  } catch {
    return manual;
  }
}
