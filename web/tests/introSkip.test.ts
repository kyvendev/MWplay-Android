// Skip intro rules (same cases as IntroSkipTest / IntroTimestampRepositoryTest in the apps) and the
// TheIntroDB / SkipDB / AniSkip lookup with a fetch test double: no request reaches the real
// services. Bodies are trimmed copies of real answers (October 2026).
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  animeIds,
  DEFAULT_INTRO_SETTINGS,
  episodeRef,
  findIntro,
  imdbEpisode,
  IntroSkipTracker,
  isReliable,
  parseAniSkip,
  parseIntroDb,
  parseKitsuMalId,
  parseSkipDb,
  readIntroSettings,
  resetIntroMemory,
  selectInterval,
  writeIntroSettings,
  type IntroInterval,
} from '../src/features/player/introSkip';

const duration = 1_440_000;
const opening: IntroInterval = { startMs: 60_000, endMs: 150_000, source: 'introdb' };
const manualMode = { enabled: true, autoSkip: false };
const autoMode = { enabled: true, autoSkip: true };
const tracker = (videoId = 'tt0000001:1:1') => {
  const instance = new IntroSkipTracker();
  instance.bind(episodeRef('series', 'tt0000001', videoId));
  return instance;
};

const got = { type: 'series', itemId: 'tt0944947', videoId: 'tt0944947:1:1' };
const gotDuration = 3_702_000;
const introDbGot = {
  imdb_id: 'tt0944947', media_type: 'tv', is_movie: false, season: 1, episode: 1,
  intro: { start_sec: 437, end_sec: 531, start_ms: 437000, end_ms: 531000, confidence: 1, submission_count: 2 },
  recap: null, outro: null, post_credits: null, no_intro: false, no_recap: true, no_outro: false,
};
const introDbUntimed = { ...introDbGot, intro: null };
const introDbNoIntro = { ...introDbGot, intro: null, no_intro: true };
const skipDbGot = {
  imdb_id: 'tt0944947', season: 1, episode: 1,
  segments: { intro: { start_ms: 447800, end_ms: 521400, adjusted: false, offset_ms: 0, match: 'exact', confidence: 0.75 }, recap: null, outro: null, preview: null },
  intro_length_estimate_ms: null,
};
const skipDbOutOfRange = { ...skipDbGot, segments: { ...skipDbGot.segments, intro: { ...skipDbGot.segments.intro, match: 'out-of-range', confidence: 0.6 } } };
const aniSkipBody = {
  found: true,
  results: [
    { interval: { startTime: 28.783, endTime: 118.783 }, skipType: 'op', skipId: 'a', episodeLength: 1500.04 },
    { interval: { startTime: 1387.996, endTime: 1500 }, skipType: 'ed', skipId: 'b', episodeLength: 1500.146 },
  ],
};
const kitsuBody = { data: [{ attributes: { externalSite: 'myanimelist/anime', externalId: '21' } }] };

type Route = [string, () => Response | Promise<Response>];
const fakeFetch = (routes: Route[]) =>
  vi.fn(async (url: string) => {
    const route = routes.find(([prefix]) => url.includes(prefix));
    return route ? route[1]() : new Response(null, { status: 404 });
  });
const json = (body: unknown) => () => new Response(JSON.stringify(body));
const asFetch = (mock: ReturnType<typeof fakeFetch>) => mock as unknown as typeof fetch;
const calls = (mock: ReturnType<typeof fakeFetch>, part: string) => mock.mock.calls.filter(([url]) => String(url).includes(part)).length;

beforeEach(() => {
  localStorage.clear();
  resetIntroMemory();
});

describe('decisions', () => {
  it('shows the button only inside the opening', () => {
    const t = tracker();
    expect(t.decide(59_000, duration, opening, manualMode)).toEqual({ kind: 'none' });
    expect(t.decide(60_000, duration, opening, manualMode)).toEqual({ kind: 'button', skipToMs: 150_000 });
    expect(t.decide(149_500, duration, opening, manualMode)).toEqual({ kind: 'none' });
    expect(t.decide(200_000, duration, opening, manualMode)).toEqual({ kind: 'none' });
  });

  it('manual skip hides the button, never jumps twice and shows it again after seeking back', () => {
    const t = tracker();
    t.decide(70_000, duration, opening, manualMode);
    expect(t.markSkipped(false)).toBe(true);
    expect(t.decide(71_000, duration, opening, manualMode)).toEqual({ kind: 'none' });
    expect(t.markSkipped(false)).toBe(false);
    t.decide(150_500, duration, opening, manualMode);
    expect(t.decide(90_000, duration, opening, manualMode)).toEqual({ kind: 'button', skipToMs: 150_000 });
  });

  it('automatic mode skips once per episode and is off by default', () => {
    const t = tracker();
    expect(t.decide(61_000, duration, opening, autoMode)).toEqual({ kind: 'auto', skipToMs: 150_000 });
    t.markSkipped(true);
    t.decide(150_200, duration, opening, autoMode);
    expect(t.decide(80_000, duration, opening, autoMode)).toEqual({ kind: 'button', skipToMs: 150_000 });
    expect(DEFAULT_INTRO_SETTINGS).toEqual({ enabled: true, autoSkip: false });
    expect(tracker().decide(61_000, duration, opening, { ...autoMode, enabled: false }).kind).toBe('none');
  });

  it('never skips without data, and movies have no episode', () => {
    expect(tracker().decide(70_000, duration, null, autoMode)).toEqual({ kind: 'none' });
    expect(episodeRef('movie', 'tt9', 'tt9')).toBeNull();
    expect(episodeRef('series', 'tt1', 'tt1')).toBeNull();
  });

  it('clears the state when the episode changes', () => {
    const t = tracker('tt0000001:1:1');
    t.decide(70_000, duration, opening, autoMode);
    t.markSkipped(true);
    t.bind(episodeRef('series', 'tt0000001', 'tt0000001:1:2'));
    expect(t.decide(70_000, duration, opening, autoMode).kind).toBe('auto');
  });
});

describe('reliability and identification', () => {
  it('rejects intervals incompatible with the video', () => {
    expect(isReliable(opening, duration)).toBe(true);
    expect(isReliable(opening, 0)).toBe(false);
    expect(isReliable({ ...opening, endMs: 62_000 }, duration)).toBe(false);
    expect(isReliable({ ...opening, endMs: 400_000 }, duration)).toBe(false);
    expect(isReliable({ ...opening, startMs: 1_400_000, endMs: 1_460_000 }, duration)).toBe(false);
    expect(isReliable({ ...opening, startMs: 800_000, endMs: 880_000 }, duration)).toBe(false);
    const aniSkip: IntroInterval = { startMs: 28_783, endMs: 118_783, source: 'aniskip', episodeLengthMs: 1_500_040 };
    expect(isReliable(aniSkip, 1_500_000)).toBe(true);
    expect(isReliable(aniSkip, 1_620_000)).toBe(false);
    expect(selectInterval([{ ...aniSkip, episodeLengthMs: 1_508_000 }, aniSkip], 1_500_000)).toBe(aniSkip);
  });

  it('identifies episodes by id, not by title', () => {
    expect(imdbEpisode(got)).toEqual({ imdbId: 'tt0944947', season: 1, episode: 1 });
    expect(imdbEpisode({ ...got, videoId: 'tt0944947:0:3' })).toEqual({ imdbId: 'tt0944947', season: 0, episode: 3 });
    expect(imdbEpisode({ ...got, videoId: 'tt0903747:1:1' })).toBeNull();
    expect(imdbEpisode({ ...got, videoId: 'tt0944947:x:1' })).toBeNull();
    expect(animeIds({ type: 'series', itemId: 'kitsu:12', videoId: 'kitsu:12:5' })).toEqual({ provider: 'kitsu', showId: '12', episode: 5 });
  });
});

describe('sources', () => {
  it('TheIntroDB answers first (through the same-origin relay) and SkipDB is not asked', async () => {
    const fetchImpl = fakeFetch([['/api/introdb', json(introDbGot)], ['skipdb', json(skipDbGot)]]);
    expect(await findIntro(got, gotDuration, asFetch(fetchImpl))).toEqual({ startMs: 437_000, endMs: 531_000, source: 'introdb' });
    expect(fetchImpl.mock.calls.map(([url]) => url)).toEqual(['/api/introdb?imdb_id=tt0944947&season=1&episode=1']);
  });

  it('SkipDB is the fallback and receives the duration in seconds', async () => {
    const fetchImpl = fakeFetch([['/api/introdb', json(introDbUntimed)], ['skipdb', json(skipDbGot)]]);
    expect(await findIntro(got, gotDuration, asFetch(fetchImpl))).toEqual({ startMs: 447_800, endMs: 521_400, source: 'skipdb', durationMatched: true });
    expect(String(fetchImpl.mock.calls[1][0])).toBe('https://api.skipdb.tv/api/segments?imdb_id=tt0944947&season=1&episode=1&type=intro&duration=3702');
  });

  it('AniSkip answers for anime, the Kitsu mapping and the answer are cached', async () => {
    const fetchImpl = fakeFetch([['kitsu.io/api/edge/anime/12/mappings', json(kitsuBody)], ['api.aniskip.com/v2/skip-times/21/1?', json(aniSkipBody)]]);
    const ref = { type: 'series', itemId: 'kitsu:12', videoId: 'kitsu:12:1' };
    expect(await findIntro(ref, 1_500_000, asFetch(fetchImpl))).toEqual({ startMs: 28_783, endMs: 118_783, source: 'aniskip', episodeLengthMs: 1_500_040 });
    await findIntro(ref, 1_500_000, asFetch(fetchImpl));
    resetIntroMemory();
    await findIntro(ref, 1_500_000, asFetch(fetchImpl));
    expect(calls(fetchImpl, 'kitsu.io')).toBe(1);
    expect(calls(fetchImpl, 'aniskip')).toBe(1);
    expect(calls(fetchImpl, 'introdb') + calls(fetchImpl, 'skipdb')).toBe(0);
  });

  it('no source with data means no button', async () => {
    const fetchImpl = fakeFetch([['/api/introdb', () => new Response(null, { status: 404 })], ['skipdb', json(skipDbOutOfRange)]]);
    expect(await findIntro(got, gotDuration, asFetch(fetchImpl))).toBeNull();
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it('a confirmed missing opening stops the lookup', async () => {
    const fetchImpl = fakeFetch([['/api/introdb', json(introDbNoIntro)], ['skipdb', json(skipDbGot)]]);
    expect(await findIntro(got, gotDuration, asFetch(fetchImpl))).toBeNull();
    expect(calls(fetchImpl, 'skipdb')).toBe(0);
  });

  it('an unavailable source is skipped and the miss is not remembered', async () => {
    let down = true;
    const fetchImpl = fakeFetch([
      ['/api/introdb', () => (down ? Promise.reject(new TypeError('Failed to fetch')) : new Response(JSON.stringify(introDbGot)))],
      ['skipdb', () => new Response(null, { status: 429 })],
    ]);
    expect(await findIntro(got, gotDuration, asFetch(fetchImpl))).toBeNull();
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    down = false;
    resetIntroMemory();
    expect((await findIntro(got, gotDuration, asFetch(fetchImpl)))?.startMs).toBe(437_000);
  });

  it('invalid identifiers and unknown durations make no requests', async () => {
    const fetchImpl = fakeFetch([]);
    expect(await findIntro({ ...got, videoId: 'tt0944947:x:1' }, gotDuration, asFetch(fetchImpl))).toBeNull();
    expect(await findIntro({ type: 'tv', itemId: 'bc:canal', videoId: 'bc:canal:1' }, gotDuration, asFetch(fetchImpl))).toBeNull();
    expect(await findIntro(got, 0, asFetch(fetchImpl))).toBeNull();
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('data incompatible with the duration is rejected', async () => {
    const fetchImpl = fakeFetch([['/api/introdb', json(introDbGot)]]);
    expect(await findIntro(got, 500_000, asFetch(fetchImpl))).toBeNull();
    expect(calls(fetchImpl, 'skipdb')).toBe(1);
  });

  it('answers for another episode are ignored', () => {
    expect(parseIntroDb(introDbGot, 'tt0944947', 1, 2)).toEqual({ kind: 'no-data' });
    expect(parseSkipDb(skipDbGot, 'tt0944947', 2, 1)).toEqual({ kind: 'no-data' });
    expect(parseSkipDb(skipDbOutOfRange, 'tt0944947', 1, 1)).toEqual({ kind: 'no-data' });
    expect(parseAniSkip({ found: false })).toEqual([]);
    expect(parseKitsuMalId(kitsuBody)).toBe('21');
  });

  it('defaults to manual mode and persists the settings without the old button option', () => {
    localStorage.setItem('mwplay.web.introSkip', JSON.stringify({ enabled: true, showButton: false, autoSkip: false }));
    expect(readIntroSettings()).toEqual({ enabled: true, autoSkip: false });
    writeIntroSettings({ enabled: true, autoSkip: true });
    expect(readIntroSettings()).toEqual({ enabled: true, autoSkip: true });
  });
});
