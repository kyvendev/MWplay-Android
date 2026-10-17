// Player lifecycle with the video engine replaced by a test double (jsdom has no media pipeline).
// Real playback of an authorized test stream was validated in a browser (see web/README.md).
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CoreProvider } from '../src/services/stremio/CoreProvider';
import { PlayerPage, describePlaybackError } from '../src/features/player/PlayerPage';
import { setPlaybackRequest } from '../src/features/player/playbackRequest';
import { createFakeCore } from './fakeCore';

const engines: { load: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> }[] = [];

vi.mock('../src/features/player/videoEngine', () => ({
  EXTERNAL_TRACK_LABEL: 'mw-external',
  VideoEngine: class {
    load = vi.fn(() => Promise.resolve());
    destroy = vi.fn();
    audioTracks = () => [];
    embeddedSubtitles = () => [];
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
    expect(describePlaybackError(true, null, { infoHash: 'a'.repeat(40) })).toContain('servidor de streaming');
    expect(describePlaybackError(false, { kind: 'network' }, { infoHash: 'a'.repeat(40) })).toContain('servidor de streaming');
    expect(describePlaybackError(false, { kind: 'unsupported-format', format: 'file' }, stream)).toContain('MKV');
    expect(describePlaybackError(false, { kind: 'unsupported-format', format: 'dash' }, stream)).toContain('DASH');
    expect(describePlaybackError(false, { kind: 'network' }, stream)).toContain('rede');
    expect(describePlaybackError(false, null, stream)).toBeNull();
  });
});
