// Skip intro rules (same cases as IntroSkipTest in the apps) and the AniSkip/Kitsu lookup with a
// fetch test double: no request reaches the real services.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  animeIds,
  clearManualIntervals,
  DEFAULT_INTRO_SETTINGS,
  episodeRef,
  introCandidates,
  IntroSkipTracker,
  isReliable,
  manualIntervals,
  parseAniSkip,
  parseKitsuMalId,
  readIntroSettings,
  saveManualInterval,
  selectInterval,
  writeIntroSettings,
  type IntroInterval,
} from '../src/features/player/introSkip';

const duration = 1_440_000;
const manual: IntroInterval = { startMs: 60_000, endMs: 150_000, source: 'manual-episode' };
const button = { enabled: true, showButton: true, autoSkip: false };
const tracker = (videoId = 'tt1:1:1') => {
  const instance = new IntroSkipTracker();
  instance.bind(episodeRef('series', 'tt1', videoId));
  return instance;
};

beforeEach(() => localStorage.clear());

describe('decisions', () => {
  it('shows the button only inside the opening', () => {
    const t = tracker();
    expect(t.decide(59_000, duration, manual, button)).toEqual({ kind: 'none' });
    expect(t.decide(60_000, duration, manual, button)).toEqual({ kind: 'button', skipToMs: 150_000 });
    expect(t.decide(149_500, duration, manual, button)).toEqual({ kind: 'none' });
    expect(t.decide(200_000, duration, manual, button)).toEqual({ kind: 'none' });
  });

  it('hides the button after the skip, never jumps twice and shows it again after seeking back', () => {
    const t = tracker();
    t.decide(70_000, duration, manual, button);
    expect(t.markSkipped(false)).toBe(true);
    expect(t.decide(71_000, duration, manual, button)).toEqual({ kind: 'none' });
    expect(t.markSkipped(false)).toBe(false);
    t.decide(150_500, duration, manual, button);
    expect(t.decide(90_000, duration, manual, button)).toEqual({ kind: 'button', skipToMs: 150_000 });
  });

  it('skips automatically only when enabled, once per episode', () => {
    const auto = { ...button, autoSkip: true };
    const t = tracker();
    expect(t.decide(61_000, duration, manual, auto)).toEqual({ kind: 'auto', skipToMs: 150_000 });
    t.markSkipped(true);
    t.decide(150_200, duration, manual, auto);
    expect(t.decide(80_000, duration, manual, auto)).toEqual({ kind: 'button', skipToMs: 150_000 });
    expect(tracker().decide(61_000, duration, manual, button).kind).toBe('button');
    expect(tracker().decide(61_000, duration, manual, { ...auto, enabled: false }).kind).toBe('none');
  });

  it('never skips without data, and movies have no episode', () => {
    expect(tracker().decide(70_000, duration, null, { ...button, autoSkip: true })).toEqual({ kind: 'none' });
    expect(episodeRef('movie', 'tt9', 'tt9')).toBeNull();
    expect(episodeRef('series', 'tt1', 'tt1')).toBeNull();
  });

  it('clears the state when the episode changes', () => {
    const t = tracker('tt1:1:1');
    t.decide(70_000, duration, manual, button);
    t.markSkipped(true);
    t.bind(episodeRef('series', 'tt1', 'tt1:1:2'));
    expect(t.decide(70_000, duration, manual, { ...button, autoSkip: true }).kind).toBe('auto');
  });
});

describe('reliability', () => {
  it('uses AniSkip data only for the matching release', () => {
    const aniSkip: IntroInterval = { startMs: 28_783, endMs: 118_783, source: 'aniskip', episodeLengthMs: 1_500_040 };
    expect(isReliable(aniSkip, 1_500_000)).toBe(true);
    expect(isReliable(aniSkip, 1_620_000)).toBe(false);
    expect(isReliable(aniSkip, 0)).toBe(false);
    expect(isReliable({ ...manual, endMs: 62_000 }, duration)).toBe(false);
    expect(isReliable({ ...manual, endMs: 2_000_000 }, duration)).toBe(false);
  });

  it('prefers manual marks, then the closest AniSkip release', () => {
    const far: IntroInterval = { startMs: 10_000, endMs: 100_000, source: 'aniskip', episodeLengthMs: 1_508_000 };
    const close: IntroInterval = { startMs: 20_000, endMs: 110_000, source: 'aniskip', episodeLengthMs: 1_500_500 };
    expect(selectInterval([far, close], 1_500_000)).toBe(close);
    const series: IntroInterval = { startMs: 30_000, endMs: 120_000, source: 'manual-series' };
    expect(selectInterval([series, far, close], 1_500_000)).toBe(series);
  });
});

describe('sources', () => {
  const aniSkipBody = {
    found: true,
    results: [
      { interval: { startTime: 28.783, endTime: 118.783 }, skipType: 'op', skipId: 'a', episodeLength: 1500.04 },
      { interval: { startTime: 1387.996, endTime: 1500 }, skipType: 'ed', skipId: 'b', episodeLength: 1500.146 },
    ],
  };

  it('parses AniSkip openings and Kitsu mappings', () => {
    expect(parseAniSkip(aniSkipBody)).toEqual([{ startMs: 28_783, endMs: 118_783, source: 'aniskip', episodeLengthMs: 1_500_040 }]);
    expect(parseAniSkip({ found: false })).toEqual([]);
    expect(parseKitsuMalId({ data: [{ attributes: { externalSite: 'myanimelist/anime', externalId: '21' } }] })).toBe('21');
    expect(animeIds({ type: 'series', itemId: 'kitsu:12', videoId: 'kitsu:12:5' })).toEqual({ provider: 'kitsu', showId: '12', episode: 5 });
    expect(animeIds({ type: 'series', itemId: 'tt1', videoId: 'tt1:1:2' })).toBeNull();
  });

  it('maps Kitsu to MyAnimeList once and queries AniSkip; other series make no requests', async () => {
    const fetchImpl = vi.fn(async (url: string) => {
      if (url.startsWith('https://kitsu.io/api/edge/anime/12/mappings')) return new Response(JSON.stringify({ data: [{ attributes: { externalSite: 'myanimelist/anime', externalId: '21' } }] }));
      if (url.startsWith('https://api.aniskip.com/v2/skip-times/21/1?')) return new Response(JSON.stringify(aniSkipBody));
      return new Response(null, { status: 404 });
    });
    const ref = { type: 'series', itemId: 'kitsu:12', videoId: 'kitsu:12:1' };
    expect(await introCandidates(ref, fetchImpl as unknown as typeof fetch)).toHaveLength(1);
    await introCandidates(ref, fetchImpl as unknown as typeof fetch);
    expect(fetchImpl.mock.calls.filter(([url]) => String(url).includes('kitsu.io'))).toHaveLength(1);

    fetchImpl.mockClear();
    expect(await introCandidates({ type: 'series', itemId: 'tt0903747', videoId: 'tt0903747:1:1' }, fetchImpl as unknown as typeof fetch)).toEqual([]);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('treats network failures as "no data"', async () => {
    const offline = vi.fn(async () => Promise.reject(new TypeError('Failed to fetch')));
    expect(await introCandidates({ type: 'series', itemId: 'mal:21', videoId: 'mal:21:1' }, offline as unknown as typeof fetch)).toEqual([]);
  });

  it('keeps manual marks for the episode or the whole series, and removes them', () => {
    const ep1 = { type: 'series', itemId: 'tt1', videoId: 'tt1:1:1' };
    const ep2 = { type: 'series', itemId: 'tt1', videoId: 'tt1:1:2' };
    saveManualInterval(ep1, 30_000, 120_000, false);
    saveManualInterval(ep1, 25_000, 115_000, true);
    expect(manualIntervals(ep1).map((item) => item.source)).toEqual(['manual-episode', 'manual-series']);
    expect(manualIntervals(ep2)).toEqual([{ startMs: 25_000, endMs: 115_000, source: 'manual-series' }]);
    clearManualIntervals(ep1);
    expect(manualIntervals(ep1)).toEqual([]);
  });

  it('defaults to the button only and persists the settings', () => {
    expect(readIntroSettings()).toEqual(DEFAULT_INTRO_SETTINGS);
    writeIntroSettings({ enabled: true, showButton: false, autoSkip: true });
    expect(readIntroSettings()).toEqual({ enabled: true, showButton: false, autoSkip: true });
  });
});
