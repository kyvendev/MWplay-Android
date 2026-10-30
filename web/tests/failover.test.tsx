// Automatic source switch: the shared rules (same cases as SourceFailoverTest in the apps) and the
// player wiring with the video engine replaced by a test double and fake timers.
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import { PlayerPage } from '../src/features/player/PlayerPage';
import { getPlaybackRequest, setPlaybackRequest, type PlaybackCandidate, type PlaybackRequest } from '../src/features/player/playbackRequest';
import { addonRank, DEFAULT_FAILOVER_CONFIG, PlaybackStartDetector, SourceFailoverSession } from '../src/shared/sourceFailover';
import { createFakeCore } from './fakeCore';
import { ctxFixture } from './fixtures/coreStates';

const engines: { load: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> }[] = [];

vi.mock('../src/features/player/videoEngine', () => ({
  EXTERNAL_TRACK_LABEL: 'mw-external',
  VideoEngine: class {
    load = vi.fn(() => Promise.resolve());
    destroy = vi.fn();
    audioTracks = () => [];
    embeddedSubtitles = () => [];
    selectEmbeddedSubtitle = vi.fn();
    selectAudioTrack = vi.fn();
    embeddedTrack = () => null;
    mediaError = () => null;
    constructor() {
      engines.push(this);
    }
  },
}));

type Source = { key: string; addon: string; seeds: number };
const torrentio: Source = { key: 't-a', addon: 'Torrentio', seeds: 50 };
const torrentioB: Source = { key: 't-b', addon: 'Torrentio', seeds: 10 };
const frost: Source = { key: 'f-a', addon: 'FrostStream', seeds: 0 };
const frostB: Source = { key: 'f-b', addon: 'Frost Stream', seeds: 3 };
const bestCine: Source = { key: 'b-a', addon: 'Best Cine', seeds: 0 };
const comet: Source = { key: 'o-a', addon: 'Comet', seeds: 99 };
const available = [torrentio, torrentioB, frost, frostB, bestCine, comet];
const bySeeds = (a: Source, b: Source) => b.seeds - a.seeds;

describe('source switch rules', () => {
  let clock = 0;
  const session = (config = DEFAULT_FAILOVER_CONFIG) => new SourceFailoverSession<Source>(config, (item) => item.key, (item) => item.addon, () => clock);
  beforeEach(() => (clock = 0));

  it('first source starts normally', () => {
    const s = session();
    s.begin(torrentio);
    clock = 8_000;
    expect(s.onStarted()).toBe(8_000);
    expect(s.attempts.map((attempt) => attempt.outcome)).toEqual(['started']);
  });

  it('a timeout switches to the next addon and the second source starts', () => {
    const s = session();
    s.begin(torrentio);
    clock = 30_000;
    expect(s.onFailed('timeout', null, available, bySeeds)).toBe(frostB);
    clock = 38_000;
    expect(s.onStarted()).toBe(8_000);
    expect(s.attempts.map((attempt) => attempt.outcome)).toEqual(['timeout', 'started']);
  });

  it('a definitive failure switches at once', () => {
    const s = session();
    s.begin(torrentio);
    clock = 2_000;
    expect(s.onFailed('failed', 'formato incompatível', available, bySeeds)).toBe(frostB);
    expect(s.attempts[0].elapsedMs).toBe(2_000);
  });

  it('every source fails and the chain ends, without unregistered addons', () => {
    const s = session();
    s.begin(torrentio);
    expect(s.onFailed('timeout', null, available, bySeeds)).toBe(frostB);
    expect(s.onFailed('failed', null, available, bySeeds)).toBe(bestCine);
    expect(s.onFailed('timeout', null, available, bySeeds)).toBeNull();
    expect(s.attempts.some((attempt) => attempt.addon === 'Comet')).toBe(false);
  });

  it('a started playback is never interrupted and duplicate reports are ignored', () => {
    const s = session();
    s.begin(frost);
    expect(s.onFailed('failed', null, [...available, frost], bySeeds)).toBe(torrentio);
    s.onStarted();
    expect(s.onFailed('timeout', null, available, bySeeds)).toBeNull();
    expect(s.onStarted()).toBeNull();
    expect(s.attempts.map((attempt) => attempt.key)).toEqual(['f-a', 't-a']);
  });

  it('cancelling, the switch being off or the cap stop it', () => {
    const cancelled = session();
    cancelled.begin(torrentio);
    cancelled.cancel();
    expect(cancelled.onFailed('timeout', null, available, bySeeds)).toBeNull();
    const off = session({ ...DEFAULT_FAILOVER_CONFIG, enabled: false });
    off.begin(torrentio);
    expect(off.onFailed('timeout', null, available, bySeeds)).toBeNull();
    expect(addonRank('Torrentio Brazuca')).toBe(0);
    expect(addonRank('BEST-CINE')).toBe(2);
    expect(addonRank('Comet')).toBeNull();
  });

  it('start needs real progress, not just opening or the resume seek', () => {
    const detector = new PlaybackStartDetector();
    expect(detector.onUpdate(false, true, 0, false)).toBe(false);
    expect(detector.onUpdate(true, false, 0, false)).toBe(false);
    expect(detector.onUpdate(true, false, 600_000, false)).toBe(false);
    expect(detector.onUpdate(true, false, 600_400, false)).toBe(false);
    expect(detector.onUpdate(true, false, 601_100, false)).toBe(true);
    expect(detector.onUpdate(true, false, 602_000, false)).toBe(false);
  });
});

describe('player with automatic source switch', () => {
  const candidate = (addon: string, url: string, order = 0): PlaybackCandidate => ({
    key: `${addon}|${order}`,
    addon,
    stream: { url },
    streamRequest: { base: `https://${addon.toLowerCase()}.example/manifest.json`, path: { resource: 'stream', type: 'movie', id: 'tt1', extra: [] } },
    playable: true,
    order,
  });
  const first = candidate('Torrentio', 'https://a.example/video.mp4');
  const second = candidate('FrostStream', 'https://b.example/video.mp4');
  const third = candidate('BestCine', 'https://c.example/video.mp4');
  const base: PlaybackRequest = {
    stream: first.stream,
    streamRequest: first.streamRequest,
    metaRequest: null,
    title: 'Filme de teste',
    subtitle: null,
    backdrop: null,
    returnTo: { type: 'movie', id: 'tt1', videoId: null },
    candidate: first,
    alternatives: [first, second, third],
  };
  const playerFor = (item: PlaybackCandidate, stream: unknown = { type: 'Ready', content: { url: item.stream.url } }) => ({
    selected: { stream: item.stream, streamRequest: item.streamRequest, metaRequest: null, subtitlesPath: null },
    stream,
    subtitles: [],
    nextVideo: null,
    libraryItem: null,
  });
  const mount = (player: unknown) => {
    setPlaybackRequest(base);
    const fake = createFakeCore({ ctx: ctxFixture(true), player });
    const view = render(
      <CoreProvider core={fake.core}>
        <PlayerPage />
      </CoreProvider>,
    );
    return { fake, view };
  };
  const live = () => engines.filter((engine) => engine.destroy.mock.calls.length === 0);
  const loads = (fake: ReturnType<typeof createFakeCore>) =>
    fake.dispatched.map((entry) => entry.action as { action: string; args?: { model?: string; args?: { stream?: { url?: string } } } }).filter((action) => action.action === 'Load' && action.args?.model === 'Player').map((action) => action.args!.args!.stream!.url);

  beforeEach(() => {
    vi.useFakeTimers();
    engines.length = 0;
    localStorage.clear();
    HTMLMediaElement.prototype.play = vi.fn(() => Promise.resolve());
    HTMLMediaElement.prototype.pause = vi.fn();
    window.location.hash = '#/player';
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('switches to the next addon after 30 s without playback, with one engine at a time', async () => {
    const { fake } = mount(playerFor(first, null));
    await act(async () => {});
    await act(async () => vi.advanceTimersByTime(29_000));
    expect(loads(fake)).toEqual([first.stream.url]);
    await act(async () => vi.advanceTimersByTime(1_000));
    expect(loads(fake)).toEqual([first.stream.url, second.stream.url]);
    expect(getPlaybackRequest()?.stream).toBe(second.stream);
    expect(document.querySelector('.player__notice')?.textContent).toBe('Torrentio não iniciou em 30 s. Tentando FrostStream…');
    expect(live()).toHaveLength(1);
  });

  it('a definitive error switches at once, and every source failing shows the choice', async () => {
    const { fake } = mount(playerFor(first, { type: 'Err', content: 'falhou' }));
    await act(async () => {});
    expect(loads(fake)).toEqual([first.stream.url, second.stream.url]);
    await act(async () => vi.advanceTimersByTime(30_000));
    await act(async () => vi.advanceTimersByTime(30_000));
    expect(loads(fake)).toEqual([first.stream.url, second.stream.url, third.stream.url]);
    expect(screen.getByRole('dialog', { name: 'Nenhuma fonte conseguiu iniciar a reprodução.' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    await act(async () => {});
    // The chain starts again from the chosen stream (which still fails at once here).
    expect(loads(fake).slice(3)).toEqual([first.stream.url, second.stream.url]);
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(live()).toHaveLength(1);
  });

  it('never interrupts a source that started playing', async () => {
    const { fake, view } = mount(playerFor(first));
    await act(async () => {});
    const element = view.container.querySelector('video')!;
    let current = 0;
    Object.defineProperty(element, 'duration', { value: 5_400, configurable: true });
    Object.defineProperty(element, 'currentTime', { get: () => current, configurable: true });
    fireEvent(element, new Event('play'));
    fireEvent(element, new Event('playing'));
    for (const position of [0.2, 0.7, 1.3]) {
      current = position;
      fireEvent(element, new Event('timeupdate'));
    }
    await act(async () => vi.advanceTimersByTime(60_000));
    expect(loads(fake)).toEqual([first.stream.url]);
    expect(document.querySelector('.player__notice')).toBeNull();
  });

  it('Continuar assistindo: finds FrostStream in the core even if it answered after the click, without restarting the clock', async () => {
    const streamPath = first.streamRequest!.path;
    const metaDetails = (groups: { name: string; url: string }[]) => ({
      selected: { metaPath: { resource: 'meta', type: 'movie', id: 'tt1', extra: [] }, streamPath },
      metaItem: null,
      libraryItem: null,
      title: null,
      streams: groups.map((group) => ({
        content: { type: 'Ready', content: [{ url: group.url }] },
        addon: { manifest: { id: group.name.toLowerCase(), name: group.name }, transportUrl: `https://${group.name.toLowerCase()}.example/manifest.json` },
      })),
    });
    setPlaybackRequest({ ...base, alternatives: [first], torrentsAvailable: false });
    const fake = createFakeCore({ ctx: ctxFixture(true), player: playerFor(first, null), meta_details: metaDetails([{ name: 'Torrentio', url: first.stream.url! }]) });
    render(
      <CoreProvider core={fake.core}>
        <PlayerPage />
      </CoreProvider>,
    );
    await act(async () => {});
    await act(async () => vi.advanceTimersByTime(20_000));
    // FrostStream answers while Torrentio is still loading.
    act(() => fake.setState('meta_details', metaDetails([{ name: 'Torrentio', url: first.stream.url! }, { name: 'FrostStream', url: second.stream.url! }])));
    await act(async () => {});
    await act(async () => vi.advanceTimersByTime(10_000));
    expect(loads(fake)).toEqual([first.stream.url, second.stream.url]);
    expect(document.querySelector('.player__notice')?.textContent).toBe('Torrentio não iniciou em 30 s. Tentando FrostStream…');
  });

  it('closing the player cancels the switch', async () => {
    const { fake, view } = mount(playerFor(first, null));
    await act(async () => {});
    view.unmount();
    await act(async () => vi.advanceTimersByTime(60_000));
    expect(loads(fake)).toEqual([first.stream.url]);
  });
});
