// Player lifecycle with the video engine replaced by a test double (jsdom has no media pipeline).
// Real playback of an authorized test stream was validated in a browser (see web/README.md).
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import { PlayerPage, describePlaybackError } from '../src/features/player/PlayerPage';
import { setPlaybackRequest } from '../src/features/player/playbackRequest';
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

const stream = { url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8' };
const request = {
  stream,
  streamRequest: { base: 'https://addon/manifest.json', path: { resource: 'stream', type: 'movie', id: 'tt1', extra: [] } },
  metaRequest: { base: 'https://meta/manifest.json', path: { resource: 'meta', type: 'movie', id: 'tt1', extra: [] } },
  title: 'Filme de teste',
  subtitle: null,
  backdrop: null,
  returnTo: { type: 'movie', id: 'tt1', videoId: null },
};

beforeEach(() => {
  engines.length = 0;
  HTMLMediaElement.prototype.play = vi.fn(() => Promise.resolve());
  HTMLMediaElement.prototype.pause = vi.fn();
  window.location.hash = '#/player';
});

afterEach(cleanup);

describe('player lifecycle', () => {
  it('loads the stream into the core, plays the converted URL with the saved position and releases everything on exit', async () => {
    setPlaybackRequest(request);
    const fake = createFakeCore({ player: { selected: null, stream: null, subtitles: [], nextVideo: null, libraryItem: null } });
    const view = render(
      <CoreProvider core={fake.core}>
        <PlayerPage />
      </CoreProvider>,
    );
    await act(async () => {});
    expect(fake.dispatched[0].action).toMatchObject({ action: 'Load', args: { model: 'Player', args: { stream } } });
    expect(engines).toHaveLength(1);
    expect(screen.getByText('Preparando a reprodução')).toBeTruthy();

    // The core finishes converting the stream; progress says the user stopped at 42 s.
    act(() =>
      fake.setState('player', {
        selected: { stream, streamRequest: request.streamRequest, metaRequest: request.metaRequest, subtitlesPath: null },
        stream: { type: 'Ready', content: { url: stream.url } },
        subtitles: [],
        nextVideo: null,
        libraryItem: { _id: 'tt1', state: { timeOffset: 42_000, video_id: 'tt1' } },
      }),
    );
    await act(async () => {});
    expect(engines[0].load).toHaveBeenCalledWith(stream.url, 42_000);

    // Progress updates re-read the model but must not reload the video or create a second engine.
    act(() => fake.setState("player", { selected: { stream }, stream: { type: 'Ready', content: { url: stream.url } }, subtitles: [], nextVideo: null, libraryItem: { _id: 'tt1', state: { timeOffset: 50_000, video_id: 'tt1' } } }));
    await act(async () => {});
    expect(engines).toHaveLength(1);
    expect(engines[0].load).toHaveBeenCalledTimes(1);

    view.unmount();
    expect(engines[0].destroy).toHaveBeenCalledTimes(1);
    await act(async () => {});
    expect(fake.dispatched.at(-1)?.action).toEqual({ action: 'Unload' });
  });

  it('ignores a Player model that still belongs to the previous content', async () => {
    setPlaybackRequest(request);
    const fake = createFakeCore({
      player: { selected: { stream: { url: 'https://old/video.mp4' } }, stream: { type: 'Ready', content: { url: 'https://old/video.mp4' } }, subtitles: [], nextVideo: null, libraryItem: null },
    });
    render(
      <CoreProvider core={fake.core}>
        <PlayerPage />
      </CoreProvider>,
    );
    await act(async () => {});
    expect(engines[0].load).not.toHaveBeenCalled();
  });
});

describe('playback errors', () => {
  it('explains what the user can do', () => {
    expect(describePlaybackError(true, null, { infoHash: 'a'.repeat(40) })).toContain('app MW Play');
    expect(describePlaybackError(false, { kind: 'network' }, { infoHash: 'a'.repeat(40) })).toContain('app MW Play');
    expect(describePlaybackError(false, { kind: 'unsupported-format', format: 'file' }, stream)).toContain('MKV');
    expect(describePlaybackError(false, { kind: 'unsupported-format', format: 'dash' }, stream)).toContain('DASH');
    expect(describePlaybackError(false, { kind: 'network' }, stream)).toContain('rede');
    expect(describePlaybackError(false, null, stream)).toBeNull();
  });
});

describe('player options', () => {
  const readyPlayer = (extra: Record<string, unknown> = {}) => ({
    selected: { stream, streamRequest: request.streamRequest, metaRequest: request.metaRequest, subtitlesPath: null },
    stream: { type: 'Ready', content: { url: stream.url } },
    subtitles: [],
    nextVideo: null,
    libraryItem: null,
    ...extra,
  });

  const streamStates = (fake: ReturnType<typeof createFakeCore>) =>
    fake.dispatched
      .map((entry) => entry.action as { action: string; args?: { action?: string; args?: { state?: unknown } } })
      .filter((action) => action.action === 'Player' && action.args?.action === 'StreamStateChanged')
      .map((action) => action.args!.args!.state);

  const mount = (player: unknown, settings: Record<string, unknown> = {}) => {
    setPlaybackRequest(request);
    const ctx = ctxFixture(true);
    ctx.profile.settings = { ...ctx.profile.settings, ...settings };
    const fake = createFakeCore({ ctx, player });
    const view = render(
      <CoreProvider core={fake.core}>
        <PlayerPage />
      </CoreProvider>,
    );
    return { fake, view };
  };

  afterEach(() => vi.unstubAllGlobals());

  it('sends the video to a TV with the browser picker when it can', async () => {
    const { view } = mount(readyPlayer());
    await act(async () => {});
    const prompt = vi.fn(() => Promise.resolve());
    Object.defineProperty(view.container.querySelector('video')!, 'remote', { value: { prompt }, configurable: true });
    fireEvent.click(screen.getByRole('button', { name: 'Transmitir' }));
    fireEvent.click(screen.getByRole('button', { name: 'Chromecast ou Smart TV' }));
    await act(async () => {});
    expect(prompt).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog', { name: 'Espelhar na TV' })).toBeNull();
  });

  it('explains how to mirror the screen when the browser cannot send the video', async () => {
    mount(readyPlayer());
    await act(async () => {});
    fireEvent.click(screen.getByRole('button', { name: 'Transmitir' }));
    // No picker in this browser: the options are another app (Android only), the link or mirroring.
    expect(screen.queryByRole('button', { name: 'Chromecast ou Smart TV' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Espelhar a tela inteira' }));
    await act(async () => {});
    const dialog = screen.getByRole('dialog', { name: 'Espelhar na TV' });
    expect(dialog.textContent).toContain('Transmitir');
    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }));
    expect(screen.queryByRole('dialog', { name: 'Espelhar na TV' })).toBeNull();
  });

  it('offers Web Video Caster and other apps on Android and never exposes the video link', async () => {
    const userAgent = Object.getOwnPropertyDescriptor(Navigator.prototype, 'userAgent')!;
    Object.defineProperty(navigator, 'userAgent', { value: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/130', configurable: true });
    try {
      mount(readyPlayer());
      await act(async () => {});
      fireEvent.click(screen.getByRole('button', { name: 'Transmitir' }));
      expect(screen.getByRole('button', { name: /Web Video Caster/ })).toBeTruthy();
      expect(screen.getByRole('button', { name: /Outro app/ })).toBeTruthy();
      expect(screen.queryByRole('button', { name: /Copiar link/ })).toBeNull();
      expect(document.body.textContent).not.toContain(stream.url);
    } finally {
      delete (navigator as unknown as Record<string, unknown>).userAgent;
      Object.defineProperty(Navigator.prototype, 'userAgent', userAgent);
    }
  });

  it('shows "Pular abertura" only inside a marked opening and skips to its end', async () => {
    const episodeRequest = { ...request, streamRequest: { ...request.streamRequest, path: { ...request.streamRequest.path, type: 'series', id: 'tt1:1:1' } }, returnTo: { type: 'series', id: 'tt1', videoId: 'tt1:1:1' } };
    localStorage.setItem('mwplay.web.introMarks', JSON.stringify({ 'e:series|tt1|tt1:1:1': [60_000, 150_000] }));
    setPlaybackRequest(episodeRequest);
    const fake = createFakeCore({ ctx: ctxFixture(true), player: { ...readyPlayer(), selected: { stream, streamRequest: episodeRequest.streamRequest, metaRequest: request.metaRequest, subtitlesPath: null } } });
    const view = render(
      <CoreProvider core={fake.core}>
        <PlayerPage />
      </CoreProvider>,
    );
    await act(async () => {});
    const videoElement = view.container.querySelector('video')!;
    let current = 30;
    Object.defineProperty(videoElement, 'duration', { value: 1440, configurable: true });
    Object.defineProperty(videoElement, 'currentTime', { get: () => current, set: (value: number) => (current = value), configurable: true });
    fireEvent(videoElement, new Event('durationchange'));
    fireEvent(videoElement, new Event('timeupdate'));
    expect(screen.queryByRole('button', { name: /Pular abertura/ })).toBeNull();

    current = 70;
    fireEvent(videoElement, new Event('timeupdate'));
    fireEvent.click(await screen.findByRole('button', { name: /Pular abertura/ }));
    expect(current).toBe(150);
    expect(screen.queryByRole('button', { name: /Pular abertura/ })).toBeNull();
    await act(async () => {});
    const seeks = fake.dispatched.map((entry) => entry.action as { action: string; args?: { action?: string; args?: { time?: number } } }).filter((action) => action.args?.action === 'Seek');
    expect(seeks.at(-1)?.args?.args?.time).toBe(150_000);
    localStorage.clear();
  });

  it('restores the saved speed and saves changes merged into the stream state', async () => {
    const { fake, view } = mount(readyPlayer({ streamState: { subtitleDelay: 500, playbackSpeed: 1.5 } }));
    await act(async () => {});
    const videoElement = view.container.querySelector('video')!;
    expect(videoElement.playbackRate).toBe(1.5);

    fireEvent.click(screen.getByRole('button', { name: 'Velocidade 1,5x' }));
    await act(async () => {});
    expect(videoElement.playbackRate).toBe(2);
    expect(streamStates(fake).at(-1)).toEqual({ subtitleDelay: 500, playbackSpeed: 2 });
  });

  it('cycles the aspect mode like the TV player', async () => {
    const { view } = mount(readyPlayer());
    await act(async () => {});
    const videoElement = view.container.querySelector('video')!;
    expect(videoElement.style.objectFit).toBe('contain');
    fireEvent.click(screen.getByRole('button', { name: 'Proporção: Ajustar' }));
    expect(videoElement.style.objectFit).toBe('fill');
    fireEvent.click(screen.getByRole('button', { name: 'Proporção: Esticar' }));
    expect(videoElement.style.objectFit).toBe('cover');
  });

  it('turns on the preferred subtitle language, lets the user switch language and tune the subtitles', async () => {
    const fetchImpl = vi.fn(async () => new Response('1\n00:00:01,000 --> 00:00:02,000\nOlá\n'));
    vi.stubGlobal('fetch', fetchImpl);
    URL.createObjectURL = vi.fn(() => 'blob:subtitle');
    URL.revokeObjectURL = vi.fn();
    const subtitles = [
      { id: 'sub-en', lang: 'eng', url: 'https://subs.example/en.srt' },
      { id: 'sub-pt', lang: 'por', url: 'https://subs.example/pt.srt' },
    ];
    const { fake, view } = mount(readyPlayer({ subtitles }), { subtitlesLanguage: 'por' });
    await act(async () => {});
    // Auto-selection waits for playback to start, then fetches the Portuguese subtitle.
    fireEvent(view.container.querySelector('video')!, new Event('playing'));
    await act(async () => {});
    expect(fetchImpl).toHaveBeenCalledWith('https://subs.example/pt.srt');
    expect(streamStates(fake)).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'Legendas' }));
    expect(screen.getByRole('radio', { name: /Português/ }).getAttribute('aria-checked')).toBe('true');
    expect(screen.getByRole('radio', { name: 'Opção 1' }).getAttribute('aria-checked')).toBe('true');

    fireEvent.click(screen.getByRole('radio', { name: /Inglês/ }));
    await act(async () => {});
    expect(fetchImpl).toHaveBeenLastCalledWith('https://subs.example/en.srt');
    expect(streamStates(fake).at(-1)).toEqual({ subtitleTrack: { id: 'ext:sub-en', embedded: false, language: 'eng' } });

    fireEvent.click(screen.getByRole('button', { name: 'Aumentar atraso' }));
    fireEvent.click(screen.getByRole('button', { name: 'Aumentar tamanho' }));
    fireEvent.click(screen.getByRole('button', { name: 'Diminuir posição' }));
    await act(async () => {});
    expect(screen.getByText('+0,25 s')).toBeTruthy();
    expect(screen.getByText('125%')).toBeTruthy();
    expect(screen.getByText('0%')).toBeTruthy();
    expect(streamStates(fake).at(-1)).toEqual({
      subtitleTrack: { id: 'ext:sub-en', embedded: false, language: 'eng' },
      subtitleDelay: 250,
      subtitleSize: 125,
      subtitleOffset: 0,
    });

    fireEvent.click(screen.getByRole('radio', { name: 'Desativadas' }));
    await act(async () => {});
    expect(streamStates(fake).at(-1)).toMatchObject({ subtitleTrack: null });
  });
});
