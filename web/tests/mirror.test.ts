import { describe, expect, it, vi } from 'vitest';
import { detectMirrorPlatform, mirrorGuides, startMirroring, type MirrorVideo } from '../src/features/player/screenMirror';

const video = (extra: Partial<MirrorVideo>) => Object.assign(document.createElement('video'), extra) as MirrorVideo;
const error = (name: string) => Object.assign(new Error(name), { name });

describe('sending the video to a TV', () => {
  it('uses AirPlay on Safari', async () => {
    const picker = vi.fn();
    expect(await startMirroring(video({ webkitShowPlaybackTargetPicker: picker }))).toBe('airplay');
    expect(picker).toHaveBeenCalled();
  });

  it('uses the Remote Playback picker on Chrome and Edge', async () => {
    const prompt = vi.fn(() => Promise.resolve());
    expect(await startMirroring(video({ remote: { prompt } }))).toBe('remote');
  });

  it('does nothing more when the person closes the picker', async () => {
    const prompt = vi.fn(() => Promise.reject(error('NotAllowedError')));
    expect(await startMirroring(video({ remote: { prompt } }))).toBe('cancelled');
  });

  it('falls back to mirroring instructions when the stream or browser is not supported', async () => {
    const prompt = vi.fn(() => Promise.reject(error('NotSupportedError')));
    expect(await startMirroring(video({ remote: { prompt } }))).toBe('instructions');
    expect(await startMirroring(video({}))).toBe('instructions');
    expect(await startMirroring(null)).toBe('instructions');
  });
});

describe('mirroring instructions', () => {
  it('shows the current device first', () => {
    expect(detectMirrorPlatform('Mozilla/5.0 (Linux; Android 14; motorola edge 40 neo)')).toBe('android');
    expect(detectMirrorPlatform('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)')).toBe('iphone');
    expect(detectMirrorPlatform('Mozilla/5.0 (Windows NT 10.0; Win64; x64)')).toBe('windows');
    expect(mirrorGuides('iphone')[0].title).toBe('iPhone ou iPad');
    expect(mirrorGuides('other')).toHaveLength(4);
  });
});
